package br.com.equipe.farmarede.dados.transformacao;

import br.com.equipe.farmarede.dados.pipeline.ContextoExecucao;
import br.com.equipe.farmarede.dados.pipeline.EtapaPipeline;
import br.com.equipe.farmarede.dados.pipeline.ResultadoEtapa;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Etapa 20 (PROCESSAMENTO): {@code raw.*} → {@code curated.*} (modelo estrela).
 *
 * <p>Ver checklist em docs/divisao-do-trabalho.md (parte do Romulo). Antes, criar a migration
 * {@code V200__curated_modelo_estrela.sql} (faixa V200–V299).
 */
@ApplicationScoped
public class TransformacaoCuradaEtapa implements EtapaPipeline {

  private final AgroalDataSource dw;

  public TransformacaoCuradaEtapa(final AgroalDataSource dw) {
    this.dw = dw;
  }

  @Override
  public String nome() {
    return "transformacao-curated";
  }

  @Override
  public int ordem() {
    return 20;
  }

  @Override
  public ResultadoEtapa executar(final ContextoExecucao contexto) {
    // TODO (Romulo): para cada entidade, pegar a última versão por chave_origem
    //  (DISTINCT ON (chave_origem) ... ORDER BY chave_origem, atualizado_na_origem DESC),
    //  extrair os campos do payload (payload->>'coluna'), limpar/tipar e fazer upsert
    //  (INSERT ... ON CONFLICT) em dim_tempo, dim_loja, dim_produto, dim_cliente
    //  (nome/e-mail pseudonimizados), fato_venda (grão: item; venda CANCELADA não é receita)
    //  e fato_estoque. Tudo numa transação; idempotente.
    throw new UnsupportedOperationException("TODO (Romulo): TransformacaoCuradaEtapa.executar");
  }
}
