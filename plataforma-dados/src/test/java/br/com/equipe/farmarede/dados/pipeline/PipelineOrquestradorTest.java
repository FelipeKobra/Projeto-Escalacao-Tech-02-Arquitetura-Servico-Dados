package br.com.equipe.farmarede.dados.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.panache.common.Sort;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Orquestrador com etapas falsas (construído à mão) gravando no {@code controle.execucao_pipeline}
 * real do Dev Services.
 */
@QuarkusTest
class PipelineOrquestradorTest {

  @Inject ExecucaoPipelineRepository execucoes;

  @Test
  void executaAsEtapasEmOrdemERegistraSucesso() {
    final EtapaFalsa ingestao = EtapaFalsa.ok("teste-10", 10, 7);
    final EtapaFalsa curada = EtapaFalsa.ok("teste-20", 20, 3);
    final EtapaFalsa marts = EtapaFalsa.ok("teste-30", 30, 1);
    final PipelineOrquestrador orquestrador = orquestrador(true, marts, ingestao, curada);

    final UUID lote = orquestrador.executar();

    final List<ExecucaoPipeline> registros = registrosDo(lote);
    assertEquals(
        List.of("teste-10", "teste-20", "teste-30"),
        registros.stream().map(ExecucaoPipeline::getEtapa).toList());
    assertTrue(registros.stream().allMatch(r -> r.getStatus() == ExecucaoPipeline.Status.SUCESSO));
    assertEquals(7, registros.getFirst().getRegistrosProcessados());
    assertEquals("detalhe de teste-10", registros.getFirst().getDetalhe());
    registros.forEach(r -> assertNotNull(r.getFinalizadoEm()));
  }

  @Test
  void falhaFicaRegistradaEInterrompeAsProximasEtapas() {
    final EtapaFalsa ingestao = EtapaFalsa.ok("teste-10", 10, 5);
    final EtapaFalsa quebrada =
        new EtapaFalsa(
            "teste-20",
            20,
            () -> {
              throw new IllegalStateException("raw corrompida");
            });
    final EtapaFalsa marts = EtapaFalsa.ok("teste-30", 30, 1);

    final UUID lote = orquestrador(true, ingestao, quebrada, marts).executar();

    final List<ExecucaoPipeline> registros = registrosDo(lote);
    assertEquals(2, registros.size());
    assertEquals(ExecucaoPipeline.Status.SUCESSO, registros.get(0).getStatus());
    final ExecucaoPipeline falha = registros.get(1);
    assertEquals(ExecucaoPipeline.Status.FALHA, falha.getStatus());
    assertTrue(falha.getDetalhe().contains("raw corrompida"), falha.getDetalhe());
    assertNotNull(falha.getFinalizadoEm());
    assertEquals(0, marts.chamadas.get(), "etapa depois da falha não roda");
  }

  @Test
  void agendamentoDesabilitadoNaoExecutaNada() {
    final EtapaFalsa etapa = EtapaFalsa.ok("teste-10", 10, 1);

    orquestrador(false, etapa).executarAgendado();

    assertEquals(0, etapa.chamadas.get());
  }

  @Test
  void agendamentoHabilitadoExecutaOPipeline() {
    final EtapaFalsa etapa = EtapaFalsa.ok("teste-10", 10, 1);

    orquestrador(true, etapa).executarAgendado();

    assertEquals(1, etapa.chamadas.get());
  }

  private PipelineOrquestrador orquestrador(
      final boolean habilitado, final EtapaPipeline... etapas) {
    return new PipelineOrquestrador(List.of(etapas), execucoes, habilitado);
  }

  private List<ExecucaoPipeline> registrosDo(final UUID lote) {
    return execucoes.list("loteId", Sort.by("id"), lote);
  }

  /** Etapa de teste: conta as chamadas e devolve o que o {@code Supplier} mandar. */
  private static final class EtapaFalsa implements EtapaPipeline {
    private final String nome;
    private final int ordem;
    private final Supplier<ResultadoEtapa> comportamento;
    private final AtomicInteger chamadas = new AtomicInteger();

    EtapaFalsa(final String nome, final int ordem, final Supplier<ResultadoEtapa> comportamento) {
      this.nome = nome;
      this.ordem = ordem;
      this.comportamento = comportamento;
    }

    static EtapaFalsa ok(final String nome, final int ordem, final long registros) {
      return new EtapaFalsa(nome, ordem, () -> new ResultadoEtapa(registros, "detalhe de " + nome));
    }

    @Override
    public String nome() {
      return nome;
    }

    @Override
    public int ordem() {
      return ordem;
    }

    @Override
    public ResultadoEtapa executar(final ContextoExecucao contexto) {
      chamadas.incrementAndGet();
      return comportamento.get();
    }
  }
}
