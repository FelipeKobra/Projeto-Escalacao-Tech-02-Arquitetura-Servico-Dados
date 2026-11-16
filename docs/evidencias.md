# Evidências da entrega

Prova, item a item, de que o que o [`Projeto1.md`](../Projeto1.md) pede foi feito. Cada linha
diz **onde está**, **como conferir** e **quem é o responsável**.

## Conferir tudo de uma vez

```bash
cp .env.example .env               # só na primeira vez (PowerShell: Copy-Item .env.example .env)
bash scripts/verificar-entrega.sh  # no Windows: Git Bash ou WSL
```

O script sobe a stack, espera o simulador gerar vendas, dispara o pipeline e confere banco, API e
documentos. Cada item aparece como `[OK]` ou `[FALHA]`, com o responsável entre colchetes. Ele
termina com código 0 só se tudo passou. Opções: `--so-docs` (só documentos, sem Docker) e
`--derrubar` (para a stack no final).

> **Legenda do status:** ✅ entregue e verificável · ⬜ pendente (indica o PR que entrega).
> Quem termina um item troca ⬜ por ✅ **no próprio PR**.

## 1. Itens obrigatórios (`Projeto1.md` → "Itens Obrigatórios")

| Item | Onde está | Como conferir | Resp. | Status |
|------|-----------|---------------|-------|--------|
| Definição do negócio escolhido | [cenario.md](cenario.md) | Ler: empresa, problema, perguntas de negócio e escopo | Felipe | ⬜ PR #1 |
| Atores identificados (produtores e consumidores) | [atores.md](atores.md) | Tabelas de produtores e consumidores, com função, dados e frequência | Felipe | ⬜ PR #1 |
| Diagrama da arquitetura (coleta → armazenamento → consumo) | [arquitetura.md](arquitetura.md) + `docs/img/arquitetura.png` | Diagrama com **legenda**; cada caixa existe no `docker-compose.yml` | Romulo | ⬜ PR #2 |
| Camadas do DW (Raw, Curada, Agregada) justificadas | [camadas-dw.md](camadas-dw.md) | Uma seção por camada. No banco: `\dn` no `dw-db` mostra `raw`, `curated`, `marts` | Felipe (Raw) · Romulo (Curada/Agregada) | ⬜ PR #1 / #2 |
| Data Marts definidos (≥ 2, foco específico) | [data-marts.md](data-marts.md) | Mart de **Vendas** e mart de **Estoque**, e `GET /marts/vendas/...` e `/marts/estoque/...` respondem 200 | Romulo | ⬜ PR #2 |
| Texto explicativo do ciclo do dado | [ciclo-de-vida.md](ciclo-de-vida.md) | Uma venda acompanhada do PDV até o dashboard | Felipe | ⬜ PR #1 |

## 2. O que a descrição do projeto pede

