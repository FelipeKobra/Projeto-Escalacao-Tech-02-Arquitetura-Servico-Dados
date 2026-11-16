package br.com.equipe.farmarede.dados.pipeline;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

/**
 * Orquestra o pipeline: executa todas as {@link EtapaPipeline} em ordem (10 ingestão, 20 curada, 30
 * marts) e registra cada uma em {@code controle.execucao_pipeline}.
 *
 * <p>Ver checklist em docs/divisao-do-trabalho.md (parte do Felipe).
 */
@ApplicationScoped
public class PipelineOrquestrador {

  private final List<EtapaPipeline> etapas;
  private final ExecucaoPipelineRepository execucoes;

  public PipelineOrquestrador(
      @All final List<EtapaPipeline> etapas, final ExecucaoPipelineRepository execucoes) {
    this.etapas = etapas;
    this.execucoes = execucoes;
  }

  // TODO (Felipe): @Scheduled(cron = "{pipeline.cron}", concurrentExecution = SKIP) chamando
  //  executar() quando pipeline.habilitado=true.

  /**
   * Executa todas as etapas em ordem crescente de {@link EtapaPipeline#ordem()}. Para cada etapa:
   * grava EM_ANDAMENTO, executa, grava SUCESSO (com registros/detalhe) ou FALHA (com a mensagem) e,
   * se falhar, não executa as próximas.
   *
   * @return o lote executado
   */
  public UUID executar() {
    // TODO (Felipe): cada registro em controle.execucao_pipeline na sua própria transação
    //  (QuarkusTransaction.requiringNew()), para a FALHA ficar gravada mesmo com rollback da etapa.
    throw new UnsupportedOperationException("TODO (Felipe): PipelineOrquestrador.executar");
  }
}
