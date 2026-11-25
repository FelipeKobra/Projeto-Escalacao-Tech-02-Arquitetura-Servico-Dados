# Camadas do Data Warehouse

> **Responsáveis:** Felipe (Raw) e Romulo (Curada, Agregada e justificativa do DW)
> **Critério:** definição das camadas do DW (15%). Meta "Avançado": coerente com boas explicações.

Cada camada é um **schema** no `dw-db`. Os metadados do pipeline ficam em `controle`.

| Camada | Schema | Conteúdo | Quem escreve | Responsável |
|--------|--------|----------|--------------|-------------|
| Raw | `raw` | Cópia fiel do OLTP em `jsonb`, append-only | Etapa 10 (ingestão) | Felipe |
| Curada | `curated` | Dados limpos, deduplicados, modelo estrela | Etapa 20 (transformação) | Romulo |
| Agregada | `marts` | Indicadores por área de negócio | Etapa 30 (agregação) | Romulo |

## Raw

**O que é:** uma cópia fiel do que existia no `oltp-db`, linha a linha, sem nenhuma regra de
negócio. São sete tabelas (`raw.loja`, `raw.produto`, `raw.cliente`, `raw.venda`, `raw.item_venda`,
`raw.movimento_estoque` e `raw.estoque_atual`), todas no mesmo formato:

| Coluna | Significado |
|--------|-------------|
| `id` | Identity da própria raw (ordem de chegada) |
| `lote_id` | Execução do pipeline que trouxe a linha. Liga a linha ao histórico em `controle.execucao_pipeline` (linhagem) |
| `chave_origem` | Chave primária no OLTP, em texto: `id`, ou `loja_id:produto_id` em `estoque_atual` |
| `payload` | A linha inteira em `jsonb` (`to_jsonb(t)`), com os nomes de coluna do OLTP |
| `atualizado_na_origem` | `atualizado_em` da linha no OLTP, que identifica a **versão** |
| `ingerido_em` | Quando a linha chegou ao DW |

**Por que guardar a linha inteira, sem transformar?**

- **Auditoria.** Dá para provar o que a origem dizia em cada momento, por exemplo o preço de um
  produto antes de um reajuste ou uma venda antes de ser cancelada.
- **Reprocessamento.** Se a regra da camada curada mudar (como tratar desconto, como deduplicar),
  ou se houver um bug, a curada e os marts podem ser reconstruídos a partir da raw sem reler o banco
  do caixa.
- **Desacoplamento do schema da origem.** Uma coluna nova no OLTP aparece no `payload` sem migration
  no DW. Só a etapa 20 decide se e como usá-la. Uma coluna removida não quebra a coleta.

**Por que append-only?** A raw nunca recebe `UPDATE` nem `DELETE`. Cada alteração na origem vira
uma **nova linha** com outro `atualizado_na_origem`, então o histórico de versões que o OLTP não
guarda fica preservado (por exemplo `venda` 42: `CONCLUIDA` às 10:00 e `CANCELADA` às 10:05). A
camada curada fica com a versão de maior `atualizado_na_origem` de cada `chave_origem`.

**Como funciona a coleta incremental (etapa 10, `ingestao-oltp-raw`).** Para cada tabela:

1. lê `controle.watermark.ultimo_atualizado_em` (até onde já foi ingerido);
2. no OLTP, seleciona `WHERE atualizado_em > watermark - janela de sobreposição`
   (`ingestao.janela-sobreposicao`, 5 min por padrão). Sem watermark, lê a tabela toda;
3. numa transação do DW, insere em `raw.<tabela>` só as versões cuja dupla
   `(chave_origem, atualizado_na_origem)` ainda não existe e avança o watermark com
   `GREATEST(atual, maior atualizado_em lido)`. As duas gravações comitam juntas.

