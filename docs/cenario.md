# Cenário: FarmaRede

> **Responsável:** Felipe · **Critério:** clareza do problema de negócio (20%)
> Meta "Avançado": claro e bem contextualizado.

## A empresa

A **FarmaRede** é uma rede regional de farmácias (fictícia) com **três lojas físicas** e uma
**loja online**:

| Código | Loja | Cidade/UF | Canal |
|--------|------|-----------|-------|
| `SP01` | FarmaRede Paulista | São Paulo/SP | PDV (balcão) |
| `SP02` | FarmaRede Pinheiros | São Paulo/SP | PDV (balcão) |
| `RJ01` | FarmaRede Centro Rio | Rio de Janeiro/RJ | PDV (balcão) |
| `WEB` | FarmaRede Online | São Paulo/SP (centro de distribuição) | e-commerce |

O mix tem **12 SKUs** em sete categorias: medicamentos de venda livre (dipirona, paracetamol,
ibuprofeno), **uso contínuo** (losartana), **antibióticos e controlados** (amoxicilina, clonazepam,
com retenção de receita), dermocosméticos, higiene, vitaminas e infantil. Cada loja tem estoque
próprio de cada produto, com um **estoque mínimo** definido pelo time de compras.

No dia a dia, três sistemas operacionais gravam no mesmo banco transacional (`oltp-db`):

- o **PDV** de cada loja física registra vendas de balcão, com ou sem cliente identificado
  (programa de fidelidade);
- o **e-commerce** registra pedidos online, sempre de cliente cadastrado e nunca pagos em dinheiro;
- o **sistema de estoque** registra entradas de fornecedor, saídas por venda, devoluções/ajustes
  e mantém o saldo corrente por loja.

## O problema

1. **Relatório pesado no banco do caixa.** Hoje a gerência roda consultas de faturamento direto no
   banco operacional. Uma consulta que soma um mês de itens de venda disputa CPU e locks com o PDV,
   e o caixa fica lento justamente no horário de pico.
2. **Dados isolados por sistema.** PDV, site e estoque usam tabelas e semânticas diferentes (canal,
   status, sinal da quantidade no movimento). Comparar PDV × e-commerce ou cruzar venda com estoque
   exige planilhas manuais, cada área com o seu número.
3. **Sem histórico.** O banco operacional guarda só o **estado atual**: se uma venda é cancelada ou
   um preço muda, a versão anterior se perde. Não dá para auditar nem reprocessar um indicador.
4. **Ruptura descoberta tarde.** Ninguém tem uma visão consolidada de quais produtos estão abaixo do
   mínimo em cada loja. A ruptura aparece quando o cliente pede e não tem, principalmente em
   medicamentos de uso contínuo, quando o cliente acaba indo para a concorrência.
5. **Dado pessoal espalhado.** Nome e e-mail de clientes circulam em planilhas de análise, o que
   contraria a LGPD (minimização e finalidade).

A solução é uma **plataforma de dados** separada do operacional: coleta incremental do `oltp-db`,
um **Data Warehouse** em camadas (Raw → Curada → Agregada) e **Data Marts** de Vendas e Estoque
consumidos por BI e API, sem tocar no banco do caixa.

## Perguntas que os dados precisam responder

**Vendas** (mart `marts.vendas_diarias` e `marts.vendas_top_produtos`)

- Qual a receita, o número de vendas e o ticket médio **por dia, loja e canal** (PDV × e-commerce)?
- Como a receita evolui por semana e por mês? Qual loja cresce mais?
- Quais produtos e categorias **mais vendem** em cada período? E os que menos giram?
- Quanto se perde em **cancelamentos** e **descontos**?

**Estoque** (mart `marts.estoque_ruptura` e `marts.estoque_giro`)

- Quais produtos estão **abaixo do estoque mínimo** em cada loja agora?
- Qual o **giro** de cada produto por loja e quantos **dias de cobertura** o saldo atual garante?
- Onde repor primeiro (uso contínuo e controlados com cobertura baixa)?

**Operação da plataforma** (`controle.execucao_pipeline`)

- Quando os dados foram atualizados pela última vez e se alguma etapa falhou.

## Escopo e premissas

- **Dados sintéticos.** O seed (`oltp-schema/.../V2__dados_referencia.sql`) cria 4 lojas, 12
  produtos, 5 clientes e 50 unidades de cada produto em cada loja. Depois disso, o
  `fontes-operacionais` simula o movimento contínuo.
- **Volume e frequência.** Por padrão o simulador roda a cada **10 s** (`SIMULADOR_INTERVALO`),
  criando **5 vendas por ciclo** (`SIMULADOR_VENDAS_POR_CICLO`) com 1 a 4 itens. Isso dá cerca de
  1.800 vendas por hora. Em cerca de 30 % dos ciclos há reposição de fornecedor, em 10 % um
  cancelamento e em 10 % um cliente novo.
- **Atualização do DW.** O pipeline roda a cada **2 minutos** (`PIPELINE_CRON`) ou sob demanda
  (`POST /pipeline/execucoes`). É um processo em lote incremental, sem streaming.
- **Ambiente.** Tudo roda localmente em Docker Compose (Postgres 17 para OLTP e DW, dois serviços
  Quarkus e Metabase opcional).
- **Fora do escopo:** pagamentos e financeiro (contas a pagar e receber), receituário e SNGPC dos
  controlados, preço por região e promoções, alta disponibilidade, autenticação das APIs e retenção
  automática de dados antigos. São evoluções possíveis, sem efeito nas perguntas acima.
