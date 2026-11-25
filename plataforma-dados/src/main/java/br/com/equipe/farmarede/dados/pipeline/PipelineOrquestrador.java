package br.com.equipe.farmarede.dados.pipeline;

import io.quarkus.arc.All;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Orquestra o pipeline: executa todas as {@link EtapaPipeline} em ordem (10 ingestão, 20 curada, 30
 * marts) e registra cada uma em {@code controle.execucao_pipeline}.
 *
 * <p>Cada registro (EM_ANDAMENTO e o status final) é gravado na sua própria transação, para que a
 * FALHA fique no histórico mesmo quando a etapa faz rollback do que estava gravando.
 */
@ApplicationScoped
public class PipelineOrquestrador {

  private static final int TAMANHO_MAXIMO_DETALHE = 2000;

  private final List<EtapaPipeline> etapas;
  private final ExecucaoPipelineRepository execucoes;
  private final boolean habilitado;
  private final AtomicBoolean emExecucao = new AtomicBoolean();

  public PipelineOrquestrador(
      @All final List<EtapaPipeline> etapas,
      final ExecucaoPipelineRepository execucoes,
      @ConfigProperty(name = "pipeline.habilitado") final boolean habilitado) {
    this.etapas = etapas.stream().sorted(Comparator.comparingInt(EtapaPipeline::ordem)).toList();
    this.execucoes = execucoes;
    this.habilitado = habilitado;
  }

  /**
   * Disparo pelo cron {@code pipeline.cron}; sem execução concorrente e respeitando o liga/desliga.
   */
  @Scheduled(cron = "{pipeline.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void executarAgendado() {
    if (!habilitado) {
      return;
    }
    executar();
  }

  /**
   * Executa todas as etapas em ordem crescente de {@link EtapaPipeline#ordem()}. Para cada etapa:
   * grava EM_ANDAMENTO, executa, grava SUCESSO (com registros/detalhe) ou FALHA (com a mensagem) e,
   * se falhar, não executa as próximas.
   *
   * @return o lote executado
   */
  public UUID executar() {
    // Cron (SKIP) e POST manual compartilham esta trava: nunca dois lotes ao mesmo tempo.
    if (!emExecucao.compareAndSet(false, true)) {
      throw new PipelineEmExecucaoException();
    }
    try {
      final ContextoExecucao contexto = ContextoExecucao.novo();
      Log.infof("Pipeline lote %s iniciado (%d etapas)", contexto.loteId(), etapas.size());
      for (final EtapaPipeline etapa : etapas) {
        if (!executarEtapa(etapa, contexto)) {
          Log.warnf("Pipeline lote %s interrompido na etapa %s", contexto.loteId(), etapa.nome());
          break;
        }
      }
      return contexto.loteId();
    } finally {
      emExecucao.set(false);
    }
  }

  private boolean executarEtapa(final EtapaPipeline etapa, final ContextoExecucao contexto) {
    final long id = QuarkusTransaction.requiringNew().call(() -> registrarInicio(etapa, contexto));
    final ResultadoEtapa resultado;
    try {
      resultado = etapa.executar(contexto);
    } catch (RuntimeException | Error e) {
      Log.errorf(e, "Etapa %s falhou no lote %s", etapa.nome(), contexto.loteId());
      QuarkusTransaction.requiringNew()
          .run(() -> finalizar(id, ExecucaoPipeline.Status.FALHA, 0, descrever(e)));
      if (e instanceof Error erro) {
        throw erro;
      }
      return false;
    }
    // Fora do try: um erro ao gravar o SUCESSO não pode marcar como FALHA uma etapa que comitou.
    QuarkusTransaction.requiringNew()
        .run(
            () ->
                finalizar(
                    id,
                    ExecucaoPipeline.Status.SUCESSO,
                    resultado.registrosProcessados(),
                    resultado.detalhe()));
    return true;
  }

  private long registrarInicio(final EtapaPipeline etapa, final ContextoExecucao contexto) {
    final ExecucaoPipeline execucao = new ExecucaoPipeline();
    execucao.setLoteId(contexto.loteId());
    execucao.setEtapa(etapa.nome());
    execucao.setStatus(ExecucaoPipeline.Status.EM_ANDAMENTO);
    execucao.setIniciadoEm(OffsetDateTime.now());
    execucoes.persist(execucao);
    return execucao.getId();
  }

  private void finalizar(
      final long id,
      final ExecucaoPipeline.Status status,
      final long registros,
      final String detalhe) {
    final ExecucaoPipeline execucao = execucoes.findById(id);
    execucao.setStatus(status);
    execucao.setFinalizadoEm(OffsetDateTime.now());
    execucao.setRegistrosProcessados(registros);
    execucao.setDetalhe(limitar(detalhe));
  }

  private static String descrever(final Throwable e) {
    return e.getClass().getSimpleName() + ": " + e.getMessage();
  }

  private static String limitar(final String texto) {
    if (texto == null || texto.length() <= TAMANHO_MAXIMO_DETALHE) {
      return texto;
    }
    return texto.substring(0, TAMANHO_MAXIMO_DETALHE);
  }

  /** Já existe um lote em andamento nesta instância. */
  public static final class PipelineEmExecucaoException extends RuntimeException {
    PipelineEmExecucaoException() {
      super("Já existe uma execução do pipeline em andamento");
    }
  }
}
