package br.com.equipe.farmarede.dados.pipeline;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/** Operação manual do pipeline e consulta do histórico. */
@Path("/pipeline/execucoes")
@Produces(MediaType.APPLICATION_JSON)
public class PipelineResource {

  private final PipelineOrquestrador orquestrador;
  private final ExecucaoPipelineRepository execucoes;

  public PipelineResource(
      final PipelineOrquestrador orquestrador, final ExecucaoPipelineRepository execucoes) {
    this.orquestrador = orquestrador;
    this.execucoes = execucoes;
  }

  /** Dispara o pipeline agora. Resposta 202 com {@code {"loteId": "..."}}. */
  @POST
  public Response disparar() {
    // TODO (Felipe): orquestrador.executar() e responder 202 com o loteId.
    throw new UnsupportedOperationException("TODO (Felipe): PipelineResource.disparar");
  }

  /** Histórico de execuções, mais recente primeiro (limite 50). Devolva um record DTO. */
  @GET
  public List<?> listar() {
    // TODO (Felipe): execucoes.findAll(Sort.descending("id")).page(0, 50) mapeado para DTO.
    throw new UnsupportedOperationException("TODO (Felipe): PipelineResource.listar");
  }
}
