package br.com.equipe.farmarede.dados.marts;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * CONSUMO: Data Mart de Estoque (compras/abastecimento, gerente de loja).
 *
 * <p>Ver checklist em docs/divisao-do-trabalho.md (parte do Romulo).
 */
@Path("/marts/estoque")
@Produces(MediaType.APPLICATION_JSON)
public class MartEstoqueResource {

  /** Produtos abaixo do estoque mínimo, por loja. */
  @GET
  @Path("/ruptura")
  public List<?> ruptura(@QueryParam("loja") final String loja) {
    // TODO (Romulo): ler marts.estoque_ruptura.
    throw new UnsupportedOperationException("TODO (Romulo): MartEstoqueResource.ruptura");
  }

  /** Giro e dias de cobertura por produto/loja. */
  @GET
  @Path("/giro")
  public List<?> giro(
      @QueryParam("loja") final String loja, @QueryParam("categoria") final String categoria) {
    // TODO (Romulo): ler marts.estoque_giro.
    throw new UnsupportedOperationException("TODO (Romulo): MartEstoqueResource.giro");
  }
}
