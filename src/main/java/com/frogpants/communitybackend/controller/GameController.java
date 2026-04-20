package com.frogpants.communitybackend.controller;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "${app.cors.allowed-origins:*}")
public class GameController {

    private final GameService gameService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public GameController(GameService gameService, ObjectMapper objectMapper, Validator validator) {
        this.gameService = gameService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok");
    }

    @PostMapping(value = "/players/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PlayerSession registerPlayer(@RequestPart("requestFile") MultipartFile requestFile) {
        PlayerRegistrationRequest request = readJsonRequest(requestFile, PlayerRegistrationRequest.class);
        return gameService.registerPlayer(request);
    }

    @PostMapping(value = "/players/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PlayerSession registerPlayer(@RequestBody PlayerRegistrationRequest request) {
        return gameService.registerPlayer(request);
    }

    @PostMapping(value = "/frontend/players", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PlayerSession getOrCreatePlayerForFrontend(@RequestPart("requestFile") MultipartFile requestFile) {
        PlayerRegistrationRequest request = readJsonRequest(requestFile, PlayerRegistrationRequest.class);
        return gameService.getOrCreatePlayerByName(request.playerName());
    }

    @PostMapping(value = "/frontend/players", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PlayerSession getOrCreatePlayerForFrontend(@RequestBody PlayerRegistrationRequest request) {
        return gameService.getOrCreatePlayerByName(request.playerName());
    }

    @GetMapping("/players/{playerId}")
    public PlayerSession getPlayer(@PathVariable String playerId) {
        return gameService.getPlayer(playerId);
    }

    @PostMapping(value = "/scores", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ScoreEntry submitScore(@RequestPart("requestFile") MultipartFile requestFile) {
        ScoreSubmissionRequest request = readJsonRequest(requestFile, ScoreSubmissionRequest.class);
        return gameService.submitScore(request);
    }

    @PostMapping(value = "/scores", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ScoreEntry submitScore(@RequestBody ScoreSubmissionRequest request) {
        return gameService.submitScore(request);
    }

    @PostMapping(value = "/frontend/scores", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ScoreEntry submitScoreForFrontend(@RequestPart("requestFile") MultipartFile requestFile) {
        FrontendScoreSubmissionRequest request = readJsonRequest(requestFile, FrontendScoreSubmissionRequest.class);
        return gameService.submitScoreByPlayerName(request);
    }

    @PostMapping(value = "/frontend/scores", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ScoreEntry submitScoreForFrontend(@RequestBody FrontendScoreSubmissionRequest request) {
        return gameService.submitScoreByPlayerName(request);
    }

    @PostMapping(value = "/multiplayer/rooms", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MultiplayerRoomDetails createMultiplayerRoom(@RequestPart("requestFile") MultipartFile requestFile) {
        MultiplayerCreateRoomRequest request = readJsonRequest(requestFile, MultiplayerCreateRoomRequest.class);
        return gameService.createRoomByPlayerName(request.playerName());
    }

    @PostMapping(value = "/multiplayer/rooms", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MultiplayerRoomDetails createMultiplayerRoom(@RequestBody MultiplayerCreateRoomRequest request) {
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

    @PostMapping(value = "/multiplayer/rooms/{roomCode}/join", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MultiplayerRoomDetails joinMultiplayerRoom(
            @PathVariable String roomCode,
            @RequestPart("requestFile") MultipartFile requestFile
    ) {
        MultiplayerJoinRoomRequest request = readJsonRequest(requestFile, MultiplayerJoinRoomRequest.class);
        return gameService.joinRoomByCode(roomCode, request.playerName());
    }

    @PostMapping(value = "/multiplayer/rooms/{roomCode}/join", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MultiplayerRoomDetails joinMultiplayerRoom(
            @PathVariable String roomCode,
            @RequestBody MultiplayerJoinRoomRequest request
    ) {
        return gameService.joinRoomByCode(roomCode, request.playerName());
    }

    @PostMapping(value = "/multiplayer/rooms/{roomCode}/leave", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MultiplayerLeaveRoomResponse leaveMultiplayerRoom(
            @PathVariable String roomCode,
            @RequestPart("requestFile") MultipartFile requestFile
    ) {
        MultiplayerJoinRoomRequest request = readJsonRequest(requestFile, MultiplayerJoinRoomRequest.class);
        return gameService.leaveRoomByCode(roomCode, request.playerName());
    }

    @PostMapping(value = "/multiplayer/rooms/{roomCode}/leave", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MultiplayerLeaveRoomResponse leaveMultiplayerRoom(
            @PathVariable String roomCode,
            @RequestBody MultiplayerJoinRoomRequest request
    ) {
        return gameService.leaveRoomByCode(roomCode, request.playerName());
    }

    @GetMapping("/multiplayer/rooms/{roomCode}/presence")
    public MultiplayerPresenceSnapshot getMultiplayerPresence(@PathVariable String roomCode) {
        return gameService.getRoomPresenceByCode(roomCode);
    }

    @PostMapping(value = "/multiplayer/rooms/{roomCode}/presence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MultiplayerPresenceSnapshot updateMultiplayerPresence(
            @PathVariable String roomCode,
            @RequestPart("requestFile") MultipartFile requestFile
    ) {
        MultiplayerPresenceUpdateRequest request = readJsonRequest(requestFile, MultiplayerPresenceUpdateRequest.class);
        return gameService.upsertPresenceByRoomCode(roomCode, request.playerName(), request.x(), request.y());
    }

    @PostMapping(value = "/multiplayer/rooms/{roomCode}/presence", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MultiplayerPresenceSnapshot updateMultiplayerPresence(
            @PathVariable String roomCode,
            @RequestBody MultiplayerPresenceUpdateRequest request
    ) {
        return gameService.upsertPresenceByRoomCode(roomCode, request.playerName(), request.x(), request.y());
    }

    @GetMapping("/leaderboard")
    public LeaderboardResponse leaderboard(@RequestParam(defaultValue = "10") int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return new LeaderboardResponse(gameService.getTopScores(boundedLimit));
    }

    private <T> T readJsonRequest(MultipartFile requestFile, Class<T> requestType) {
        if (requestFile == null || requestFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestFile is required");
        }

        try {
            T request = objectMapper.readValue(requestFile.getBytes(), requestType);
            validateRequest(request);
            return request;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestFile must contain valid JSON", e);
        }
    }

    private <T> void validateRequest(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .collect(Collectors.joining(", "));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}