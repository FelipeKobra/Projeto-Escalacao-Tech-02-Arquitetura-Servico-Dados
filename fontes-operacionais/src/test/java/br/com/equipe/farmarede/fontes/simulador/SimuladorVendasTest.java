package br.com.equipe.farmarede.fontes.simulador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.equipe.farmarede.fontes.operacional.ClienteRepository;
import br.com.equipe.farmarede.fontes.operacional.EstoqueAtual;
import br.com.equipe.farmarede.fontes.operacional.EstoqueAtualRepository;
import br.com.equipe.farmarede.fontes.operacional.ItemVenda;
import br.com.equipe.farmarede.fontes.operacional.Loja;
import br.com.equipe.farmarede.fontes.operacional.MovimentoEstoque;
import br.com.equipe.farmarede.fontes.operacional.MovimentoEstoqueRepository;
import br.com.equipe.farmarede.fontes.operacional.Venda;
import br.com.equipe.farmarede.fontes.operacional.VendaRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Simulador dos produtores. Cada teste roda em {@link TestTransaction} (rollback no fim) para não
 * alterar as contagens do seed que outros testes conferem.
 */
@QuarkusTest
class SimuladorVendasTest {

  @Inject SimuladorVendas simulador;
  @Inject SimuladorConfig config;
  @Inject VendaRepository vendas;
  @Inject MovimentoEstoqueRepository movimentos;
  @Inject EstoqueAtualRepository estoque;
  @Inject ClienteRepository clientes;

  @Test
  @TestTransaction
  void cicloCriaVendasComItensEValorTotalCoerente() {
    final long ultimaVenda = maxId("Venda");

    final int criadas = simulador.executarCiclo(new Random(42));

    final List<Venda> novas = vendas.list("id > ?1", ultimaVenda);
    assertEquals(config.vendasPorCiclo(), criadas);
    assertEquals(criadas, novas.size());
    for (final Venda venda : novas) {
      final int itens = venda.getItens().size();
      assertTrue(itens >= 1 && itens <= 4, "venda com " + itens + " itens");
      assertEquals(0, somaDosItens(venda).compareTo(venda.getValorTotal()));
      if (venda.getCanal() == Venda.Canal.ECOMMERCE) {
        assertNotNull(venda.getCliente(), "e-commerce sempre tem cliente");
        assertEquals(Loja.Tipo.ONLINE, venda.getLoja().getTipo());
        assertTrue(venda.getFormaPagamento() != Venda.FormaPagamento.DINHEIRO);
      } else {
        assertEquals(Loja.Tipo.FISICA, venda.getLoja().getTipo());
      }
    }
  }

  @Test
  @TestTransaction
  void cadaItemGeraSaidaVendaEBaixaNoEstoque() {
    final long ultimaVenda = maxId("Venda");
    final long ultimoMovimento = maxId("MovimentoEstoque");
    final long saldoAntes = saldoTotal();

    simulador.executarCiclo(new Random(7));

    final List<ItemVenda> itens =
        vendas.list("id > ?1", ultimaVenda).stream().flatMap(v -> v.getItens().stream()).toList();
    final List<MovimentoEstoque> novos = movimentos.list("id > ?1", ultimoMovimento);
    final List<MovimentoEstoque> saidas =
        novos.stream().filter(m -> m.getTipo() == MovimentoEstoque.Tipo.SAIDA_VENDA).toList();
    assertEquals(itens.size(), saidas.size());
    assertEquals(
        itens.stream().mapToInt(ItemVenda::getQuantidade).sum(),
        -saidas.stream().mapToInt(MovimentoEstoque::getQuantidade).sum());
    assertTrue(saidas.stream().allMatch(m -> m.getQuantidade() < 0));
    // O saldo muda exatamente pela soma dos movimentos do ciclo (vendas, reposições, devoluções).
    assertEquals(
        saldoAntes + novos.stream().mapToInt(MovimentoEstoque::getQuantidade).sum(), saldoTotal());
  }

  @Test
  @TestTransaction
  void estoqueNuncaFicaNegativo() {
    estoque.update("quantidade = 1");
    estoque.getEntityManager().clear();

    for (int ciclo = 0; ciclo < 5; ciclo++) {
      simulador.executarCiclo(new Random(ciclo));
    }

    estoque.getEntityManager().flush();
    assertEquals(0, estoque.count("quantidade < 0"));
  }

  @Test
  @TestTransaction
  void reposicaoGeraEntradaEAumentaSaldo() {
    final long ultimoMovimento = maxId("MovimentoEstoque");
    final long saldoAntes = saldoTotal();

    simulador.reporEstoque(new Random(1));

    final List<MovimentoEstoque> entradas =
        movimentos.list("id > ?1 and tipo = ?2", ultimoMovimento, MovimentoEstoque.Tipo.ENTRADA);
    assertFalse(entradas.isEmpty());
    assertTrue(entradas.stream().allMatch(m -> m.getQuantidade() > 0));
    assertEquals(
        saldoAntes + entradas.stream().mapToInt(MovimentoEstoque::getQuantidade).sum(),
        saldoTotal());
  }

  @Test
  @TestTransaction
  void cancelamentoMarcaVendaEDevolveEstoque() {
    simulador.executarCiclo(new Random(3));
    final long ultimoMovimento = maxId("MovimentoEstoque");
    final long saldoAntes = saldoTotal();

    final Venda cancelada = simulador.cancelarVendaRecente(new Random(3)).orElseThrow();

    assertEquals(Venda.Status.CANCELADA, cancelada.getStatus());
    final List<MovimentoEstoque> devolucoes =
        movimentos.list("id > ?1 and tipo = ?2", ultimoMovimento, MovimentoEstoque.Tipo.AJUSTE);
    assertEquals(cancelada.getItens().size(), devolucoes.size());
    assertEquals(
        saldoAntes + cancelada.getItens().stream().mapToInt(ItemVenda::getQuantidade).sum(),
        saldoTotal());
  }

  @Test
  @TestTransaction
  void clienteNovoEhCadastradoComEmailUnico() {
    final long antes = clientes.count();

    simulador.criarClienteNovo(new Random(5));
    simulador.criarClienteNovo(new Random(5));

    assertEquals(antes + 2, clientes.count());
  }

  @Test
  @TestTransaction
  void agendadoNaoFazNadaQuandoDesabilitado() {
    final long antes = vendas.count();

    simulador.executarAgendado();

    assertFalse(config.habilitado());
    assertEquals(antes, vendas.count());
  }

  private static BigDecimal somaDosItens(final Venda venda) {
    return venda.getItens().stream()
        .map(
            i ->
                i.getPrecoUnitario()
                    .multiply(BigDecimal.valueOf(i.getQuantidade()))
                    .subtract(i.getDesconto()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private long maxId(final String entidade) {
    return vendas
        .getEntityManager()
        .createQuery("select coalesce(max(e.id), 0) from " + entidade + " e", Long.class)
        .getSingleResult();
  }

  private long saldoTotal() {
    estoque.getEntityManager().flush();
    return estoque.listAll().stream().mapToLong(EstoqueAtual::getQuantidade).sum();
  }
}
