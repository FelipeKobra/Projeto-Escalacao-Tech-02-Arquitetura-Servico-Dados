package br.com.equipe.farmarede.dados.marts;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.List;

/** Caso de uso para consulta paginada e filtrada de {@code marts.vendas_diarias}. */
@ApplicationScoped
public class ConsultarVendasDiariasUseCase {

  private static final String SQL_BASE =
      """
      SELECT data, loja_codigo, loja_nome, loja_uf, canal,
             qtd_vendas, qtd_itens, receita_bruta, receita_liquida, receita_cancelada, ticket_medio
      FROM marts.vendas_diarias
      """;

  private final AgroalDataSource dw;

  public ConsultarVendasDiariasUseCase(final AgroalDataSource dw) {
    this.dw = dw;
  }

  /** Retorna métricas diárias de vendas filtradas por período, loja e canal. */
  public List<VendaDiariaDto> executar(
      final LocalDate de,
      final LocalDate ate,
      final String loja,
      final String canal,
      final Integer pagina,
      final Integer tamanho) {
    final ConsultaMartHelper.FiltroSql filtro =
        new ConsultaMartHelper.FiltroSql()
            .ondeComparacao("data >= ?", de)
            .ondeComparacao("data <= ?", ate)
            .ondeIgualUpper("loja_codigo", loja)
            .ondeIgualUpper("canal", canal);

    return ConsultaMartHelper.consultar(
        dw,
        SQL_BASE,
        filtro,
        "data DESC, loja_codigo ASC, canal ASC",
        pagina,
        tamanho,
        rs ->
            new VendaDiariaDto(
                rs.getObject("data", LocalDate.class),
                rs.getString("loja_codigo"),
                rs.getString("loja_nome"),
                rs.getString("loja_uf"),
                rs.getString("canal"),
                rs.getLong("qtd_vendas"),
                rs.getLong("qtd_itens"),
                rs.getBigDecimal("receita_bruta"),
                rs.getBigDecimal("receita_liquida"),
                rs.getBigDecimal("receita_cancelada"),
                rs.getBigDecimal("ticket_medio")));
  }
}
