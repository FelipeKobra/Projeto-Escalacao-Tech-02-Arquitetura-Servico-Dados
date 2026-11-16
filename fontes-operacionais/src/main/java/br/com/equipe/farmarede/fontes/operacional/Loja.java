package br.com.equipe.farmarede.fontes.operacional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Loja da rede (física ou o e-commerce). Tabela {@code loja} do OLTP, criada pelo Flyway.
 *
 * <p>Não usamos {@code @Data} do Lombok em entidades: o equals/hashCode/toString gerado quebra a
 * identidade JPA e dispara lazy loading.
 */
@Entity
@Table(name = "loja")
@Getter
@Setter
@NoArgsConstructor
public class Loja {

  public enum Tipo {
    FISICA,
    ONLINE
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 10)
  private String codigo;

  @Column(nullable = false, length = 120)
  private String nome;

  @Column(nullable = false, length = 80)
  private String cidade;

  @Column(nullable = false, length = 2)
  private String uf;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Tipo tipo;

  @CreationTimestamp
  @Column(name = "criado_em", nullable = false, updatable = false)
  private OffsetDateTime criadoEm;

  /** Watermark da ingestão: atualizado pelo Hibernate a cada insert/update. */
  @UpdateTimestamp
  @Column(name = "atualizado_em", nullable = false)
  private OffsetDateTime atualizadoEm;
}
