package br.com.equipe.farmarede.dados.pipeline;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/** API de operação do pipeline: disparo manual e histórico. */
@QuarkusTest
class PipelineResourceTest {

  private static final String UUID_REGEX =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  @Test
  void postDisparaOPipelineERespondeAceitoComOLote() {
    given()
        .when()
        .post("/pipeline/execucoes")
        .then()
        .statusCode(202)
        .contentType(ContentType.JSON)
        .body("loteId", matchesPattern(UUID_REGEX));
  }

  @Test
  void getListaOHistoricoComOLoteMaisRecentePrimeiro() {
    final String lote =
        given().when().post("/pipeline/execucoes").then().statusCode(202).extract().path("loteId");

    given()
        .when()
        .get("/pipeline/execucoes")
        .then()
        .statusCode(200)
        .body("size()", lessThanOrEqualTo(50))
        .body("[0].loteId", equalTo(lote))
        .body("loteId", hasItem(lote))
        .body("etapa", hasItem("ingestao-oltp-raw"))
        .body("status", everyItem(notNullValue()))
        .body("[0].iniciadoEm", notNullValue());
  }
}
