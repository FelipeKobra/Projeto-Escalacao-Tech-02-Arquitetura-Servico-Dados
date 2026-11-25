package br.com.equipe.farmarede.dados.transformacao;

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
import java.util.StringJoiner;

/**
 * Etapa 20 (PROCESSAMENTO): {@code raw.*} → {@code curated.*} (modelo estrela).
 *
 * <p>Seleciona a versão mais recente de cada {@code chave_origem} na camada raw, aplica limpeza,
 * tipagem e pseudonimização LGPD e realiza upsert idempotente nas dimensões e fatos. Vendas {@code
 * CANCELADA} são mantidas para rastreabilidade, porém com {@code valor_liquido = 0}.
 */
@ApplicationScoped
public class TransformacaoCuradaEtapa implements EtapaPipeline {

  private static final String SQL_TRAVA =
      "SELECT pg_advisory_xact_lock(hashtext('transformacao-curated'))";

  private static final String SQL_DIM_TEMPO =
      """
      WITH datas AS (
          SELECT DISTINCT ((payload->>'realizada_em')::timestamptz AT TIME ZONE 'America/Sao_Paulo')::date AS data
          FROM (
              SELECT DISTINCT ON (chave_origem) payload
              FROM raw.venda
              ORDER BY chave_origem, atualizado_na_origem DESC, id DESC
          ) v
          WHERE NULLIF(trim(payload->>'realizada_em'), '') IS NOT NULL
          UNION
          SELECT DISTINCT ((payload->>'ocorrido_em')::timestamptz AT TIME ZONE 'America/Sao_Paulo')::date AS data
          FROM (
              SELECT DISTINCT ON (chave_origem) payload
              FROM raw.movimento_estoque
              ORDER BY chave_origem, atualizado_na_origem DESC, id DESC
          ) m
          WHERE NULLIF(trim(payload->>'ocorrido_em'), '') IS NOT NULL
          UNION
          SELECT DISTINCT (coalesce(NULLIF(trim(payload->>'atualizado_em'), '')::timestamptz, atualizado_na_origem)
              AT TIME ZONE 'America/Sao_Paulo')::date AS data
          FROM (
              SELECT DISTINCT ON (chave_origem) payload, atualizado_na_origem
              FROM raw.estoque_atual
              ORDER BY chave_origem, atualizado_na_origem DESC, id DESC
          ) e
      )
      INSERT INTO curated.dim_tempo (
          data, ano, mes, dia, trimestre, dia_semana, nome_mes, nome_dia_semana, fim_de_semana
      )
      SELECT
          d.data,
          extract(year FROM d.data)::smallint,
          extract(month FROM d.data)::smallint,
          extract(day FROM d.data)::smallint,
          extract(quarter FROM d.data)::smallint,
          extract(isodow FROM d.data)::smallint,
          (ARRAY[
              'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
              'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'
          ])[extract(month FROM d.data)::int],
          (ARRAY[
              'Segunda-feira', 'Terça-feira', 'Quarta-feira', 'Quinta-feira',
              'Sexta-feira', 'Sábado', 'Domingo'
          ])[extract(isodow FROM d.data)::int],
          extract(isodow FROM d.data) IN (6, 7)
      FROM datas d
      ON CONFLICT (data) DO NOTHING
      """;

  private static final String SQL_DIM_LOJA =
      """
      WITH ultima_loja AS (
          SELECT DISTINCT ON (chave_origem) payload
          FROM raw.loja
          ORDER BY chave_origem, atualizado_na_origem DESC, id DESC
      )
      INSERT INTO curated.dim_loja (id_origem, codigo, nome, cidade, uf, tipo, atualizado_em)
      SELECT
          (payload->>'id')::bigint,
          upper(trim(payload->>'codigo')),
          regexp_replace(trim(payload->>'nome'), '\\s+', ' ', 'g'),
          regexp_replace(trim(payload->>'cidade'), '\\s+', ' ', 'g'),
          upper(trim(payload->>'uf')),
          upper(trim(payload->>'tipo')),
          now()
      FROM ultima_loja
      ON CONFLICT (id_origem) DO UPDATE SET
          codigo = EXCLUDED.codigo,
          nome = EXCLUDED.nome,
          cidade = EXCLUDED.cidade,
          uf = EXCLUDED.uf,
          tipo = EXCLUDED.tipo,
          atualizado_em = now()
      """;

