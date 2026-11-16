package br.com.equipe.farmarede.dados;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/**
 * Garante o contrato da base: os schemas do DW existem (V1) e, em teste, o datasource {@code oltp}
 * tem o schema + seed do módulo oltp-schema. Ingestão e transformações podem partir daqui.
 */
@QuarkusTest
class EstruturaDadosTest {

  @Inject AgroalDataSource dw;

  @Inject
  @DataSource("oltp")
  AgroalDataSource oltp;

  @Test
  void dwTemOsQuatroSchemasDoContrato() throws SQLException {
    final long schemas =
        contar(
            dw,
            "SELECT count(*) FROM information_schema.schemata"
                + " WHERE schema_name IN ('raw', 'curated', 'marts', 'controle')");
    assertEquals(4, schemas);
  }

  @Test
  void rawTemUmaTabelaPorTabelaDoOltp() throws SQLException {
    final long tabelas =
        contar(dw, "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'raw'");
    assertEquals(7, tabelas);
  }

  @Test
  void oltpDeTesteTemDadosDeReferencia() throws SQLException {
    assertEquals(4, contar(oltp, "SELECT count(*) FROM loja"));
    assertEquals(12, contar(oltp, "SELECT count(*) FROM produto"));
  }

  @Test
  void healthCheckFicaUpComOsDoisBancos() {
    given().when().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP"));
  }

  private static long contar(final AgroalDataSource ds, final String sql) throws SQLException {
    try (Connection con = ds.getConnection();
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    }
  }
}
