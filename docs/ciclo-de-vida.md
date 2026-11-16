# Ciclo de vida do dado

> **Responsável:** Felipe (com os nomes de tabela/mart do Romulo) · **Critério:** explicação do
> fluxo de dados (15%). Meta "Avançado": completo, do início ao consumo final.

Fio condutor: **uma venda de Dipirona no PDV da loja SP01** acompanhada do início ao fim.

```text
fontes-operacionais ──► oltp-db ──► [etapa 10] raw.* ──► [etapa 20] curated.* ──► [etapa 30] marts.* ──► Metabase / API
   (produtor)          (OLTP)      ─────────────────── dw-db (plataforma-dados) ───────────────────      (consumidores)
```

## 1. Criação (produtor)

O operador de caixa da **SP01** registra a venda. No projeto quem faz esse papel é o
`SimuladorVendas` do container `fontes-operacionais`, que a cada `SIMULADOR_INTERVALO` (10 s) abre
**uma transação** no `oltp-db` e grava:

| Tabela | O que entra (exemplo) |
|--------|-----------------------|
| `venda` | `id=1042`, `loja_id` da SP01, `canal=PDV`, `status=CONCLUIDA`, `forma_pagamento=PIX`, `cliente_id` (ou nulo, venda de balcão), `valor_total=17.80`, `realizada_em` |
| `item_venda` | `venda_id=1042`, `produto_id` da Dipirona (`MED-0001`), `quantidade=2`, `preco_unitario=8.90`, `desconto=0` |
| `movimento_estoque` | `tipo=SAIDA_VENDA`, `quantidade=-2`, loja SP01, Dipirona, `ocorrido_em` |
| `estoque_atual` | saldo SP01/Dipirona baixa de 50 para 48 (com lock de linha; nunca fica negativo) |

Todas essas linhas recebem `atualizado_em = now()`. Esse carimbo é a "versão" da linha e o
**watermark** da coleta. Se a venda for cancelada depois, o `UPDATE` em `venda.status` muda o
`atualizado_em` (pelo Hibernate e pelo trigger `marcar_atualizado_em`) e grava um movimento
`AJUSTE +2` que devolve o estoque.

## 2. Coleta (ingestão)

O `PipelineOrquestrador` da `plataforma-dados` dispara um **lote** (`lote_id` UUID) pelo cron
`PIPELINE_CRON` (a cada 2 min) ou por `POST /pipeline/execucoes`, que responde `202` com o
`loteId` (ou `409` se já houver um lote rodando). Para cada etapa ele grava
`EM_ANDAMENTO → SUCESSO/FALHA` em `controle.execucao_pipeline`.