  private static final String SQL_DIM_PRODUTO =
      """
      WITH ultimo_produto AS (
          SELECT DISTINCT ON (chave_origem) payload
          FROM raw.produto
          ORDER BY chave_origem, atualizado_na_origem DESC, id DESC
      )
      INSERT INTO curated.dim_produto (
          id_origem, sku, nome, categoria, fabricante, preco_unitario, controlado, atualizado_em
      )
      SELECT
          (payload->>'id')::bigint,
          upper(trim(payload->>'sku')),
          regexp_replace(trim(payload->>'nome'), '\\s+', ' ', 'g'),
          regexp_replace(trim(payload->>'categoria'), '\\s+', ' ', 'g'),
          regexp_replace(trim(payload->>'fabricante'), '\\s+', ' ', 'g'),
          (payload->>'preco_unitario')::numeric(10, 2),
          coalesce((payload->>'controlado')::boolean, false),
          now()
      FROM ultimo_produto
      ON CONFLICT (id_origem) DO UPDATE SET
          sku = EXCLUDED.sku,
          nome = EXCLUDED.nome,
          categoria = EXCLUDED.categoria,
          fabricante = EXCLUDED.fabricante,
          preco_unitario = EXCLUDED.preco_unitario,
          controlado = EXCLUDED.controlado,
          atualizado_em = now()
      """;

  private static final String SQL_DIM_CLIENTE =
      """
      WITH ultimo_cliente AS (
          SELECT DISTINCT ON (chave_origem) payload
          FROM raw.cliente
          ORDER BY chave_origem, atualizado_na_origem DESC, id DESC
      )
      INSERT INTO curated.dim_cliente (
          id_origem, chave_pseudonimo, cidade, uf, faixa_etaria, atualizado_em
      )
      SELECT
          (payload->>'id')::bigint,
          encode(
              sha256(
                  convert_to(
                      'farmarede-lgpd:' || (payload->>'id') || ':' || lower(trim(coalesce(payload->>'email', ''))),
                      'UTF8')),
              'hex'),
          regexp_replace(trim(payload->>'cidade'), '\\s+', ' ', 'g'),
          upper(trim(payload->>'uf')),
          CASE
              WHEN NULLIF(trim(payload->>'data_nascimento'), '') IS NULL THEN 'NAO_INFORMADA'
              WHEN extract(year FROM age(current_date, (payload->>'data_nascimento')::date)) < 18 THEN 'MENOR_18'
              WHEN extract(year FROM age(current_date, (payload->>'data_nascimento')::date)) BETWEEN 18 AND 29 THEN '18-29'
              WHEN extract(year FROM age(current_date, (payload->>'data_nascimento')::date)) BETWEEN 30 AND 44 THEN '30-44'
              WHEN extract(year FROM age(current_date, (payload->>'data_nascimento')::date)) BETWEEN 45 AND 59 THEN '45-59'
              ELSE '60+'
          END,
          now()
      FROM ultimo_cliente
      ON CONFLICT (id_origem) DO UPDATE SET
          chave_pseudonimo = EXCLUDED.chave_pseudonimo,
          cidade = EXCLUDED.cidade,
          uf = EXCLUDED.uf,
          faixa_etaria = EXCLUDED.faixa_etaria,
          atualizado_em = now()
      """;

