package br.com.equipe.farmarede.fontes.resumo;

import br.com.equipe.farmarede.fontes.operacional.ClienteRepository;
import br.com.equipe.farmarede.fontes.operacional.LojaRepository;
import br.com.equipe.farmarede.fontes.operacional.MovimentoEstoqueRepository;
import br.com.equipe.farmarede.fontes.operacional.ProdutoRepository;
import br.com.equipe.farmarede.fontes.operacional.VendaRepository;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** {@code GET /fontes/resumo}: contagem de registros por tabela do OLTP. */
@Path("/fontes/resumo")
@Produces(MediaType.APPLICATION_JSON)
public class ResumoResource {

  private final LojaRepository lojas;
  private final ProdutoRepository produtos;
  private final ClienteRepository clientes;
  private final VendaRepository vendas;
  private final MovimentoEstoqueRepository movimentos;

  public ResumoResource(
      final LojaRepository lojas,
      final ProdutoRepository produtos,
      final ClienteRepository clientes,
      final VendaRepository vendas,
      final MovimentoEstoqueRepository movimentos) {
    this.lojas = lojas;
    this.produtos = produtos;
    this.clientes = clientes;
    this.vendas = vendas;
    this.movimentos = movimentos;
  }

  @GET
  public ResumoFontes resumo() {
    return new ResumoFontes(
        lojas.count(), produtos.count(), clientes.count(), vendas.count(), movimentos.count());
  }
}
