package br.com.equipe.farmarede.dados.marts;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Linha do mart {@code marts.estoque_giro} exposta em {@code GET /marts/estoque/giro}. */
public record EstoqueGiroDto(
    LocalDate dataReferencia,
    String lojaCodigo,
    String lojaNome,
    String lojaUf,
    String sku,
    String produtoNome,
    String categoria,
    int quantidadeSaldo,
    int estoqueMinimo,
    long qtdVendida30d,
    BigDecimal mediaDiariaVendas,
    BigDecimal giroEstoque,
    BigDecimal diasCobertura) {}
