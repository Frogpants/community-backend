package com.frogpants.communitybackend.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.frogpants.communitybackend.model.FrontendScoreSubmissionRequest;
import com.frogpants.communitybackend.model.MultiplayerLeaveRoomResponse;
import com.frogpants.communitybackend.model.MultiplayerPresenceEntry;
import com.frogpants.communitybackend.model.MultiplayerPresenceSnapshot;
import com.frogpants.communitybackend.model.MultiplayerRoomDetails;
import com.frogpants.communitybackend.model.MultiplayerRoomMember;
import com.frogpants.communitybackend.model.MultiplayerRoomSummary;
import com.frogpants.communitybackend.model.PlayerRegistrationRequest;
import com.frogpants.communitybackend.model.PlayerSession;
import com.frogpants.communitybackend.model.ScoreEntry;
import com.frogpants.communitybackend.model.ScoreSubmissionRequest;
import com.frogpants.communitybackend.model.TaskDataEntry;
import com.frogpants.communitybackend.model.TaskDataRequest;

@Service
public class GameService {

    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final DataSource dataSource;

    public GameService(DataSource dataSource) {
        this.dataSource = dataSource;
        initializeDatabase();
    }

    public PlayerSession registerPlayer(PlayerRegistrationRequest request) {
        String playerId = UUID.randomUUID().toString();
        String playerName = request.playerName().trim();
        Instant registeredAt = Instant.now();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO players (player_id, player_name, registered_at) VALUES (?, ?, ?)")) {
            statement.setString(1, playerId);
            statement.setString(2, playerName);
            statement.setString(3, registeredAt.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to register player", e);
        }

        PlayerSession session = new PlayerSession(playerId, playerName, registeredAt);
        return session;
    }

    public PlayerSession getOrCreatePlayerByName(String rawPlayerName) {
        String playerName = rawPlayerName.trim();

        PlayerSession existing = findPlayerByName(playerName);
        if (existing != null) {
            return existing;
        }

        String playerId = UUID.randomUUID().toString();
        Instant registeredAt = Instant.now();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO players (player_id, player_name, registered_at) VALUES (?, ?, ?)")) {
            statement.setString(1, playerId);
            statement.setString(2, playerName);
            statement.setString(3, registeredAt.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create player", e);
        }

        return new PlayerSession(playerId, playerName, registeredAt);
    }

    public PlayerSession getPlayer(String playerId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_id, player_name, registered_at FROM players WHERE player_id = ?")) {
            statement.setString(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found");
                }
                return new PlayerSession(
                        resultSet.getString("player_id"),
                        resultSet.getString("player_name"),
                        Instant.parse(resultSet.getString("registered_at"))
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load player", e);
        }
    }

    public ScoreEntry submitScore(ScoreSubmissionRequest request) {
        PlayerSession session;
        try {
            session = getPlayer(request.playerId());
        } catch (ResponseStatusException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found");
        }

        String resolvedPlayerName = request.playerName();
        if (resolvedPlayerName == null || resolvedPlayerName.isBlank()) {
            resolvedPlayerName = session.playerName();
        }

        Instant submittedAt = Instant.now();
        ScoreEntry scoreEntry = new ScoreEntry(
                session.playerId(),
                resolvedPlayerName.trim(),
                request.score(),
                request.level(),
            submittedAt
        );

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO scores (player_id, player_name, score, level, submitted_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, scoreEntry.playerId());
            statement.setString(2, scoreEntry.playerName());
            statement.setLong(3, scoreEntry.score());
            statement.setInt(4, scoreEntry.level());
            statement.setString(5, scoreEntry.submittedAt().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to submit score", e);
        }

        return scoreEntry;
    }

    public ScoreEntry submitScoreByPlayerName(FrontendScoreSubmissionRequest request) {
        PlayerSession session = getOrCreatePlayerByName(request.playerName());

        ScoreSubmissionRequest scoreSubmissionRequest = new ScoreSubmissionRequest(
                session.playerId(),
                session.playerName(),
                request.score(),
                request.level()
        );

        return submitScore(scoreSubmissionRequest);
    }

