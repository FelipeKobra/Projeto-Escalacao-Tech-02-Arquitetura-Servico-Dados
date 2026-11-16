package br.com.equipe.farmarede.dados.ingestao;

import br.com.equipe.farmarede.dados.pipeline.ContextoExecucao;
import br.com.equipe.farmarede.dados.pipeline.EtapaPipeline;
import br.com.equipe.farmarede.dados.pipeline.ResultadoEtapa;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Etapa 10 (COLETA): copia as linhas novas/alteradas do OLTP para {@code raw.*}, de forma
 * incremental pelo watermark {@code atualizado_em}.
 *
 * <p>Ver checklist em docs/divisao-do-trabalho.md (parte do Felipe).
 */
@ApplicationScoped
public class IngestaoOltpEtapa implements EtapaPipeline {

  /** Tabelas do OLTP ingeridas, cada uma com sua tabela homônima em {@code raw}. */
  static final List<String> TABELAS =
      List.of(
          "loja",
          "produto",
          "cliente",
          "venda",
          "item_venda",
          "movimento_estoque",
          "estoque_atual");

  private final AgroalDataSource oltp;
  private final AgroalDataSource dw;

  public IngestaoOltpEtapa(
      @DataSource("oltp") final AgroalDataSource oltp, final AgroalDataSource dw) {
    this.oltp = oltp;
    this.dw = dw;
  }

  @Override
  public String nome() {
    return "ingestao-oltp-raw";
  }

  @Override
  public int ordem() {
    return 10;
  }

  @Override
  public ResultadoEtapa executar(final ContextoExecucao contexto) {
    // TODO (Felipe): para cada tabela em TABELAS:
    //  1. ler controle.watermark (ausente = '-infinity') no DW;
    //  2. no OLTP: SELECT to_jsonb(t), t.atualizado_em FROM <tabela> t
    //     WHERE t.atualizado_em > (watermark - janela de sobreposição) ORDER BY atualizado_em;
    //  3. no DW, numa transação: INSERT em raw.<tabela> (lote_id, chave_origem, payload,
    //     atualizado_na_origem) ignorando o que já existe e UPSERT em controle.watermark.
    //  Use PreparedStatement (nome de tabela só da lista fixa TABELAS, nunca de entrada externa).
    throw new UnsupportedOperationException("TODO (Felipe): IngestaoOltpEtapa.executar");
  }
}
