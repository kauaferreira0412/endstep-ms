alter table games add column dice_roll jsonb;

alter table game_players add column afk boolean not null default false;
alter table game_players add column last_active_at timestamptz;
alter table game_players add column controller_user_id bigint references users (id) on delete set null;

create index idx_game_players_controller_user_id on game_players (controller_user_id);
