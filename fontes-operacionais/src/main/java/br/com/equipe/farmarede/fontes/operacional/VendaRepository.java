package br.com.equipe.farmarede.fontes.operacional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/** Acesso a {@link Venda} (os itens vão em cascata). */
@ApplicationScoped
public class VendaRepository implements PanacheRepository<Venda> {}
