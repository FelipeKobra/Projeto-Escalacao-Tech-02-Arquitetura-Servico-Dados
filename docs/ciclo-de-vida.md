# Ciclo de vida do dado

> **Responsável:** Felipe (com os nomes de tabela/mart do Romulo) · **Critério:** explicação do
> fluxo de dados (15%). Meta "Avançado": completo, do início ao consumo final.

Fio condutor: **uma venda de Dipirona no PDV da loja SP01** acompanhada do início ao fim.

## 1. Criação (produtor)

<!-- TODO (Felipe): o simulador cria a venda + itens + movimento de estoque no oltp-db; quais
     tabelas e colunas; atualizado_em. -->

## 2. Coleta (ingestão)

<!-- TODO (Felipe): o PipelineOrquestrador dispara (cron ou POST /pipeline/execucoes); a etapa 10
     lê pelo watermark e grava em raw.venda / raw.item_venda / raw.movimento_estoque; registro em
     controle.execucao_pipeline. -->

## 3. Processamento (curada)

<!-- TODO (Felipe + Romulo): a etapa 20 deduplica, limpa e carrega fato_venda / dimensões. -->

## 4. Armazenamento analítico (agregada)

<!-- TODO (Felipe + Romulo): a etapa 30 atualiza marts.vendas_diarias e marts.estoque_*. -->

## 5. Consumo

<!-- TODO (Felipe): a diretoria vê no Metabase e o gerente consulta /marts/estoque/ruptura. -->

## 6. Retenção, reprocessamento e qualidade

<!-- TODO (Felipe): raw permite reprocessar; o que acontece se uma etapa falhar; o que é
     guardado por quanto tempo; LGPD. -->

## Latência ponta a ponta

<!-- TODO (Felipe): intervalo do simulador + cron do pipeline = em quanto tempo uma venda aparece
     no dashboard. -->
