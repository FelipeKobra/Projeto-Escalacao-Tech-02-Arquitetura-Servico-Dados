package br.com.equipe.farmarede.dados.pipeline;

/**
 * Resultado de uma etapa bem-sucedida.
 *
 * @param registrosProcessados quantas linhas a etapa gravou/atualizou
 * @param detalhe texto livre para o histórico (ex.: "venda=12, item_venda=30")
 */
public record ResultadoEtapa(long registrosProcessados, String detalhe) {}
