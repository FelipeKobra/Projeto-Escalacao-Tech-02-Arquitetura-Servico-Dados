package br.com.equipe.farmarede.fontes.operacional;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Cabeçalho da venda (PDV ou e-commerce). Os itens são salvos em cascata. */
@Entity
@Table(name = "venda")
@Getter
@Setter
@NoArgsConstructor
public class Venda {

  public enum Canal {
    PDV,
    ECOMMERCE
  }

  public enum Status {
    CONCLUIDA,
    CANCELADA
  }

  public enum FormaPagamento {
    DINHEIRO,
    DEBITO,
    CREDITO,
    PIX
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "loja_id")
  private Loja loja;

  /** Nulo em venda de balcão sem identificação do cliente. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cliente_id")
  private Cliente cliente;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Canal canal;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Status status;

  @Enumerated(EnumType.STRING)
  @Column(name = "forma_pagamento", nullable = false, length = 20)
  private FormaPagamento formaPagamento;

  @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
  private BigDecimal valorTotal;

  @Column(name = "realizada_em", nullable = false)
  private OffsetDateTime realizadaEm;

  @Setter(lombok.AccessLevel.NONE)
  @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ItemVenda> itens = new ArrayList<>();

  @CreationTimestamp
  @Column(name = "criado_em", nullable = false, updatable = false)
  private OffsetDateTime criadoEm;

  @UpdateTimestamp
  @Column(name = "atualizado_em", nullable = false)
  private OffsetDateTime atualizadoEm;

  /** Mantém os dois lados da associação consistentes. */
  public void adicionarItem(final ItemVenda item) {
    item.setVenda(this);
    itens.add(item);
  }

  /** Visão somente leitura; use {@link #adicionarItem(ItemVenda)} para incluir. */
  public List<ItemVenda> getItens() {
    return List.copyOf(itens);
  }
}
