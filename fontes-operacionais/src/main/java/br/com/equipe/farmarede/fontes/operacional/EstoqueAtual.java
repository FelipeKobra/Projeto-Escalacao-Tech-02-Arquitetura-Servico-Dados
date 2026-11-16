package br.com.equipe.farmarede.fontes.operacional;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/** Saldo corrente de um produto numa loja (chave composta loja + produto). */
@Entity
@Table(name = "estoque_atual")
@Getter
@Setter
@NoArgsConstructor
public class EstoqueAtual {

  /** Chave composta. Record é imutável e já traz equals/hashCode corretos. */
  @Embeddable
  public record Id(
      @Column(name = "loja_id") Long lojaId, @Column(name = "produto_id") Long produtoId)
      implements Serializable {}

  @EmbeddedId private Id id;

  @Column(nullable = false)
  private int quantidade;

  @Column(name = "estoque_minimo", nullable = false)
  private int estoqueMinimo;

  @UpdateTimestamp
  @Column(name = "atualizado_em", nullable = false)
  private OffsetDateTime atualizadoEm;
}
