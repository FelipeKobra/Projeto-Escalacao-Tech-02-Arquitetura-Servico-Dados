-- =============================================================================
-- Camada Curada (modelo estrela) da FarmaRede.
-- Dimensões e fatos com chaves substitutas, dados limpos/tipados e LGPD.
-- =============================================================================

CREATE TABLE curated.dim_tempo (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data            DATE        NOT NULL UNIQUE,
    ano             SMALLINT    NOT NULL,
    mes             SMALLINT    NOT NULL,
    dia             SMALLINT    NOT NULL,
    trimestre       SMALLINT    NOT NULL,
    dia_semana      SMALLINT    NOT NULL,
    nome_mes        VARCHAR(20) NOT NULL,
    nome_dia_semana VARCHAR(20) NOT NULL,
    fim_de_semana   BOOLEAN     NOT NULL
);

CREATE TABLE curated.dim_loja (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_origem     BIGINT       NOT NULL UNIQUE,
    codigo        VARCHAR(10)  NOT NULL,
    nome          VARCHAR(120) NOT NULL,
    cidade        VARCHAR(80)  NOT NULL,
    uf            CHAR(2)      NOT NULL,
    tipo          VARCHAR(10)  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE curated.dim_produto (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_origem      BIGINT         NOT NULL UNIQUE,
    sku            VARCHAR(20)    NOT NULL,
    nome           VARCHAR(160)   NOT NULL,
    categoria      VARCHAR(60)    NOT NULL,
    fabricante     VARCHAR(80)    NOT NULL,
    preco_unitario NUMERIC(10, 2) NOT NULL,
    controlado     BOOLEAN        NOT NULL,
    atualizado_em  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- LGPD: sem nome, e-mail ou data de nascimento em claro na camada curada.
CREATE TABLE curated.dim_cliente (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_origem        BIGINT      NOT NULL UNIQUE,
    chave_pseudonimo CHAR(64)    NOT NULL UNIQUE,
    cidade           VARCHAR(80) NOT NULL,
    uf               CHAR(2)     NOT NULL,
    faixa_etaria     VARCHAR(20) NOT NULL,
    atualizado_em    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Grão: item de venda. Vendas CANCELADA permanecem para rastreabilidade, com valor_liquido = 0.
CREATE TABLE curated.fato_venda (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_item_origem  BIGINT         NOT NULL UNIQUE,
    id_venda_origem BIGINT         NOT NULL,
    tempo_id        BIGINT         NOT NULL REFERENCES curated.dim_tempo (id),
    loja_id         BIGINT         NOT NULL REFERENCES curated.dim_loja (id),
    produto_id      BIGINT         NOT NULL REFERENCES curated.dim_produto (id),
    cliente_id      BIGINT         REFERENCES curated.dim_cliente (id),
    canal           VARCHAR(10)    NOT NULL,
    status          VARCHAR(10)    NOT NULL,
    forma_pagamento VARCHAR(20)    NOT NULL,
    quantidade      INT            NOT NULL,
    preco_unitario  NUMERIC(10, 2) NOT NULL,
    desconto        NUMERIC(10, 2) NOT NULL DEFAULT 0,
    valor_bruto     NUMERIC(12, 2) NOT NULL,
    valor_liquido   NUMERIC(12, 2) NOT NULL,
    valor_cancelado NUMERIC(12, 2) NOT NULL DEFAULT 0,
    realizada_em    TIMESTAMPTZ    NOT NULL,
    atualizado_em   TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_fato_venda_tempo_loja ON curated.fato_venda (tempo_id, loja_id);
CREATE INDEX idx_fato_venda_produto ON curated.fato_venda (produto_id);
CREATE INDEX idx_fato_venda_venda_origem ON curated.fato_venda (id_venda_origem);
CREATE INDEX idx_fato_venda_status ON curated.fato_venda (status);

-- Grão: saldo diário por loja e produto, enriquecido com os movimentos do dia.
CREATE TABLE curated.fato_estoque (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tempo_id         BIGINT      NOT NULL REFERENCES curated.dim_tempo (id),
    loja_id          BIGINT      NOT NULL REFERENCES curated.dim_loja (id),
    produto_id       BIGINT      NOT NULL REFERENCES curated.dim_produto (id),
    quantidade_saldo INT         NOT NULL,
    estoque_minimo   INT         NOT NULL,
    em_ruptura       BOOLEAN     NOT NULL,
    qtd_entradas     INT         NOT NULL DEFAULT 0,
    qtd_saidas_venda INT         NOT NULL DEFAULT 0,
    qtd_ajustes      INT         NOT NULL DEFAULT 0,
    qtd_perdas       INT         NOT NULL DEFAULT 0,
    atualizado_em    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tempo_id, loja_id, produto_id)
);

CREATE INDEX idx_fato_estoque_loja_ruptura ON curated.fato_estoque (loja_id, em_ruptura);
CREATE INDEX idx_fato_estoque_produto ON curated.fato_estoque (produto_id);
