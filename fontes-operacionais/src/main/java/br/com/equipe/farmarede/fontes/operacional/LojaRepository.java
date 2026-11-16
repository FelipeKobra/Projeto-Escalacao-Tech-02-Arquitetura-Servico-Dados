package br.com.equipe.farmarede.fontes.operacional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/** Acesso a {@link Loja}. Consultas específicas entram aqui como métodos novos. */
@ApplicationScoped
public class LojaRepository implements PanacheRepository<Loja> {}
