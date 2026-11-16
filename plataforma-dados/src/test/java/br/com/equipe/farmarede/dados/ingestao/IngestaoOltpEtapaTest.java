package br.com.equipe.farmarede.dados.ingestao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.equipe.farmarede.dados.pipeline.ContextoExecucao;
import br.com.equipe.farmarede.dados.pipeline.ResultadoEtapa;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Ingestão OLTP → raw. Os bancos do Dev Services podem ser reaproveitados entre execuções, então os
 * testes comparam o estado antes/depois em vez de contar a partir de zero.
 */
@QuarkusTest
class IngestaoOltpEtapaTest {

  @Inject IngestaoOltpEtapa etapa;
  @Inject AgroalDataSource dw;

  @Inject
  @DataSource("oltp")
  AgroalDataSource oltp;

  @Test
  void nomeEOrdemSeguemOContrato() {
    assertEquals("ingestao-oltp-raw", etapa.nome());
    assertEquals(10, etapa.ordem());
  }

  @Test
  void copiaTodaChaveDoOltpEAvancaOWatermark() throws SQLException {
    etapa.executar(ContextoExecucao.novo());

    for (final String tabela : IngestaoOltpEtapa.TABELAS) {
      final long linhasNaOrigem = contar(oltp, "SELECT count(*) FROM " + tabela);
      assertEquals(
          linhasNaOrigem,
          contar(dw, "SELECT count(DISTINCT chave_origem) FROM raw." + tabela),
          "chaves em raw." + tabela);
      // Tabela vazia na origem (ex.: venda sem simulador) ainda não tem watermark.
      if (linhasNaOrigem == 0) {
        continue;
      }
      assertTrue(
          instante(oltp, "SELECT max(atualizado_em) FROM " + tabela).isEqual(watermarkDe(tabela)),
          "watermark de " + tabela);
    }
  }

  @Test
  void segundaExecucaoSemMudancaNaOrigemNaoInsereNada() throws SQLException {
    etapa.executar(ContextoExecucao.novo());
    final long antes = contar(dw, "SELECT count(*) FROM raw.venda");

    final ResultadoEtapa resultado = etapa.executar(ContextoExecucao.novo());

    assertEquals(0, resultado.registrosProcessados(), resultado.detalhe());
    assertEquals(antes, contar(dw, "SELECT count(*) FROM raw.venda"));
  }

  @Test
  void atualizacaoNaOrigemGeraNovaVersaoComOLoteDaExecucao() throws SQLException {
    etapa.executar(ContextoExecucao.novo());
    final long produtoId = contar(oltp, "SELECT min(id) FROM produto");
    executar(
        oltp, "UPDATE produto SET preco_unitario = preco_unitario + 1 WHERE id = ?", produtoId);
    final String precoNovo =
        texto(oltp, "SELECT preco_unitario::text FROM produto WHERE id = " + produtoId);
    final ContextoExecucao contexto = ContextoExecucao.novo();

    final ResultadoEtapa resultado = etapa.executar(contexto);

    assertEquals(1, resultado.registrosProcessados(), resultado.detalhe());
    assertTrue(resultado.detalhe().contains("produto=1"), resultado.detalhe());
    assertEquals(
        precoNovo,
        texto(
            dw,
            "SELECT payload->>'preco_unitario' FROM raw.produto WHERE lote_id = '"
                + contexto.loteId()
                + "' AND chave_origem = '"
                + produtoId
                + "'"));
  }

  @Test
  void linhaQueComitouAtrasadaDentroDaJanelaEhIngerida() throws SQLException {
    etapa.executar(ContextoExecucao.novo());
    final OffsetDateTime watermark = watermarkDe("cliente");
    final String email = "atrasado." + UUID.randomUUID() + "@exemplo.com";
    executar(
        oltp,
        "INSERT INTO cliente (nome, email, cidade, uf, atualizado_em)"
            + " VALUES ('Cliente Atrasado', ?, 'Santos', 'SP', ?)",
        email,
        watermark.minusMinutes(1));

    etapa.executar(ContextoExecucao.novo());

    assertEquals(
        1,
        contar(dw, "SELECT count(*) FROM raw.cliente WHERE payload->>'email' = '" + email + "'"));
    assertTrue(watermark.isEqual(watermarkDe("cliente")), "o watermark nunca volta");
  }

  @Test
  void chaveDoEstoqueAtualEhLojaDoisPontosProduto() throws SQLException {
    etapa.executar(ContextoExecucao.novo());
    final String chave =
        texto(
            oltp,
            "SELECT loja_id || ':' || produto_id FROM estoque_atual"
                + " ORDER BY loja_id, produto_id LIMIT 1");

    assertTrue(
        contar(dw, "SELECT count(*) FROM raw.estoque_atual WHERE chave_origem = '" + chave + "'")
            >= 1);
  }

  private OffsetDateTime watermarkDe(final String tabela) throws SQLException {
    return instante(
        dw,
        "SELECT ultimo_atualizado_em FROM controle.watermark WHERE tabela_origem = '"
            + tabela
            + "'");
  }

  private static long contar(final AgroalDataSource ds, final String sql) throws SQLException {
    return Long.parseLong(texto(ds, sql));
  }

  private static String texto(final AgroalDataSource ds, final String sql) throws SQLException {
    try (Connection con = ds.getConnection();
        PreparedStatement st = con.prepareStatement(sql);
        ResultSet rs = st.executeQuery()) {
      rs.next();
      return rs.getString(1);
    }
  }

  private static OffsetDateTime instante(final AgroalDataSource ds, final String sql)
      throws SQLException {
    try (Connection con = ds.getConnection();
        PreparedStatement st = con.prepareStatement(sql);
        ResultSet rs = st.executeQuery()) {
      rs.next();
      return rs.getObject(1, OffsetDateTime.class);
    }
  }

  private static void executar(final AgroalDataSource ds, final String sql, final Object... params)
      throws SQLException {
    try (Connection con = ds.getConnection();
        PreparedStatement st = con.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        st.setObject(i + 1, params[i]);
      }
      st.executeUpdate();
    }
  }
}
