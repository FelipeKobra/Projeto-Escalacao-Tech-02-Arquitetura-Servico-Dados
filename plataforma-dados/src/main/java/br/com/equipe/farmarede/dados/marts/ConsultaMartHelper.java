package br.com.equipe.farmarede.dados.marts;

import io.agroal.api.AgroalDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Utilitário compartilhado de consulta parametrizada e paginação dos endpoints {@code /marts}. */
final class ConsultaMartHelper {

  static final int LIMITE_PADRAO = 50;
  static final int LIMITE_MAXIMO = 100;

  private ConsultaMartHelper() {}

  @FunctionalInterface
  interface MapeadorLinha<T> {
    T mapear(ResultSet rs) throws SQLException;
  }

  static final class FiltroSql {
    private final StringBuilder clausulas = new StringBuilder();
    private final List<Object> parametros = new ArrayList<>();

    FiltroSql ondeIgual(final String coluna, final String valor) {
      normalizar(valor)
          .ifPresent(
              v -> {
                clausulas.append(" AND ").append(coluna).append(" = ?");
                parametros.add(v);
              });
      return this;
    }

    FiltroSql ondeIgualUpper(final String coluna, final String valor) {
      normalizar(valor)
          .ifPresent(
              v -> {
                clausulas.append(" AND ").append(coluna).append(" = upper(?)");
                parametros.add(v);
              });
      return this;
    }

    FiltroSql ondeIgualIgnoreCase(final String coluna, final String valor) {
      normalizar(valor)
          .ifPresent(
              v -> {
                clausulas.append(" AND lower(").append(coluna).append(") = lower(?)");
                parametros.add(v);
              });
      return this;
    }

    FiltroSql ondeComparacao(final String expressao, final Object valor) {
      if (valor != null) {
        clausulas.append(" AND ").append(expressao);
        parametros.add(valor);
      }
      return this;
    }
  }

  static <T> List<T> consultar(
      final AgroalDataSource dw,
      final String selectBase,
      final FiltroSql filtro,
      final String orderBy,
      final Integer pagina,
      final Integer limite,
      final MapeadorLinha<T> mapper) {
    final int paginaEfetiva = pagina == null || pagina < 0 ? 0 : pagina;
    final int limiteEfetivo =
        limite == null || limite <= 0 ? LIMITE_PADRAO : Math.min(limite, LIMITE_MAXIMO);
    final int offset = paginaEfetiva * limiteEfetivo;

    final String sql =
        selectBase + " WHERE 1=1" + filtro.clausulas + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?";
    try (Connection con = dw.getConnection();
        PreparedStatement st = con.prepareStatement(sql)) {
      int idx = 1;
      for (final Object param : filtro.parametros) {
        st.setObject(idx++, param);
      }
      st.setInt(idx++, limiteEfetivo);
      st.setInt(idx, offset);

      final List<T> resultados = new ArrayList<>();
      try (ResultSet rs = st.executeQuery()) {
        while (rs.next()) {
          resultados.add(mapper.mapear(rs));
        }
      }
      return resultados;
    } catch (SQLException e) {
      throw new IllegalStateException("Falha ao consultar mart: " + e.getMessage(), e);
    }
  }

  private static Optional<String> normalizar(final String valor) {
    if (valor == null || valor.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(valor.trim());
  }
}
