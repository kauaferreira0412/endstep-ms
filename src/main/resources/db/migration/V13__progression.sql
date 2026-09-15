create table user_stats (
    user_id bigint primary key references users (id) on delete cascade,
    xp bigint not null default 0,
    level int not null default 1,
    games_played int not null default 0,
    games_won int not null default 0,
    updated_at timestamptz not null default now()
);

create table achievements (
    id bigserial primary key,
    code varchar(64) not null unique,
    name varchar(120) not null,
    description varchar(240) not null,
    metric varchar(32) not null,
    target int not null,
    xp_reward int not null,
    position int not null default 0
);

create table user_achievements (
    id bigserial primary key,
    user_id bigint not null references users (id) on delete cascade,
    achievement_id bigint not null references achievements (id) on delete cascade,
    progress int not null default 0,
    unlocked_at timestamptz,
    unique (user_id, achievement_id)
);

create index idx_user_achievements_user_id on user_achievements (user_id);

alter table games add column xp_awarded boolean not null default false;

insert into achievements (code, name, description, metric, target, xp_reward, position) values
('GAMES_PLAYED_1', 'Primeiros Passos', 'Jogue sua primeira partida', 'GAMES_PLAYED', 1, 50, 1),
('GAMES_PLAYED_25', 'Habitué da Mesa', 'Jogue 25 partidas', 'GAMES_PLAYED', 25, 150, 2),
('GAMES_PLAYED_100', 'Veterano de Guerra', 'Jogue 100 partidas', 'GAMES_PLAYED', 100, 400, 3),
('GAMES_PLAYED_500', 'Lenda das Mesas', 'Jogue 500 partidas', 'GAMES_PLAYED', 500, 1000, 4),
('GAMES_WON_1', 'Primeira Vitória', 'Vença sua primeira partida', 'GAMES_WON', 1, 75, 5),
('GAMES_WON_25', 'Dominador', 'Vença 25 partidas', 'GAMES_WON', 25, 300, 6),
('GAMES_WON_100', 'Invencível', 'Vença 100 partidas', 'GAMES_WON', 100, 800, 7),
('GAMES_WON_500', 'Ás das Mesas', 'Vença 500 partidas', 'GAMES_WON', 500, 2000, 8),
('LEVEL_10', 'Aprendiz', 'Alcance o nível 10', 'LEVEL', 10, 100, 9),
('LEVEL_25', 'Adepto', 'Alcance o nível 25', 'LEVEL', 25, 250, 10),
('LEVEL_50', 'Mestre', 'Alcance o nível 50', 'LEVEL', 50, 500, 11),
('LEVEL_75', 'Grão-Mestre', 'Alcance o nível 75', 'LEVEL', 75, 1000, 12),
('LEVEL_100', 'Lendário', 'Alcance o nível máximo, 100', 'LEVEL', 100, 2000, 13);
