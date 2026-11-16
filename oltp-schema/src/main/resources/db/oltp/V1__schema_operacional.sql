-- =============================================================================
-- Banco OPERACIONAL (OLTP) da FarmaRede: o que os sistemas de loja (PDV),
-- e-commerce e estoque gravam no dia a dia. É a FONTE da plataforma de dados.
-- Toda tabela tem atualizado_em: é o watermark da ingestão incremental.
-- =============================================================================

CREATE TABLE loja (
    id            BIGSERIAL PRIMARY KEY,
    codigo        VARCHAR(10)  NOT NULL UNIQUE,
    nome          VARCHAR(120) NOT NULL,
    cidade        VARCHAR(80)  NOT NULL,
    uf            CHAR(2)      NOT NULL,
    tipo          VARCHAR(10)  NOT NULL CHECK (tipo IN ('FISICA', 'ONLINE')),
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE produto (
    id             BIGSERIAL PRIMARY KEY,
    sku            VARCHAR(20)   NOT NULL UNIQUE,
    nome           VARCHAR(160)  NOT NULL,
    categoria      VARCHAR(60)   NOT NULL,
    fabricante     VARCHAR(80)   NOT NULL,
    preco_unitario NUMERIC(10, 2) NOT NULL CHECK (preco_unitario > 0),
    controlado     BOOLEAN       NOT NULL DEFAULT FALSE,
    criado_em      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE cliente (
    id              BIGSERIAL PRIMARY KEY,
    nome            VARCHAR(120) NOT NULL,
    email           VARCHAR(160) NOT NULL UNIQUE,
    cidade          VARCHAR(80)  NOT NULL,
    uf              CHAR(2)      NOT NULL,
    data_nascimento DATE,
    criado_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE venda (
    id              BIGSERIAL PRIMARY KEY,
    loja_id         BIGINT        NOT NULL REFERENCES loja (id),
    -- Cliente é opcional: venda de balcão sem identificação.
    cliente_id      BIGINT        REFERENCES cliente (id),
    canal           VARCHAR(10)   NOT NULL CHECK (canal IN ('PDV', 'ECOMMERCE')),
    status          VARCHAR(10)   NOT NULL CHECK (status IN ('CONCLUIDA', 'CANCELADA')),
    forma_pagamento VARCHAR(20)   NOT NULL CHECK (forma_pagamento IN ('DINHEIRO', 'DEBITO', 'CREDITO', 'PIX')),
    valor_total     NUMERIC(12, 2) NOT NULL CHECK (valor_total >= 0),
    realizada_em    TIMESTAMPTZ   NOT NULL,
    criado_em       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizado_em   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE item_venda (
    id             BIGSERIAL PRIMARY KEY,
    venda_id       BIGINT        NOT NULL REFERENCES venda (id),
    produto_id     BIGINT        NOT NULL REFERENCES produto (id),
    quantidade     INT           NOT NULL CHECK (quantidade > 0),
    preco_unitario NUMERIC(10, 2) NOT NULL CHECK (preco_unitario > 0),
    desconto       NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (desconto >= 0),
    atualizado_em  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE movimento_estoque (
    id            BIGSERIAL PRIMARY KEY,
    loja_id       BIGINT      NOT NULL REFERENCES loja (id),
    produto_id    BIGINT      NOT NULL REFERENCES produto (id),
    tipo          VARCHAR(12) NOT NULL CHECK (tipo IN ('ENTRADA', 'SAIDA_VENDA', 'AJUSTE', 'PERDA')),
    -- Positivo para entrada, negativo para saída/perda.
    quantidade    INT         NOT NULL,
    ocorrido_em   TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Saldo corrente por loja/produto (o que o sistema de estoque mostra hoje).
CREATE TABLE estoque_atual (
    loja_id        BIGINT      NOT NULL REFERENCES loja (id),
    produto_id     BIGINT      NOT NULL REFERENCES produto (id),
    quantidade     INT         NOT NULL CHECK (quantidade >= 0),
    estoque_minimo INT         NOT NULL DEFAULT 10,
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (loja_id, produto_id)
);

-- Garante o watermark mesmo em UPDATE fora do Hibernate (SQL nativo, bulk update, psql).
CREATE FUNCTION marcar_atualizado_em() RETURNS TRIGGER AS $$
BEGIN
    NEW.atualizado_em := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    tabela TEXT;
BEGIN
    FOREACH tabela IN ARRAY ARRAY['loja', 'produto', 'cliente', 'venda', 'item_venda',
                                  'movimento_estoque', 'estoque_atual'] LOOP
        EXECUTE format(
            'CREATE TRIGGER %I BEFORE UPDATE ON %I
                 FOR EACH ROW EXECUTE FUNCTION marcar_atualizado_em()',
            'trg_' || tabela || '_atualizado_em', tabela);
    END LOOP;
END $$;

CREATE INDEX idx_venda_atualizado_em ON venda (atualizado_em);
CREATE INDEX idx_item_venda_atualizado_em ON item_venda (atualizado_em);
CREATE INDEX idx_movimento_estoque_atualizado_em ON movimento_estoque (atualizado_em);
CREATE INDEX idx_estoque_atual_atualizado_em ON estoque_atual (atualizado_em);
CREATE INDEX idx_cliente_atualizado_em ON cliente (atualizado_em);
CREATE INDEX idx_produto_atualizado_em ON produto (atualizado_em);
CREATE INDEX idx_loja_atualizado_em ON loja (atualizado_em);
