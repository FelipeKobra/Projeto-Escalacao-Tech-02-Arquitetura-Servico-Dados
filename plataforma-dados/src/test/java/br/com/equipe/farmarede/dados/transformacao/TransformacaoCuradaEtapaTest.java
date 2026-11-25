package br.com.equipe.farmarede.dados.transformacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.equipe.farmarede.dados.pipeline.ContextoExecucao;
import br.com.equipe.farmarede.dados.pipeline.ResultadoEtapa;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TransformacaoCuradaEtapaTest {

  @Inject TransformacaoCuradaEtapa etapa;
  @Inject AgroalDataSource dw;

  @BeforeEach
  void limparTabelas() throws SQLException {
    try (Connection con = dw.getConnection();
        Statement st = con.createStatement()) {
      st.execute(
          """
          TRUNCATE TABLE
              curated.fato_venda,
              curated.fato_estoque,
              curated.dim_cliente,
              curated.dim_produto,
              curated.dim_loja,
              curated.dim_tempo,
              raw.item_venda,
              raw.venda,
              raw.movimento_estoque,
              raw.estoque_atual,
              raw.cliente,
              raw.produto,
              raw.loja
          RESTART IDENTITY CASCADE
          """);
    }
  }

  @Test
  void deveExporNomeEOrdemCombinados() {
    assertEquals("transformacao-curated", etapa.nome());
    assertEquals(20, etapa.ordem());
  }

  @Test
  void deveTransformarDimensoesEFatosComChavesSubstitutasEPseudonimizacao() throws SQLException {
    final UUID lote = UUID.randomUUID();
    final OffsetDateTime t1 = OffsetDateTime.parse("2026-05-10T14:30:00-03:00");

    inserirRaw(
        "loja",
        lote,
        "1",
        """
        {"id": 1, "codigo": " sp01 ", "nome": " FarmaRede Paulista ", "cidade": " São Paulo ", "uf": "sp", "tipo": "FISICA", "atualizado_em": "2026-05-10T14:00:00-03:00"}
        """,
        t1);
    inserirRaw(
        "loja",
        lote,
        "2",
        """
        {"id": 2, "codigo": "WEB", "nome": "FarmaRede Online", "cidade": "São Paulo", "uf": "SP", "tipo": "ONLINE", "atualizado_em": "2026-05-10T14:00:00-03:00"}
        """,
        t1);

    inserirRaw(
        "produto",
        lote,
        "10",
        """
        {"id": 10, "sku": "MED-0001", "nome": " Dipirona 500mg ", "categoria": "Medicamentos", "fabricante": "Genéricos BR", "preco_unitario": 8.90, "controlado": false, "atualizado_em": "2026-05-10T14:00:00-03:00"}
        """,
        t1);
    inserirRaw(
        "produto",
        lote,
        "11",
        """
        {"id": 11, "sku": "MED-0004", "nome": "Amoxicilina 500mg", "categoria": "Antibióticos", "fabricante": "Farmalab", "preco_unitario": 32.40, "controlado": true, "atualizado_em": "2026-05-10T14:00:00-03:00"}
        """,
        t1);

    inserirRaw(
        "cliente",
        lote,
        "5",
        """
        {"id": 5, "nome": "Maria Silva", "email": "Maria.Silva@Exemplo.com", "cidade": "São Paulo", "uf": "sp", "data_nascimento": "1990-04-12", "atualizado_em": "2026-05-10T14:00:00-03:00"}
        """,
        t1);

    // Venda 100: ecommerce com cliente identificado e desconto
    inserirRaw(
        "venda",
        lote,
        "100",
        """
        {"id": 100, "loja_id": 2, "cliente_id": 5, "canal": "ECOMMERCE", "status": "CONCLUIDA", "forma_pagamento": "CREDITO", "valor_total": 30.40, "realizada_em": "2026-05-10T14:15:00-03:00", "atualizado_em": "2026-05-10T14:15:00-03:00"}
        """,
        t1);
    // Venda 101: balcão PDV sem cliente
    inserirRaw(
        "venda",
        lote,
        "101",
        """
        {"id": 101, "loja_id": 1, "cliente_id": null, "canal": "PDV", "status": "CONCLUIDA", "forma_pagamento": "PIX", "valor_total": 17.80, "realizada_em": "2026-05-10T14:20:00-03:00", "atualizado_em": "2026-05-10T14:20:00-03:00"}
        """,
        t1);

    inserirRaw(
        "item_venda",
        lote,
        "1000",
        """
        {"id": 1000, "venda_id": 100, "produto_id": 11, "quantidade": 1, "preco_unitario": 32.40, "desconto": 2.00, "atualizado_em": "2026-05-10T14:15:00-03:00"}
        """,
        t1);
    inserirRaw(
        "item_venda",
        lote,
        "1001",
        """
        {"id": 1001, "venda_id": 101, "produto_id": 10, "quantidade": 2, "preco_unitario": 8.90, "desconto": 0.00, "atualizado_em": "2026-05-10T14:20:00-03:00"}
        """,
        t1);

    inserirRaw(
        "movimento_estoque",
        lote,
        "500",
        """
        {"id": 500, "loja_id": 1, "produto_id": 10, "tipo": "ENTRADA", "quantidade": 50, "ocorrido_em": "2026-05-10T09:00:00-03:00", "atualizado_em": "2026-05-10T09:00:00-03:00"}
        """,
        t1);
    inserirRaw(
        "movimento_estoque",
        lote,
        "501",
        """
        {"id": 501, "loja_id": 1, "produto_id": 10, "tipo": "SAIDA_VENDA", "quantidade": -2, "ocorrido_em": "2026-05-10T14:20:00-03:00", "atualizado_em": "2026-05-10T14:20:00-03:00"}
        """,
        t1);

    inserirRaw(
        "estoque_atual",
        lote,
        "1:10",
        """
        {"loja_id": 1, "produto_id": 10, "quantidade": 48, "estoque_minimo": 10, "atualizado_em": "2026-05-10T14:20:00-03:00"}
        """,
        t1);

    final ResultadoEtapa resultado = etapa.executar(ContextoExecucao.novo());
    assertTrue(resultado.registrosProcessados() > 0);
    assertNotNull(resultado.detalhe());

    try (Connection con = dw.getConnection();
        Statement st = con.createStatement()) {
      assertEquals(2, contar(st, "SELECT count(*) FROM curated.dim_loja"));
      assertEquals(2, contar(st, "SELECT count(*) FROM curated.dim_produto"));
      assertEquals(1, contar(st, "SELECT count(*) FROM curated.dim_cliente"));
      assertEquals(1, contar(st, "SELECT count(*) FROM curated.dim_tempo"));
      assertEquals(2, contar(st, "SELECT count(*) FROM curated.fato_venda"));
      assertEquals(1, contar(st, "SELECT count(*) FROM curated.fato_estoque"));

      // Limpeza de strings na dim_loja
      try (ResultSet rs =
          st.executeQuery("SELECT codigo, nome, uf FROM curated.dim_loja WHERE id_origem = 1")) {
        assertTrue(rs.next());
        assertEquals("SP01", rs.getString("codigo"));
        assertEquals("FarmaRede Paulista", rs.getString("nome"));
        assertEquals("SP", rs.getString("uf"));
      }

      // LGPD: dim_cliente não tem colunas de dado pessoal em claro e usa pseudônimo SHA-256
      assertEquals(
          0,
          contar(
              st,
              """
              SELECT count(*) FROM information_schema.columns
              WHERE table_schema = 'curated' AND table_name = 'dim_cliente'
                AND column_name IN ('nome', 'email', 'data_nascimento')
              """));
      try (ResultSet rs =
          st.executeQuery(
              "SELECT chave_pseudonimo, uf, faixa_etaria FROM curated.dim_cliente WHERE id_origem = 5")) {
        assertTrue(rs.next());
        final String pseudonimo = rs.getString("chave_pseudonimo");
        assertEquals(64, pseudonimo.length());
        assertFalse(pseudonimo.toLowerCase().contains("maria"));
        assertEquals("SP", rs.getString("uf"));
        assertNotNull(rs.getString("faixa_etaria"));
      }

      // Grão item em fato_venda (com cliente nulo no PDV e preenchido no e-commerce)
      try (ResultSet rs =
          st.executeQuery(
              "SELECT cliente_id, valor_bruto, valor_liquido, valor_cancelado FROM curated.fato_venda WHERE id_item_origem = 1000")) {
        assertTrue(rs.next());
        assertNotNull(rs.getObject("cliente_id"));
        assertEquals(0, new BigDecimal("32.40").compareTo(rs.getBigDecimal("valor_bruto")));
        assertEquals(0, new BigDecimal("30.40").compareTo(rs.getBigDecimal("valor_liquido")));
        assertEquals(0, BigDecimal.ZERO.compareTo(rs.getBigDecimal("valor_cancelado")));
      }
      try (ResultSet rs =
          st.executeQuery(
              "SELECT cliente_id, valor_liquido FROM curated.fato_venda WHERE id_item_origem = 1001")) {
        assertTrue(rs.next());
        assertNull(rs.getObject("cliente_id"));
        assertEquals(0, new BigDecimal("17.80").compareTo(rs.getBigDecimal("valor_liquido")));
      }

      // fato_estoque com saldo diário e movimentos agregados do dia
      try (ResultSet rs =
          st.executeQuery(
              "SELECT quantidade_saldo, estoque_minimo, em_ruptura, qtd_entradas, qtd_saidas_venda FROM curated.fato_estoque")) {
        assertTrue(rs.next());
        assertEquals(48, rs.getInt("quantidade_saldo"));
        assertEquals(10, rs.getInt("estoque_minimo"));
        assertFalse(rs.getBoolean("em_ruptura"));
        assertEquals(50, rs.getInt("qtd_entradas"));
        assertEquals(2, rs.getInt("qtd_saidas_venda"));
      }
    }
  }

  @Test
  void deveDeduplicarPelaUltimaVersaoDeCadaChaveOrigem() throws SQLException {
    final UUID lote1 = UUID.randomUUID();
    final UUID lote2 = UUID.randomUUID();
    final OffsetDateTime antigo = OffsetDateTime.parse("2026-05-10T10:00:00-03:00");
    final OffsetDateTime recente = OffsetDateTime.parse("2026-05-10T12:00:00-03:00");

    inserirRaw(
        "loja",
        lote1,
        "1",
        """
        {"id": 1, "codigo": "SP01", "nome": "Nome Antigo", "cidade": "São Paulo", "uf": "SP", "tipo": "FISICA"}
        """,
        antigo);
    inserirRaw(
        "loja",
        lote2,
        "1",
        """
        {"id": 1, "codigo": "SP01", "nome": "  FarmaRede Paulista Atualizada  ", "cidade": "São Paulo", "uf": "SP", "tipo": "FISICA"}
        """,
        recente);

    inserirRaw(
        "produto",
        lote1,
        "10",
        """
        {"id": 10, "sku": "MED-0001", "nome": "Dipirona", "categoria": "Medicamentos", "fabricante": "Genéricos BR", "preco_unitario": 8.00, "controlado": false}
        """,
        antigo);
    inserirRaw(
        "produto",
        lote2,
        "10",
        """
        {"id": 10, "sku": "MED-0001", "nome": "Dipirona 500mg", "categoria": "Medicamentos", "fabricante": "Genéricos BR", "preco_unitario": 9.50, "controlado": false}
        """,
        recente);

    inserirRaw(
        "estoque_atual",
        lote1,
        "1:10",
        """
        {"loja_id": 1, "produto_id": 10, "quantidade": 50, "estoque_minimo": 10, "atualizado_em": "2026-05-10T10:00:00-03:00"}
        """,
        antigo);
    inserirRaw(
        "estoque_atual",
        lote2,
        "1:10",
        """
        {"loja_id": 1, "produto_id": 10, "quantidade": 4, "estoque_minimo": 10, "atualizado_em": "2026-05-10T12:00:00-03:00"}
        """,
        recente);

    etapa.executar(ContextoExecucao.novo());

    try (Connection con = dw.getConnection();
        Statement st = con.createStatement()) {
      assertEquals(1, contar(st, "SELECT count(*) FROM curated.dim_loja"));
      assertEquals(1, contar(st, "SELECT count(*) FROM curated.dim_produto"));
      assertEquals(1, contar(st, "SELECT count(*) FROM curated.fato_estoque"));

      try (ResultSet rs =
          st.executeQuery("SELECT nome FROM curated.dim_loja WHERE id_origem = 1")) {
        assertTrue(rs.next());
        assertEquals("FarmaRede Paulista Atualizada", rs.getString("nome"));
      }
      try (ResultSet rs =
          st.executeQuery("SELECT preco_unitario FROM curated.dim_produto WHERE id_origem = 10")) {
        assertTrue(rs.next());
        assertEquals(0, new BigDecimal("9.50").compareTo(rs.getBigDecimal("preco_unitario")));
      }
      try (ResultSet rs =
          st.executeQuery("SELECT quantidade_saldo, em_ruptura FROM curated.fato_estoque")) {
        assertTrue(rs.next());
        assertEquals(4, rs.getInt("quantidade_saldo"));
        assertTrue(rs.getBoolean("em_ruptura"));
      }
    }
  }

  @Test
  void naoDeveContarVendaCanceladaComoReceita() throws SQLException {
    final UUID lote1 = UUID.randomUUID();
    final UUID lote2 = UUID.randomUUID();
    final OffsetDateTime t1 = OffsetDateTime.parse("2026-05-10T10:00:00-03:00");
    final OffsetDateTime t2 = OffsetDateTime.parse("2026-05-10T10:15:00-03:00");

    inserirRaw(
        "loja",
        lote1,
        "1",
        """
        {"id": 1, "codigo": "SP01", "nome": "FarmaRede Paulista", "cidade": "São Paulo", "uf": "SP", "tipo": "FISICA"}
        """,
        t1);
    inserirRaw(
        "produto",
        lote1,
        "10",
        """
        {"id": 10, "sku": "MED-0001", "nome": "Dipirona", "categoria": "Medicamentos", "fabricante": "Genéricos BR", "preco_unitario": 10.00, "controlado": false}
        """,
        t1);
    inserirRaw(
        "venda",
        lote1,
        "200",
        """
        {"id": 200, "loja_id": 1, "cliente_id": null, "canal": "PDV", "status": "CONCLUIDA", "forma_pagamento": "DINHEIRO", "valor_total": 20.00, "realizada_em": "2026-05-10T10:00:00-03:00", "atualizado_em": "2026-05-10T10:00:00-03:00"}
        """,
        t1);
    inserirRaw(
        "item_venda",
        lote1,
        "2000",
        """
        {"id": 2000, "venda_id": 200, "produto_id": 10, "quantidade": 2, "preco_unitario": 10.00, "desconto": 0.00, "atualizado_em": "2026-05-10T10:00:00-03:00"}
        """,
        t1);

    etapa.executar(ContextoExecucao.novo());

    // Cancelamento posterior chega só em raw.venda (item_venda não muda)
    inserirRaw(
        "venda",
        lote2,
        "200",
        """
        {"id": 200, "loja_id": 1, "cliente_id": null, "canal": "PDV", "status": "CANCELADA", "forma_pagamento": "DINHEIRO", "valor_total": 20.00, "realizada_em": "2026-05-10T10:00:00-03:00", "atualizado_em": "2026-05-10T10:15:00-03:00"}
        """,
        t2);

    etapa.executar(ContextoExecucao.novo());

    try (Connection con = dw.getConnection();
        Statement st = con.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT status, valor_bruto, valor_liquido, valor_cancelado FROM curated.fato_venda WHERE id_item_origem = 2000")) {
      assertTrue(rs.next());
      assertEquals("CANCELADA", rs.getString("status"));
      assertEquals(0, new BigDecimal("20.00").compareTo(rs.getBigDecimal("valor_bruto")));
      assertEquals(0, BigDecimal.ZERO.compareTo(rs.getBigDecimal("valor_liquido")));
      assertEquals(0, new BigDecimal("20.00").compareTo(rs.getBigDecimal("valor_cancelado")));
    }
  }

  @Test
  void deveSerIdempotenteAoReexecutarSemNovasLinhasNaRaw() throws SQLException {
    final UUID lote = UUID.randomUUID();
    final OffsetDateTime t1 = OffsetDateTime.parse("2026-05-10T14:30:00-03:00");

    inserirRaw(
        "loja",
        lote,
        "1",
        """
        {"id": 1, "codigo": "SP01", "nome": "FarmaRede Paulista", "cidade": "São Paulo", "uf": "SP", "tipo": "FISICA"}
        """,
        t1);
    inserirRaw(
        "produto",
        lote,
        "10",
        """
        {"id": 10, "sku": "MED-0001", "nome": "Dipirona", "categoria": "Medicamentos", "fabricante": "Genéricos BR", "preco_unitario": 8.90, "controlado": false}
        """,
        t1);
    inserirRaw(
        "venda",
        lote,
        "300",
        """
        {"id": 300, "loja_id": 1, "cliente_id": null, "canal": "PDV", "status": "CONCLUIDA", "forma_pagamento": "PIX", "valor_total": 8.90, "realizada_em": "2026-05-10T14:30:00-03:00", "atualizado_em": "2026-05-10T14:30:00-03:00"}
        """,
        t1);
    inserirRaw(
        "item_venda",
        lote,
        "3000",
        """
        {"id": 3000, "venda_id": 300, "produto_id": 10, "quantidade": 1, "preco_unitario": 8.90, "desconto": 0.00, "atualizado_em": "2026-05-10T14:30:00-03:00"}
        """,
        t1);
    inserirRaw(
        "estoque_atual",
        lote,
        "1:10",
        """
        {"loja_id": 1, "produto_id": 10, "quantidade": 49, "estoque_minimo": 10, "atualizado_em": "2026-05-10T14:30:00-03:00"}
        """,
        t1);

    etapa.executar(ContextoExecucao.novo());
    final long idFatoAntes;
    try (Connection con = dw.getConnection();
        Statement st = con.createStatement()) {
      idFatoAntes = contar(st, "SELECT id FROM curated.fato_venda WHERE id_item_origem = 3000");
    }

    etapa.executar(ContextoExecucao.novo());

    try (Connection con = dw.getConnection();
        Statement st = con.createStatement()) {
      assertEquals(1, contar(st, "SELECT count(*) FROM curated.dim_loja"));
      assertEquals(1, contar(st, "SELECT count(*) FROM curated.dim_produto"));
      assertEquals(1, contar(st, "SELECT count(*) FROM curated.dim_tempo"));
      assertEquals(1, contar(st, "SELECT count(*) FROM curated.fato_venda"));
      assertEquals(1, contar(st, "SELECT count(*) FROM curated.fato_estoque"));
      assertEquals(
          idFatoAntes, contar(st, "SELECT id FROM curated.fato_venda WHERE id_item_origem = 3000"));
    }
  }

  private void inserirRaw(
      final String tabela,
      final UUID lote,
      final String chaveOrigem,
      final String payloadJson,
      final OffsetDateTime atualizadoNaOrigem)
      throws SQLException {
    final String sql =
        "INSERT INTO raw."
            + tabela
            + " (lote_id, chave_origem, payload, atualizado_na_origem) VALUES (?, ?, ?::jsonb, ?)";
    try (Connection con = dw.getConnection();
        PreparedStatement st = con.prepareStatement(sql)) {
      st.setObject(1, lote);
      st.setString(2, chaveOrigem);
      st.setString(3, payloadJson);
      st.setObject(4, atualizadoNaOrigem);
      st.executeUpdate();
    }
  }

  private static long contar(final Statement st, final String sql) throws SQLException {
    try (ResultSet rs = st.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    }
  }
}
