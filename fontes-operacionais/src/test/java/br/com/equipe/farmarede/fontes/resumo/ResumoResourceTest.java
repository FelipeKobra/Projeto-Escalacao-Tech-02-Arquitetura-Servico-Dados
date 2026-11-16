package br.com.equipe.farmarede.fontes.resumo;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Garante que o Flyway aplicou o schema OLTP e os dados de referência (V1 + V2). */
@QuarkusTest
class ResumoResourceTest {

  @Test
  void resumoMostraDadosDeReferencia() {
    given()
        .when()
        .get("/fontes/resumo")
        .then()
        .statusCode(200)
        .body("lojas", equalTo(4))
        .body("produtos", equalTo(12))
        .body("clientes", equalTo(5))
        .body("movimentosEstoque", equalTo(48));
  }

  @Test
  void healthCheckFicaUp() {
    given().when().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP"));
  }
}
