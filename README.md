# FarmaRede: Plataforma de Dados (Projeto 1: Arquitetura de Dados)

Arquitetura de dados **executável** para uma rede de farmácias fictícia, a **FarmaRede** (lojas
físicas + e-commerce). Os sistemas operacionais (PDV, site e estoque) produzem dados num banco
transacional. Uma plataforma em Quarkus coleta esses dados, processa nas camadas **Raw → Curada →
Agregada** de um **Data Warehouse** e entrega **Data Marts** de Vendas e Estoque para os
consumidores (API REST e Metabase). Tudo roda em containers orquestrados com **Docker Compose**.

O enunciado está em [`Projeto1.md`](Projeto1.md) e fica no repositório até a entrega final.

## Início rápido (3 passos)

Precisa só de **Docker** (Docker Desktop no Windows/macOS).

```bash
# 1. Configuração (uma vez)
cp .env.example .env                      # PowerShell: Copy-Item .env.example .env

# 2. Sobe tudo e espera ficar saudável (o primeiro build leva alguns minutos)
docker compose up -d --build --wait

# 3. Confere cada item do Projeto1.md automaticamente
bash scripts/verificar-entrega.sh         # Windows: Git Bash ou WSL
```

O passo 3 mostra `[OK]`/`[FALHA]` para cada ponto pedido no enunciado, com o responsável.
A prova de cada item, com links e comandos para conferir manualmente, está em
**[docs/evidencias.md](docs/evidencias.md)**.

Para parar: `docker compose down`. Para parar e apagar os dados: `docker compose down -v`.

## Equipe

| Integrante | Parte |
|------------|-------|
| Felipe | Negócio, atores, coleta (simulador + ingestão), camada Raw, pipeline e ciclo de vida do dado |
| Romulo | Camadas Curada e Agregada, Data Marts, API de consumo, Metabase e diagrama completo |

Tarefas, contratos e ordem de merge: [docs/divisao-do-trabalho.md](docs/divisao-do-trabalho.md).

## Entregáveis

Status e evidência de cada item: [docs/evidencias.md](docs/evidencias.md).

| Item obrigatório | Documento |
|------------------|-----------|
| Definição do negócio escolhido | [docs/cenario.md](docs/cenario.md) |
| Atores identificados | [docs/atores.md](docs/atores.md) |
| Diagrama da arquitetura de dados (com legenda) | [docs/arquitetura.md](docs/arquitetura.md) |
| Camadas do DW (Raw, Curada, Agregada) | [docs/camadas-dw.md](docs/camadas-dw.md) |
| Data Marts definidos (≥ 2) | [docs/data-marts.md](docs/data-marts.md) |
| Texto explicativo do ciclo do dado | [docs/ciclo-de-vida.md](docs/ciclo-de-vida.md) |

## Arquitetura em uma linha

```
PDV / E-commerce / Estoque → [fontes-operacionais] → oltp-db → [plataforma-dados: ingestão] →
dw-db (raw → curated → marts) → [API /marts] + [Metabase] → Comercial / Compras / Marketing
```

| Container | Papel | Porta (host) |
|-----------|-------|--------------|
| `oltp-db` | Postgres 17, banco **operacional** (fonte) | 5433 |
| `dw-db` | Postgres 17, **Data Warehouse** (schemas `raw`, `curated`, `marts`, `controle`) | 5434 |
| `fontes-operacionais` | Quarkus. Simula os produtores e grava no OLTP | 8081 |
| `plataforma-dados` | Quarkus. Coleta, processa, orquestra o pipeline e expõe os marts | 8080 |
| `metabase` | BI para os consumidores (profile `bi`, opcional) | 3000 |

## Stack

Java 21 · Quarkus 3.40 (LTS) · Maven multi-módulo (`./mvnw`) · PostgreSQL 17 · Flyway ·
Hibernate ORM/Panache · Quarkus Scheduler · SmallRye Health/OpenAPI · Lombok · Spotless (Google
Java Format) · Docker Compose · Metabase · GitHub Actions.

| Módulo | Conteúdo |
|--------|----------|
| `oltp-schema` | Migrations Flyway do banco operacional (`classpath:db/oltp`): tabelas e dados de referência |
| `fontes-operacionais` | Entidades do OLTP, simulador de vendas/estoque e `GET /fontes/resumo` |
| `plataforma-dados` | `pipeline/` (contrato `EtapaPipeline` + orquestrador), `ingestao/`, `transformacao/`, `marts/` e migrations do DW (`db/migration`) |

## Como rodar

