# Camadas do Data Warehouse

> **Responsáveis:** Felipe (Raw) e Romulo (Curada, Agregada e justificativa do DW)
> **Critério:** definição das camadas do DW (15%). Meta "Avançado": coerente com boas explicações.

Cada camada é um **schema** no `dw-db`. Os metadados do pipeline ficam em `controle`.

| Camada | Schema | Conteúdo | Quem escreve | Responsável |
|--------|--------|----------|--------------|-------------|
| Raw | `raw` | Cópia fiel do OLTP em `jsonb`, append-only | Etapa 10 (ingestão) | Felipe |
| Curada | `curated` | Dados limpos, deduplicados, modelo estrela | Etapa 20 (transformação) | Romulo |
| Agregada | `marts` | Indicadores por área de negócio | Etapa 30 (agregação) | Romulo |

## Raw

<!-- TODO (Felipe): por que guardar a linha inteira sem transformar (auditoria, reprocessamento,
     desacoplamento do schema da origem); por que append-only; o que são lote_id, chave_origem e
     atualizado_na_origem; como funciona o watermark. -->

## Curada

<!-- TODO (Romulo): regras de limpeza/tipagem, deduplicação (última versão por chave), modelo
     estrela (fatos e dimensões, grão de cada fato), chaves substitutas e pseudonimização LGPD. -->

## Agregada

<!-- TODO (Romulo): por que pré-agregar (consulta rápida para BI), o que é recalculado a cada
     execução e como os marts se ligam às perguntas do cenário. -->

## Por que um DW e não só o banco relacional?

<!-- TODO (Romulo): OLTP otimizado para escrita/transação × DW para leitura analítica; consultas
     pesadas não podem travar o caixa; histórico; integração de várias fontes; modelo
     dimensional; dados limpos e consistentes para todos os consumidores. Use exemplos da FarmaRede. -->
