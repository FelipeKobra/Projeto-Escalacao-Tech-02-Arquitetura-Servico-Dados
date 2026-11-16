package br.com.equipe.farmarede.fontes.simulador;

import br.com.equipe.farmarede.fontes.operacional.Cliente;
import br.com.equipe.farmarede.fontes.operacional.ClienteRepository;
import br.com.equipe.farmarede.fontes.operacional.EstoqueAtual;
import br.com.equipe.farmarede.fontes.operacional.EstoqueAtualRepository;
import br.com.equipe.farmarede.fontes.operacional.ItemVenda;
import br.com.equipe.farmarede.fontes.operacional.Loja;
import br.com.equipe.farmarede.fontes.operacional.LojaRepository;
import br.com.equipe.farmarede.fontes.operacional.MovimentoEstoque;
import br.com.equipe.farmarede.fontes.operacional.MovimentoEstoqueRepository;
import br.com.equipe.farmarede.fontes.operacional.Produto;
import br.com.equipe.farmarede.fontes.operacional.ProdutoRepository;
import br.com.equipe.farmarede.fontes.operacional.Venda;
import br.com.equipe.farmarede.fontes.operacional.VendaRepository;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Simula os PRODUTORES de dados da FarmaRede: PDV das lojas físicas, e-commerce e estoque.
 *
 * <p>A cada ciclo cria vendas (PDV e e-commerce) com baixa de estoque e, de vez em quando, uma
 * reposição de fornecedor, um cancelamento e um cliente novo. Os métodos recebem o {@link
 * RandomGenerator} para que os testes usem semente fixa.
 */
@ApplicationScoped
public class SimuladorVendas {

  static final double PROB_ECOMMERCE = 0.3;
  static final double PROB_CLIENTE_IDENTIFICADO_NO_PDV = 0.4;
  static final double PROB_DESCONTO = 0.2;
  static final double PROB_REPOSICAO = 0.3;
  static final double PROB_CANCELAMENTO = 0.1;
  static final double PROB_CLIENTE_NOVO = 0.1;
  static final int MAX_ITENS_POR_VENDA = 4;
  static final int MAX_UNIDADES_POR_ITEM = 3;
  static final int MAX_PRODUTOS_POR_REPOSICAO = 3;
  static final int LOTE_MINIMO_REPOSICAO = 40;
  static final int VARIACAO_LOTE_REPOSICAO = 20;
  static final int VENDAS_RECENTES_CANCELAVEIS = 10;
  private static final BigDecimal PERCENTUAL_DESCONTO = new BigDecimal("0.10");

  private final SimuladorConfig config;
  private final LojaRepository lojas;
  private final ProdutoRepository produtos;
  private final ClienteRepository clientes;
  private final VendaRepository vendas;
  private final MovimentoEstoqueRepository movimentos;
  private final EstoqueAtualRepository estoque;
  private final GeradorClientes geradorClientes = new GeradorClientes();

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

