alter table game_cards alter column oracle_card_id drop not null;

alter table game_cards add column is_token     boolean     not null default false;
alter table game_cards add column token_name   varchar(120);
alter table game_cards add column token_pt     varchar(16);
alter table game_cards add column token_colors varchar(8);
alter table game_cards add column token_text   text;

create index idx_game_cards_is_token     on game_cards (is_token);
create index idx_game_cards_token_name   on game_cards using gin (token_name gin_trgm_ops);
create index idx_game_cards_token_pt     on game_cards (token_pt);
create index idx_game_cards_token_colors on game_cards (token_colors);
create index idx_game_cards_token_text   on game_cards using gin (token_text gin_trgm_ops);
