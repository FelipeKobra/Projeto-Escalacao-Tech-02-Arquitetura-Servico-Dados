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
class MartEstoqueResourceTest {

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
        {"id": 11, "sku": "HIG-0002", "nome": "Creme dental 90g", "categoria": "Higiene", "fabricante": "Sorriso", "preco_unitario": 6.50, "controlado": false}
        """,
        ts);

    // SP01 MED-0001 em ruptura (saldo 2, minimo 10, deficit 8) + venda de 6
    inserirRaw(
        "venda",
        lote,
        "200",
        """
        {"id": 200, "loja_id": 1, "cliente_id": null, "canal": "PDV", "status": "CONCLUIDA", "forma_pagamento": "PIX", "valor_total": 60.00, "realizada_em": "2026-05-10T10:00:00-03:00"}
        """,
        ts);
    inserirRaw(
        "item_venda",
        lote,
        "2000",
        """
        {"id": 2000, "venda_id": 200, "produto_id": 10, "quantidade": 6, "preco_unitario": 10.00, "desconto": 0.00}
        """,
        ts);
    inserirRaw(
        "estoque_atual",
        lote,
        "1:10",
        """
        {"loja_id": 1, "produto_id": 10, "quantidade": 2, "estoque_minimo": 10, "atualizado_em": "2026-05-10T12:00:00-03:00"}
        """,
        ts);
    // RJ01 HIG-0002 em ruptura menor (saldo 7, minimo 10, deficit 3)
    inserirRaw(
        "estoque_atual",
        lote,
        "2:11",
        """
        {"loja_id": 2, "produto_id": 11, "quantidade": 7, "estoque_minimo": 10, "atualizado_em": "2026-05-10T12:00:00-03:00"}
        """,
        ts);
    // SP01 HIG-0002 sem ruptura (saldo 30)
    inserirRaw(
        "estoque_atual",
        lote,
        "1:11",
        """
        {"loja_id": 1, "produto_id": 11, "quantidade": 30, "estoque_minimo": 10, "atualizado_em": "2026-05-10T12:00:00-03:00"}
        """,
        ts);

    transformacao.executar(ContextoExecucao.novo());
    agregacao.executar(ContextoExecucao.novo());
  }

  @Test
  void deveConsultarEstoqueRupturaComFiltroPorLojaEPaginacao() {
    given()
        .when()
        .get("/marts/estoque/ruptura")
        .then()
        .statusCode(200)
        .body("$", hasSize(2))
        .body("[0].lojaCodigo", equalTo("SP01"))
        .body("[0].deficit", equalTo(8))
        .body("[1].lojaCodigo", equalTo("RJ01"))
        .body("[1].deficit", equalTo(3));

    given()
        .queryParam("loja", "rj01")
        .when()
        .get("/marts/estoque/ruptura")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].sku", equalTo("HIG-0002"));

    given()
        .queryParam("pagina", 1)
        .queryParam("tamanho", 1)
        .when()
        .get("/marts/estoque/ruptura")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].lojaCodigo", equalTo("RJ01"));
  }

  @Test
  void deveConsultarEstoqueGiroComFiltrosDeLojaECategoria() {
    given()
        .when()
        .get("/marts/estoque/giro")
        .then()
        .statusCode(200)
        .body("$", hasSize(3))
        .body("[0].sku", equalTo("MED-0001"))
        .body("[0].qtdVendida30d", equalTo(6))
        .body("[0].giroEstoque", equalTo(3.0F));

    given()
        .queryParam("loja", "SP01")
        .queryParam("categoria", "higiene")
        .when()
        .get("/marts/estoque/giro")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].sku", equalTo("HIG-0002"))
        .body("[0].quantidadeSaldo", equalTo(30));
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
