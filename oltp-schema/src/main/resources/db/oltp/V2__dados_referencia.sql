-- Dados de referência (sintéticos): lojas, catálogo, alguns clientes e estoque inicial.
-- Vendas e movimentos são gerados pelo simulador do fontes-operacionais.

INSERT INTO loja (codigo, nome, cidade, uf, tipo) VALUES
    ('SP01', 'FarmaRede Paulista',      'São Paulo',      'SP', 'FISICA'),
    ('SP02', 'FarmaRede Pinheiros',     'São Paulo',      'SP', 'FISICA'),
    ('RJ01', 'FarmaRede Centro Rio',    'Rio de Janeiro', 'RJ', 'FISICA'),
    ('WEB',  'FarmaRede Online',        'São Paulo',      'SP', 'ONLINE');

INSERT INTO produto (sku, nome, categoria, fabricante, preco_unitario, controlado) VALUES
    ('MED-0001', 'Dipirona 500mg 10 comp.',          'Medicamentos',   'Genéricos BR',  8.90, FALSE),
    ('MED-0002', 'Paracetamol 750mg 20 comp.',       'Medicamentos',   'Genéricos BR', 12.50, FALSE),
    ('MED-0003', 'Ibuprofeno 400mg 10 caps.',        'Medicamentos',   'Farmalab',     15.90, FALSE),
    ('MED-0004', 'Amoxicilina 500mg 21 caps.',       'Antibióticos',   'Farmalab',     32.40, TRUE),
    ('MED-0005', 'Losartana 50mg 30 comp.',          'Uso contínuo',   'Genéricos BR', 18.70, FALSE),
    ('MED-0006', 'Clonazepam 2mg 30 comp.',          'Controlados',    'Neurofarma',   24.90, TRUE),
    ('HIG-0001', 'Protetor solar FPS 50 200ml',      'Dermocosméticos','SolCare',      59.90, FALSE),
    ('HIG-0002', 'Creme dental 90g',                 'Higiene',        'Sorriso',       6.50, FALSE),
    ('HIG-0003', 'Shampoo anticaspa 200ml',          'Higiene',        'CapilarMax',   27.80, FALSE),
    ('VIT-0001', 'Vitamina C 1g 30 comp. efervesc.', 'Vitaminas',      'VitaBR',       29.90, FALSE),
    ('VIT-0002', 'Vitamina D 2000UI 60 caps.',       'Vitaminas',      'VitaBR',       45.00, FALSE),
    ('INF-0001', 'Fralda infantil M 40 un.',         'Infantil',       'BabySoft',     64.90, FALSE);

INSERT INTO cliente (nome, email, cidade, uf, data_nascimento) VALUES
    ('Ana Souza',      'ana.souza@exemplo.com',      'São Paulo',      'SP', '1988-03-14'),
    ('Bruno Lima',     'bruno.lima@exemplo.com',     'São Paulo',      'SP', '1975-11-02'),
    ('Carla Mendes',   'carla.mendes@exemplo.com',   'Rio de Janeiro', 'RJ', '1993-07-21'),
    ('Diego Rocha',    'diego.rocha@exemplo.com',    'Niterói',        'RJ', '1969-01-30'),
    ('Elaine Castro',  'elaine.castro@exemplo.com',  'Campinas',       'SP', '2001-09-09');

-- Estoque inicial: 50 unidades de cada produto em cada loja.
INSERT INTO estoque_atual (loja_id, produto_id, quantidade, estoque_minimo)
SELECT l.id, p.id, 50, 10 FROM loja l CROSS JOIN produto p;

INSERT INTO movimento_estoque (loja_id, produto_id, tipo, quantidade, ocorrido_em)
SELECT l.id, p.id, 'ENTRADA', 50, now() FROM loja l CROSS JOIN produto p;
