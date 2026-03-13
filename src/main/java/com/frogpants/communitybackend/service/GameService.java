package com.frogpants.communitybackend.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.frogpants.communitybackend.model.PlayerRegistrationRequest;
import com.frogpants.communitybackend.model.PlayerSession;
import com.frogpants.communitybackend.model.ScoreEntry;
import com.frogpants.communitybackend.model.FrontendScoreSubmissionRequest;
import com.frogpants.communitybackend.model.ScoreSubmissionRequest;

@Service
public class GameService {

    private final String databaseUrl;

    public GameService(@Value("${app.database.url}") String databaseUrl) {
        this.databaseUrl = databaseUrl;
        initializeDatabase();
    }

    public PlayerSession registerPlayer(PlayerRegistrationRequest request) {
        String playerId = UUID.randomUUID().toString();
        String playerName = request.playerName().trim();
        Instant registeredAt = Instant.now();

        try (Connection connection = DriverManager.getConnection(databaseUrl);
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

        try (Connection connection = DriverManager.getConnection(databaseUrl);
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
        try (Connection connection = DriverManager.getConnection(databaseUrl);
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

        try (Connection connection = DriverManager.getConnection(databaseUrl);
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

        try (Connection connection = DriverManager.getConnection(databaseUrl);
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

        try (Connection connection = DriverManager.getConnection(databaseUrl);
             Statement statement = connection.createStatement()) {
            for (String command : schemaSql.split(";")) {
                String trimmed = command.trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize SQLite schema", e);
        }
    }

    private PlayerSession findPlayerByName(String playerName) {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
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
}