  /** Disparado pelo scheduler a cada {@code simulador.intervalo}; respeita o liga/desliga. */
  @Scheduled(
      every = "${simulador.intervalo}",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  @Transactional
  public void executarAgendado() {
    if (!config.habilitado()) {
      return;
    }
    final int criadas = executarCiclo();
    Log.debugf("Simulador: %d vendas criadas no ciclo", criadas);
  }

  /**
   * Um ciclo de simulação: {@code config.vendasPorCiclo()} vendas (PDV e e-commerce) com 1 a 4
   * itens cada; para cada item, movimento SAIDA_VENDA (quantidade negativa) e baixa no
   * estoque_atual. Às vezes: reposição (ENTRADA), cancelamento e cliente novo.
   *
   * @return quantas vendas foram criadas
   */
  @Transactional
  public int executarCiclo() {
    return executarCiclo(ThreadLocalRandom.current());
  }

  @Transactional
  int executarCiclo(final RandomGenerator sorteio) {
    final List<Loja> fisicas = lojas.list("tipo", Loja.Tipo.FISICA);
    final List<Loja> online = lojas.list("tipo", Loja.Tipo.ONLINE);
    final List<Produto> catalogo = produtos.listAll();
    int criadas = 0;
    for (int i = 0; i < config.vendasPorCiclo(); i++) {
      if (criarVenda(sorteio, fisicas, online, catalogo).isPresent()) {
        criadas++;
      }
    }
    if (sorteio.nextDouble() < PROB_REPOSICAO) {
      reporEstoque(sorteio);
    }
    if (sorteio.nextDouble() < PROB_CANCELAMENTO) {
      cancelarVendaRecente(sorteio);
    }
    if (sorteio.nextDouble() < PROB_CLIENTE_NOVO) {
      criarClienteNovo(sorteio);
    }
    return criadas;
  }

  /**
   * Reposição de fornecedor (ENTRADA): os saldos abaixo do mínimo ou, se não houver nenhum, alguns
   * produtos sorteados (pedido programado).
   *
   * @return quantos saldos foram repostos
   */
  @Transactional
  int reporEstoque(final RandomGenerator sorteio) {
    List<EstoqueAtual> candidatos =
        estoque.find("quantidade < estoqueMinimo").withLock(LockModeType.PESSIMISTIC_WRITE).list();
    if (candidatos.isEmpty()) {
      candidatos =
          new ArrayList<>(estoque.findAll().withLock(LockModeType.PESSIMISTIC_WRITE).list());
      Collections.shuffle(candidatos, sorteio);
    }
    final List<EstoqueAtual> repostos =
        candidatos.subList(0, Math.min(MAX_PRODUTOS_POR_REPOSICAO, candidatos.size()));
    for (final EstoqueAtual saldo : repostos) {
      final int lote = LOTE_MINIMO_REPOSICAO + sorteio.nextInt(VARIACAO_LOTE_REPOSICAO);
      movimentarEstoque(saldo, MovimentoEstoque.Tipo.ENTRADA, lote);
    }
    return repostos.size();
  }

  /** Cancela uma das vendas concluídas mais recentes e devolve os itens ao estoque (AJUSTE). */
  @Transactional
  Optional<Venda> cancelarVendaRecente(final RandomGenerator sorteio) {
    final List<Venda> recentes =
        vendas
            .find("status", Sort.descending("id"), Venda.Status.CONCLUIDA)
            .page(0, VENDAS_RECENTES_CANCELAVEIS)
            .list();
    if (recentes.isEmpty()) {
      return Optional.empty();
    }
    final Venda venda = recentes.get(sorteio.nextInt(recentes.size()));
    venda.setStatus(Venda.Status.CANCELADA);
    for (final ItemVenda item : venda.getItens()) {
      final EstoqueAtual saldo = saldoDe(venda.getLoja(), item.getProduto());
      movimentarEstoque(saldo, MovimentoEstoque.Tipo.AJUSTE, item.getQuantidade());
    }
    return Optional.of(venda);
  }

  /** Cadastro de um cliente novo (fidelidade / primeira compra no site). */
  @Transactional
  Cliente criarClienteNovo(final RandomGenerator sorteio) {
    final Cliente cliente = geradorClientes.gerar(sorteio);
    clientes.persist(cliente);
    return cliente;
  }

  private Optional<Venda> criarVenda(
      final RandomGenerator sorteio,
      final List<Loja> fisicas,
      final List<Loja> online,
      final List<Produto> catalogo) {
    final boolean ecommerce = !online.isEmpty() && sorteio.nextDouble() < PROB_ECOMMERCE;
    final Venda venda = new Venda();
    venda.setCanal(ecommerce ? Venda.Canal.ECOMMERCE : Venda.Canal.PDV);
    venda.setLoja(sortear(ecommerce ? online : fisicas, sorteio));
    if (ecommerce || sorteio.nextDouble() < PROB_CLIENTE_IDENTIFICADO_NO_PDV) {
      venda.setCliente(sortearCliente(sorteio));
    }
    venda.setStatus(Venda.Status.CONCLUIDA);
    venda.setFormaPagamento(sortearPagamento(ecommerce, sorteio));
    venda.setRealizadaEm(OffsetDateTime.now());

    final List<Produto> escolhidos = new ArrayList<>(catalogo);
    Collections.shuffle(escolhidos, sorteio);
    final int quantosItens = 1 + sorteio.nextInt(MAX_ITENS_POR_VENDA);
    for (final Produto produto : escolhidos.subList(0, Math.min(quantosItens, catalogo.size()))) {
      adicionarItemSeHouverEstoque(venda, produto, sorteio);
    }
    if (venda.getItens().isEmpty()) {
      return Optional.empty();
    }
    venda.setValorTotal(
        venda.getItens().stream()
            .map(SimuladorVendas::valorLiquido)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    vendas.persist(venda);
    return Optional.of(venda);
  }

  /** Inclui o item na venda e baixa o estoque; sem saldo, o produto fica de fora. */
  private void adicionarItemSeHouverEstoque(
      final Venda venda, final Produto produto, final RandomGenerator sorteio) {
    final EstoqueAtual saldo = saldoDe(venda.getLoja(), produto);
    if (saldo == null || saldo.getQuantidade() <= 0) {
      return;
    }
    final int quantidade =
        Math.min(1 + sorteio.nextInt(MAX_UNIDADES_POR_ITEM), saldo.getQuantidade());
    final ItemVenda item = new ItemVenda();
    item.setProduto(produto);
    item.setQuantidade(quantidade);
    item.setPrecoUnitario(produto.getPrecoUnitario());
    item.setDesconto(
        sorteio.nextDouble() < PROB_DESCONTO
            ? valorBruto(item).multiply(PERCENTUAL_DESCONTO).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO);
    venda.adicionarItem(item);
    movimentarEstoque(saldo, MovimentoEstoque.Tipo.SAIDA_VENDA, -quantidade);
  }

  /** Grava o movimento e aplica a mesma quantidade (com sinal) no saldo corrente. */
  private void movimentarEstoque(
      final EstoqueAtual saldo, final MovimentoEstoque.Tipo tipo, final int quantidade) {
    final int novoSaldo = saldo.getQuantidade() + quantidade;
    if (novoSaldo < 0) {
      throw new IllegalStateException(
          "Estoque negativo para loja=%d produto=%d"
              .formatted(saldo.getId().lojaId(), saldo.getId().produtoId()));
    }
    saldo.setQuantidade(novoSaldo);
    final MovimentoEstoque movimento = new MovimentoEstoque();
    movimento.setLoja(lojas.findById(saldo.getId().lojaId()));
    movimento.setProduto(produtos.findById(saldo.getId().produtoId()));
    movimento.setTipo(tipo);
    movimento.setQuantidade(quantidade);
    movimento.setOcorridoEm(OffsetDateTime.now());
    movimentos.persist(movimento);
  }

  /** Lê o saldo com lock de linha: duas instâncias do simulador não perdem baixa uma da outra. */
  private EstoqueAtual saldoDe(final Loja loja, final Produto produto) {
    return estoque.findById(
        new EstoqueAtual.Id(loja.getId(), produto.getId()), LockModeType.PESSIMISTIC_WRITE);
  }

  private Cliente sortearCliente(final RandomGenerator sorteio) {
    final long total = clientes.count();
    if (total == 0) {
      return criarClienteNovo(sorteio);
    }
    return clientes
        .findAll(Sort.by("id"))
        .page(sorteio.nextInt(Math.toIntExact(total)), 1)
        .firstResult();
  }

  private static Venda.FormaPagamento sortearPagamento(
      final boolean ecommerce, final RandomGenerator sorteio) {
    final Venda.FormaPagamento[] formas = Venda.FormaPagamento.values();
    final Venda.FormaPagamento forma = formas[sorteio.nextInt(formas.length)];
    // Site não recebe dinheiro em espécie.
    return ecommerce && forma == Venda.FormaPagamento.DINHEIRO ? Venda.FormaPagamento.PIX : forma;
  }

  private static <T> T sortear(final List<T> opcoes, final RandomGenerator sorteio) {
    return opcoes.get(sorteio.nextInt(opcoes.size()));
  }

  private static BigDecimal valorBruto(final ItemVenda item) {
    return item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade()));
  }

  private static BigDecimal valorLiquido(final ItemVenda item) {
    return valorBruto(item).subtract(item.getDesconto());
  }
}
