package br.com.equipe.farmarede.dados.marts;

import br.com.equipe.farmarede.dados.pipeline.ContextoExecucao;
import br.com.equipe.farmarede.dados.pipeline.EtapaPipeline;
import br.com.equipe.farmarede.dados.pipeline.ResultadoEtapa;
import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.StringJoiner;

/**
 * Etapa 30 (CAMADA AGREGADA): {@code curated.*} → {@code marts.*} (Data Marts de Vendas e Estoque).
 *
 * <p>Recalcula de forma idempotente, numa única transação, as tabelas pré-agregadas de vendas e
 * estoque a partir do modelo estrela em {@code curated}.
 */
@ApplicationScoped
public class AgregacaoMartsEtapa implements EtapaPipeline {

  private static final String SQL_TRAVA =
      "SELECT pg_advisory_xact_lock(hashtext('agregacao-marts'))";

  private static final String SQL_LIMPAR_MARTS =
      """
      TRUNCATE TABLE
          marts.vendas_diarias,
          marts.vendas_top_produtos,
          marts.estoque_ruptura,
          marts.estoque_giro
      """;

  private static final String SQL_VENDAS_DIARIAS =
      """
      INSERT INTO marts.vendas_diarias (
          data, loja_codigo, loja_nome, loja_uf, canal,
          qtd_vendas, qtd_itens, receita_bruta, receita_liquida, receita_cancelada,
          ticket_medio, atualizado_em
      )
      SELECT
          dt.data,
          dl.codigo,
          dl.nome,
          dl.uf,
          fv.canal,
          count(DISTINCT CASE WHEN fv.status <> 'CANCELADA' THEN fv.id_venda_origem END) AS qtd_vendas,
          coalesce(sum(CASE WHEN fv.status <> 'CANCELADA' THEN fv.quantidade ELSE 0 END), 0) AS qtd_itens,
          coalesce(sum(CASE WHEN fv.status <> 'CANCELADA' THEN fv.valor_bruto ELSE 0.00 END), 0.00) AS receita_bruta,
          coalesce(sum(fv.valor_liquido), 0.00) AS receita_liquida,
          coalesce(sum(fv.valor_cancelado), 0.00) AS receita_cancelada,
          CASE
              WHEN count(DISTINCT CASE WHEN fv.status <> 'CANCELADA' THEN fv.id_venda_origem END) > 0
              THEN round(
                  coalesce(sum(fv.valor_liquido), 0.00)
                      / count(DISTINCT CASE WHEN fv.status <> 'CANCELADA' THEN fv.id_venda_origem END),
                  2)
              ELSE 0.00
          END AS ticket_medio,
          now()
      FROM curated.fato_venda fv
      JOIN curated.dim_tempo dt ON dt.id = fv.tempo_id
      JOIN curated.dim_loja dl ON dl.id = fv.loja_id
      GROUP BY dt.data, dl.codigo, dl.nome, dl.uf, fv.canal
      """;

  private static final String SQL_VENDAS_TOP_PRODUTOS =
      """
      WITH agregado AS (
          SELECT
              to_char(dt.data, 'YYYY-MM') AS periodo,
              dp.categoria,
              dp.sku,
              dp.nome AS produto_nome,
              dp.fabricante,
              count(DISTINCT fv.id_venda_origem) AS qtd_vendas,
              sum(fv.quantidade) AS qtd_vendida,
              sum(fv.valor_liquido) AS receita_liquida
          FROM curated.fato_venda fv
          JOIN curated.dim_tempo dt ON dt.id = fv.tempo_id
          JOIN curated.dim_produto dp ON dp.id = fv.produto_id
          WHERE fv.status <> 'CANCELADA'
          GROUP BY to_char(dt.data, 'YYYY-MM'), dp.categoria, dp.sku, dp.nome, dp.fabricante
      )
      INSERT INTO marts.vendas_top_produtos (
          periodo, categoria, sku, produto_nome, fabricante,
          qtd_vendas, qtd_vendida, receita_liquida,
          ranking_categoria, ranking_geral, atualizado_em
      )
      SELECT
          periodo,
          categoria,
          sku,
          produto_nome,
          fabricante,
          qtd_vendas,
          qtd_vendida,
          receita_liquida,
          row_number() OVER (
              PARTITION BY periodo, categoria
              ORDER BY receita_liquida DESC, qtd_vendida DESC, sku ASC
          )::int,
          row_number() OVER (
              PARTITION BY periodo
              ORDER BY receita_liquida DESC, qtd_vendida DESC, sku ASC
          )::int,
          now()
      FROM agregado
      """;