| Pedido | Como foi atendido | Como conferir | Resp. | Status |
|--------|-------------------|---------------|-------|--------|
| Simular a arquitetura de uma empresa real ou fictícia | Rede de farmácias fictícia **FarmaRede** (3 lojas físicas + e-commerce) | [cenario.md](cenario.md) | Felipe | ⬜ PR #1 |
| Definir produtores e consumidores | Ver item "Atores" acima | [atores.md](atores.md) | Felipe | ⬜ PR #1 |
| Camada de **Coleta** | `fontes-operacionais` (produtores simulados) → `oltp-db` → ingestão incremental (etapa 10) | `GET /fontes/resumo` cresce; `controle.execucao_pipeline` tem `ingestao-oltp-raw = SUCESSO` | Felipe | ⬜ PR #1 |
| Camada de **Processamento** | Etapas 20 (raw → curated) e 30 (curated → marts) da `plataforma-dados` | `controle.execucao_pipeline` tem `transformacao-curated` e `agregacao-marts = SUCESSO` | Romulo | ⬜ PR #2 |
| Camada de **Armazenamento** | `oltp-db` (operacional) e `dw-db` (DW, com schemas por camada) | `docker compose ps` mostra os dois Postgres `healthy` | Base | ✅ |
| Um Data Warehouse com Data Marts | `dw-db` com schemas `raw`/`curated`/`marts`/`controle` | `docker compose exec dw-db psql -U farmarede -d dw -c '\dn'` | Base + Romulo | ✅ estrutura · ⬜ marts PR #2 |
| Justificar o uso do DW em vez de só um banco relacional | Seção "Por que um DW e não só o banco relacional?" | [camadas-dw.md](camadas-dw.md#por-que-um-dw-e-não-só-o-banco-relacional) | Romulo | ⬜ PR #2 |
| Simular dados coletados (vendas, produtos, ...) | Dados de referência (`oltp-schema/.../V2__dados_referencia.sql`) + simulador contínuo | `curl localhost:8081/fontes/resumo`: `vendas` > 0 e crescendo | Base (seed) ✅ · Felipe (simulador) | ⬜ PR #1 |

## 3. Entregáveis (`Projeto1.md` → "Entregáveis")

| Entregável | Arquivo | Resp. | Status |
|------------|---------|-------|--------|
| Diagrama completo com legenda | [arquitetura.md](arquitetura.md) + `docs/img/arquitetura.png` | Romulo | ⬜ PR #2 |
| Documento explicando o ciclo de vida do dado | [ciclo-de-vida.md](ciclo-de-vida.md) | Felipe | ⬜ PR #1 |
| Explicação do cenário escolhido | [cenario.md](cenario.md) | Felipe | ⬜ PR #1 |

## 4. Critérios de avaliação: onde buscamos o nível "Avançado"

| Critério (peso) | O que entregamos para o nível "Avançado" | Evidência |
|-----------------|-------------------------------------------|-----------|
| Clareza do problema de negócio (20%) | Problema concreto da FarmaRede ligado a perguntas que os marts respondem | [cenario.md](cenario.md) |
| Diagrama arquitetural (20%) | Diagrama legível, com legenda, que **roda de verdade** (cada caixa é um container) | [arquitetura.md](arquitetura.md), `docker-compose.yml` |
| Identificação dos atores (15%) | Produtores, consumidores e atores técnicos, com função, dados e frequência | [atores.md](atores.md) |
| Definição das camadas do DW (15%) | Raw/Curada/Agregada como schemas reais, cada uma com regra e justificativa | [camadas-dw.md](camadas-dw.md), migrations do DW |
| Definição dos Data Marts (15%) | 2 marts com público, perguntas, grão e métricas, consultáveis por API e Metabase | [data-marts.md](data-marts.md), `/marts/...` |
| Explicação do fluxo de dados (15%) | Uma venda rastreada do PDV até o dashboard, com latência e reprocessamento | [ciclo-de-vida.md](ciclo-de-vida.md) |

## 5. Extras técnicos (além do pedido)

| Item | Evidência | Status |
|------|-----------|--------|
| Orquestração com Docker Compose (healthchecks, ordem de subida, volumes, `.env`) | `docker-compose.yml`; `docker compose up -d --build --wait` | ✅ |
| CI no GitHub Actions (formatação, testes com Postgres real, smoke test do Compose) | `.github/workflows/ci.yml`, aba **Actions** do repositório | ✅ |
| Testes automatizados com Postgres via Dev Services | `./mvnw verify` | ✅ base · ⬜ PR #1 / #2 |
| Histórico do pipeline (linhagem por lote) | `GET /pipeline/execucoes` e `controle.execucao_pipeline` | ⬜ PR #1 |
| LGPD: dado pessoal pseudonimizado na camada curada | `curated.dim_cliente` sem nome/e-mail em claro | ⬜ PR #2 |
| BI para consumidores (Metabase) | `docker compose --profile bi up -d` → http://localhost:3000 e prints em `docs/img/` | ⬜ PR #2 |

## 6. Roteiro manual de demonstração

Para a apresentação, ou para conferir sem o script. Troque as portas se mudou no `.env`.

```bash
docker compose up -d --build --wait                         # 1. sobe tudo
docker compose ps                                           # 2. todos "healthy"
curl localhost:8081/fontes/resumo                           # 3. produtores gerando dados
curl -X POST localhost:8080/pipeline/execucoes              # 4. roda coleta → curada → marts
curl localhost:8080/pipeline/execucoes                      # 5. histórico: 3 etapas SUCESSO
docker compose exec dw-db psql -U farmarede -d dw -c '\dn'  # 6. camadas do DW
docker compose exec dw-db psql -U farmarede -d dw -c 'select count(*) from raw.venda'
curl "localhost:8080/marts/vendas/diarias"                  # 7. mart de vendas
curl "localhost:8080/marts/estoque/ruptura"                 # 8. mart de estoque
docker compose --profile bi up -d                           # 9. Metabase: http://localhost:3000
```
