CREATE TABLE IF NOT EXISTS players (
    player_id TEXT PRIMARY KEY,
    player_name TEXT NOT NULL,
    registered_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS scores (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id TEXT NOT NULL,
    player_name TEXT NOT NULL,
    score INTEGER NOT NULL,
    level INTEGER NOT NULL,
    submitted_at TEXT NOT NULL,
    FOREIGN KEY (player_id) REFERENCES players(player_id)
);

CREATE INDEX IF NOT EXISTS idx_scores_score_submitted
    ON scores(score DESC, submitted_at ASC);

CREATE INDEX IF NOT EXISTS idx_players_name
    ON players(player_name);

CREATE TABLE IF NOT EXISTS multiplayer_rooms (
    room_id TEXT PRIMARY KEY,
    room_code TEXT NOT NULL UNIQUE,
    host_player_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (host_player_id) REFERENCES players(player_id)
);

CREATE TABLE IF NOT EXISTS multiplayer_room_members (
    room_id TEXT NOT NULL,
    player_id TEXT NOT NULL,
    joined_at TEXT NOT NULL,
    PRIMARY KEY (room_id, player_id),
    FOREIGN KEY (room_id) REFERENCES multiplayer_rooms(room_id) ON DELETE CASCADE,
    FOREIGN KEY (player_id) REFERENCES players(player_id)
);

CREATE INDEX IF NOT EXISTS idx_multiplayer_room_members_room
    ON multiplayer_room_members(room_id);

CREATE TABLE IF NOT EXISTS multiplayer_player_presence (
    room_id TEXT NOT NULL,
    player_id TEXT NOT NULL,
    player_name TEXT NOT NULL,
    pos_x REAL NOT NULL,
    pos_y REAL NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (room_id, player_id),
    FOREIGN KEY (room_id) REFERENCES multiplayer_rooms(room_id) ON DELETE CASCADE,
    FOREIGN KEY (player_id) REFERENCES players(player_id)
);

CREATE INDEX IF NOT EXISTS idx_multiplayer_presence_room_updated
    ON multiplayer_player_presence(room_id, updated_at DESC);
