package br.com.equipe.farmarede.dados.pipeline;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/** Acesso ao histórico em {@code controle.execucao_pipeline}. */
@ApplicationScoped
public class ExecucaoPipelineRepository implements PanacheRepository<ExecucaoPipeline> {}
