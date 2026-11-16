package br.com.equipe.farmarede.fontes.operacional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Evento de estoque (entrada de fornecedor, saída por venda, ajuste de inventário ou perda). A
 * quantidade é positiva para entrada e negativa para saída/perda.
 */
@Entity
@Table(name = "movimento_estoque")
@Getter
@Setter
@NoArgsConstructor
public class MovimentoEstoque {

  public enum Tipo {
    ENTRADA,
    SAIDA_VENDA,
    AJUSTE,
    PERDA
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "loja_id")
  private Loja loja;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "produto_id")
  private Produto produto;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private Tipo tipo;

  @Column(nullable = false)
  private int quantidade;

  @Column(name = "ocorrido_em", nullable = false)
  private OffsetDateTime ocorridoEm;

  @UpdateTimestamp
  @Column(name = "atualizado_em", nullable = false)
  private OffsetDateTime atualizadoEm;
}
