# Atores: produtores e consumidores de dados

> **Responsável:** Felipe · **Critério:** identificação dos atores (15%)
> Meta "Avançado": atores completos com funções claras.

Um **produtor** gera o dado na operação; um **consumidor** usa o dado tratado para decidir. Na
FarmaRede os produtores escrevem só no `oltp-db` e os consumidores leem só o `dw-db` (marts) ou a
API da `plataforma-dados`. Nenhum consumidor acessa o banco do caixa.

## Produtores

| Ator | Sistema / container | Dados produzidos (tabelas do `oltp-db`) | Frequência |
|------|---------------------|------------------------------------------|------------|
| Operador de caixa (PDV) das lojas SP01, SP02 e RJ01 | `fontes-operacionais` (simulador, canal `PDV`) → `oltp-db` | `venda` (canal PDV, cliente opcional), `item_venda`, `movimento_estoque` (`SAIDA_VENDA`) e baixa em `estoque_atual` | Contínua (a cada `SIMULADOR_INTERVALO`, 10 s por padrão) |
| Cliente no e-commerce | `fontes-operacionais` (simulador, canal `ECOMMERCE`, loja `WEB`) → `oltp-db` | `venda` (sempre com `cliente_id`, sem pagamento em dinheiro), `item_venda`, `movimento_estoque` e baixa em `estoque_atual` da loja WEB | Contínua, cerca de 30 % das vendas |
| Atendimento / SAC (cancelamentos) | `fontes-operacionais` → `oltp-db` | `venda.status = CANCELADA` e devolução dos itens (`movimento_estoque` `AJUSTE` positivo) | Ocasional (cerca de 10 % dos ciclos) |
| Recebimento / sistema de estoque | `fontes-operacionais` → `oltp-db` | `movimento_estoque` (`ENTRADA` de fornecedor) e aumento em `estoque_atual`, priorizando o que está abaixo do mínimo | Ocasional (cerca de 30 % dos ciclos) |
| Cadastro (catálogo, lojas e clientes) | Seed Flyway `V2__dados_referencia.sql` + simulador → `oltp-db` | `loja`, `produto` (preço, categoria, controlado), `cliente` (dado pessoal) | Carga inicial e cliente novo ocasional (cerca de 10 % dos ciclos) |

Todo `INSERT`/`UPDATE` no OLTP atualiza `atualizado_em` (Hibernate e o trigger da base). Esse é o
**watermark** que a coleta usa para ler só o que mudou.

## Consumidores

| Ator | Como consome | Data Mart / interface | Decisões que toma |
|------|--------------|------------------------|-------------------|
| Diretoria comercial | Dashboard no Metabase (perfil `bi`) | `marts.vendas_diarias`, `marts.vendas_top_produtos` | Metas por loja e canal, investimento em e-commerce × lojas físicas, mix de produtos |
| Compras / abastecimento | Metabase e `GET /marts/estoque/...` | `marts.estoque_ruptura`, `marts.estoque_giro` | O que repor, quanto e para qual loja; ajuste do estoque mínimo pelo giro e pela cobertura |
| Marketing | Metabase | `marts.vendas_top_produtos`, `marts.vendas_diarias` (por canal) | Campanhas por categoria, combate a produtos parados, ações para o canal online |
| Gerente de loja | API `GET /marts/vendas/diarias?loja=...` e `GET /marts/estoque/ruptura?loja=...` | Marts de Vendas e de Estoque filtrados pela própria loja | Pedido de transferência entre lojas, escala da equipe nos dias de pico, conferência de ruptura |

## Atores técnicos

| Ator | Interface | Responsabilidade |
|------|-----------|------------------|
| Engenharia de dados | `POST`/`GET /pipeline/execucoes`, `controle.execucao_pipeline`, `controle.watermark`, logs da `plataforma-dados` | Opera o pipeline (cron de 2 min ou disparo manual), acompanha SUCESSO/FALHA por lote, reprocessa a partir da `raw` e evolui as migrations |
| DPO / encarregado LGPD | Revisão das camadas `curated` e `marts` | Garante que nome e e-mail do cliente só existem no OLTP e na `raw` (acesso restrito) e chegam **pseudonimizados** em `curated.dim_cliente`; nenhum mart expõe dado pessoal |
| Desenvolvedores dos sistemas operacionais | `oltp-db` (dono do schema, via `oltp-schema`) | Mantêm `atualizado_em` em toda tabela e avisam a engenharia de dados antes de mudar o schema de origem |
