# endstep-ms

Backend da **Endstep** — plataforma privada de mesa virtual de Magic: The Gathering.
Java 21 + Spring Boot 3.3 + Spring Data JPA/Hibernate + PostgreSQL + Flyway
+ Spring Security (JWT) + OAuth2 client (Google).

## Autenticação (Fase 2)

- **JWT** (jjwt): access token 30 min + refresh token 30 dias **rotativo** (hash sha-256 no banco).
- Cadastro/login por email + senha (BCrypt). Papéis `USER` / `MODERATOR` / `ADMIN`.
  Emails em `endstep.auth.admin-emails` viram `ADMIN` no cadastro.
- **Login com Google**: opcional. Ative o profile `google` e defina `GOOGLE_CLIENT_ID` /
  `GOOGLE_CLIENT_SECRET` (ver `src/main/resources/application-google.yml`). Sem isso, o resto funciona.
- `/api/admin/**` exige `ROLE_ADMIN`; `/api/cards/**` e `/api/sets/**` exigem autenticação;
  `/api/auth/**` e `/actuator/health` são públicos.

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/register` | cadastro → `{accessToken, refreshToken, user}` |
| POST | `/api/auth/login` | login |
| POST | `/api/auth/refresh` | rotaciona o refresh token |
| POST | `/api/auth/logout` | revoga o refresh token (ou todos) |
| GET | `/api/auth/config` | `{googleEnabled}` |
| GET | `/oauth2/authorization/google` | inicia o fluxo Google (se configurado) |
| GET | `/api/users/me` · PUT `/api/users/me` | perfil |

## Arquitetura de dados

- **Persistência**: Spring Data JPA (`JpaRepository` + `@Entity`). Sem `JdbcTemplate`, sem conexão manual.
- **Consultas de leitura**: SQL nativo (`@Query(nativeQuery = true)` + projeções de interface).
- **Índices**: por requisito do projeto, **toda coluna é indexada** — btree nos escalares,
  GIN `gin_trgm_ops` nos campos `text` grandes (`oracle_text`, `flavor_text`, `comment`, ...).
- Upserts idempotentes: `findBy...` + `save`; `deleteBy...` em bloco (`@Modifying`) antes de reinserir faces/legalidades.

## Organização dos pacotes (`com.endstep.ms`)

Por camada:

| Pacote | Conteúdo |
|---|---|
| `controller` | `CardController`, `SetController`, `AdminSyncController` |
| `service` | `CardQueryService`, `CardSyncService`, `CardIngestService`, `CardSyncBootstrap`, `SkippedCardException` |
| `repository` | os 7 `JpaRepository` |
| `entity` | `CardOracle`, `CardPrinting`, `CardFace`, `Legality`, `Ruling`, `SetEntity`, `CardSyncRun` |
| `dto` | records de resposta da API |
| `projection` | projeções de interface para as queries nativas |
| `scryfall` | integração com a API do Scryfall (`ScryfallClient`, `ScryfallJson`, `BulkDataEntry`) |
| `config` | `WebConfig` (CORS), `AsyncConfig` (pool da sync), `HttpClientConfig`, `EndstepProperties` |
| `common` | `ApiExceptionHandler`, `PageResponse`, `NotFoundException`, `SyncCounters` |

## Etapa 1 (atual) — base de cartas

- Esquema Flyway: `sets`, `card_oracles`, `card_printings`, `card_faces`, `legalities`, `rulings`, `card_sync_runs`.
- `CardSyncService` + `CardIngestService`: baixam o Bulk Data do Scryfall (`default_cards` + `rulings`,
  formato `.jsonl.gz`), fazem streaming e gravam via JPA.
- API de leitura: busca de cartas, detalhe, impressões, sets.
- Painel admin: dispara e acompanha a sincronização (`card_sync_runs`).

## Pré-requisitos

- JDK 21 (Temurin recomendado)
- Docker Desktop (para Postgres + Redis)

## Rodar

Duas opções:

**A) Backend no IntelliJ + infra no Docker** (dev)

```bash
docker compose -f ../docker-compose.yml up -d      # Postgres + Redis
```

No IntelliJ: abra `endstep-ms` como projeto Maven (JDK 21) e rode `EndstepMsApplication`.

**B) Backend inteiro em container**

```bash
docker compose up -d --build                       # este arquivo: backend + Postgres + Redis
```

Sobe em `http://localhost:8080`. O Flyway cria o esquema no primeiro start.
Postgres: `localhost:5433` (`endstep`/`endstep`/`endstep`) · Redis: `localhost:6380`.
Portas do host desviadas de 5432/6379/5173 para conviver com outros projetos.

## Índices

Declarados em **dois lugares**, com os mesmos nomes:
- `V1__initial_card_schema.sql` — a criação real (inclui GIN `gin_trgm_ops` nos `text` grandes).
- `@Table(indexes = …)` em cada `@Entity` — espelho/documentação. Como `ddl-auto=none`,
  o Hibernate não cria nada; o Flyway é a fonte de verdade.

## Popular a base de cartas

```bash
curl -X POST http://localhost:8080/api/admin/cards/sync -H "X-Admin-Token: dev-admin-token"
curl http://localhost:8080/api/admin/cards/sync/status -H "X-Admin-Token: dev-admin-token"
```

Primeira execução baixa ~78 MB (`default_cards.jsonl.gz`) para `.data/scryfall/` e processa
~105 mil cartas. Leva **~20 min** — a escrita é pesada porque toda coluna é indexada.
Execuções seguintes reaproveitam o arquivo do dia.

Trocar o dataset: `endstep.scryfall.bulk-type` no `application.yml` (`oracle_cards`, `all_cards`, ...).

## Endpoints (Etapa 1)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/cards/search?q=sol+ring&page=0&size=30` | Busca paginada (trigram + prefixo + match exato) |
| GET | `/api/cards/{oracleId}` | Detalhe: oracle + legalidades + rulings + impressões + faces |
| GET | `/api/cards/{oracleId}/printings` | Impressões da carta |
| GET | `/api/sets?page=0&size=50` | Lista de sets |
| GET | `/api/sets/{code}` | Set por código |
| POST | `/api/admin/cards/sync` | Dispara sincronização (header `X-Admin-Token`) |
| GET | `/api/admin/cards/sync/status` | Últimas execuções |
| GET | `/actuator/health` | Health check |

## Configuração

Tudo em `src/main/resources/application.yml`, prefixo `endstep`.
`application-docker.yml` sobrescreve hosts (`postgres`, `redis`) quando roda no Compose (`SPRING_PROFILES_ACTIVE=docker`).
