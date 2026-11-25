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

**O que é:** uma cópia fiel do que existia no `oltp-db`, linha a linha, sem nenhuma regra de
negócio. São sete tabelas (`raw.loja`, `raw.produto`, `raw.cliente`, `raw.venda`, `raw.item_venda`,
`raw.movimento_estoque` e `raw.estoque_atual`), todas no mesmo formato:

| Coluna | Significado |
|--------|-------------|
| `id` | Identity da própria raw (ordem de chegada) |
| `lote_id` | Execução do pipeline que trouxe a linha. Liga a linha ao histórico em `controle.execucao_pipeline` (linhagem) |
| `chave_origem` | Chave primária no OLTP, em texto: `id`, ou `loja_id:produto_id` em `estoque_atual` |
| `payload` | A linha inteira em `jsonb` (`to_jsonb(t)`), com os nomes de coluna do OLTP |
| `atualizado_na_origem` | `atualizado_em` da linha no OLTP, que identifica a **versão** |
| `ingerido_em` | Quando a linha chegou ao DW |

**Por que guardar a linha inteira, sem transformar?**

- **Auditoria.** Dá para provar o que a origem dizia em cada momento, por exemplo o preço de um
  produto antes de um reajuste ou uma venda antes de ser cancelada.
- **Reprocessamento.** Se a regra da camada curada mudar (como tratar desconto, como deduplicar),
  ou se houver um bug, a curada e os marts podem ser reconstruídos a partir da raw sem reler o banco
  do caixa.
- **Desacoplamento do schema da origem.** Uma coluna nova no OLTP aparece no `payload` sem migration
  no DW. Só a etapa 20 decide se e como usá-la. Uma coluna removida não quebra a coleta.

**Por que append-only?** A raw nunca recebe `UPDATE` nem `DELETE`. Cada alteração na origem vira
uma **nova linha** com outro `atualizado_na_origem`, então o histórico de versões que o OLTP não
guarda fica preservado (por exemplo `venda` 42: `CONCLUIDA` às 10:00 e `CANCELADA` às 10:05). A
camada curada fica com a versão de maior `atualizado_na_origem` de cada `chave_origem`.

**Como funciona a coleta incremental (etapa 10, `ingestao-oltp-raw`).** Para cada tabela:

1. lê `controle.watermark.ultimo_atualizado_em` (até onde já foi ingerido);
2. no OLTP, seleciona `WHERE atualizado_em > watermark - janela de sobreposição`
   (`ingestao.janela-sobreposicao`, 5 min por padrão). Sem watermark, lê a tabela toda;
3. numa transação do DW, insere em `raw.<tabela>` só as versões cuja dupla
   `(chave_origem, atualizado_na_origem)` ainda não existe e avança o watermark com
   `GREATEST(atual, maior atualizado_em lido)`. As duas gravações comitam juntas.

A **janela de sobreposição** existe porque `atualizado_em` é gravado quando a transação no OLTP
começa, mas a linha só fica visível quando ela comita. Uma venda que comita atrasada pode ter um
`atualizado_em` menor que o watermark já salvo. Reler os últimos minutos recupera essa linha, e a
deduplicação garante que as linhas relidas não se repitam. Por isso a etapa é **idempotente**:
rodar duas vezes sem mudança na origem insere 0 linhas. Um advisory lock no DW impede duas
ingestões simultâneas.

**Limitações conhecidas.** Uma transação no OLTP mais longa que a janela, ou o relógio da aplicação
adiantado em relação ao do banco por mais que a janela, pode fazer uma versão ser pulada. Nesse
caso, aumente `INGESTAO_JANELA_SOBREPOSICAO`. A primeira carga lê a tabela inteira para a memória,
o que é adequado ao volume simulado. Com volume real, a leitura seria paginada por
`(atualizado_em, id)`.

**Acesso.** A raw contém dado pessoal (`raw.cliente.payload` tem nome e e-mail) e por isso não é
exposta a consumidores. Quem a lê é a etapa 20, que pseudonimiza o cliente na camada curada.

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