A **etapa 10 (`ingestao-oltp-raw`)** lê no `oltp-db` as linhas com
`atualizado_em > watermark - 5 min` e grava em `raw.venda`, `raw.item_venda`,
`raw.movimento_estoque` e `raw.estoque_atual`:
`lote_id`, `chave_origem` (`'1042'`; no estoque `'1:1'` = loja:produto), `payload` (a linha inteira
em `jsonb`) e `atualizado_na_origem`. Na mesma transação avança `controle.watermark` de cada tabela.
Se a venda for cancelada, o próximo lote grava **outra** linha em `raw.venda` para a chave `1042`,
agora com `status=CANCELADA`. A raw guarda as duas versões (detalhes em
[camadas-dw.md](camadas-dw.md#raw)).

## 3. Processamento (curada)

A **etapa 20 (`transformacao-curated`)**, do Romulo, pega a **versão mais recente** de cada
`chave_origem` na raw, tipa e limpa os campos do `payload` e faz upsert no modelo estrela:

- `curated.dim_loja` (SP01), `curated.dim_produto` (Dipirona, categoria Medicamentos),
  `curated.dim_tempo` (dia da venda) e `curated.dim_cliente` (nome e e-mail **pseudonimizados**);
- `curated.fato_venda`, com grão de **item de venda**: 2 unidades × R$ 8,90, ligadas às dimensões
  por chaves substitutas. A venda `CANCELADA` não conta como receita;
- `curated.fato_estoque`, com grão de **saldo diário por loja/produto**: SP01/Dipirona = 48.

## 4. Armazenamento analítico (agregada)

A **etapa 30 (`agregacao-marts`)** recalcula os Data Marts a partir da curada:

- **Vendas:** `marts.vendas_diarias` (a receita do dia da SP01 no canal PDV sobe R$ 17,80, com mais
  1 venda e o ticket médio recalculado) e `marts.vendas_top_produtos` (a Dipirona soma 2 unidades no
  ranking de Medicamentos);
- **Estoque:** `marts.estoque_ruptura` (a Dipirona só aparece se o saldo da SP01 ficar abaixo do
  mínimo de 10) e `marts.estoque_giro` (o giro da Dipirona na SP01 aumenta e os dias de cobertura
  diminuem).

Se qualquer etapa falhar, o orquestrador grava `FALHA` com a mensagem e **não executa as
seguintes**. Os marts continuam com os números do último lote bom, nunca com dados pela metade.

## 5. Consumo

- A **diretoria comercial** abre o dashboard de Vendas no **Metabase** (`docker compose --profile bi`,
  conectado ao `dw-db`) e vê a receita de hoje da SP01 no PDV, já com a venda de Dipirona.
- O **gerente da SP01** consulta `GET /marts/estoque/ruptura?loja=SP01` e
  `GET /marts/vendas/diarias?loja=SP01` na `plataforma-dados`.
- **Compras** usa `marts.estoque_giro` para decidir a próxima reposição. No simulador, a reposição é
  um movimento `ENTRADA` no OLTP, e o ciclo recomeça.
- A **engenharia de dados** acompanha cada lote em `GET /pipeline/execucoes` (etapa, status,
  registros processados e detalhe por tabela).

## 6. Retenção, reprocessamento e qualidade

- **Reprocessamento:** a raw é append-only e guarda todas as versões. Para corrigir uma regra da
  curada ou dos marts basta reexecutar as etapas 20 e 30 a partir da raw, sem reler o OLTP. Para
  reingerir, apaga-se o watermark da tabela, e a deduplicação por
  `(chave_origem, atualizado_na_origem)` evita duplicar o que já está lá.
- **Falhas:** cada registro em `controle.execucao_pipeline` é gravado em transação própria, então a
  `FALHA` fica no histórico mesmo quando a etapa faz rollback. O watermark só avança junto com a
  gravação na raw, então um lote que falhou é simplesmente relido no próximo.
- **Qualidade:** na origem há constraints (`CHECK` de status, canal, quantidade positiva e estoque
  nunca negativo). Na coleta, a ingestão é idempotente e conta as linhas por tabela. Na curada
  acontecem a deduplicação e a tipagem, e a venda cancelada sai da receita.
- **Retenção:** o OLTP guarda o estado atual. A raw guarda o histórico completo de versões, sem
  expurgo no projeto; em produção, seria particionada por `ingerido_em` e arquivada após o prazo
  legal. A curada e os marts podem ser reconstruídos a partir da raw.
- **LGPD:** nome e e-mail do cliente existem só no OLTP e na raw (acesso restrito à engenharia de
  dados). A partir da curada o cliente é pseudonimizado, e nenhum mart expõe dado pessoal.

## Latência ponta a ponta

| Trecho | Tempo típico |
|--------|--------------|
| Venda acontece → gravada no `oltp-db` | imediato (o simulador gera um lote de vendas a cada `SIMULADOR_INTERVALO`, 10 s) |
| `oltp-db` → próximo lote do pipeline | até 2 min (`PIPELINE_CRON`), ou imediato com `POST /pipeline/execucoes` |
| Etapas 10 → 20 → 30 | segundos (volume simulado) |
| Marts → Metabase / API | na próxima consulta ou atualização do dashboard |

Uma venda aparece no dashboard em **até cerca de 2 minutos** no pior caso, e em poucos segundos
com o disparo manual. Isso atende às perguntas da FarmaRede, que são de acompanhamento diário de
vendas e ruptura, sem necessidade de tempo real.
