package br.com.equipe.farmarede.dados.ingestao;

import br.com.equipe.farmarede.dados.pipeline.ContextoExecucao;
import br.com.equipe.farmarede.dados.pipeline.EtapaPipeline;
import br.com.equipe.farmarede.dados.pipeline.ResultadoEtapa;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Etapa 10 (COLETA): copia as linhas novas/alteradas do OLTP para {@code raw.*}, de forma
 * incremental pelo watermark {@code atualizado_em}.
 *
 * <p>Para cada tabela: lê no OLTP o que mudou desde {@code watermark - janela de sobreposição} e,
 * numa transação do DW, insere em {@code raw.<tabela>} só as versões ({@code chave_origem} + {@code
 * atualizado_na_origem}) que ainda não existem e avança o watermark. A janela cobre transações que
 * comitam atrasadas com {@code atualizado_em} menor que o watermark; a deduplicação torna a
 * releitura inofensiva (rodar duas vezes sem mudança na origem não insere nada).
 */
@ApplicationScoped
public class IngestaoOltpEtapa implements EtapaPipeline {

  /** Tabelas do OLTP ingeridas, cada uma com sua tabela homônima em {@code raw}. */
  static final List<String> TABELAS =
      List.of(
          "loja",
          "produto",
          "cliente",
          "venda",
          "item_venda",
          "movimento_estoque",
          "estoque_atual");

  /** Expressão SQL da chave de origem por tabela (estoque_atual tem chave composta). */
  private static final Map<String, String> CHAVE_ORIGEM =
      Map.of(
          "loja", "t.id::text",
          "produto", "t.id::text",
          "cliente", "t.id::text",
          "venda", "t.id::text",
          "item_venda", "t.id::text",
          "movimento_estoque", "t.id::text",
          "estoque_atual", "t.loja_id || ':' || t.produto_id");

  /** Serializa ingestões concorrentes (cron + POST manual) no DW. */
  private static final String SQL_TRAVA = "SELECT pg_advisory_xact_lock(hashtext('ingestao-raw'))";

  private static final String SQL_WATERMARK =
      "SELECT ultimo_atualizado_em FROM controle.watermark WHERE tabela_origem = ?";

  private static final String SQL_AVANCAR_WATERMARK =
      """
      INSERT INTO controle.watermark (tabela_origem, ultimo_atualizado_em, atualizado_em)
      VALUES (?, ?, now())
      ON CONFLICT (tabela_origem) DO UPDATE SET
          ultimo_atualizado_em = GREATEST(controle.watermark.ultimo_atualizado_em,
                                          EXCLUDED.ultimo_atualizado_em),
          atualizado_em = now()
      """;

  private final AgroalDataSource oltp;
  private final AgroalDataSource dw;
  private final Duration janelaSobreposicao;

  public IngestaoOltpEtapa(
      @DataSource("oltp") final AgroalDataSource oltp,
      final AgroalDataSource dw,
      @ConfigProperty(name = "ingestao.janela-sobreposicao", defaultValue = "5m")
          final Duration janelaSobreposicao) {
    this.oltp = oltp;
    this.dw = dw;
    this.janelaSobreposicao = janelaSobreposicao;
  }

  @Override
  public String nome() {
    return "ingestao-oltp-raw";
  }

  @Override
  public int ordem() {
    return 10;
  }

  @Override
  public ResultadoEtapa executar(final ContextoExecucao contexto) {
    long total = 0;
    final StringJoiner detalhe = new StringJoiner(", ");
    for (final String tabela : TABELAS) {
      final int inseridas = ingerir(tabela, contexto.loteId());
      total += inseridas;
      detalhe.add(tabela + "=" + inseridas);
    }
    Log.infof("Ingestão lote %s: %d linhas novas (%s)", contexto.loteId(), total, detalhe);
    return new ResultadoEtapa(total, detalhe.toString());
  }

