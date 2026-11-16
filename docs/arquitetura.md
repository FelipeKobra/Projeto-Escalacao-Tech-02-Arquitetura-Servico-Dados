# Diagrama da arquitetura de dados

> **Responsável:** Romulo · **Critério:** diagrama arquitetural (20%)
> Meta "Avançado": completo, legível e coerente, **com legenda**.

O diagrama abaixo é o **ponto de partida** da base. O Romulo completa com as tabelas `curated`/`marts`
definitivas, os consumidores, a legenda e exporta um PNG em `docs/img/arquitetura.png`.

```mermaid
flowchart LR
  subgraph Produtores
    PDV[PDV lojas físicas]
    ECOM[E-commerce]
    EST[Estoque / recebimento]
  end

  subgraph Coleta
    FO[fontes-operacionais<br/>Quarkus :8081]
    OLTP[(oltp-db<br/>Postgres)]
    ING[Ingestão incremental<br/>watermark atualizado_em]
  end

  subgraph DW["Data Warehouse (dw-db)"]
    RAW[(raw.*<br/>cópia fiel, append-only)]
    CUR[(curated.*<br/>modelo estrela)]
    MARTS[(marts.*<br/>vendas / estoque)]
    CTRL[(controle.*<br/>execuções e watermarks)]
  end

  subgraph Consumo
    API[API /marts<br/>plataforma-dados :8080]
    MB[Metabase :3000]
  end

  PDV --> FO
  ECOM --> FO
  EST --> FO
  FO --> OLTP
  OLTP --> ING --> RAW --> CUR --> MARTS
  ING -.-> CTRL
  MARTS --> API
  MARTS --> MB
  API --> COM[Comercial]
  MB --> COMP[Compras]
  MB --> MKT[Marketing]
```

## Legenda

<!-- TODO (Romulo): formas (retângulo = serviço, cilindro = armazenamento), cores por camada,
     linha cheia = fluxo de dados, tracejada = metadados/controle, frequência de cada seta. -->

## Componentes

<!-- TODO (Romulo): tabela componente → tecnologia → responsabilidade → container/porta. -->
