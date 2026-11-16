package br.com.equipe.farmarede.dados.pipeline;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dados de uma execução do pipeline, compartilhados por todas as etapas.
 *
 * @param loteId identificador da execução; a ingestão grava em {@code raw.*.lote_id}
 * @param iniciadoEm instante em que o orquestrador começou a execução
 */
public record ContextoExecucao(UUID loteId, OffsetDateTime iniciadoEm) {

  public static ContextoExecucao novo() {
    return new ContextoExecucao(UUID.randomUUID(), OffsetDateTime.now());
  }
}
