package br.com.equipe.farmarede.dados.marts;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Linha do mart {@code marts.vendas_diarias} exposta em {@code GET /marts/vendas/diarias}. */
public record VendaDiariaDto(
    LocalDate data,
    String lojaCodigo,
    String lojaNome,
    String lojaUf,
    String canal,
    long qtdVendas,
    long qtdItens,
    BigDecimal receitaBruta,
    BigDecimal receitaLiquida,
    BigDecimal receitaCancelada,
    BigDecimal ticketMedio) {}
