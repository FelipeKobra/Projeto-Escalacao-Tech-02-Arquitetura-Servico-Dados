# Projeto 1: Arquitetura de Dados para um Problema Real

## Objetivo: Projetar uma arquitetura de dados personalizada, com DW, Data Marts, atores e camadas bem definidos.

## Descrição:

Os alunos devem simular a arquitetura de dados de uma empresa real ou fictícia (ex: farmácia, escola, e-commerce), com foco em:

- Definir os atores (produtores e consumidores de dados)
- Criar um diagrama de arquitetura de dados com:
- Camadas: Coleta, Processamento, Armazenamento
- Um Data Warehouse com Data Marts
- Justificativa do uso (por que DW, e não apenas banco relacional?)
- Simular dados que seriam coletados (ex: vendas, produtos)

## Entregáveis:

- Diagrama completo com legenda
- Documento explicando o ciclo de vida do dado
- Explicação do cenário escolhido

### Itens Obrigatórios

| Item                            | Descrição                                               | Entregue? |
|---------------------------------|----------------------------------------------------------|-----------|
| Definição do negócio escolhido  | Ex: escola, mercado, loja                                | ✅ [`docs/cenario.md`](docs/cenario.md) |
| Atores identificados            | Produtores e consumidores                                | ✅ [`docs/atores.md`](docs/atores.md) |
| Diagrama da arquitetura de dados| Representa coleta → armazenamento → consumo              | ✅ [`docs/arquitetura.md`](docs/arquitetura.md) |
| Camadas do DW                   | Raw, Curada, Agregada identificadas e justificadas       | ✅ [`docs/camadas-dw.md`](docs/camadas-dw.md) |
| Data Marts definidos            | Pelo menos 2, com foco específico                        | ✅ [`docs/data-marts.md`](docs/data-marts.md) |
| Texto explicativo do ciclo do dado | Como os dados circulam e são usados                  | ✅ [`docs/ciclo-de-vida.md`](docs/ciclo-de-vida.md) |

## Critérios de Avaliação

| Critério                      | Peso | Básico (0-2)                    | Intermediário (3-4)               | Avançado (5)                          |
|-------------------------------|------|----------------------------------|-----------------------------------|--------------------------------------|
| Clareza do problema de negócio| 20%  | Superficial ou confuso          | Descrito com algum foco           | Claro e bem contextualizado          |
| Diagrama arquitetural         | 20%  | Incompleto ou errado            | Completo, mas pouco detalhado     | Completo, legível e coerente         |
| Identificação dos Atores      | 15%  | Poucos ou mal definidos         | Atores corretos                   | Atores completos com funções claras  |
| Definição das camadas do DW   | 15%  | Sem justificativa clara         | Parcialmente coerente             | Coerente com boas explicações        |
| Definição dos Data Marts      | 15%  | Genéricos ou ausentes           | Um ou dois definidos              | Relevantes e bem pensados            |
| Explicação do fluxo de dados  | 15%  | Vago ou ausente                 | Parcialmente correto              | Completo, do início ao consumo final |
