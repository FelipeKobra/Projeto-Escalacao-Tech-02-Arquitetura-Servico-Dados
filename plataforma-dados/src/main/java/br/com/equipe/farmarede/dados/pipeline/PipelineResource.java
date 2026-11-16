package br.com.equipe.farmarede.dados.pipeline;

import io.quarkus.panache.common.Sort;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

/** Operação manual do pipeline e consulta do histórico. */
@Path("/pipeline/execucoes")
@Produces(MediaType.APPLICATION_JSON)
public class PipelineResource {

  static final int LIMITE_HISTORICO = 50;

  private final PipelineOrquestrador orquestrador;
  private final ExecucaoPipelineRepository execucoes;

  public PipelineResource(
      final PipelineOrquestrador orquestrador, final ExecucaoPipelineRepository execucoes) {
    this.orquestrador = orquestrador;
    this.execucoes = execucoes;
  }

  /** Resposta do disparo manual. */
  public record LoteDisparado(UUID loteId) {}

  /** Corpo de erro (409 quando já há um lote em andamento). */
  public record Erro(String mensagem) {}

  /**
   * Dispara o pipeline agora. Resposta 202 com {@code {"loteId": "..."}}, ou 409 se já houver um
   * lote em andamento; o resultado de cada etapa (inclusive falha) fica no histórico.
   */
  @POST
  public Response disparar() {
    try {
      final UUID lote = orquestrador.executar();
      return Response.accepted(new LoteDisparado(lote)).build();
    } catch (PipelineOrquestrador.PipelineEmExecucaoException e) {
      return Response.status(Response.Status.CONFLICT).entity(new Erro(e.getMessage())).build();
    }
  }

  /** Histórico de execuções, mais recente primeiro (limite 50). */
  @GET
  public List<ExecucaoPipelineDto> listar() {
    return execucoes.findAll(Sort.descending("id")).page(0, LIMITE_HISTORICO).stream()
        .map(ExecucaoPipelineDto::de)
        .toList();
  }
}