  private int ingerir(final String tabela, final UUID loteId) {
    final Optional<OffsetDateTime> desde =
        lerWatermark(tabela).map(watermark -> watermark.minus(janelaSobreposicao));
    final List<LinhaOrigem> linhas = lerOrigem(tabela, desde);
    if (linhas.isEmpty()) {
      return 0;
    }
    // Leitura do OLTP fora da transação: só o DW participa dela (sem 2PC entre os bancos).
    return QuarkusTransaction.requiringNew().call(() -> gravarRaw(tabela, loteId, linhas));
  }

  private Optional<OffsetDateTime> lerWatermark(final String tabela) {
    try (Connection con = dw.getConnection();
        PreparedStatement st = con.prepareStatement(SQL_WATERMARK)) {
      st.setString(1, tabela);
      try (ResultSet rs = st.executeQuery()) {
        return rs.next() ? Optional.of(rs.getObject(1, OffsetDateTime.class)) : Optional.empty();
      }
    } catch (SQLException e) {
      throw new IngestaoException("Falha ao ler o watermark de " + tabela, e);
    }
  }

  private List<LinhaOrigem> lerOrigem(final String tabela, final Optional<OffsetDateTime> desde) {
    // Nome de tabela e expressão de chave vêm só das constantes acima, nunca de entrada externa.
    final String sql =
        "SELECT "
            + CHAVE_ORIGEM.get(tabela)
            + " AS chave, to_jsonb(t)::text AS payload, t.atualizado_em FROM "
            + tabela
            + " t"
            + (desde.isPresent() ? " WHERE t.atualizado_em > ?" : "")
            + " ORDER BY t.atualizado_em";
    try (Connection con = oltp.getConnection();
        PreparedStatement st = con.prepareStatement(sql)) {
      if (desde.isPresent()) {
        st.setObject(1, desde.get());
      }
      final List<LinhaOrigem> linhas = new ArrayList<>();
      try (ResultSet rs = st.executeQuery()) {
        while (rs.next()) {
          linhas.add(
              new LinhaOrigem(
                  rs.getString("chave"),
                  rs.getString("payload"),
                  rs.getObject("atualizado_em", OffsetDateTime.class)));
        }
      }
      return linhas;
    } catch (SQLException e) {
      throw new IngestaoException("Falha ao ler " + tabela + " no OLTP", e);
    }
  }

  private int gravarRaw(final String tabela, final UUID loteId, final List<LinhaOrigem> linhas) {
    final String sqlInsert =
        "INSERT INTO raw."
            + tabela
            + " (lote_id, chave_origem, payload, atualizado_na_origem)"
            + " SELECT ?, ?, ?::jsonb, ? WHERE NOT EXISTS (SELECT 1 FROM raw."
            + tabela
            + " r WHERE r.chave_origem = ? AND r.atualizado_na_origem = ?)";
    try (Connection con = dw.getConnection();
        PreparedStatement trava = con.prepareStatement(SQL_TRAVA);
        PreparedStatement insert = con.prepareStatement(sqlInsert);
        PreparedStatement watermark = con.prepareStatement(SQL_AVANCAR_WATERMARK)) {
      trava.execute();
      for (final LinhaOrigem linha : linhas) {
        insert.setObject(1, loteId);
        insert.setString(2, linha.chave());
        insert.setString(3, linha.payload());
        insert.setObject(4, linha.atualizadoEm());
        insert.setString(5, linha.chave());
        insert.setObject(6, linha.atualizadoEm());
        insert.addBatch();
      }
      int inseridas = 0;
      for (final int afetadas : insert.executeBatch()) {
        inseridas += Math.max(afetadas, 0);
      }
      watermark.setString(1, tabela);
      watermark.setObject(2, linhas.getLast().atualizadoEm());
      watermark.executeUpdate();
      return inseridas;
    } catch (SQLException e) {
      throw new IngestaoException("Falha ao gravar raw." + tabela, e);
    }
  }

  /** Uma linha lida do OLTP, já com a chave de origem e o payload em JSON. */
  private record LinhaOrigem(String chave, String payload, OffsetDateTime atualizadoEm) {}

  /** Erro de leitura/escrita na ingestão; o orquestrador registra como FALHA da etapa. */
  static final class IngestaoException extends RuntimeException {
    IngestaoException(final String mensagem, final Throwable causa) {
      super(mensagem + ": " + causa.getMessage(), causa);
    }
  }
}
