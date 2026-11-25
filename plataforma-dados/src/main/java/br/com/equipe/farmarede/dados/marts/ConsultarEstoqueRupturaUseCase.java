package br.com.equipe.farmarede.dados.marts;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.List;

/** Caso de uso para consulta de produtos em ruptura em {@code marts.estoque_ruptura}. */
@ApplicationScoped
public class ConsultarEstoqueRupturaUseCase {

  private static final String SQL_BASE =
      """
      SELECT data_referencia, loja_codigo, loja_nome, loja_uf,
             sku, produto_nome, categoria, quantidade_saldo, estoque_minimo, deficit
      FROM marts.estoque_ruptura
      """;

  private final AgroalDataSource dw;

  public ConsultarEstoqueRupturaUseCase(final AgroalDataSource dw) {
    this.dw = dw;
  }

  /** Retorna os produtos abaixo do estoque mínimo ordenados pelo maior déficit. */
  public List<EstoqueRupturaDto> executar(
      final String loja, final Integer pagina, final Integer tamanho) {
    final ConsultaMartHelper.FiltroSql filtro =
        new ConsultaMartHelper.FiltroSql().ondeIgualUpper("loja_codigo", loja);

    return ConsultaMartHelper.consultar(
        dw,
        SQL_BASE,
        filtro,
        "deficit DESC, loja_codigo ASC, sku ASC",
        pagina,
        tamanho,
        rs ->
            new EstoqueRupturaDto(
                rs.getObject("data_referencia", LocalDate.class),
                rs.getString("loja_codigo"),
                rs.getString("loja_nome"),
                rs.getString("loja_uf"),
                rs.getString("sku"),
                rs.getString("produto_nome"),
                rs.getString("categoria"),
                rs.getInt("quantidade_saldo"),
                rs.getInt("estoque_minimo"),
                rs.getInt("deficit")));
  }
}
