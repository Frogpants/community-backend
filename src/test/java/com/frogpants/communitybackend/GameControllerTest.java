package com.frogpants.communitybackend;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void registerAndSubmitScoreFlowWorks() throws Exception {
        String playerResponse = mockMvc.perform(post("/api/players/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"playerName\": \"Avery\"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName").value("Avery"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String playerId = playerResponse.replaceAll(".*\"playerId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"playerId\": \"%s\",
                                  \"score\": 4200,
                                  \"level\": 3
                                }
                                """.formatted(playerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(4200))
                .andExpect(jsonPath("$.level").value(3));

        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[?(@.playerName=='Avery' && @.score==4200)]").value(hasSize(greaterThan(0))));
    }

    @Test
    void frontendFlowWorksWithPlayerNameOnly() throws Exception {
        mockMvc.perform(post("/api/frontend/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"playerName\": \"Jules\"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName").value("Jules"));

        mockMvc.perform(post("/api/frontend/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"playerName\": \"Jules\",
                                  \"score\": 9001,
                                  \"level\": 6
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName").value("Jules"))
                .andExpect(jsonPath("$.score").value(9001))
                .andExpect(jsonPath("$.level").value(6));

        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[?(@.playerName=='Jules' && @.score==9001)]").value(hasSize(greaterThan(0))));
    }

    @Test
    void multiplayerRoomLifecycleWorks() throws Exception {
        String createRoomResponse = mockMvc.perform(post("/api/multiplayer/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerName": "Mia"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").exists())
                .andExpect(jsonPath("$.members[0].playerName").value("Mia"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String roomCode = createRoomResponse.replaceAll(".*\\\"roomCode\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/multiplayer/rooms/{roomCode}/join", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerName": "Noah"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value(roomCode))
                .andExpect(jsonPath("$.members.length()").value(2));

        mockMvc.perform(get("/api/multiplayer/rooms/{roomCode}", roomCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value(roomCode))
                .andExpect(jsonPath("$.members.length()").value(2));

        mockMvc.perform(get("/api/multiplayer/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.roomCode=='" + roomCode + "')]" ).value(hasSize(greaterThan(0))));

        mockMvc.perform(post("/api/multiplayer/rooms/{roomCode}/leave", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerName": "Noah"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value(roomCode))
                .andExpect(jsonPath("$.roomClosed").value(false))
                .andExpect(jsonPath("$.playerCount").value(1));

        mockMvc.perform(post("/api/multiplayer/rooms/{roomCode}/leave", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerName": "Mia"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value(roomCode))
                .andExpect(jsonPath("$.roomClosed").value(true))
                .andExpect(jsonPath("$.playerCount").value(0));

        mockMvc.perform(get("/api/multiplayer/rooms/{roomCode}", roomCode))
                .andExpect(status().isNotFound());
    }
}