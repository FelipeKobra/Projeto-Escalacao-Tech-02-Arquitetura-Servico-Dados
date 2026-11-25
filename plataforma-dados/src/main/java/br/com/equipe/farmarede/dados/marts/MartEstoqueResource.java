package br.com.equipe.farmarede.dados.marts;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** CONSUMO: Data Mart de Estoque (compras/abastecimento, gerente de loja). */
@Path("/marts/estoque")
@Produces(MediaType.APPLICATION_JSON)
public class MartEstoqueResource {

  private final ConsultarEstoqueRupturaUseCase consultarEstoqueRuptura;
  private final ConsultarEstoqueGiroUseCase consultarEstoqueGiro;

  public MartEstoqueResource(
      final ConsultarEstoqueRupturaUseCase consultarEstoqueRuptura,
      final ConsultarEstoqueGiroUseCase consultarEstoqueGiro) {
    this.consultarEstoqueRuptura = consultarEstoqueRuptura;
    this.consultarEstoqueGiro = consultarEstoqueGiro;
  }

  /** Produtos abaixo do estoque mínimo, por loja. */
  @GET
  @Path("/ruptura")
  public List<EstoqueRupturaDto> ruptura(
      @QueryParam("loja") final String loja,
      @QueryParam("pagina") final Integer pagina,
      @QueryParam("tamanho") final Integer tamanho) {
    return consultarEstoqueRuptura.executar(loja, pagina, tamanho);
  }

  /** Giro e dias de cobertura por produto/loja. */
  @GET
  @Path("/giro")
  public List<EstoqueGiroDto> giro(
      @QueryParam("loja") final String loja,
      @QueryParam("categoria") final String categoria,
      @QueryParam("pagina") final Integer pagina,
      @QueryParam("tamanho") final Integer tamanho) {
    return consultarEstoqueGiro.executar(loja, categoria, pagina, tamanho);
  }
}
