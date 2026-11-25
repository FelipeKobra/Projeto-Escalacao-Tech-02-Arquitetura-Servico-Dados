-- =============================================================================
-- Camada Agregada (Data Marts) da FarmaRede.
-- Tabelas pré-agregadas para consumo analítico via API REST (/marts) e Metabase.
-- =============================================================================

-- Mart de Vendas: receita, volume, cancelamentos e ticket médio por dia/loja/canal.
CREATE TABLE marts.vendas_diarias (
    data              DATE           NOT NULL,
    loja_codigo       VARCHAR(10)    NOT NULL,
    loja_nome         VARCHAR(120)   NOT NULL,
    loja_uf           CHAR(2)        NOT NULL,
    canal             VARCHAR(10)    NOT NULL,
    qtd_vendas        BIGINT         NOT NULL,
    qtd_itens         BIGINT         NOT NULL,
    receita_bruta     NUMERIC(14, 2) NOT NULL,
    receita_liquida   NUMERIC(14, 2) NOT NULL,
    receita_cancelada NUMERIC(14, 2) NOT NULL,
    ticket_medio      NUMERIC(12, 2) NOT NULL,
    atualizado_em     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (data, loja_codigo, canal)
);

CREATE INDEX idx_vendas_diarias_loja_data ON marts.vendas_diarias (loja_codigo, data);

-- Mart de Vendas: ranking de produtos por categoria e período (YYYY-MM).
CREATE TABLE marts.vendas_top_produtos (
    periodo           VARCHAR(7)     NOT NULL,
    categoria         VARCHAR(60)    NOT NULL,
    sku               VARCHAR(20)    NOT NULL,
    produto_nome      VARCHAR(160)   NOT NULL,
    fabricante        VARCHAR(80)    NOT NULL,
    qtd_vendas        BIGINT         NOT NULL,
    qtd_vendida       BIGINT         NOT NULL,
    receita_liquida   NUMERIC(14, 2) NOT NULL,
    ranking_categoria INT            NOT NULL,
    ranking_geral     INT            NOT NULL,
    atualizado_em     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (periodo, sku)
);

CREATE INDEX idx_vendas_top_produtos_cat_rank
    ON marts.vendas_top_produtos (categoria, ranking_categoria);

-- Mart de Estoque: produtos abaixo do estoque mínimo por loja (último saldo conhecido).
CREATE TABLE marts.estoque_ruptura (
    data_referencia  DATE         NOT NULL,
    loja_codigo      VARCHAR(10)  NOT NULL,
    loja_nome        VARCHAR(120) NOT NULL,
    loja_uf          CHAR(2)      NOT NULL,
    sku              VARCHAR(20)  NOT NULL,
    produto_nome     VARCHAR(160) NOT NULL,
    categoria        VARCHAR(60)  NOT NULL,
    quantidade_saldo INT          NOT NULL,
    estoque_minimo   INT          NOT NULL,
    deficit          INT          NOT NULL,
    atualizado_em    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (loja_codigo, sku)
);

-- Mart de Estoque: giro e dias de cobertura (janela móvel de até 30 dias) por produto/loja.
CREATE TABLE marts.estoque_giro (
    data_referencia     DATE           NOT NULL,
    loja_codigo         VARCHAR(10)    NOT NULL,
    loja_nome           VARCHAR(120)   NOT NULL,
    loja_uf             CHAR(2)        NOT NULL,
    sku                 VARCHAR(20)    NOT NULL,
    produto_nome        VARCHAR(160)   NOT NULL,
    categoria           VARCHAR(60)    NOT NULL,
    quantidade_saldo    INT            NOT NULL,
    estoque_minimo      INT            NOT NULL,
    qtd_vendida_30d     BIGINT         NOT NULL,
    media_diaria_vendas NUMERIC(10, 2) NOT NULL,
    giro_estoque        NUMERIC(10, 2),
    dias_cobertura      NUMERIC(10, 1),
    atualizado_em       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (loja_codigo, sku)
);

CREATE INDEX idx_estoque_giro_cat ON marts.estoque_giro (categoria, loja_codigo);
