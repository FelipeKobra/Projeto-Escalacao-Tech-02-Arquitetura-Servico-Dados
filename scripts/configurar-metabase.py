#!/usr/bin/env python3
"""Provisiona o Metabase na primeira subida (usuário, banco dw-db e dashboards dos Data Marts).

Idempotente: se os dashboards já existirem, não duplica nem sobrescreve.
"""

import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import List, Optional, Tuple


def carregar_dotenv() -> None:
    """Carrega .env da raiz do projeto caso o script seja executado fora do Compose."""
    env_path = Path(__file__).resolve().parent.parent / ".env"
    if not env_path.is_file():
        return
    for linha in env_path.read_text(encoding="utf-8").splitlines():
        linha = linha.strip()
        if not linha or linha.startswith("#") or "=" not in linha:
            continue
        chave, valor = linha.split("=", 1)
        os.environ.setdefault(chave.strip(), valor.strip())


carregar_dotenv()


def exigir_env(nome: str) -> str:
    valor = os.environ.get(nome, "").strip()
    if not valor:
        raise SystemExit(
            f"Variável de ambiente obrigatória ausente: {nome}. Defina no arquivo .env."
        )
    return valor


METABASE_URL = os.environ.get("METABASE_URL", "http://localhost:3000").rstrip("/")
DW_DB_HOST = os.environ.get("DW_DB_HOST", "dw-db")
DW_DB_PORT = int(os.environ.get("DW_DB_PORT", "5432"))
DW_DB_NAME = os.environ.get("DW_DB_NAME", "dw")
DW_DB_USER = os.environ.get("DW_DB_USER", "farmarede")
DW_DB_PASSWORD = exigir_env("DW_DB_PASSWORD")
ADMIN_EMAIL = exigir_env("METABASE_ADMIN_EMAIL")
ADMIN_PASSWORD = exigir_env("METABASE_ADMIN_PASSWORD")

DASH_VENDAS = "Mart de Vendas — FarmaRede"
DASH_ESTOQUE = "Mart de Estoque — FarmaRede"


