alter table games add column winner_user_id bigint references users (id) on delete set null;

create index idx_games_winner_user_id on games (winner_user_id);
