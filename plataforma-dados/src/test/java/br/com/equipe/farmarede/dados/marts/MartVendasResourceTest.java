package br.com.equipe.farmarede.dados.marts;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import br.com.equipe.farmarede.dados.pipeline.ContextoExecucao;
import br.com.equipe.farmarede.dados.transformacao.TransformacaoCuradaEtapa;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class MartVendasResourceTest {

  @Inject TransformacaoCuradaEtapa transformacao;
  @Inject AgregacaoMartsEtapa agregacao;
  @Inject AgroalDataSource dw;

  @BeforeEach
  void prepararFixtures() throws SQLException {
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

    final UUID lote = UUID.randomUUID();
    final OffsetDateTime ts = OffsetDateTime.parse("2026-05-10T12:00:00-03:00");

    inserirRaw(
        "loja",
        lote,
        "1",
        """
        {"id": 1, "codigo": "SP01", "nome": "FarmaRede Paulista", "cidade": "São Paulo", "uf": "SP", "tipo": "FISICA"}
        """,
        ts);
    inserirRaw(
        "loja",
        lote,
        "2",
        """
        {"id": 2, "codigo": "RJ01", "nome": "FarmaRede Centro Rio", "cidade": "Rio de Janeiro", "uf": "RJ", "tipo": "FISICA"}
        """,
        ts);
    inserirRaw(
        "produto",
        lote,
        "10",
        """
        {"id": 10, "sku": "MED-0001", "nome": "Dipirona 500mg", "categoria": "Medicamentos", "fabricante": "Genéricos BR", "preco_unitario": 10.00, "controlado": false}
        """,
        ts);
    inserirRaw(
        "produto",
        lote,
        "11",
        """
        {"id": 11, "sku": "HIG-0001", "nome": "Protetor Solar", "categoria": "Dermocosméticos", "fabricante": "SolCare", "preco_unitario": 50.00, "controlado": false}
        """,
        ts);
    inserirRaw(
        "produto",
        lote,
        "12",
        """
        {"id": 12, "sku": "MED-0002", "nome": "Paracetamol 750mg", "categoria": "Medicamentos", "fabricante": "Genéricos BR", "preco_unitario": 20.00, "controlado": false}
        """,
        ts);

    // Dia 2026-05-10 na SP01 PDV: Dipirona 2x10 = 20.00
    inserirRaw(
        "venda",
        lote,
        "100",
        """
        {"id": 100, "loja_id": 1, "cliente_id": null, "canal": "PDV", "status": "CONCLUIDA", "forma_pagamento": "PIX", "valor_total": 20.00, "realizada_em": "2026-05-10T10:00:00-03:00"}
        """,
        ts);
    inserirRaw(
        "item_venda",
        lote,
        "1000",
        """
        {"id": 1000, "venda_id": 100, "produto_id": 10, "quantidade": 2, "preco_unitario": 10.00, "desconto": 0.00}
        """,
        ts);

    // Dia 2026-05-11 na RJ01 ECOMMERCE: Protetor 1x50 = 50.00 + Paracetamol 1x20 = 20.00
    inserirRaw(
        "venda",
        lote,
        "101",
        """
        {"id": 101, "loja_id": 2, "cliente_id": null, "canal": "ECOMMERCE", "status": "CONCLUIDA", "forma_pagamento": "CREDITO", "valor_total": 70.00, "realizada_em": "2026-05-11T15:00:00-03:00"}
        """,
        ts);
    inserirRaw(
        "item_venda",
        lote,
        "1001",
        """
        {"id": 1001, "venda_id": 101, "produto_id": 11, "quantidade": 1, "preco_unitario": 50.00, "desconto": 0.00}
        """,
        ts);
    inserirRaw(
        "item_venda",
        lote,
        "1002",
        """
        {"id": 1002, "venda_id": 101, "produto_id": 12, "quantidade": 1, "preco_unitario": 20.00, "desconto": 0.00}
        """,
        ts);

    transformacao.executar(ContextoExecucao.novo());
    agregacao.executar(ContextoExecucao.novo());
  }

  @Test
  void deveConsultarVendasDiariasComFiltrosEPaginacao() {
    given()
        .when()
        .get("/marts/vendas/diarias")
        .then()
        .statusCode(200)
        .body("$", hasSize(2))
        .body("[0].data", equalTo("2026-05-11"))
        .body("[0].lojaCodigo", equalTo("RJ01"))
        .body("[0].receitaLiquida", equalTo(70.0F));

    given()
        .queryParam("de", "2026-05-10")
        .queryParam("ate", "2026-05-10")
        .queryParam("loja", "sp01")
        .when()
        .get("/marts/vendas/diarias")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].lojaCodigo", equalTo("SP01"))
        .body("[0].receitaLiquida", equalTo(20.0F));

    given()
        .queryParam("pagina", 1)
        .queryParam("tamanho", 1)
        .when()
        .get("/marts/vendas/diarias")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].lojaCodigo", equalTo("SP01"));
  }

  @Test
  void deveConsultarTopProdutosPorCategoriaELimite() {
    given()
        .queryParam("categoria", "medicamentos")
        .queryParam("limite", 1)
        .when()
        .get("/marts/vendas/top-produtos")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].sku", equalTo("MED-0001"))
        .body("[0].rankingCategoria", equalTo(1));

    given()
        .queryParam("periodo", "2026-05")
        .queryParam("limite", 500)
        .when()
        .get("/marts/vendas/top-produtos")
        .then()
        .statusCode(200)
        .body("$", hasSize(3))
        .body("[0].sku", equalTo("HIG-0001"))
        .body("[0].rankingGeral", equalTo(1));
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
}
