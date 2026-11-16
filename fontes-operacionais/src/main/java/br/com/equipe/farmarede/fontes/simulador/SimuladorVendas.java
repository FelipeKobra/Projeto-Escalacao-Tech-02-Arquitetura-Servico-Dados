package br.com.equipe.farmarede.fontes.simulador;

import br.com.equipe.farmarede.fontes.operacional.ClienteRepository;
import br.com.equipe.farmarede.fontes.operacional.EstoqueAtualRepository;
import br.com.equipe.farmarede.fontes.operacional.LojaRepository;
import br.com.equipe.farmarede.fontes.operacional.MovimentoEstoqueRepository;
import br.com.equipe.farmarede.fontes.operacional.ProdutoRepository;
import br.com.equipe.farmarede.fontes.operacional.VendaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Simula os PRODUTORES de dados da FarmaRede: PDV das lojas físicas, e-commerce e estoque.
 *
 * <p>Ver checklist em docs/divisao-do-trabalho.md (parte do Felipe).
 */
@ApplicationScoped
public class SimuladorVendas {

  private final SimuladorConfig config;
  private final LojaRepository lojas;
  private final ProdutoRepository produtos;
  private final ClienteRepository clientes;
  private final VendaRepository vendas;
  private final MovimentoEstoqueRepository movimentos;
  private final EstoqueAtualRepository estoque;

  public SimuladorVendas(
      final SimuladorConfig config,
      final LojaRepository lojas,
      final ProdutoRepository produtos,
      final ClienteRepository clientes,
      final VendaRepository vendas,
      final MovimentoEstoqueRepository movimentos,
      final EstoqueAtualRepository estoque) {
    this.config = config;
    this.lojas = lojas;
    this.produtos = produtos;
    this.clientes = clientes;
    this.vendas = vendas;
    this.movimentos = movimentos;
    this.estoque = estoque;
  }

  // TODO (Felipe): @Scheduled(every = "${simulador.intervalo}", skipExecutionIf = ...) que chama
  //  executarCiclo() só quando config.habilitado().

  /**
   * Um ciclo de simulação: {@code config.vendasPorCiclo()} vendas (PDV e e-commerce) com 1 a 4
   * itens cada; para cada item, movimento SAIDA_VENDA (quantidade negativa) e baixa no
   * estoque_atual. Às vezes: reposição (ENTRADA), cancelamento e cliente novo.
   *
   * @return quantas vendas foram criadas
   */
  @Transactional
  public int executarCiclo() {
    // TODO (Felipe): implementar. Venda.adicionarItem(...) mantém a associação; valor_total = soma
    //  dos itens - descontos; e-commerce sempre com cliente; nunca deixar estoque negativo.
    throw new UnsupportedOperationException("TODO (Felipe): SimuladorVendas.executarCiclo");
  }
}
