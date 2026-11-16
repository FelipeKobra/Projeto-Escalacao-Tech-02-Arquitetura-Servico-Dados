# Divisão do trabalho

O enunciado ([`Projeto1.md`](../Projeto1.md)) está dividido em duas partes, uma por integrante,
cada uma com sua branch e seu PR. A **base** (já na `main`) traz o que as duas partes
compartilham, para ninguém ficar esperando o outro para começar.

## O que já está pronto na base (`main`)

| Item | Onde |
|------|------|
| POM multi-módulo (Quarkus 3.40.1.2, Java 21), Lombok e Spotless | `pom.xml` |
| Schema OLTP + dados de referência (4 lojas, 12 produtos, 5 clientes, estoque inicial) | `oltp-schema/src/main/resources/db/oltp/` |
| Entidades e repositórios Panache do OLTP | `fontes-operacionais/.../fontes/operacional/` |
| `GET /fontes/resumo` (contagem por tabela) | `fontes-operacionais/.../fontes/resumo/` |
| DW V1: schemas `raw`/`curated`/`marts`/`controle`, 7 tabelas `raw.*`, `controle.execucao_pipeline` e `controle.watermark` | `plataforma-dados/src/main/resources/db/migration/V1__estrutura_dw.sql` |
| Datasource padrão (DW) + datasource nomeado `oltp` (leitura da fonte) | `plataforma-dados/src/main/resources/application.properties` |
| Contrato do pipeline: `EtapaPipeline`, `ContextoExecucao`, `ResultadoEtapa`, entidade `ExecucaoPipeline` | `plataforma-dados/.../dados/pipeline/` |
| `Dockerfile` (multi-stage, um para os dois apps), `docker-compose.yml` com healthchecks, `.env.example` | raiz |
| CI: Spotless + testes + `docker compose up --wait` + smoke test | `.github/workflows/ci.yml` |
| Esqueletos dos entregáveis (títulos e o que cada seção precisa ter) | `docs/` |

Cada branch já tem um commit inicial com o esqueleto das classes da parte (métodos com `TODO`) e
um PR em rascunho com o checklist abaixo.

## Felipe: negócio, atores, coleta, Raw e pipeline

**Branch:** `feat/felipe-coleta-raw` · **Critérios do enunciado:** clareza do negócio (20%),
atores (15%), fluxo de dados (15%) e parte das camadas (Raw)

- [ ] `docs/cenario.md`: a FarmaRede, o problema de negócio e as perguntas que os dados respondem.
- [ ] `docs/atores.md`: produtores e consumidores, com função, dados produzidos ou consumidos e frequência.
- [ ] `SimuladorVendas` (`fontes-operacionais/.../simulador/`) com `@Scheduled(every = "${simulador.intervalo}")`:
  - cria vendas PDV e e-commerce com 1 a 4 itens (cliente opcional no PDV, obrigatório no e-commerce);
  - para cada item, grava `movimento_estoque` (`SAIDA_VENDA`, quantidade negativa) e baixa `estoque_atual`;
  - de vez em quando: reposição (`ENTRADA`), cancelamento de venda (`status = CANCELADA`) e cliente novo;
  - configuração em `@ConfigMapping(prefix = "simulador")` (as chaves já estão no `application.properties`).
- [ ] `IngestaoOltpEtapa` (`plataforma-dados/.../ingestao/`, `EtapaPipeline` de **ordem 10**):
  - para cada tabela do OLTP, lê as linhas com `atualizado_em > watermark` pelo datasource `oltp`;
  - grava em `raw.<tabela>`: `lote_id`, `chave_origem` (PK; em `estoque_atual` use `loja_id:produto_id`),
    `payload` (linha inteira em JSON, `to_jsonb(t)`) e `atualizado_na_origem`;
  - atualiza `controle.watermark` na mesma transação do DW. Rodar duas vezes sem mudança na
    origem não pode inserir nada;
  - relê com uma **janela de sobreposição** (watermark menos alguns minutos) e ignora o que já
    existe com a mesma `chave_origem` + `atualizado_na_origem`. Uma transação que comita
    atrasada pode trazer `atualizado_em` menor que o watermark. O trigger da base garante que
    todo `UPDATE` no OLTP atualiza `atualizado_em`.
- [ ] (Opcional) Usuário **somente leitura** no OLTP para a plataforma (`GRANT SELECT`), com
      `OLTP_RO_USER`/`OLTP_RO_PASSWORD` no `.env`. Hoje a plataforma usa o mesmo usuário dono.
- [ ] `PipelineOrquestrador` (`plataforma-dados/.../pipeline/`):
  - injeta `@All List<EtapaPipeline>`, ordena por `ordem()` e executa cada etapa;
  - registra `EM_ANDAMENTO` → `SUCESSO`/`FALHA` em `controle.execucao_pipeline`;
  - interrompe as próximas etapas em caso de falha;
  - agendado por `@Scheduled(cron = "{pipeline.cron}")`, respeitando `pipeline.habilitado` e sem
    execução concorrente (`concurrentExecution = SKIP`).
