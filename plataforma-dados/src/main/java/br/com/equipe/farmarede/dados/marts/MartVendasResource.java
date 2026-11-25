package br.com.equipe.farmarede.dados.marts;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.util.List;

/** CONSUMO: Data Mart de Vendas (diretoria comercial, marketing). */
@Path("/marts/vendas")
@Produces(MediaType.APPLICATION_JSON)
public class MartVendasResource {

  private final ConsultarVendasDiariasUseCase consultarVendasDiarias;
  private final ConsultarTopProdutosUseCase consultarTopProdutos;

  public MartVendasResource(
      final ConsultarVendasDiariasUseCase consultarVendasDiarias,
      final ConsultarTopProdutosUseCase consultarTopProdutos) {
    this.consultarVendasDiarias = consultarVendasDiarias;
    this.consultarTopProdutos = consultarTopProdutos;
  }

  /** Receita, nº de vendas e ticket médio por dia/loja/canal. */
  @GET
  @Path("/diarias")
  public List<VendaDiariaDto> diarias(
      @QueryParam("de") final LocalDate de,
      @QueryParam("ate") final LocalDate ate,
      @QueryParam("loja") final String loja,
      @QueryParam("canal") final String canal,
      @QueryParam("pagina") final Integer pagina,
      @QueryParam("tamanho") final Integer tamanho) {
    return consultarVendasDiarias.executar(de, ate, loja, canal, pagina, tamanho);
  }

  /** Ranking de produtos por receita/quantidade, opcionalmente por categoria e período. */
  @GET
  @Path("/top-produtos")
  public List<TopProdutoDto> topProdutos(
      @QueryParam("categoria") final String categoria,
      @QueryParam("periodo") final String periodo,
      @QueryParam("limite") final Integer limite,
      @QueryParam("pagina") final Integer pagina) {
    return consultarTopProdutos.executar(categoria, periodo, limite, pagina);
  }
}
