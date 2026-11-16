package br.com.equipe.farmarede.fontes.operacional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/** Acesso a {@link Produto}. */
@ApplicationScoped
public class ProdutoRepository implements PanacheRepository<Produto> {}