  private static final String SQL_FATO_VENDA =
      """
      WITH ultima_venda AS (
          SELECT DISTINCT ON (chave_origem) payload
          FROM raw.venda
          ORDER BY chave_origem, atualizado_na_origem DESC, id DESC
      ),
      ultimo_item AS (
          SELECT DISTINCT ON (chave_origem) payload
          FROM raw.item_venda
          ORDER BY chave_origem, atualizado_na_origem DESC, id DESC
      ),
      itens_enriquecidos AS (
          SELECT
              (i.payload->>'id')::bigint AS id_item_origem,
              (v.payload->>'id')::bigint AS id_venda_origem,
              dt.id AS tempo_id,
              dl.id AS loja_id,
              dp.id AS produto_id,
              dc.id AS cliente_id,
              upper(trim(v.payload->>'canal')) AS canal,
              upper(trim(v.payload->>'status')) AS status,
              upper(trim(v.payload->>'forma_pagamento')) AS forma_pagamento,
              (i.payload->>'quantidade')::int AS quantidade,
              (i.payload->>'preco_unitario')::numeric(10, 2) AS preco_unitario,
              coalesce((i.payload->>'desconto')::numeric(10, 2), 0.00) AS desconto,
              round((i.payload->>'quantidade')::int * (i.payload->>'preco_unitario')::numeric(10, 2), 2) AS valor_bruto,
              (v.payload->>'realizada_em')::timestamptz AS realizada_em
          FROM ultimo_item i
          JOIN ultima_venda v
              ON (v.payload->>'id')::bigint = (i.payload->>'venda_id')::bigint
          JOIN curated.dim_tempo dt
              ON dt.data = ((v.payload->>'realizada_em')::timestamptz AT TIME ZONE 'America/Sao_Paulo')::date
          JOIN curated.dim_loja dl
              ON dl.id_origem = (v.payload->>'loja_id')::bigint
          JOIN curated.dim_produto dp
              ON dp.id_origem = (i.payload->>'produto_id')::bigint
          LEFT JOIN curated.dim_cliente dc
              ON NULLIF(trim(v.payload->>'cliente_id'), '') IS NOT NULL
             AND dc.id_origem = (v.payload->>'cliente_id')::bigint
      )
      INSERT INTO curated.fato_venda (
          id_item_origem, id_venda_origem, tempo_id, loja_id, produto_id, cliente_id,
          canal, status, forma_pagamento, quantidade, preco_unitario, desconto,
          valor_bruto, valor_liquido, valor_cancelado, realizada_em, atualizado_em
      )
      SELECT
          id_item_origem,
          id_venda_origem,
          tempo_id,
          loja_id,
          produto_id,
          cliente_id,
          canal,
          status,
          forma_pagamento,
          quantidade,
          preco_unitario,
          desconto,
          valor_bruto,
          CASE WHEN status = 'CANCELADA' THEN 0.00 ELSE greatest(valor_bruto - desconto, 0.00) END,
          CASE WHEN status = 'CANCELADA' THEN greatest(valor_bruto - desconto, 0.00) ELSE 0.00 END,
          realizada_em,
          now()
      FROM itens_enriquecidos
      ON CONFLICT (id_item_origem) DO UPDATE SET
          id_venda_origem = EXCLUDED.id_venda_origem,
          tempo_id = EXCLUDED.tempo_id,
          loja_id = EXCLUDED.loja_id,
          produto_id = EXCLUDED.produto_id,
          cliente_id = EXCLUDED.cliente_id,
          canal = EXCLUDED.canal,
          status = EXCLUDED.status,
          forma_pagamento = EXCLUDED.forma_pagamento,
          quantidade = EXCLUDED.quantidade,
          preco_unitario = EXCLUDED.preco_unitario,
          desconto = EXCLUDED.desconto,
          valor_bruto = EXCLUDED.valor_bruto,
          valor_liquido = EXCLUDED.valor_liquido,
          valor_cancelado = EXCLUDED.valor_cancelado,
          realizada_em = EXCLUDED.realizada_em,
          atualizado_em = now()
      """;

