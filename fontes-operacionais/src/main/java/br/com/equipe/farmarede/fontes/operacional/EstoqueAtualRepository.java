package br.com.equipe.farmarede.fontes.operacional;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/** Acesso a {@link EstoqueAtual} pela chave composta {@link EstoqueAtual.Id}. */
@ApplicationScoped
public class EstoqueAtualRepository
    implements PanacheRepositoryBase<EstoqueAtual, EstoqueAtual.Id> {}
