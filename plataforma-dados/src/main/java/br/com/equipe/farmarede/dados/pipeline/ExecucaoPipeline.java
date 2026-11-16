package br.com.equipe.farmarede.dados.pipeline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Uma linha do histórico do pipeline: uma etapa dentro de um lote. */
@Entity
@Table(schema = "controle", name = "execucao_pipeline")
@Getter
@Setter
@NoArgsConstructor
public class ExecucaoPipeline {

  public enum Status {
    EM_ANDAMENTO,
    SUCESSO,
    FALHA
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "lote_id", nullable = false)
  private UUID loteId;

  @Column(nullable = false, length = 60)
  private String etapa;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private Status status;

  @Column(name = "iniciado_em", nullable = false)
  private OffsetDateTime iniciadoEm;

  @Column(name = "finalizado_em")
  private OffsetDateTime finalizadoEm;

  @Column(name = "registros_processados", nullable = false)
  private long registrosProcessados;

  @Column(columnDefinition = "text")
  private String detalhe;
}