  private static final String SQL_FATO_ESTOQUE =
      """
      WITH ultimo_estoque AS (
          SELECT DISTINCT ON (chave_origem) payload, atualizado_na_origem
          FROM raw.estoque_atual
          ORDER BY chave_origem, atualizado_na_origem DESC, id DESC
      ),
      ultimo_movimento AS (
          SELECT DISTINCT ON (chave_origem) payload
          FROM raw.movimento_estoque
          ORDER BY chave_origem, atualizado_na_origem DESC, id DESC
      ),
      movimentos_dia AS (
          SELECT
              ((payload->>'ocorrido_em')::timestamptz AT TIME ZONE 'America/Sao_Paulo')::date AS data,
              (payload->>'loja_id')::bigint AS loja_id_origem,
              (payload->>'produto_id')::bigint AS produto_id_origem,
              coalesce(sum(CASE WHEN upper(trim(payload->>'tipo')) = 'ENTRADA' THEN abs((payload->>'quantidade')::int) ELSE 0 END), 0)::int AS qtd_entradas,
              coalesce(sum(CASE WHEN upper(trim(payload->>'tipo')) = 'SAIDA_VENDA' THEN abs((payload->>'quantidade')::int) ELSE 0 END), 0)::int AS qtd_saidas_venda,
              coalesce(sum(CASE WHEN upper(trim(payload->>'tipo')) = 'AJUSTE' THEN (payload->>'quantidade')::int ELSE 0 END), 0)::int AS qtd_ajustes,
              coalesce(sum(CASE WHEN upper(trim(payload->>'tipo')) = 'PERDA' THEN abs((payload->>'quantidade')::int) ELSE 0 END), 0)::int AS qtd_perdas
          FROM ultimo_movimento
          GROUP BY 1, 2, 3
      ),
      saldo_dia AS (
          SELECT
              (coalesce(NULLIF(trim(e.payload->>'atualizado_em'), '')::timestamptz, e.atualizado_na_origem)
                  AT TIME ZONE 'America/Sao_Paulo')::date AS data,
              (e.payload->>'loja_id')::bigint AS loja_id_origem,
              (e.payload->>'produto_id')::bigint AS produto_id_origem,
              (e.payload->>'quantidade')::int AS quantidade_saldo,
              (e.payload->>'estoque_minimo')::int AS estoque_minimo
          FROM ultimo_estoque e
      )
      INSERT INTO curated.fato_estoque (
          tempo_id, loja_id, produto_id, quantidade_saldo, estoque_minimo, em_ruptura,
          qtd_entradas, qtd_saidas_venda, qtd_ajustes, qtd_perdas, atualizado_em
      )
      SELECT
          dt.id,
          dl.id,
          dp.id,
          s.quantidade_saldo,
          s.estoque_minimo,
          s.quantidade_saldo < s.estoque_minimo,
          coalesce(m.qtd_entradas, 0),
          coalesce(m.qtd_saidas_venda, 0),
          coalesce(m.qtd_ajustes, 0),
          coalesce(m.qtd_perdas, 0),
          now()
      FROM saldo_dia s
      JOIN curated.dim_tempo dt ON dt.data = s.data
      JOIN curated.dim_loja dl ON dl.id_origem = s.loja_id_origem
      JOIN curated.dim_produto dp ON dp.id_origem = s.produto_id_origem
      LEFT JOIN movimentos_dia m
          ON m.data = s.data
         AND m.loja_id_origem = s.loja_id_origem
         AND m.produto_id_origem = s.produto_id_origem
      ON CONFLICT (tempo_id, loja_id, produto_id) DO UPDATE SET
          quantidade_saldo = EXCLUDED.quantidade_saldo,
          estoque_minimo = EXCLUDED.estoque_minimo,
          em_ruptura = EXCLUDED.em_ruptura,
          qtd_entradas = EXCLUDED.qtd_entradas,
          qtd_saidas_venda = EXCLUDED.qtd_saidas_venda,
          qtd_ajustes = EXCLUDED.qtd_ajustes,
          qtd_perdas = EXCLUDED.qtd_perdas,
          atualizado_em = now()
      """;

  private final AgroalDataSource dw;

  public TransformacaoCuradaEtapa(final AgroalDataSource dw) {
    this.dw = dw;
  }

  @Override
  public String nome() {
    return "transformacao-curated";
  }

  @Override
  public int ordem() {
    return 20;
  }

  @Override
  public ResultadoEtapa executar(final ContextoExecucao contexto) {
    return QuarkusTransaction.requiringNew().call(() -> transformar(contexto));
  }

  private ResultadoEtapa transformar(final ContextoExecucao contexto) {
    try (Connection con = dw.getConnection();
        PreparedStatement trava = con.prepareStatement(SQL_TRAVA)) {
      trava.execute();

      final int tempos = executarUpsert(con, SQL_DIM_TEMPO);
      final int lojas = executarUpsert(con, SQL_DIM_LOJA);
      final int produtos = executarUpsert(con, SQL_DIM_PRODUTO);
      final int clientes = executarUpsert(con, SQL_DIM_CLIENTE);
      final int vendas = executarUpsert(con, SQL_FATO_VENDA);
      final int estoques = executarUpsert(con, SQL_FATO_ESTOQUE);

      final long total = (long) tempos + lojas + produtos + clientes + vendas + estoques;
      final String detalhe =
          new StringJoiner(", ")
              .add("dim_tempo=" + tempos)
              .add("dim_loja=" + lojas)
              .add("dim_produto=" + produtos)
              .add("dim_cliente=" + clientes)
              .add("fato_venda=" + vendas)
              .add("fato_estoque=" + estoques)
              .toString();

      Log.infof(
          "Transformação curated lote %s: %d registros (%s)", contexto.loteId(), total, detalhe);
      return new ResultadoEtapa(total, detalhe);
    } catch (SQLException e) {
      throw new TransformacaoException("Falha ao transformar raw -> curated", e);
    }
  }

  private static int executarUpsert(final Connection con, final String sql) throws SQLException {
    try (PreparedStatement st = con.prepareStatement(sql)) {
      return st.executeUpdate();
    }
  }

  /** Erro de transformação na camada curada; o orquestrador registra como FALHA da etapa. */
  static final class TransformacaoException extends RuntimeException {
    TransformacaoException(final String mensagem, final Throwable causa) {
      super(mensagem + ": " + causa.getMessage(), causa);
    }
  }
}
