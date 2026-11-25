package br.com.equipe.farmarede.dados.marts;

import java.time.LocalDate;

/** Linha do mart {@code marts.estoque_ruptura} exposta em {@code GET /marts/estoque/ruptura}. */
public record EstoqueRupturaDto(
    LocalDate dataReferencia,
    String lojaCodigo,
    String lojaNome,
    String lojaUf,
    String sku,
    String produtoNome,
    String categoria,
    int quantidadeSaldo,
    int estoqueMinimo,
    int deficit) {}
