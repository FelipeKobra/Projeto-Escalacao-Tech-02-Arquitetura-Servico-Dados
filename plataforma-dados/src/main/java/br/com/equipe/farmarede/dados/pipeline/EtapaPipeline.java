package br.com.equipe.farmarede.dados.pipeline;

/**
 * Uma etapa do pipeline de dados. Cada etapa é um bean CDI ({@code @ApplicationScoped}) e o {@code
 * PipelineOrquestrador} executa todas em ordem crescente de {@link #ordem()}, registrando o
 * resultado em {@code controle.execucao_pipeline}.
 *
 * <p>Ordens combinadas: 10 = ingestão OLTP→raw (Felipe), 20 = raw→curated (Romulo), 30 =
 * curated→marts (Romulo).
 */
public interface EtapaPipeline {

  /** Nome curto e estável, gravado em {@code controle.execucao_pipeline.etapa}. */
  String nome();

  int ordem();

  /**
   * Executa a etapa. Deve ser idempotente (rodar duas vezes com os mesmos dados não duplica nada) e
   * lançar exceção em caso de falha; o orquestrador registra a falha e interrompe as próximas.
   */
  ResultadoEtapa executar(ContextoExecucao contexto);
}
