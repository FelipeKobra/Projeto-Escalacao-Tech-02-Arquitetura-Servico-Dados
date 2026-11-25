package br.com.equipe.farmarede.dados.marts;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/** Caso de uso para consulta do ranking de produtos em {@code marts.vendas_top_produtos}. */
@ApplicationScoped
public class ConsultarTopProdutosUseCase {

  private static final String SQL_BASE =
      """
      SELECT periodo, categoria, sku, produto_nome, fabricante,
             qtd_vendas, qtd_vendida, receita_liquida, ranking_categoria, ranking_geral
      FROM marts.vendas_top_produtos
      """;

  private final AgroalDataSource dw;

  public ConsultarTopProdutosUseCase(final AgroalDataSource dw) {
    this.dw = dw;
  }

  /** Retorna o ranking de produtos por receita/quantidade, com filtros por categoria e período. */
  public List<TopProdutoDto> executar(
      final String categoria, final String periodo, final Integer limite, final Integer pagina) {
    final ConsultaMartHelper.FiltroSql filtro =
        new ConsultaMartHelper.FiltroSql()
            .ondeIgualIgnoreCase("categoria", categoria)
            .ondeIgual("periodo", periodo);

    return ConsultaMartHelper.consultar(
        dw,
        SQL_BASE,
        filtro,
        "periodo DESC, ranking_geral ASC, sku ASC",
        pagina,
        limite,
        rs ->
            new TopProdutoDto(
                rs.getString("periodo"),
                rs.getString("categoria"),
                rs.getString("sku"),
                rs.getString("produto_nome"),
                rs.getString("fabricante"),
                rs.getLong("qtd_vendas"),
                rs.getLong("qtd_vendida"),
                rs.getBigDecimal("receita_liquida"),
                rs.getInt("ranking_categoria"),
                rs.getInt("ranking_geral")));
  }
}
