# Atores: produtores e consumidores de dados

> **Responsável:** Felipe · **Critério:** identificação dos atores (15%)
> Meta "Avançado": atores completos com funções claras.

## Produtores

| Ator | Sistema / container | Dados produzidos | Frequência |
|------|---------------------|------------------|------------|
| <!-- TODO (Felipe) --> Operador de caixa (PDV) | `fontes-operacionais` → `oltp-db` | vendas e itens (canal PDV) | contínua |
| <!-- TODO --> Cliente no e-commerce | | | |
| <!-- TODO --> Sistema de estoque / recebimento | | | |
| <!-- TODO --> Cadastro (catálogo e clientes) | | | |

## Consumidores

| Ator | Como consome | Data Mart / interface | Decisões que toma |
|------|--------------|------------------------|-------------------|
| <!-- TODO (Felipe, alinhado com o Romulo) --> Diretoria comercial | Metabase | `marts.vendas_*` | |
| <!-- TODO --> Compras / abastecimento | | `marts.estoque_*` | |
| <!-- TODO --> Marketing | | | |
| <!-- TODO --> Gerente de loja | API `/marts/...` | | |

## Atores técnicos

<!-- TODO (Felipe): engenharia de dados (opera o pipeline, olha controle.execucao_pipeline),
     DPO/LGPD (dado pessoal do cliente pseudonimizado na curada). -->