def req(method: str, path: str, body: Optional[dict] = None, token: Optional[str] = None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["X-Metabase-Session"] = token
    data = json.dumps(body).encode("utf-8") if body is not None else None
    request = urllib.request.Request(
        f"{METABASE_URL}{path}", data=data, headers=headers, method=method
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {path} -> HTTP {exc.code}: {detail}") from exc


def aguardar_metabase(timeout_s: int = 240) -> None:
    inicio = time.monotonic()
    while time.monotonic() - inicio < timeout_s:
        try:
            health = req("GET", "/api/health")
            if health.get("status") == "ok":
                return
        except Exception:
            pass
        time.sleep(3)
    raise TimeoutError(f"Metabase não ficou pronto em {timeout_s}s ({METABASE_URL})")


def detalhes_dw() -> dict:
    return {
        "name": "FarmaRede DW",
        "engine": "postgres",
        "details": {
            "host": DW_DB_HOST,
            "port": DW_DB_PORT,
            "dbname": DW_DB_NAME,
            "user": DW_DB_USER,
            "password": DW_DB_PASSWORD,
            "ssl": False,
            "schema-filters-type": "inclusion",
            "schema-filters-patterns": "marts,curated",
        },
    }


def autenticar_ou_configurar() -> str:
    props = req("GET", "/api/session/properties")
    if not props.get("has-user-setup"):
        setup_token = props["setup-token"]
        resposta = req(
            "POST",
            "/api/setup",
            {
                "token": setup_token,
                "user": {
                    "first_name": "Romulo",
                    "last_name": "FarmaRede",
                    "email": ADMIN_EMAIL,
                    "password": ADMIN_PASSWORD,
                    "site_name": "FarmaRede BI",
                },
                "prefs": {
                    "site_name": "FarmaRede BI",
                    "site_locale": "pt",
                    "allow_tracking": False,
                },
                "database": detalhes_dw(),
            },
        )
        print("Metabase inicializado com usuário admin e conexão FarmaRede DW.")
        return resposta["id"]

    login = req(
        "POST",
        "/api/session",
        {"username": ADMIN_EMAIL, "password": ADMIN_PASSWORD},
    )
    return login["id"]


def garantir_banco_dw(session: str) -> int:
    dbs = req("GET", "/api/database", token=session)
    db_list = dbs.get("data", dbs) if isinstance(dbs, dict) else dbs
    dw_db = next((d for d in db_list if d.get("name") == "FarmaRede DW"), None)
    if not dw_db:
        dw_db = req("POST", "/api/database", detalhes_dw(), token=session)
    db_id = dw_db["id"]
    req("POST", f"/api/database/{db_id}/sync_schema", {}, token=session)
    return db_id


def criar_card(
    session: str,
    db_id: int,
    name: str,
    sql: str,
    display: str,
    viz_settings: Optional[dict] = None,
) -> int:
    payload = {
        "name": name,
        "display": display,
        "visualization_settings": viz_settings or {},
        "dataset_query": {
            "type": "native",
            "database": db_id,
            "native": {"query": sql},
        },
    }
    return req("POST", "/api/card", payload, token=session)["id"]


def criar_dashboard(
    session: str, name: str, description: str, layout: List[Tuple]
) -> int:
    dash = req(
        "POST",
        "/api/dashboard",
        {"name": name, "description": description},
        token=session,
    )
    dash_id = dash["id"]
    dashcards = [
        {
            "id": -idx,
            "card_id": card_id,
            "col": col,
            "row": row,
            "size_x": size_x,
            "size_y": size_y,
            "parameter_mappings": [],
            "visualization_settings": {},
        }
        for idx, (card_id, col, row, size_x, size_y) in enumerate(layout, start=1)
    ]
    req("PUT", f"/api/dashboard/{dash_id}", {"dashcards": dashcards}, token=session)
    return dash_id


def destacar_na_tela_inicial(session: str, dash_ids: List[int]) -> None:
    """Fixa os dashboards na coleção raiz, favorita-os e define /collection/root como landing page."""
    for pos, dash_id in enumerate(dash_ids, start=1):
        req("PUT", f"/api/dashboard/{dash_id}", {"collection_position": pos}, token=session)
        try:
            req("POST", f"/api/bookmark/dashboard/{dash_id}", {}, token=session)
        except RuntimeError:
            pass
    req("PUT", "/api/setting/expand-bookmarks-in-nav", {"value": True}, token=session)
    req("PUT", "/api/setting/landing-page", {"value": "/collection/root"}, token=session)


def provisionar_dashboards(session: str, db_id: int) -> None:
    existentes = {d["name"]: d["id"] for d in req("GET", "/api/dashboard", token=session)}

    if DASH_VENDAS not in existentes:
        v1 = criar_card(
            session,
            db_id,
            "Receita Líquida Total (R$)",
            "SELECT coalesce(round(sum(receita_liquida), 2), 0) AS receita_liquida FROM marts.vendas_diarias",
            "scalar",
        )
        v2 = criar_card(
            session,
            db_id,
            "Total de Vendas Concluídas",
            "SELECT coalesce(sum(qtd_vendas), 0) AS qtd_vendas FROM marts.vendas_diarias",
            "scalar",
        )
        v3 = criar_card(
            session,
            db_id,
            "Ticket Médio Geral (R$)",
            "SELECT coalesce(round(sum(receita_liquida) / nullif(sum(qtd_vendas), 0), 2), 0) AS ticket_medio FROM marts.vendas_diarias",
            "scalar",
        )
        v4 = criar_card(
            session,
            db_id,
            "Receita Líquida por Loja e Canal",
            "SELECT loja_codigo || ' (' || canal || ')' AS loja_canal, sum(receita_liquida) AS receita_liquida FROM marts.vendas_diarias GROUP BY 1 ORDER BY 2 DESC",
            "bar",
            {
                "graph.dimensions": ["loja_canal"],
                "graph.metrics": ["receita_liquida"],
                "graph.y_axis.title_text": "Receita Líquida (R$)",
            },
        )
        v5 = criar_card(
            session,
            db_id,
            "Top Produtos por Receita Líquida",
            "SELECT produto_nome, sum(receita_liquida) AS receita_liquida FROM marts.vendas_top_produtos GROUP BY produto_nome ORDER BY 2 DESC LIMIT 8",
            "row",
            {
                "graph.dimensions": ["produto_nome"],
                "graph.metrics": ["receita_liquida"],
            },
        )
        v6 = criar_card(
            session,
            db_id,
            "Vendas Diárias por Loja e Canal",
            "SELECT data, loja_codigo, loja_nome, canal, qtd_vendas, qtd_itens, receita_liquida, receita_cancelada, ticket_medio FROM marts.vendas_diarias ORDER BY data DESC, receita_liquida DESC",
            "table",
        )
        dash_vendas_id = criar_dashboard(
            session,
            DASH_VENDAS,
            "Indicadores comerciais: receita líquida, ticket médio, volume por canal/loja e ranking de produtos.",
            [
                (v1, 0, 0, 8, 3),
                (v2, 8, 0, 8, 3),
                (v3, 16, 0, 8, 3),
                (v4, 0, 3, 12, 7),
                (v5, 12, 3, 12, 7),
                (v6, 0, 10, 24, 6),
            ],
        )
        print(f"Dashboard criado: {DASH_VENDAS} (id={dash_vendas_id})")
    else:
        dash_vendas_id = existentes[DASH_VENDAS]
        print(f"Dashboard já existe: {DASH_VENDAS} (id={dash_vendas_id})")

    if DASH_ESTOQUE not in existentes:
        e1 = criar_card(
            session,
            db_id,
            "Itens em Ruptura (Abaixo do Mínimo)",
            "SELECT count(*) AS itens_em_ruptura FROM marts.estoque_ruptura",
            "scalar",
        )
        e2 = criar_card(
            session,
            db_id,
            "SKUs x Lojas Monitorados",
            "SELECT count(*) AS total_monitorado FROM marts.estoque_giro",
            "scalar",
        )
        e3 = criar_card(
            session,
            db_id,
            "Menor Cobertura em Dias (Top 10)",
            "SELECT loja_codigo || ' - ' || sku AS loja_produto, dias_cobertura FROM marts.estoque_giro WHERE dias_cobertura IS NOT NULL ORDER BY dias_cobertura ASC LIMIT 10",
            "row",
            {
                "graph.dimensions": ["loja_produto"],
                "graph.metrics": ["dias_cobertura"],
            },
        )
        e4 = criar_card(
            session,
            db_id,
            "Giro Médio de Estoque por Categoria",
            "SELECT categoria, round(avg(giro_estoque), 2) AS giro_medio FROM marts.estoque_giro GROUP BY categoria ORDER BY 2 DESC",
            "bar",
            {
                "graph.dimensions": ["categoria"],
                "graph.metrics": ["giro_medio"],
            },
        )
        e5 = criar_card(
            session,
            db_id,
            "Ruptura Atual por Loja",
            "SELECT loja_codigo, loja_nome, sku, produto_nome, categoria, quantidade_saldo, estoque_minimo, deficit FROM marts.estoque_ruptura ORDER BY deficit DESC",
            "table",
        )
        e6 = criar_card(
            session,
            db_id,
            "Giro e Dias de Cobertura (Top Urgência)",
            "SELECT loja_codigo, sku, produto_nome, categoria, quantidade_saldo, estoque_minimo, qtd_vendida_30d, media_diaria_vendas, giro_estoque, dias_cobertura FROM marts.estoque_giro ORDER BY dias_cobertura ASC NULLS LAST LIMIT 12",
            "table",
        )
        dash_estoque_id = criar_dashboard(
            session,
            DASH_ESTOQUE,
            "Monitoramento de abastecimento: ruptura, giro de estoque e dias de cobertura por loja/produto.",
            [
                (e1, 0, 0, 12, 3),
                (e2, 12, 0, 12, 3),
                (e3, 0, 3, 12, 7),
                (e4, 12, 3, 12, 7),
                (e5, 0, 10, 12, 6),
                (e6, 12, 10, 12, 6),
            ],
        )
        print(f"Dashboard criado: {DASH_ESTOQUE} (id={dash_estoque_id})")
    else:
        dash_estoque_id = existentes[DASH_ESTOQUE]
        print(f"Dashboard já existe: {DASH_ESTOQUE} (id={dash_estoque_id})")

    destacar_na_tela_inicial(session, [dash_vendas_id, dash_estoque_id])


def main() -> int:
    aguardar_metabase()
    session = autenticar_ou_configurar()
    db_id = garantir_banco_dw(session)
    provisionar_dashboards(session, db_id)
    return 0


if __name__ == "__main__":
    sys.exit(main())