A **janela de sobreposição** existe porque `atualizado_em` é gravado quando a transação no OLTP
começa, mas a linha só fica visível quando ela comita. Uma venda que comita atrasada pode ter um
`atualizado_em` menor que o watermark já salvo. Reler os últimos minutos recupera essa linha, e a
deduplicação garante que as linhas relidas não se repitam. Por isso a etapa é **idempotente**:
rodar duas vezes sem mudança na origem insere 0 linhas. Um advisory lock no DW impede duas
ingestões simultâneas.

**Limitações conhecidas.** Uma transação no OLTP mais longa que a janela, ou o relógio da aplicação
adiantado em relação ao do banco por mais que a janela, pode fazer uma versão ser pulada. Nesse
caso, aumente `INGESTAO_JANELA_SOBREPOSICAO`. A primeira carga lê a tabela inteira para a memória,
o que é adequado ao volume simulado. Com volume real, a leitura seria paginada por
`(atualizado_em, id)`.

**Acesso.** A raw contém dado pessoal (`raw.cliente.payload` tem nome e e-mail) e por isso não é
exposta a consumidores. Quem a lê é a etapa 20, que pseudonimiza o cliente na camada curada.

## Curada

**O que é:** a camada analítica padronizada em **modelo estrela** (`curated.*`), criada pela
migration [`V200__curated_modelo_estrela.sql`](../plataforma-dados/src/main/resources/db/migration/V200__curated_modelo_estrela.sql)
e populada pela **etapa 20 (`transformacao-curated`)** a partir da camada `raw`.

**Regras de transformação aplicadas:**

- **Deduplicação (última versão):** para cada tabela `raw.*`, a etapa seleciona a versão mais
  recente de cada `chave_origem` (`DISTINCT ON (chave_origem) ... ORDER BY chave_origem,
  atualizado_na_origem DESC, id DESC`).
- **Limpeza e tipagem:** valores em `jsonb` (`payload->>'campo'`) são convertidos para seus tipos
  analíticos (`BIGINT`, `INT`, `NUMERIC`, `BOOLEAN`, `TIMESTAMPTZ`), espaços excedentes são
  removidos (`trim`/`regexp_replace`) e códigos (`codigo`, `sku`, `uf`, `canal`, `status`,
  `forma_pagamento`) são padronizados em maiúsculas.
- **Chaves substitutas e upsert idempotente:** todas as dimensões e fatos possuem chave primária
  substituta (`BIGINT GENERATED ALWAYS AS IDENTITY`) desacoplada dos IDs do OLTP. Os registros são
  atualizados com `INSERT ... ON CONFLICT DO UPDATE`, preservando as chaves substitutas e tornando
  a reexecução segura.
- **Tratamento de cancelamentos:** vendas `CANCELADA` permanecem em `curated.fato_venda` para
  rastreabilidade operacional, porém com `valor_liquido = 0.00` e o montante estornado isolado em
  `valor_cancelado`.
- **LGPD (pseudonimização):** `curated.dim_cliente` **não armazena** `nome`, `email` nem
  `data_nascimento` em claro. O identificador analítico é `chave_pseudonimo` (`SHA-256` em
  hexadecimal de 64 caracteres) acompanhado apenas de `cidade`, `uf` e `faixa_etaria` (`MENOR_18`,
  `18-29`, `30-44`, `45-59`, `60+` ou `NAO_INFORMADA`).

**Modelo estrela (`curated`):**

