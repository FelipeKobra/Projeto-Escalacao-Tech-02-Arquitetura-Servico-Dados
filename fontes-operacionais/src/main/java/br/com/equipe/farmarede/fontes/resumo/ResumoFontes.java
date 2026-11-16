package br.com.equipe.farmarede.fontes.resumo;

/** Volume atual de registros no banco operacional (útil para acompanhar o simulador). */
public record ResumoFontes(
    long lojas, long produtos, long clientes, long vendas, long movimentosEstoque) {}
