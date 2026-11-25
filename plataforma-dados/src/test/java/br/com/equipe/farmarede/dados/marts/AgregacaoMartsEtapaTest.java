package br.com.equipe.farmarede.dados.marts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.equipe.farmarede.dados.pipeline.ContextoExecucao;
import br.com.equipe.farmarede.dados.pipeline.ResultadoEtapa;
import br.com.equipe.farmarede.dados.transformacao.TransformacaoCuradaEtapa;
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
class AgregacaoMartsEtapaTest {

  @Inject TransformacaoCuradaEtapa transformacao;
  @Inject AgregacaoMartsEtapa agregacao;
  @Inject AgroalDataSource dw;

  @BeforeEach
  void limparTabelas() throws SQLException {
    try (Connection con = dw.getConnection();
        Statement st = con.createStatement()) {
      st.execute(
          """
          TRUNCATE TABLE
              marts.vendas_diarias,
              marts.vendas_top_produtos,
              marts.estoque_ruptura,
              marts.estoque_giro,
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
    assertEquals("agregacao-marts", agregacao.nome());
    assertEquals(30, agregacao.ordem());
  }

  @Test
  void deveAgregarVendasDiariasETopProdutosSemContarCancelada() throws SQLException {
    final UUID lote = UUID.randomUUID();
    final OffsetDateTime t1 = OffsetDateTime.parse("2026-05-10T14:00:00-03:00");

    inserirRaw(
        "loja",
        lote,
        "1",
        """
        {"id": 1, "codigo": "SP01", "nome": "FarmaRede Paulista", "cidade": "São Paulo", "uf": "SP", "tipo": "FISICA"}
        """,
        t1);
    inserirRaw(
        "loja",
        lote,
        "2",
        """
        {"id": 2, "codigo": "WEB", "nome": "FarmaRede Online", "cidade": "São Paulo", "uf": "SP", "tipo": "ONLINE"}
        """,
        t1);
    inserirRaw(
        "produto",
        lote,
        "10",
        """
        {"id": 10, "sku": "MED-0001", "nome": "Dipirona 500mg", "categoria": "Medicamentos", "fabricante": "Genéricos BR", "preco_unitario": 10.00, "controlado": false}
        """,
        t1);
    inserirRaw(
        "produto",
        lote,
        "11",
        """
        {"id": 11, "sku": "MED-0002", "nome": "Paracetamol 750mg", "categoria": "Medicamentos", "fabricante": "Genéricos BR", "preco_unitario": 25.00, "controlado": false}
        """,
        t1);
    inserirRaw(
        "produto",
        lote,
        "12",
        """
        {"id": 12, "sku": "HIG-0001", "nome": "Protetor Solar FPS 50", "categoria": "Dermocosméticos", "fabricante": "SolCare", "preco_unitario": 60.00, "controlado": false}
        """,
        t1);

    // Venda 100 (PDV SP01, CONCLUIDA): 2x Dipirona = 20.00 + 1x Paracetamol = 25.00 -> total 45.00
    inserirRaw(
        "venda",
        lote,
        "100",
        """
        {"id": 100, "loja_id": 1, "cliente_id": null, "canal": "PDV", "status": "CONCLUIDA", "forma_pagamento": "PIX", "valor_total": 45.00, "realizada_em": "2026-05-10T10:00:00-03:00"}
        """,
        t1);
    inserirRaw(
        "item_venda",
        lote,
        "1000",
        """
        {"id": 1000, "venda_id": 100, "produto_id": 10, "quantidade": 2, "preco_unitario": 10.00, "desconto": 0.00}
        """,
        t1);
    inserirRaw(
        "item_venda",
        lote,
        "1001",
        """
        {"id": 1001, "venda_id": 100, "produto_id": 11, "quantidade": 1, "preco_unitario": 25.00, "desconto": 0.00}
        """,
        t1);

    // Venda 101 (PDV SP01, CONCLUIDA): 1x Dipirona com desconto 1.00 = 9.00
    inserirRaw(
        "venda",
        lote,
        "101",
        """
        {"id": 101, "loja_id": 1, "cliente_id": null, "canal": "PDV", "status": "CONCLUIDA", "forma_pagamento": "DINHEIRO", "valor_total": 9.00, "realizada_em": "2026-05-10T11:00:00-03:00"}
        """,
        t1);
    inserirRaw(
        "item_venda",
        lote,
        "1002",
        """
        {"id": 1002, "venda_id": 101, "produto_id": 10, "quantidade": 1, "preco_unitario": 10.00, "desconto": 1.00}
        """,
        t1);

    // Venda 102 (PDV SP01, CANCELADA): 1x Protetor Solar = 60.00 (não deve somar receita)
    inserirRaw(
        "venda",
        lote,
        "102",
        """
        {"id": 102, "loja_id": 1, "cliente_id": null, "canal": "PDV", "status": "CANCELADA", "forma_pagamento": "CREDITO", "valor_total": 60.00, "realizada_em": "2026-05-10T12:00:00-03:00"}
        """,
        t1);
    inserirRaw(
        "item_venda",
        lote,
        "1003",
        """
        {"id": 1003, "venda_id": 102, "produto_id": 12, "quantidade": 1, "preco_unitario": 60.00, "desconto": 0.00}
        """,
        t1);

    // Venda 103 (ECOMMERCE WEB, CONCLUIDA): 1x Protetor Solar = 60.00
    inserirRaw(
        "venda",
        lote,
        "103",
        """
        {"id": 103, "loja_id": 2, "cliente_id": null, "canal": "ECOMMERCE", "status": "CONCLUIDA", "forma_pagamento": "CREDITO", "valor_total": 60.00, "realizada_em": "2026-05-10T13:00:00-03:00"}
        """,
        t1);
    inserirRaw(
        "item_venda",
        lote,
        "1004",
        """
        {"id": 1004, "venda_id": 103, "produto_id": 12, "quantidade": 1, "preco_unitario": 60.00, "desconto": 0.00}
        """,
        t1);

    transformacao.executar(ContextoExecucao.novo());
    final ResultadoEtapa resultado = agregacao.executar(ContextoExecucao.novo());
    assertTrue(resultado.registrosProcessados() > 0);
    assertNotNull(resultado.detalhe());

    try (Connection con = dw.getConnection();
        Statement st = con.createStatement()) {
      assertEquals(2, contar(st, "SELECT count(*) FROM marts.vendas_diarias"));
      assertEquals(3, contar(st, "SELECT count(*) FROM marts.vendas_top_produtos"));

      try (ResultSet rs =
          st.executeQuery(
              """
              SELECT qtd_vendas, qtd_itens, receita_liquida, receita_cancelada, ticket_medio
              FROM marts.vendas_diarias
              WHERE loja_codigo = 'SP01' AND canal = 'PDV'
              """)) {
        assertTrue(rs.next());
        assertEquals(2, rs.getLong("qtd_vendas"));
        assertEquals(4, rs.getLong("qtd_itens"));
        assertEquals(0, new BigDecimal("54.00").compareTo(rs.getBigDecimal("receita_liquida")));
        assertEquals(0, new BigDecimal("60.00").compareTo(rs.getBigDecimal("receita_cancelada")));
        assertEquals(0, new BigDecimal("27.00").compareTo(rs.getBigDecimal("ticket_medio")));
      }

      // Ranking geral: HIG-0001 (60.00) -> 1, MED-0001 (29.00) -> 2, MED-0002 (25.00) -> 3
      try (ResultSet rs =
          st.executeQuery(
              """
              SELECT sku, qtd_vendida, receita_liquida, ranking_categoria, ranking_geral
              FROM marts.vendas_top_produtos
              WHERE sku = 'MED-0001'
              """)) {
        assertTrue(rs.next());
        assertEquals(3, rs.getLong("qtd_vendida"));
        assertEquals(0, new BigDecimal("29.00").compareTo(rs.getBigDecimal("receita_liquida")));
        assertEquals(1, rs.getInt("ranking_categoria"));
        assertEquals(2, rs.getInt("ranking_geral"));
      }
    }
  }

  @Test
  void deveCalcularRupturaEGiroDeEstoqueERefletirReposicao() throws SQLException {
    final UUID lote1 = UUID.randomUUID();
    final UUID lote2 = UUID.randomUUID();
    final OffsetDateTime t1 = OffsetDateTime.parse("2026-05-10T10:00:00-03:00");
    final OffsetDateTime t2 = OffsetDateTime.parse("2026-05-11T10:00:00-03:00");

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
        {"id": 10, "sku": "MED-0001", "nome": "Dipirona 500mg", "categoria": "Medicamentos", "fabricante": "Genéricos BR", "preco_unitario": 10.00, "controlado": false}
        """,
        t1);
    inserirRaw(
        "produto",
        lote1,
        "11",
        """
        {"id": 11, "sku": "HIG-0002", "nome": "Creme dental 90g", "categoria": "Higiene", "fabricante": "Sorriso", "preco_unitario": 6.50, "controlado": false}
        """,
        t1);

    // Dipirona em ruptura (saldo 4 < minimo 10) e com 8 unidades vendidas em 2 dias
    inserirRaw(
        "venda",
        lote1,
        "200",
        """
        {"id": 200, "loja_id": 1, "cliente_id": null, "canal": "PDV", "status": "CONCLUIDA", "forma_pagamento": "PIX", "valor_total": 80.00, "realizada_em": "2026-05-10T09:00:00-03:00"}
        """,
        t1);
    inserirRaw(
        "item_venda",
        lote1,
        "2000",
        """
        {"id": 2000, "venda_id": 200, "produto_id": 10, "quantidade": 8, "preco_unitario": 10.00, "desconto": 0.00}
        """,
        t1);
    inserirRaw(
        "estoque_atual",
        lote1,
        "1:10",
        """
        {"loja_id": 1, "produto_id": 10, "quantidade": 4, "estoque_minimo": 10, "atualizado_em": "2026-05-10T10:00:00-03:00"}
        """,
        t1);
    // Creme dental sem ruptura (saldo 25 >= 10) e sem vendas
    inserirRaw(
        "estoque_atual",
        lote1,
        "1:11",
        """
        {"loja_id": 1, "produto_id": 11, "quantidade": 25, "estoque_minimo": 10, "atualizado_em": "2026-05-10T10:00:00-03:00"}
        """,
        t1);

    transformacao.executar(ContextoExecucao.novo());
    agregacao.executar(ContextoExecucao.novo());

    try (Connection con = dw.getConnection();
        Statement st = con.createStatement()) {
      assertEquals(1, contar(st, "SELECT count(*) FROM marts.estoque_ruptura"));
      assertEquals(2, contar(st, "SELECT count(*) FROM marts.estoque_giro"));

      try (ResultSet rs =
          st.executeQuery(
              "SELECT sku, quantidade_saldo, estoque_minimo, deficit FROM marts.estoque_ruptura")) {
        assertTrue(rs.next());
        assertEquals("MED-0001", rs.getString("sku"));
        assertEquals(4, rs.getInt("quantidade_saldo"));
        assertEquals(10, rs.getInt("estoque_minimo"));
        assertEquals(6, rs.getInt("deficit"));
      }

      try (ResultSet rs =
          st.executeQuery(
              """
              SELECT qtd_vendida_30d, media_diaria_vendas, giro_estoque, dias_cobertura
              FROM marts.estoque_giro WHERE sku = 'MED-0001'
              """)) {
        assertTrue(rs.next());
        assertEquals(8, rs.getLong("qtd_vendida_30d"));
        assertEquals(0, new BigDecimal("8.00").compareTo(rs.getBigDecimal("media_diaria_vendas")));
        assertEquals(0, new BigDecimal("2.00").compareTo(rs.getBigDecimal("giro_estoque")));
        assertEquals(0, new BigDecimal("0.5").compareTo(rs.getBigDecimal("dias_cobertura")));
      }

      try (ResultSet rs =
          st.executeQuery(
              "SELECT qtd_vendida_30d, dias_cobertura FROM marts.estoque_giro WHERE sku = 'HIG-0002'")) {
        assertTrue(rs.next());
        assertEquals(0, rs.getLong("qtd_vendida_30d"));
        assertNull(rs.getObject("dias_cobertura"));
      }
    }

    // Reposição no dia seguinte tira MED-0001 da ruptura; marts.estoque_ruptura deve ficar vazio
    inserirRaw(
        "estoque_atual",
        lote2,
        "1:10",
        """
        {"loja_id": 1, "produto_id": 10, "quantidade": 30, "estoque_minimo": 10, "atualizado_em": "2026-05-11T10:00:00-03:00"}
        """,
        t2);

    transformacao.executar(ContextoExecucao.novo());
    agregacao.executar(ContextoExecucao.novo());
    // Reexecução idempotente sem mudanças
    agregacao.executar(ContextoExecucao.novo());

    try (Connection con = dw.getConnection();
        Statement st = con.createStatement()) {
      assertEquals(0, contar(st, "SELECT count(*) FROM marts.estoque_ruptura"));
      assertEquals(2, contar(st, "SELECT count(*) FROM marts.estoque_giro"));
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
