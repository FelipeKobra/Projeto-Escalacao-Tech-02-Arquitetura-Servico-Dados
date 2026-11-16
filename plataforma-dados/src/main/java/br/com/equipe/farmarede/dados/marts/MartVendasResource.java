package br.com.equipe.farmarede.dados.marts;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.util.List;

/**
 * CONSUMO: Data Mart de Vendas (diretoria comercial, marketing).
 *
 * <p>Ver checklist em docs/divisao-do-trabalho.md (parte do Romulo). Respostas como records DTO;
 * consultas parametrizadas; paginação com limite máximo.
 */
@Path("/marts/vendas")
@Produces(MediaType.APPLICATION_JSON)
public class MartVendasResource {

  /** Receita, nº de vendas e ticket médio por dia/loja/canal. */
  @GET
  @Path("/diarias")
  public List<?> diarias(
      @QueryParam("de") final LocalDate de,
      @QueryParam("ate") final LocalDate ate,
      @QueryParam("loja") final String loja) {
    // TODO (Romulo): ler marts.vendas_diarias com os filtros.
    throw new UnsupportedOperationException("TODO (Romulo): MartVendasResource.diarias");
  }

  /** Ranking de produtos por receita/quantidade, opcionalmente por categoria. */
  @GET
  @Path("/top-produtos")
  public List<?> topProdutos(
      @QueryParam("categoria") final String categoria, @QueryParam("limite") final Integer limite) {
    // TODO (Romulo): ler marts.vendas_top_produtos.
    throw new UnsupportedOperationException("TODO (Romulo): MartVendasResource.topProdutos");
  }
}