| Tabela | Papel | Grão / Chave de negócio | Principais atributos |
|--------|-------|-------------------------|----------------------|
| `dim_tempo` | Dimensão | 1 dia (`data`, fuso `America/Sao_Paulo`) | `ano`, `mes`, `dia`, `trimestre`, `dia_semana`, `fim_de_semana` |
| `dim_loja` | Dimensão | 1 loja (`id_origem`) | `codigo`, `nome`, `cidade`, `uf`, `tipo` (`FISICA`/`ONLINE`) |
| `dim_produto` | Dimensão | 1 produto (`id_origem`) | `sku`, `nome`, `categoria`, `fabricante`, `preco_unitario`, `controlado` |
| `dim_cliente` | Dimensão | 1 cliente (`id_origem`) | `chave_pseudonimo` (SHA-256), `cidade`, `uf`, `faixa_etaria` |
| `fato_venda` | Fato | **Item de venda** (`id_item_origem`) | FKs substitutas (`tempo_id`, `loja_id`, `produto_id`, `cliente_id` opcional), `canal`, `status`, `quantidade`, `valor_bruto`, `valor_liquido`, `valor_cancelado` |
| `fato_estoque` | Fato | **Saldo diário por loja/produto** (`tempo_id`, `loja_id`, `produto_id`) | `quantidade_saldo`, `estoque_minimo`, `em_ruptura`, `qtd_entradas`, `qtd_saidas_venda`, `qtd_ajustes`, `qtd_perdas` |

## Agregada

**O que é:** a camada de consumo direto (`marts.*`), criada pela migration
[`V250__marts.sql`](../plataforma-dados/src/main/resources/db/migration/V250__marts.sql) e
recalculada pela **etapa 30 (`agregacao-marts`)**.

**Por que pré-agregar?** Consultas de dashboards no Metabase e endpoints REST da loja (`GET
/marts/...`) precisam responder em milissegundos, sem repetir agregações e *window functions* sobre
milhares de itens de `fato_venda` a cada clique do usuário. A pré-agregação também garante que API e
BI exibam exatamente os mesmos números oficiais.

**O que é recalculado e como responde às perguntas do cenário:** a cada execução bem-sucedida da
etapa 20, a etapa 30 executa em transação única (`TRUNCATE` + `INSERT ... SELECT` com lock
advisory) os quatro Data Marts:

- `marts.vendas_diarias`: responde receita líquida, nº de vendas, cancelamentos e ticket médio por
  dia, loja e canal (`PDV` × `ECOMMERCE`).
- `marts.vendas_top_produtos`: responde quais produtos e categorias mais faturam e vendem por mês.
- `marts.estoque_ruptura`: lista somente itens com `quantidade_saldo < estoque_minimo` no último
  saldo conhecido e calcula o `deficit` para reposição imediata.
- `marts.estoque_giro`: calcula vendas dos últimos 30 dias, média diária, giro e dias de cobertura
  por loja e SKU para orientar compras.

## Por que um DW e não só o banco relacional?

Usar o `oltp-db` para tudo causaria conflitos operacionais e limitações analíticas graves na
FarmaRede:

1. **Cargas opostas (OLTP × OLAP):** o `oltp-db` é otimizado para transações curtas de escrita e
   leitura pontual (finalizar uma venda no caixa PDV `SP01` e baixar `estoque_atual` com lock de
   linha). Consultas analíticas longas varrendo meses de `item_venda` competem por CPU, I/O e
   buffers, degradando o tempo de resposta do caixa no horário de pico.
2. **Preservação de histórico:** o banco relacional operacional guarda apenas o estado atual — se a
   venda `1042` muda de `CONCLUIDA` para `CANCELADA` ou um produto sofre reajuste de preço, a versão
   anterior é sobrescrita. A camada `raw` append-only preserva todas as versões para auditoria e
   reprocessamento.
3. **Integração e padronização semântica:** PDV, e-commerce (`WEB`) e controle de estoque gravam
   regras próprias (ex.: `cliente_id` nulo no balcão, sinais negativos em `movimento_estoque`). Na
   camada `curated`, tudo é limpo, tipado e consolidado num modelo dimensional único.
4. **Modelo dimensional para BI:** o esquema estrela (`fato_venda`, `fato_estoque` + `dim_*`) e os
   `marts` pré-agregados simplificam consultas no Metabase e na API REST, evitando junções complexas
   e divergência de fórmulas entre áreas.
5. **Governança e LGPD:** separar o DW permite restringir `oltp-db` e `raw` apenas à operação e ao
   pipeline técnico, expondo para consumo somente clientes pseudonimizados (`curated.dim_cliente`) e
   indicadores agregados (`marts.*`).