Pré-requisitos: **Docker** (com Compose v2) e, para desenvolver, **JDK 21**.

```bash
cp .env.example .env                     # PowerShell: Copy-Item .env.example .env
docker compose up -d --build --wait      # sobe tudo e espera os healthchecks
docker compose --profile bi up -d        # opcional: Metabase em http://localhost:3000
docker compose ps                        # todos devem estar "healthy"
docker compose logs -f plataforma-dados  # acompanhar o pipeline
docker compose down                      # para (dados preservados nos volumes)
docker compose down -v                   # para e apaga os dados
```

### Conectando o Metabase ao `dw-db`

1. Suba a stack com o perfil `bi`: `docker compose --profile bi up -d --wait`.
2. Acesse `http://localhost:3000` e conclua o assistente inicial (ou faça login).
3. Em **Admin → Databases → Add database**, escolha **PostgreSQL** e preencha:
   - **Host:** `dw-db` (nome do serviço na rede interna do Compose)
   - **Port:** `5432`
   - **Database name:** `dw`
   - **Username / Password:** os valores de `DW_DB_USER` e `DW_DB_PASSWORD` do `.env` (padrão `farmarede` / `troque-me-dw`)
   - **Schemas (opcional):** filtre por `marts,curated` para não expor `raw` aos consumidores.
4. Após sincronizar, consulte as tabelas `marts.vendas_diarias`, `marts.vendas_top_produtos`, `marts.estoque_ruptura` e `marts.estoque_giro` (exemplos e prints em [docs/data-marts.md](docs/data-marts.md)).

Se alguma porta já estiver em uso na sua máquina, troque `FONTES_PORTA`, `PLATAFORMA_PORTA`,
`METABASE_PORTA`, `OLTP_DB_PORTA` ou `DW_DB_PORTA` no `.env`.

| URL | O quê |
|-----|-------|
| http://localhost:8081/fontes/resumo | Volume de registros no OLTP (cresce com o simulador) |
| http://localhost:8081/q/swagger-ui | Swagger das fontes |
| http://localhost:8080/q/swagger-ui | Swagger da plataforma (pipeline e marts) |
| http://localhost:8080/q/health | Health da plataforma (DW + OLTP) |

### Desenvolvimento (sem subir a stack inteira)

```bash
./mvnw -pl fontes-operacionais -am quarkus:dev   # porta 8081, Postgres via Dev Services
./mvnw -pl plataforma-dados -am quarkus:dev      # porta 8080, DW + OLTP (com seed) via Dev Services
./mvnw spotless:apply                            # formata (Google Java Format)
./mvnw verify                                    # todos os testes (precisa de Docker rodando)
```

Em `dev`/`test`, o Quarkus sobe os Postgres sozinho com o **Dev Services**. Na plataforma, o OLTP
de dev/test já recebe o schema e os dados de referência do módulo `oltp-schema`.

> **Erro "Migration checksum mismatch" nos testes?** Se o seu `~/.testcontainers.properties` tem
> `testcontainers.reuse.enable=true`, o Dev Services reaproveita containers antigos. Remova os
> containers com o label `io.quarkus.devservice.launch-mode=TEST` e rode de novo. Migration já
> mergeada **nunca** se edita: crie uma nova versão.

## Configuração

Todas as chaves aceitam variável de ambiente (o compose lê do `.env`):

| Variável | Uso | Padrão no `.env.example` |
|----------|-----|--------------------------|
| `OLTP_DB_USER` / `OLTP_DB_PASSWORD` | Credenciais do banco operacional | `farmarede` / `troque-me-oltp` |
| `DW_DB_USER` / `DW_DB_PASSWORD` | Credenciais do DW | `farmarede` / `troque-me-dw` |
| `SIMULADOR_HABILITADO`, `SIMULADOR_INTERVALO`, `SIMULADOR_VENDAS_POR_CICLO` | Simulador de vendas | `true`, `10s`, `5` |
| `PIPELINE_HABILITADO`, `PIPELINE_CRON` | Agenda do pipeline ingestão → curada → marts | `true`, `0 */2 * * * ?` |

O `.env` não vai para o git. Sem as senhas definidas, o compose e as apps em `prod` se recusam
a subir.

## Convenções

- Commits em [Conventional Commits](https://www.conventionalcommits.org/pt-br/v1.0.0/)
  (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`).
- Migrations do DW por faixa: `V1–V99` base, `V100–V199` Felipe, `V200–V299` Romulo.
- O CI (`.github/workflows/ci.yml`) roda `spotless:check`, `verify` e sobe o Compose com smoke test
  em todo PR.
