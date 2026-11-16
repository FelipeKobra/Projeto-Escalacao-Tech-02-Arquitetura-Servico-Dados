-- =============================================================================
-- Data Warehouse da FarmaRede: estrutura base (contrato entre Felipe e Romulo).
--   raw      -> cópia fiel do OLTP, append-only, com metadados de ingestão (Felipe)
--   curated  -> dados limpos, deduplicados, modelo estrela (Romulo, V200+)
--   marts    -> agregados por área de negócio: vendas e estoque (Romulo, V200+)
--   controle -> metadados do pipeline: execuções e watermarks (base / Felipe)
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS raw;
CREATE SCHEMA IF NOT EXISTS curated;
CREATE SCHEMA IF NOT EXISTS marts;
CREATE SCHEMA IF NOT EXISTS controle;

-- ---------- controle ----------
CREATE TABLE controle.execucao_pipeline (
    id                    BIGSERIAL PRIMARY KEY,
    lote_id               UUID        NOT NULL,
    etapa                 VARCHAR(60) NOT NULL,
    status                VARCHAR(15) NOT NULL CHECK (status IN ('EM_ANDAMENTO', 'SUCESSO', 'FALHA')),
    iniciado_em           TIMESTAMPTZ NOT NULL,
    finalizado_em         TIMESTAMPTZ,
    registros_processados BIGINT      NOT NULL DEFAULT 0,
    detalhe               TEXT
);
CREATE INDEX idx_execucao_pipeline_lote ON controle.execucao_pipeline (lote_id);

-- Até onde (atualizado_em do OLTP) cada tabela de origem já foi ingerida.
CREATE TABLE controle.watermark (
    tabela_origem        VARCHAR(60) PRIMARY KEY,
    ultimo_atualizado_em TIMESTAMPTZ NOT NULL,
    atualizado_em        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- raw ----------
-- Todas as tabelas raw têm o MESMO formato: a linha do OLTP vai inteira em "payload"
-- (jsonb, nomes de coluna do OLTP) e nada é alterado. Uma mesma chave_origem pode
-- aparecer várias vezes (cada atualização na origem gera uma nova linha); a camada
-- curada fica com a versão de maior atualizado_na_origem.
-- Uma tabela raw por tabela do OLTP, cada uma com sua própria identity.
DO $$
DECLARE
    tabela TEXT;
BEGIN
    FOREACH tabela IN ARRAY ARRAY['loja', 'produto', 'cliente', 'venda', 'item_venda',
                                  'movimento_estoque', 'estoque_atual'] LOOP
        EXECUTE format(
            'CREATE TABLE raw.%I (
                 id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                 lote_id              UUID         NOT NULL,
                 chave_origem         VARCHAR(100) NOT NULL,
                 payload              JSONB        NOT NULL,
                 atualizado_na_origem TIMESTAMPTZ  NOT NULL,
                 ingerido_em          TIMESTAMPTZ  NOT NULL DEFAULT now()
             )', tabela);
        EXECUTE format(
            'CREATE INDEX %I ON raw.%I (chave_origem, atualizado_na_origem)',
            'idx_raw_' || tabela || '_chave', tabela);
    END LOOP;
END $$;
