# community-backend

Java backend scaffold for a C++ 2D game.

This project gives you a minimal HTTP API that a C++ client can call for:

- health checks
- player registration
- score submission
- leaderboard reads

## Stack

- Java 17
- Spring Boot 3
- Maven
- SQLite (initialized on startup with `schema.sql`)

## Run locally

```bash
mvn spring-boot:run
```

The service starts on `http://localhost:8080`.

SQLite data is stored at `./community-backend.db`.

## Build and test

```bash
mvn test
mvn package
```

## API

### Health

```http
GET /api/health
```

Response:

```json
{
	"status": "ok"
}
```

### Register player

```http
POST /api/players/register
Content-Type: application/json
```

```json
{
	"playerName": "Avery"
}
```

### Frontend player session (get-or-create by name)

```http
POST /api/frontend/players
Content-Type: application/json
```

```json
{
  "playerName": "Avery"
}
```

### Submit score

```http
POST /api/scores
Content-Type: application/json
```

```json
{
	"playerId": "player-id-from-register",
	"score": 4200,
	"level": 3
}
```

### Read leaderboard

```http
GET /api/leaderboard?limit=10
```

### Frontend score submit (by player name)

```http
POST /api/frontend/scores
Content-Type: application/json
```

```json
{
	"playerName": "Avery",
	"score": 4200,
	"level": 3
}
```

### Multiplayer create room

```http
POST /api/multiplayer/rooms
Content-Type: application/json
```

```json
{
	"playerName": "Frogpants"
}
```

### Multiplayer join room

```http
POST /api/multiplayer/rooms/{roomCode}/join
Content-Type: application/json
```

```json
{
	"playerName": "Frogpants"
}
```

### Multiplayer leave room

```http
POST /api/multiplayer/rooms/{roomCode}/leave
Content-Type: application/json
```

```json
{
	"playerName": "Frogpants"
}
```

### Multiplayer room info and listing

```http
GET /api/multiplayer/rooms/{roomCode}
GET /api/multiplayer/rooms?limit=20
```

## Calling it from C++

Your game can talk to this backend with any HTTP client library, for example:

- libcurl
- cpr
- Boost.Beast
- Poco

Typical flow:

1. Register the player when the game starts or when a profile is created.
2. Store the returned `playerId` in memory or a local save file.
3. Submit scores after a run ends.
4. Pull the leaderboard when rendering score screens.

## Notes

- Data is persisted in SQLite, so player and score data survives restarts.
- The schema is auto-initialized at startup from `src/main/resources/schema.sql`.
- If you outgrow SQLite, the same service can move to PostgreSQL or MySQL with datasource changes.
- CORS is enabled via `app.cors.allowed-origins` in `application.properties` (defaults to `*`).
- If you want login, matchmaking, inventory, or cloud save support, add those as separate services instead of overloading the score flow.
