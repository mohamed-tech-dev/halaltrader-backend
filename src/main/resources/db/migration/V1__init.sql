CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    asset_type VARCHAR(20) NOT NULL,
    halal_screening VARCHAR(20) NOT NULL,
    halal_reason VARCHAR(500),
    sector VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE portfolios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    cash_balance NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE portfolio_positions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id UUID NOT NULL REFERENCES portfolios(id),
    asset_id UUID NOT NULL REFERENCES assets(id),
    quantity NUMERIC(19, 8) NOT NULL,
    average_buy_price NUMERIC(19, 4) NOT NULL
);

CREATE TABLE trades (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id UUID NOT NULL REFERENCES portfolios(id),
    asset_id UUID NOT NULL REFERENCES assets(id),
    action VARCHAR(10) NOT NULL,
    quantity NUMERIC(19, 8) NOT NULL,
    price NUMERIC(19, 4) NOT NULL,
    total_amount NUMERIC(19, 4) NOT NULL,
    ai_reasoning TEXT,
    technical_data TEXT,
    executed_at TIMESTAMP,
    simulated_pnl NUMERIC(19, 4)
);

-- Seed: approved halal assets
INSERT INTO assets (symbol, name, asset_type, halal_screening, halal_reason, sector) VALUES
    ('ISWD.L', 'iShares MSCI World Islamic ETF', 'ETF',       'APPROVED', 'ETF islamique certifié',        'Islamic'),
    ('GLD',    'SPDR Gold Trust',                'COMMODITY', 'APPROVED', 'Or physique halal',              'Commodity'),
    ('AAPL',   'Apple Inc',                      'STOCK',     'APPROVED', 'Secteur tech, ratio dette OK',  'Technology'),
    ('MSFT',   'Microsoft Corp',                 'STOCK',     'APPROVED', 'Secteur tech, ratio dette OK',  'Technology'),
    ('NVDA',   'NVIDIA Corp',                    'STOCK',     'APPROVED', 'Secteur tech, ratio dette OK',  'Technology');

-- Seed: initial portfolio
INSERT INTO portfolios (name, cash_balance) VALUES
    ('Simulation Portfolio', 100000.00);
