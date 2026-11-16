package br.com.equipe.farmarede.fontes.operacional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/** Acesso a {@link MovimentoEstoque}. */
@ApplicationScoped
public class MovimentoEstoqueRepository implements PanacheRepository<MovimentoEstoque> {}