  private static final String SQL_ESTOQUE_RUPTURA =
      """
      WITH ultimo_saldo AS (
          SELECT DISTINCT ON (fe.loja_id, fe.produto_id)
              dt.data AS data_referencia,
              dl.codigo AS loja_codigo,
              dl.nome AS loja_nome,
              dl.uf AS loja_uf,
              dp.sku,
              dp.nome AS produto_nome,
              dp.categoria,
              fe.quantidade_saldo,
              fe.estoque_minimo,
              fe.em_ruptura
          FROM curated.fato_estoque fe
          JOIN curated.dim_tempo dt ON dt.id = fe.tempo_id
          JOIN curated.dim_loja dl ON dl.id = fe.loja_id
          JOIN curated.dim_produto dp ON dp.id = fe.produto_id
          ORDER BY fe.loja_id, fe.produto_id, dt.data DESC
      )
      INSERT INTO marts.estoque_ruptura (
          data_referencia, loja_codigo, loja_nome, loja_uf,
          sku, produto_nome, categoria, quantidade_saldo, estoque_minimo, deficit, atualizado_em
      )
      SELECT
          data_referencia,
          loja_codigo,
          loja_nome,
          loja_uf,
          sku,
          produto_nome,
          categoria,
          quantidade_saldo,
          estoque_minimo,
          estoque_minimo - quantidade_saldo,
          now()
      FROM ultimo_saldo
      WHERE em_ruptura
      """;

  private static final String SQL_ESTOQUE_GIRO =
      """
      WITH janela_base AS (
          SELECT min(data) AS min_data FROM curated.dim_tempo
      ),
      ultimo_saldo AS (
          SELECT DISTINCT ON (fe.loja_id, fe.produto_id)
              fe.loja_id,
              fe.produto_id,
              dt.data AS data_referencia,
              dl.codigo AS loja_codigo,
              dl.nome AS loja_nome,
              dl.uf AS loja_uf,
              dp.sku,
              dp.nome AS produto_nome,
              dp.categoria,
              fe.quantidade_saldo,
              fe.estoque_minimo
          FROM curated.fato_estoque fe
          JOIN curated.dim_tempo dt ON dt.id = fe.tempo_id
          JOIN curated.dim_loja dl ON dl.id = fe.loja_id
          JOIN curated.dim_produto dp ON dp.id = fe.produto_id
          ORDER BY fe.loja_id, fe.produto_id, dt.data DESC
      ),
      vendas_janela AS (
          SELECT
              s.loja_id,
              s.produto_id,
              coalesce(sum(fv.quantidade), 0) AS qtd_vendida_30d
          FROM ultimo_saldo s
          LEFT JOIN (
              curated.fato_venda fv
              JOIN curated.dim_tempo dt ON dt.id = fv.tempo_id
          ) ON fv.loja_id = s.loja_id
           AND fv.produto_id = s.produto_id
           AND fv.status <> 'CANCELADA'
           AND dt.data BETWEEN (s.data_referencia - 29) AND s.data_referencia
          GROUP BY s.loja_id, s.produto_id
      ),
      metricas AS (
          SELECT
              s.data_referencia,
              s.loja_codigo,
              s.loja_nome,
              s.loja_uf,
              s.sku,
              s.produto_nome,
              s.categoria,
              s.quantidade_saldo,
              s.estoque_minimo,
              coalesce(v.qtd_vendida_30d, 0) AS qtd_vendida_30d,
              greatest(1, least(30, (s.data_referencia - coalesce(j.min_data, s.data_referencia)) + 1)) AS dias_janela
          FROM ultimo_saldo s
          CROSS JOIN janela_base j
          LEFT JOIN vendas_janela v
              ON v.loja_id = s.loja_id
             AND v.produto_id = s.produto_id
      )
      INSERT INTO marts.estoque_giro (
          data_referencia, loja_codigo, loja_nome, loja_uf,
          sku, produto_nome, categoria, quantidade_saldo, estoque_minimo,
          qtd_vendida_30d, media_diaria_vendas, giro_estoque, dias_cobertura, atualizado_em
      )
      SELECT
          data_referencia,
          loja_codigo,
          loja_nome,
          loja_uf,
          sku,
          produto_nome,
          categoria,
          quantidade_saldo,
          estoque_minimo,
          qtd_vendida_30d,
          round(qtd_vendida_30d::numeric / dias_janela, 2) AS media_diaria_vendas,
          CASE
              WHEN quantidade_saldo > 0
              THEN round(qtd_vendida_30d::numeric / quantidade_saldo, 2)
              ELSE NULL
          END AS giro_estoque,
          CASE
              WHEN qtd_vendida_30d > 0
              THEN round(quantidade_saldo::numeric / (qtd_vendida_30d::numeric / dias_janela), 1)
              ELSE NULL
          END AS dias_cobertura,
          now()
      FROM metricas
      """;