    public List<ScoreEntry> getTopScores(int limit) {
        List<ScoreEntry> entries = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_id, player_name, score, level, submitted_at " +
                             "FROM scores ORDER BY score DESC, submitted_at ASC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(new ScoreEntry(
                            resultSet.getString("player_id"),
                            resultSet.getString("player_name"),
                            resultSet.getLong("score"),
                            resultSet.getInt("level"),
                            Instant.parse(resultSet.getString("submitted_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load leaderboard", e);
        }

        return entries;
    }

    public MultiplayerRoomDetails createRoomByPlayerName(String rawPlayerName) {
        PlayerSession host = getOrCreatePlayerByName(rawPlayerName);
        String roomId = UUID.randomUUID().toString();
        String roomCode = generateUniqueRoomCode();
        Instant createdAt = Instant.now();
        Instant joinedAt = Instant.now();

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement roomStatement = connection.prepareStatement(
                    "INSERT INTO multiplayer_rooms (room_id, room_code, host_player_id, created_at) VALUES (?, ?, ?, ?)");
                 PreparedStatement memberStatement = connection.prepareStatement(
                         "INSERT INTO multiplayer_room_members (room_id, player_id, joined_at) VALUES (?, ?, ?)")) {
                roomStatement.setString(1, roomId);
                roomStatement.setString(2, roomCode);
                roomStatement.setString(3, host.playerId());
                roomStatement.setString(4, createdAt.toString());
                roomStatement.executeUpdate();

                memberStatement.setString(1, roomId);
                memberStatement.setString(2, host.playerId());
                memberStatement.setString(3, joinedAt.toString());
                memberStatement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create multiplayer room", e);
        }

