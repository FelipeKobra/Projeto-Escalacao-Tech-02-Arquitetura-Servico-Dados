package br.com.equipe.farmarede.fontes.operacional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/** Acesso a {@link Cliente}. */
@ApplicationScoped
public class ClienteRepository implements PanacheRepository<Cliente> {}
