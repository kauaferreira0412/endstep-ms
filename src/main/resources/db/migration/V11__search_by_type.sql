create index if not exists idx_card_oracles_type_line_trgm
    on card_oracles using gin (type_line gin_trgm_ops);
