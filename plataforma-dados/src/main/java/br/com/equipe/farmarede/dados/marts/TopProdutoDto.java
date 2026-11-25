package br.com.equipe.farmarede.dados.marts;

import java.math.BigDecimal;

/**
 * Linha do mart {@code marts.vendas_top_produtos} exposta em {@code GET
 * /marts/vendas/top-produtos}.
 */
public record TopProdutoDto(
    String periodo,
    String categoria,
    String sku,
    String produtoNome,
    String fabricante,
    long qtdVendas,
    long qtdVendida,
    BigDecimal receitaLiquida,
    int rankingCategoria,
    int rankingGeral) {}