  private final AgroalDataSource dw;

  public AgregacaoMartsEtapa(final AgroalDataSource dw) {
    this.dw = dw;
  }

  @Override
  public String nome() {
    return "agregacao-marts";
  }

  @Override
  public int ordem() {
    return 30;
  }

  @Override
  public ResultadoEtapa executar(final ContextoExecucao contexto) {
    return QuarkusTransaction.requiringNew().call(() -> agregar(contexto));
  }

  private ResultadoEtapa agregar(final ContextoExecucao contexto) {
    try (Connection con = dw.getConnection();
        PreparedStatement trava = con.prepareStatement(SQL_TRAVA);
        Statement st = con.createStatement()) {
      trava.execute();
      st.execute(SQL_LIMPAR_MARTS);

      final int vendasDiarias = executarInsert(con, SQL_VENDAS_DIARIAS);
      final int topProdutos = executarInsert(con, SQL_VENDAS_TOP_PRODUTOS);
      final int estoqueRuptura = executarInsert(con, SQL_ESTOQUE_RUPTURA);
      final int estoqueGiro = executarInsert(con, SQL_ESTOQUE_GIRO);

      final long total = (long) vendasDiarias + topProdutos + estoqueRuptura + estoqueGiro;
      final String detalhe =
          new StringJoiner(", ")
              .add("vendas_diarias=" + vendasDiarias)
              .add("vendas_top_produtos=" + topProdutos)
              .add("estoque_ruptura=" + estoqueRuptura)
              .add("estoque_giro=" + estoqueGiro)
              .toString();

      Log.infof("Agregação marts lote %s: %d registros (%s)", contexto.loteId(), total, detalhe);
      return new ResultadoEtapa(total, detalhe);
    } catch (SQLException e) {
      throw new AgregacaoException("Falha ao agregar curated -> marts", e);
    }
  }

  private static int executarInsert(final Connection con, final String sql) throws SQLException {
    try (PreparedStatement st = con.prepareStatement(sql)) {
      return st.executeUpdate();
    }
  }

  /** Erro de agregação na camada marts; o orquestrador registra como FALHA da etapa. */
  static final class AgregacaoException extends RuntimeException {
    AgregacaoException(final String mensagem, final Throwable causa) {
      super(mensagem + ": " + causa.getMessage(), causa);
    }
  }
}