- [ ] `PipelineResource`: `POST /pipeline/execucoes` (dispara agora, `202` com o `loteId`) e
      `GET /pipeline/execucoes` (histórico, mais recente primeiro).
- [ ] Seção **Raw** em `docs/camadas-dw.md` (por que guardar a cópia fiel e append-only).
- [ ] `docs/ciclo-de-vida.md`: uma venda do PDV até o dashboard, passando por cada container/tabela.
- [ ] Testes `@QuarkusTest`: o simulador gera venda + movimento + baixa de estoque; a ingestão é
      incremental e idempotente; o orquestrador registra sucesso e falha.

## Romulo: Curada, Agregada, Data Marts e consumo

**Branch:** `feat/romulo-dw-marts-consumo` · **Critérios do enunciado:** diagrama (20%),
camadas do DW (15%) e Data Marts (15%)

- [ ] Migration `V200__curated_modelo_estrela.sql`: `curated.dim_tempo`, `dim_loja`, `dim_produto`,
      `dim_cliente` (nome/e-mail **pseudonimizados**, LGPD), `fato_venda` (grão: item de venda) e
      `fato_estoque` (grão: saldo diário por loja/produto), com chaves substitutas.
- [ ] `TransformacaoCuradaEtapa` (`plataforma-dados/.../transformacao/`, **ordem 20**): pega a
      versão mais recente de cada `chave_origem` na `raw`, limpa e tipa os dados e faz upsert nas
      dimensões e fatos. Venda `CANCELADA` não entra como receita.
- [ ] Migration `V250__marts.sql`:
  - `marts.vendas_diarias` (receita, ticket médio e nº de vendas por dia/loja/canal);
  - `marts.vendas_top_produtos` (ranking por categoria/período);
  - `marts.estoque_ruptura` (produtos abaixo do mínimo por loja);
  - `marts.estoque_giro` (giro e dias de cobertura por produto/loja).
- [ ] `AgregacaoMartsEtapa` (`.../marts/`, **ordem 30**): recalcula os marts a partir da `curated`.
- [ ] API de consumo: `MartVendasResource` (`GET /marts/vendas/diarias`, `/marts/vendas/top-produtos`)
      e `MartEstoqueResource` (`GET /marts/estoque/ruptura`, `/marts/estoque/giro`), com filtros
      por período e loja e paginação.
- [ ] Metabase: conectar no `dw-db`, 1 dashboard por mart e prints em `docs/img/`. Explicar no
      README como conectar.
- [ ] `docs/arquitetura.md`: diagrama completo **com legenda** (Mermaid + PNG exportado em `docs/img/`).
- [ ] `docs/data-marts.md`: cada mart com público, perguntas respondidas, grão, métricas e exemplo de consulta.
- [ ] Seções **Curada**, **Agregada** e **DW × banco relacional** em `docs/camadas-dw.md`.
- [ ] Testes `@QuarkusTest`: inserir fixtures direto na `raw.*` (não depende da ingestão do Felipe),
      rodar as etapas 20 e 30 e conferir dimensões, fatos e marts. Deduplicação e cancelamento
      também precisam de teste.

## Começando com o Claude Code (ECC)

Na branch da sua parte, cole o comando correspondente numa sessão nova do Claude Code. O
`/ecc:plan` lê o contexto, propõe o plano e **espera sua confirmação** antes de escrever código.

**Felipe** (`git checkout feat/felipe-coleta-raw`):

```text
/ecc:plan Implementar a parte do Felipe no projeto FarmaRede (PR #1, branch feat/felipe-coleta-raw). Antes de planejar, leia Projeto1.md, docs/divisao-do-trabalho.md (seção "Felipe"), docs/evidencias.md (linhas do Felipe), o PR com `gh pr view 1` e o esqueleto já commitado (SimuladorConfig, SimuladorVendas, IngestaoOltpEtapa, PipelineOrquestrador, PipelineResource). Entregas: (1) simulador agendado no fontes-operacionais gerando vendas PDV/e-commerce, itens, movimentos SAIDA_VENDA e baixa de estoque_atual, com reposição, cancelamento e cliente novo ocasionais; (2) IngestaoOltpEtapa (ordem 10) incremental e idempotente OLTP→raw pelo controle.watermark, com janela de sobreposição e sem duplicar chave_origem+atualizado_na_origem; (3) PipelineOrquestrador com @Scheduled(cron="{pipeline.cron}", SKIP) respeitando pipeline.habilitado, registrando EM_ANDAMENTO→SUCESSO/FALHA em controle.execucao_pipeline em transação própria e parando nas falhas; (4) POST /pipeline/execucoes (202 + loteId) e GET /pipeline/execucoes (DTO record, mais recente primeiro); (5) testes @QuarkusTest em TDD para simulador, ingestão (incl. idempotência) e orquestrador (sucesso e falha); (6) docs/cenario.md, docs/atores.md, seção "Raw" de docs/camadas-dw.md e docs/ciclo-de-vida.md completos e sem TODO; (7) minhas linhas de docs/evidencias.md trocadas de ⬜ para ✅. Restrições: não editar migrations existentes (novas só V100–V199); não mudar o contrato EtapaPipeline nem o formato de raw.* sem combinar com o Romulo; ./mvnw spotless:apply e ./mvnw verify verdes; Conventional Commits pequenos na branch; nada direto na main. Pronto quando: CI do PR #1 verde e `bash scripts/verificar-entrega.sh` sem FALHA nos itens marcados [Felipe] (os [Romulo] podem falhar até o PR #2). Termine marcando o PR #1 como Ready for review.
```