        return getRoomByCode(roomCode);
    }

    public List<MultiplayerRoomSummary> listRooms(int limit) {
        List<MultiplayerRoomSummary> rooms = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT r.room_id, r.room_code, r.created_at, COUNT(m.player_id) AS player_count " +
                             "FROM multiplayer_rooms r " +
                             "LEFT JOIN multiplayer_room_members m ON m.room_id = r.room_id " +
                             "GROUP BY r.room_id, r.room_code, r.created_at " +
                             "ORDER BY r.created_at DESC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rooms.add(new MultiplayerRoomSummary(
                            resultSet.getString("room_id"),
                            resultSet.getString("room_code"),
                            resultSet.getInt("player_count"),
                            Instant.parse(resultSet.getString("created_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list multiplayer rooms", e);
        }

        return rooms;
    }

    public MultiplayerRoomDetails getRoomByCode(String rawRoomCode) {
        String roomCode = normalizeRoomCode(rawRoomCode);
        String roomId;
        String hostPlayerId;
        Instant createdAt;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement roomStatement = connection.prepareStatement(
                     "SELECT room_id, host_player_id, created_at FROM multiplayer_rooms WHERE room_code = ?")) {
            roomStatement.setString(1, roomCode);
            try (ResultSet roomResult = roomStatement.executeQuery()) {
                if (!roomResult.next()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
                }

                roomId = roomResult.getString("room_id");
                hostPlayerId = roomResult.getString("host_player_id");
                createdAt = Instant.parse(roomResult.getString("created_at"));
            }

            List<MultiplayerRoomMember> members = new ArrayList<>();
            try (PreparedStatement membersStatement = connection.prepareStatement(
                    "SELECT p.player_id, p.player_name, m.joined_at " +
                            "FROM multiplayer_room_members m " +
                            "JOIN players p ON p.player_id = m.player_id " +
                            "WHERE m.room_id = ? ORDER BY m.joined_at ASC")) {
                membersStatement.setString(1, roomId);
                try (ResultSet membersResult = membersStatement.executeQuery()) {
                    while (membersResult.next()) {
                        members.add(new MultiplayerRoomMember(
                                membersResult.getString("player_id"),
                                membersResult.getString("player_name"),
                                Instant.parse(membersResult.getString("joined_at"))
                        ));
                    }
                }
            }

            return new MultiplayerRoomDetails(roomId, roomCode, hostPlayerId, createdAt, members);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load multiplayer room", e);
        }
    }

    public MultiplayerRoomDetails joinRoomByCode(String rawRoomCode, String rawPlayerName) {
        String roomCode = normalizeRoomCode(rawRoomCode);
        PlayerSession player = getOrCreatePlayerByName(rawPlayerName);
        String roomId = getRoomIdByCode(roomCode);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO multiplayer_room_members (room_id, player_id, joined_at) VALUES (?, ?, ?)")) {
            statement.setString(1, roomId);
            statement.setString(2, player.playerId());
            statement.setString(3, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to join multiplayer room", e);
        }

        return getRoomByCode(roomCode);
    }

    public MultiplayerLeaveRoomResponse leaveRoomByCode(String rawRoomCode, String rawPlayerName) {
        String roomCode = normalizeRoomCode(rawRoomCode);
        String roomId = getRoomIdByCode(roomCode);
        PlayerSession player = findPlayerByName(rawPlayerName.trim());
        if (player == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found");
        }

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement removePresence = connection.prepareStatement(
                    "DELETE FROM multiplayer_player_presence WHERE room_id = ? AND player_id = ?")) {
                removePresence.setString(1, roomId);
                removePresence.setString(2, player.playerId());
                removePresence.executeUpdate();
            }

            try (PreparedStatement removeMember = connection.prepareStatement(
                    "DELETE FROM multiplayer_room_members WHERE room_id = ? AND player_id = ?")) {
                removeMember.setString(1, roomId);
                removeMember.setString(2, player.playerId());
                removeMember.executeUpdate();
            }

            int memberCount;
            try (PreparedStatement countMembers = connection.prepareStatement(
                    "SELECT COUNT(*) AS member_count FROM multiplayer_room_members WHERE room_id = ?")) {
                countMembers.setString(1, roomId);
                try (ResultSet countResult = countMembers.executeQuery()) {
                    countResult.next();
                    memberCount = countResult.getInt("member_count");
                }
            }

            if (memberCount == 0) {
                try (PreparedStatement deleteRoom = connection.prepareStatement(
                        "DELETE FROM multiplayer_rooms WHERE room_id = ?")) {
                    deleteRoom.setString(1, roomId);
                    deleteRoom.executeUpdate();
                }
                return new MultiplayerLeaveRoomResponse(roomCode, true, 0);
            }

            String hostPlayerId;
            try (PreparedStatement hostStatement = connection.prepareStatement(
                    "SELECT host_player_id FROM multiplayer_rooms WHERE room_id = ?")) {
                hostStatement.setString(1, roomId);
                try (ResultSet hostResult = hostStatement.executeQuery()) {
                    hostResult.next();
                    hostPlayerId = hostResult.getString("host_player_id");
                }
            }

            if (player.playerId().equals(hostPlayerId)) {
                String nextHostPlayerId;
                try (PreparedStatement nextHostStatement = connection.prepareStatement(
                        "SELECT player_id FROM multiplayer_room_members WHERE room_id = ? ORDER BY joined_at ASC LIMIT 1")) {
                    nextHostStatement.setString(1, roomId);
                    try (ResultSet nextHostResult = nextHostStatement.executeQuery()) {
                        nextHostResult.next();
                        nextHostPlayerId = nextHostResult.getString("player_id");
                    }
                }

                try (PreparedStatement updateHost = connection.prepareStatement(
                        "UPDATE multiplayer_rooms SET host_player_id = ? WHERE room_id = ?")) {
                    updateHost.setString(1, nextHostPlayerId);
                    updateHost.setString(2, roomId);
                    updateHost.executeUpdate();
                }
            }

            return new MultiplayerLeaveRoomResponse(roomCode, false, memberCount);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to leave multiplayer room", e);
        }
    }

    public MultiplayerPresenceSnapshot upsertPresenceByRoomCode(
            String rawRoomCode,
            String rawPlayerName,
            double x,
            double y
    ) {
        String roomCode = normalizeRoomCode(rawRoomCode);
        PlayerSession player = getOrCreatePlayerByName(rawPlayerName);
        String roomId = getRoomIdByCode(roomCode);
        Instant now = Instant.now();

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ensureMember = connection.prepareStatement(
                    "INSERT OR IGNORE INTO multiplayer_room_members (room_id, player_id, joined_at) VALUES (?, ?, ?)")) {
                ensureMember.setString(1, roomId);
                ensureMember.setString(2, player.playerId());
                ensureMember.setString(3, now.toString());
                ensureMember.executeUpdate();
            }

            try (PreparedStatement upsertPresence = connection.prepareStatement(
                    "INSERT INTO multiplayer_player_presence (room_id, player_id, player_name, pos_x, pos_y, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT(room_id, player_id) DO UPDATE SET " +
                            "player_name=excluded.player_name, pos_x=excluded.pos_x, pos_y=excluded.pos_y, updated_at=excluded.updated_at")) {
                upsertPresence.setString(1, roomId);
                upsertPresence.setString(2, player.playerId());
                upsertPresence.setString(3, player.playerName());
                upsertPresence.setDouble(4, x);
                upsertPresence.setDouble(5, y);
                upsertPresence.setString(6, now.toString());
                upsertPresence.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update multiplayer presence", e);
        }

        return getRoomPresenceByCode(roomCode);
    }

    public MultiplayerPresenceSnapshot getRoomPresenceByCode(String rawRoomCode) {
        String roomCode = normalizeRoomCode(rawRoomCode);
        String roomId = getRoomIdByCode(roomCode);
        List<MultiplayerPresenceEntry> players = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT p.player_id, p.player_name, p.pos_x, p.pos_y, p.updated_at " +
                             "FROM multiplayer_player_presence p " +
                             "JOIN multiplayer_room_members m ON m.room_id = p.room_id AND m.player_id = p.player_id " +
                             "WHERE p.room_id = ? AND datetime(p.updated_at) >= datetime('now', '-5 seconds') ORDER BY p.updated_at DESC")) {
            statement.setString(1, roomId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    players.add(new MultiplayerPresenceEntry(
                            resultSet.getString("player_id"),
                            resultSet.getString("player_name"),
                            resultSet.getDouble("pos_x"),
                            resultSet.getDouble("pos_y"),
                            Instant.parse(resultSet.getString("updated_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load multiplayer presence", e);
        }

        return new MultiplayerPresenceSnapshot(roomCode, players);
    }

    public TaskDataEntry saveTaskData(TaskDataRequest request) {
        TaskSubject subject = resolveTaskSubject(request);
        boolean completed = request.completed() != null && request.completed();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO \"task data\" (user_name, task_name, room_number, completed) VALUES (?, ?, ?, ?) " +
                             "ON CONFLICT(user_name, task_name, room_number) DO UPDATE SET " +
                             "completed=excluded.completed")) {
            statement.setString(1, subject.userName());
            statement.setString(2, subject.taskName());
            statement.setInt(3, subject.roomNumber());
            statement.setInt(4, completed ? 1 : 0);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save task data", e);
        }

        return getTaskDataByKey(subject.userName(), subject.taskName(), subject.roomNumber());
    }

    public TaskDataEntry completeTaskData(TaskDataRequest request) {
        TaskSubject subject = resolveTaskSubject(request);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO \"task data\" (user_name, task_name, room_number, completed) VALUES (?, ?, ?, 1) " +
                             "ON CONFLICT(user_name, task_name, room_number) DO UPDATE SET " +
                             "completed=1")) {
            statement.setString(1, subject.userName());
            statement.setString(2, subject.taskName());
            statement.setInt(3, subject.roomNumber());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to complete task data", e);
        }

        return getTaskDataByKey(subject.userName(), subject.taskName(), subject.roomNumber());
    }

    public List<TaskDataEntry> getTaskData() {
        List<TaskDataEntry> entries = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT user_name, task_name, room_number, completed FROM \"task data\" " +
                             "ORDER BY room_number ASC, user_name COLLATE NOCASE ASC, task_name COLLATE NOCASE ASC")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(new TaskDataEntry(
                            resultSet.getString("user_name"),
                            resultSet.getString("task_name"),
                            resultSet.getString("task_name"),
                            resultSet.getInt("room_number"),
                            resultSet.getInt("completed") == 1
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load task data", e);
        }

        return entries;
    }

    private void initializeDatabase() {
        String schemaSql;
        try {
            schemaSql = new String(
                    new ClassPathResource("schema.sql").getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load schema.sql", e);
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA cache_size=-8000");
            for (String command : schemaSql.split(";")) {
                String trimmed = command.trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }

            ensureTaskDataSchema(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize SQLite schema", e);
        }
    }

    private void ensureTaskDataSchema(Connection connection) throws SQLException {
        Set<String> columns = new HashSet<>();

        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(\"task data\")");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name").toLowerCase(Locale.ROOT));
            }
        }

        if (columns.isEmpty()) {
            return;
        }

        boolean needsMigration = !columns.contains("user_name")
                || !columns.contains("task_name")
                || !columns.contains("room_number")
                || !columns.contains("completed")
                || columns.contains("player_id")
                || columns.contains("created_at")
                || columns.contains("updated_at");
        if (needsMigration) {
            migrateTaskDataTable(connection);
            return;
        }

        try (Statement indexStatement = connection.createStatement()) {
            indexStatement.execute("CREATE INDEX IF NOT EXISTS idx_task_data_completed ON \"task data\"(completed)");
            indexStatement.execute("CREATE INDEX IF NOT EXISTS idx_task_data_user_name ON \"task data\"(user_name)");
            indexStatement.execute("CREATE INDEX IF NOT EXISTS idx_task_data_room_number ON \"task data\"(room_number)");
        }
    }

    private void migrateTaskDataTable(Connection connection) throws SQLException {
        String legacyTaskTable = "\"task data_legacy\"";
        String newTaskTable = "\"task data\"";
        String fallbackPlayerName = "legacy-task-user";
        PlayerSession fallbackPlayer = ensurePlayerByName(connection, fallbackPlayerName);

        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + legacyTaskTable);
            statement.execute("ALTER TABLE " + newTaskTable + " RENAME TO " + legacyTaskTable);
            statement.execute(
                    "CREATE TABLE " + newTaskTable + " (" +
                            "user_name TEXT NOT NULL, " +
                            "task_name TEXT NOT NULL, " +
                            "room_number INTEGER NOT NULL DEFAULT -1, " +
                            "completed INTEGER NOT NULL DEFAULT 0 CHECK (completed IN (0, 1)), " +
                            "PRIMARY KEY (user_name, task_name, room_number)" +
                            ")"
            );
            statement.execute("CREATE INDEX IF NOT EXISTS idx_task_data_completed ON \"task data\"(completed)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_task_data_user_name ON \"task data\"(user_name)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_task_data_room_number ON \"task data\"(room_number)");
        }

        try (PreparedStatement copyStatement = connection.prepareStatement(
                "SELECT * FROM \"task data_legacy\"")) {
            try (ResultSet resultSet = copyStatement.executeQuery()) {
                ResultSetMetaData metaData = resultSet.getMetaData();
                while (resultSet.next()) {
                    String taskName = firstNonBlank(resultSet, metaData, "task_name", "name", "task", "title");
                    if (taskName == null) {
                        continue;
                    }

                    String userName = firstNonBlank(resultSet, metaData, "user_name", "username", "player_name", "playername");
                    if (userName == null) {
                        userName = fallbackPlayer.playerName();
                    }

                    boolean completed = readBooleanColumn(resultSet, metaData, "completed", false);
                    int roomNumber = readIntColumn(resultSet, metaData, -1, "room_number", "room", "room_id");

                    try (PreparedStatement insertStatement = connection.prepareStatement(
                            "INSERT INTO \"task data\" (user_name, task_name, room_number, completed) VALUES (?, ?, ?, ?)")) {
                        insertStatement.setString(1, userName);
                        insertStatement.setString(2, taskName);
                        insertStatement.setInt(3, roomNumber);
                        insertStatement.setInt(4, completed ? 1 : 0);
                        insertStatement.executeUpdate();
                    }
                }
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE \"task data_legacy\"");
        }
    }

    private String firstNonBlank(ResultSet resultSet, ResultSetMetaData metaData, String... candidateColumns) throws SQLException {
        for (String candidateColumn : candidateColumns) {
            for (int index = 1; index <= metaData.getColumnCount(); index++) {
                String columnLabel = metaData.getColumnLabel(index);
                String columnName = metaData.getColumnName(index);
                if (candidateColumn.equalsIgnoreCase(columnLabel) || candidateColumn.equalsIgnoreCase(columnName)) {
                    String value = resultSet.getString(index);
                    if (value != null && !value.isBlank()) {
                        return value.trim();
                    }
                    break;
                }
            }
        }

        return null;
    }

    private boolean readBooleanColumn(ResultSet resultSet, ResultSetMetaData metaData, String candidateColumn, boolean defaultValue) throws SQLException {
        for (int index = 1; index <= metaData.getColumnCount(); index++) {
            String columnLabel = metaData.getColumnLabel(index);
            String columnName = metaData.getColumnName(index);
            if (candidateColumn.equalsIgnoreCase(columnLabel) || candidateColumn.equalsIgnoreCase(columnName)) {
                String rawValue = resultSet.getString(index);
                if (rawValue == null || rawValue.isBlank()) {
                    return defaultValue;
                }

                String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
                return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("y");
            }
        }

        return defaultValue;
    }

    private int readIntColumn(ResultSet resultSet, ResultSetMetaData metaData, int defaultValue, String... candidateColumns) throws SQLException {
        for (String candidateColumn : candidateColumns) {
            for (int index = 1; index <= metaData.getColumnCount(); index++) {
                String columnLabel = metaData.getColumnLabel(index);
                String columnName = metaData.getColumnName(index);
                if (!candidateColumn.equalsIgnoreCase(columnLabel) && !candidateColumn.equalsIgnoreCase(columnName)) {
                    continue;
                }

                String rawValue = resultSet.getString(index);
                if (rawValue == null || rawValue.isBlank()) {
                    return defaultValue;
                }

                try {
                    return Integer.parseInt(rawValue.trim());
                } catch (NumberFormatException ignored) {
                    return defaultValue;
                }
            }
        }

        return defaultValue;
    }

    private PlayerSession ensurePlayerByName(Connection connection, String playerName) throws SQLException {
        try (PreparedStatement findStatement = connection.prepareStatement(
                "SELECT player_id, player_name, registered_at FROM players WHERE lower(player_name) = lower(?) ORDER BY registered_at ASC LIMIT 1")) {
            findStatement.setString(1, playerName);
            try (ResultSet resultSet = findStatement.executeQuery()) {
                if (resultSet.next()) {
                    return new PlayerSession(
                            resultSet.getString("player_id"),
                            resultSet.getString("player_name"),
                            Instant.parse(resultSet.getString("registered_at"))
                    );
                }
            }
        }

        String playerId = UUID.randomUUID().toString();
        Instant registeredAt = Instant.now();

        try (PreparedStatement insertStatement = connection.prepareStatement(
                "INSERT INTO players (player_id, player_name, registered_at) VALUES (?, ?, ?)")) {
            insertStatement.setString(1, playerId);
            insertStatement.setString(2, playerName);
            insertStatement.setString(3, registeredAt.toString());
            insertStatement.executeUpdate();
        }

        return new PlayerSession(playerId, playerName, registeredAt);
    }

    private TaskDataEntry getTaskDataByKey(String userName, String taskName, int roomNumber) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT user_name, task_name, room_number, completed FROM \"task data\" " +
                             "WHERE user_name = ? AND task_name = ? AND room_number = ? LIMIT 1")) {
            statement.setString(1, userName);
            statement.setString(2, taskName);
            statement.setInt(3, roomNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
                }

                return new TaskDataEntry(
                        resultSet.getString("user_name"),
                        resultSet.getString("task_name"),
                        resultSet.getString("task_name"),
                        resultSet.getInt("room_number"),
                        resultSet.getInt("completed") == 1
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load task data", e);
        }
    }

    private TaskSubject resolveTaskSubject(TaskDataRequest request) {
        String taskName = resolveTaskName(request);
        int roomNumber = resolveRoomNumber(request);
        String requestedUserName = resolveRequestedUserName(request);

        PlayerSession player;
        if (request.playerId() != null && !request.playerId().isBlank()) {
            player = getPlayer(request.playerId().trim());
        } else if (requestedUserName != null) {
            player = getOrCreatePlayerByName(requestedUserName);
        } else {
            player = getOrCreatePlayerByName("legacy-task-user");
        }

        String userName = requestedUserName != null
                ? requestedUserName
                : player.playerName();

        return new TaskSubject(userName, taskName, roomNumber);
    }

    private String resolveTaskName(TaskDataRequest request) {
        if (request.taskName() != null && !request.taskName().isBlank()) {
            return request.taskName().trim();
        }

        if (request.simplifiedTaskName() != null && !request.simplifiedTaskName().isBlank()) {
            return request.simplifiedTaskName().trim();
        }

        if (request.name() != null && !request.name().isBlank()) {
            return request.name().trim();
        }

        Integer roomNumber = firstNonNull(request.room(), request.roomNumber());
        Integer taskNumber = firstNonNull(request.taskId(), request.taskNumber());
        if (roomNumber != null && taskNumber != null) {
            return "room:" + roomNumber + ":task:" + taskNumber;
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Task request must provide taskName/name or both room and taskId"
        );
    }

    private int resolveRoomNumber(TaskDataRequest request) {
        Integer roomNumber = firstNonNull(request.room(), request.roomNumber());
        if (roomNumber != null) {
            return roomNumber;
        }
        return -1;
    }

    private String resolveRequestedUserName(TaskDataRequest request) {
        if (request.userName() != null && !request.userName().isBlank()) {
            return request.userName().trim();
        }

        if (request.characterDisplayName() != null && !request.characterDisplayName().isBlank()) {
            return request.characterDisplayName().trim();
        }

        return null;
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private record TaskSubject(String userName, String taskName, int roomNumber) {}

    private PlayerSession findPlayerByName(String playerName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_id, player_name, registered_at FROM players " +
                             "WHERE lower(player_name) = lower(?) ORDER BY registered_at ASC LIMIT 1")) {
            statement.setString(1, playerName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return new PlayerSession(
                        resultSet.getString("player_id"),
                        resultSet.getString("player_name"),
                        Instant.parse(resultSet.getString("registered_at"))
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load player by name", e);
        }
    }

    private String getRoomIdByCode(String roomCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT room_id FROM multiplayer_rooms WHERE room_code = ?")) {
            statement.setString(1, roomCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
                }
                return resultSet.getString("room_id");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to resolve room", e);
        }
    }

    private String generateUniqueRoomCode() {
        for (int attempt = 0; attempt < 50; attempt++) {
            String candidate = randomRoomCode(6);
            if (!roomCodeExists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique room code");
    }

    private boolean roomCodeExists(String roomCode) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM multiplayer_rooms WHERE room_code = ? LIMIT 1")) {
            statement.setString(1, roomCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check room code", e);
        }
    }

    private String randomRoomCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = ThreadLocalRandom.current().nextInt(ROOM_CODE_CHARS.length());
            builder.append(ROOM_CODE_CHARS.charAt(index));
        }
        return builder.toString();
    }

    private String normalizeRoomCode(String rawRoomCode) {
        return rawRoomCode.trim().toUpperCase(Locale.ROOT);
    }
}
