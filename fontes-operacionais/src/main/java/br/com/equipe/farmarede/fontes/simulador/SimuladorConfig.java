package br.com.equipe.farmarede.fontes.simulador;

import io.smallrye.config.ConfigMapping;
import java.time.Duration;

/**
 * Configuração do simulador (chaves {@code simulador.*} no application.properties, sobrescritas por
 * {@code SIMULADOR_HABILITADO}, {@code SIMULADOR_INTERVALO} e {@code SIMULADOR_VENDAS_POR_CICLO}).
 */
@ConfigMapping(prefix = "simulador")
public interface SimuladorConfig {

  boolean habilitado();

  /** Intervalo entre ciclos (ex.: 10s). O @Scheduled usa a mesma chave como expressão. */
  Duration intervalo();

  int vendasPorCiclo();
}