**Romulo** (`git checkout feat/romulo-dw-marts-consumo`):

```text
/ecc:plan Implementar a parte do Romulo no projeto FarmaRede (PR #2, branch feat/romulo-dw-marts-consumo). Antes de planejar, leia Projeto1.md, docs/divisao-do-trabalho.md (seção "Romulo"), docs/evidencias.md (linhas do Romulo), o PR com `gh pr view 2` e o esqueleto já commitado (TransformacaoCuradaEtapa, AgregacaoMartsEtapa, MartVendasResource, MartEstoqueResource). Entregas: (1) migration V200 com modelo estrela em curated (dim_tempo, dim_loja, dim_produto, dim_cliente pseudonimizada, fato_venda no grão item, fato_estoque no grão saldo diário) com chaves substitutas; (2) TransformacaoCuradaEtapa (ordem 20) pegando a última versão por chave_origem da raw, limpando, tipando e fazendo upsert idempotente, sem contar venda CANCELADA como receita; (3) migration V250 com marts.vendas_diarias, marts.vendas_top_produtos, marts.estoque_ruptura e marts.estoque_giro, e AgregacaoMartsEtapa (ordem 30) recalculando-os; (4) GET /marts/vendas/diarias, /marts/vendas/top-produtos, /marts/estoque/ruptura e /marts/estoque/giro com filtros, paginação e DTOs record; (5) testes @QuarkusTest em TDD com fixtures inseridas direto em raw.* (não depende do PR #1); (6) Metabase com 1 dashboard por mart e prints em docs/img/; (7) docs/arquitetura.md com diagrama completo e legenda + docs/img/arquitetura.png, docs/data-marts.md e as seções Curada, Agregada e "Por que um DW" de docs/camadas-dw.md, sem TODO; (8) minhas linhas de docs/evidencias.md trocadas de ⬜ para ✅. Restrições: migrations só na faixa V200–V299; não mudar o contrato EtapaPipeline nem o formato de raw.* sem combinar com o Felipe; ./mvnw spotless:apply e ./mvnw verify verdes; Conventional Commits; nada direto na main; antes da revisão, git merge origin/main. Pronto quando: CI do PR #2 verde e `bash scripts/verificar-entrega.sh` sem nenhuma FALHA. Termine marcando o PR #2 como Ready for review.
```

## Pontos de contato entre as partes

| De → Para | Contrato |
|-----------|----------|
| Base → ambos | Formato das tabelas `raw.*` (`lote_id`, `chave_origem`, `payload jsonb` com os nomes de coluna do OLTP, `atualizado_na_origem`) e `controle.*`. Mudou? Combine antes e faça uma nova migration. |
| Felipe → Romulo | `PipelineOrquestrador` executa toda `EtapaPipeline` registrada como bean. O Romulo só implementa a interface com ordem 20 e 30; até o merge do Felipe, ele testa as etapas chamando `executar(ContextoExecucao.novo())` direto. |
| Romulo → Felipe | `docs/ciclo-de-vida.md` (Felipe) cita os nomes de tabela e mart definidos pelo Romulo. Alinhem os nomes antes do texto final. |

Se um contrato precisar mudar, avise no grupo e comente no PR do colega antes.

## Fluxo de trabalho

```bash
git fetch origin
git checkout feat/<sua-branch>      # a branch já existe, com o PR em rascunho
# ... codar ...
./mvnw spotless:apply               # formata (Google Java Format)
./mvnw verify                       # tudo verde antes de subir (precisa de Docker)
docker compose up -d --build --wait # confere a stack inteira
git add -A
git commit -m "feat: descrição curta"
git push
```

- Ao terminar, marque o PR como "Ready for review" e peça revisão do colega.
- **Ordem de merge:** Felipe → Romulo. O Romulo atualiza a branch com
  `git fetch origin && git merge origin/main`, resolve conflitos e roda `./mvnw verify` de novo.
- Ninguém commita direto na `main`.

## Pronto quando

- [ ] `bash scripts/verificar-entrega.sh` termina com **0 FALHA**.
- [ ] Todas as linhas de [docs/evidencias.md](evidencias.md) com ✅.
- [ ] `./mvnw verify` verde e CI verde.
- [ ] `docker compose up -d --build --wait` sobe tudo e, depois de alguns ciclos, `GET /marts/...`
      retorna dados gerados pelo simulador.
- [ ] Todos os itens obrigatórios do `Projeto1.md` com documento e a coluna "Entregue?" preenchida.
