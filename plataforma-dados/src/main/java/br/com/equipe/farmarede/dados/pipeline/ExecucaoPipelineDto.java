package br.com.equipe.farmarede.dados.pipeline;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Uma linha do histórico do pipeline como exposta em {@code GET /pipeline/execucoes}. */
public record ExecucaoPipelineDto(
    long id,
    UUID loteId,
    String etapa,
    ExecucaoPipeline.Status status,
    OffsetDateTime iniciadoEm,
    OffsetDateTime finalizadoEm,
    long registrosProcessados,
    String detalhe) {

  public static ExecucaoPipelineDto de(final ExecucaoPipeline execucao) {
    return new ExecucaoPipelineDto(
        execucao.getId(),
        execucao.getLoteId(),
        execucao.getEtapa(),
        execucao.getStatus(),
        execucao.getIniciadoEm(),
        execucao.getFinalizadoEm(),
        execucao.getRegistrosProcessados(),
        execucao.getDetalhe());
  }
}
