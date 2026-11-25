package br.com.equipe.farmarede.dados.marts;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.List;

/** Caso de uso para consulta de giro e cobertura em {@code marts.estoque_giro}. */
@ApplicationScoped
public class ConsultarEstoqueGiroUseCase {

  private static final String SQL_BASE =
      """
      SELECT data_referencia, loja_codigo, loja_nome, loja_uf,
             sku, produto_nome, categoria, quantidade_saldo, estoque_minimo,
             qtd_vendida_30d, media_diaria_vendas, giro_estoque, dias_cobertura
      FROM marts.estoque_giro
      """;

  private final AgroalDataSource dw;

  public ConsultarEstoqueGiroUseCase(final AgroalDataSource dw) {
    this.dw = dw;
  }

  /** Retorna o giro e dias de cobertura filtrados por loja e categoria. */
  public List<EstoqueGiroDto> executar(
      final String loja, final String categoria, final Integer pagina, final Integer tamanho) {
    final ConsultaMartHelper.FiltroSql filtro =
        new ConsultaMartHelper.FiltroSql()
            .ondeIgualUpper("loja_codigo", loja)
            .ondeIgualIgnoreCase("categoria", categoria);

    return ConsultaMartHelper.consultar(
        dw,
        SQL_BASE,
        filtro,
        "giro_estoque DESC NULLS LAST, loja_codigo ASC, sku ASC",
        pagina,
        tamanho,
        rs ->
            new EstoqueGiroDto(
                rs.getObject("data_referencia", LocalDate.class),
                rs.getString("loja_codigo"),
                rs.getString("loja_nome"),
                rs.getString("loja_uf"),
                rs.getString("sku"),
                rs.getString("produto_nome"),
                rs.getString("categoria"),
                rs.getInt("quantidade_saldo"),
                rs.getInt("estoque_minimo"),
                rs.getLong("qtd_vendida_30d"),
                rs.getBigDecimal("media_diaria_vendas"),
                rs.getBigDecimal("giro_estoque"),
                rs.getBigDecimal("dias_cobertura")));
  }
}
