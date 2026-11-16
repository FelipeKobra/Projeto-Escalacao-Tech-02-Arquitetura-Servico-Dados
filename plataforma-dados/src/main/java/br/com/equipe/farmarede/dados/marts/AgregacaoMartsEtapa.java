package br.com.equipe.farmarede.dados.marts;

import br.com.equipe.farmarede.dados.pipeline.ContextoExecucao;
import br.com.equipe.farmarede.dados.pipeline.EtapaPipeline;
import br.com.equipe.farmarede.dados.pipeline.ResultadoEtapa;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Etapa 30 (CAMADA AGREGADA): {@code curated.*} → {@code marts.*} (Data Marts de Vendas e Estoque).
 *
 * <p>Ver checklist em docs/divisao-do-trabalho.md (parte do Romulo). Antes, criar a migration
 * {@code V250__marts.sql}.
 */
@ApplicationScoped
public class AgregacaoMartsEtapa implements EtapaPipeline {

  private final AgroalDataSource dw;

  public AgregacaoMartsEtapa(final AgroalDataSource dw) {
    this.dw = dw;
  }

  @Override
  public String nome() {
    return "agregacao-marts";
  }

  @Override
  public int ordem() {
    return 30;
  }

  @Override
  public ResultadoEtapa executar(final ContextoExecucao contexto) {
    // TODO (Romulo): recalcular marts.vendas_diarias, marts.vendas_top_produtos,
    //  marts.estoque_ruptura e marts.estoque_giro a partir da curated (TRUNCATE + INSERT ... SELECT
    //  numa transação, ou upsert por período). Retornar o total de linhas gravadas.
    throw new UnsupportedOperationException("TODO (Romulo): AgregacaoMartsEtapa.executar");
  }
}
