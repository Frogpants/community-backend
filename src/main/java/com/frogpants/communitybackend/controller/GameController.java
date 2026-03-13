package com.frogpants.communitybackend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.frogpants.communitybackend.model.FrontendScoreSubmissionRequest;
import com.frogpants.communitybackend.model.HealthResponse;
import com.frogpants.communitybackend.model.LeaderboardResponse;
import com.frogpants.communitybackend.model.MultiplayerCreateRoomRequest;
import com.frogpants.communitybackend.model.MultiplayerJoinRoomRequest;
import com.frogpants.communitybackend.model.MultiplayerLeaveRoomResponse;
import com.frogpants.communitybackend.model.MultiplayerPresenceSnapshot;
import com.frogpants.communitybackend.model.MultiplayerPresenceUpdateRequest;
import com.frogpants.communitybackend.model.MultiplayerRoomDetails;
import com.frogpants.communitybackend.model.MultiplayerRoomSummary;
import com.frogpants.communitybackend.model.PlayerRegistrationRequest;
import com.frogpants.communitybackend.model.PlayerSession;
import com.frogpants.communitybackend.model.ScoreEntry;
import com.frogpants.communitybackend.model.ScoreSubmissionRequest;
import com.frogpants.communitybackend.service.GameService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "${app.cors.allowed-origins:*}")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok");
    }

    @PostMapping("/players/register")
    public PlayerSession registerPlayer(@Valid @RequestBody PlayerRegistrationRequest request) {
        return gameService.registerPlayer(request);
    }

    @PostMapping("/frontend/players")
    public PlayerSession getOrCreatePlayerForFrontend(@Valid @RequestBody PlayerRegistrationRequest request) {
        return gameService.getOrCreatePlayerByName(request.playerName());
    }

    @GetMapping("/players/{playerId}")
    public PlayerSession getPlayer(@PathVariable String playerId) {
        return gameService.getPlayer(playerId);
    }

    @PostMapping("/scores")
    public ScoreEntry submitScore(@Valid @RequestBody ScoreSubmissionRequest request) {
        return gameService.submitScore(request);
    }

    @PostMapping("/frontend/scores")
    public ScoreEntry submitScoreForFrontend(@Valid @RequestBody FrontendScoreSubmissionRequest request) {
        return gameService.submitScoreByPlayerName(request);
    }

    @PostMapping("/multiplayer/rooms")
    public MultiplayerRoomDetails createMultiplayerRoom(@Valid @RequestBody MultiplayerCreateRoomRequest request) {
        return gameService.createRoomByPlayerName(request.playerName());
    }

    @GetMapping("/multiplayer/rooms")
    public List<MultiplayerRoomSummary> listMultiplayerRooms(@RequestParam(defaultValue = "20") int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return gameService.listRooms(boundedLimit);
    }

    @GetMapping("/multiplayer/rooms/{roomCode}")
    public MultiplayerRoomDetails getMultiplayerRoom(@PathVariable String roomCode) {
        return gameService.getRoomByCode(roomCode);
    }

    @PostMapping("/multiplayer/rooms/{roomCode}/join")
    public MultiplayerRoomDetails joinMultiplayerRoom(
            @PathVariable String roomCode,
            @Valid @RequestBody MultiplayerJoinRoomRequest request
    ) {
        return gameService.joinRoomByCode(roomCode, request.playerName());
    }

    @PostMapping("/multiplayer/rooms/{roomCode}/leave")
    public MultiplayerLeaveRoomResponse leaveMultiplayerRoom(
            @PathVariable String roomCode,
            @Valid @RequestBody MultiplayerJoinRoomRequest request
    ) {
        return gameService.leaveRoomByCode(roomCode, request.playerName());
    }

    @GetMapping("/multiplayer/rooms/{roomCode}/presence")
    public MultiplayerPresenceSnapshot getMultiplayerPresence(@PathVariable String roomCode) {
        return gameService.getRoomPresenceByCode(roomCode);
    }

    @PostMapping("/multiplayer/rooms/{roomCode}/presence")
    public MultiplayerPresenceSnapshot updateMultiplayerPresence(
            @PathVariable String roomCode,
            @Valid @RequestBody MultiplayerPresenceUpdateRequest request
    ) {
        return gameService.upsertPresenceByRoomCode(roomCode, request.playerName(), request.x(), request.y());
    }

    @GetMapping("/leaderboard")
    public LeaderboardResponse leaderboard(@RequestParam(defaultValue = "10") int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return new LeaderboardResponse(gameService.getTopScores(boundedLimit));
    }
}