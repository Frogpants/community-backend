package com.frogpants.communitybackend;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
                void registerPlayerAcceptsJsonBody() throws Exception {
                                mockMvc.perform(post("/api/players/register")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "playerName": "Avery"
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isOk())
                                                                .andExpect(jsonPath("$.playerName").value("Avery"));
                }

    @Test
    void registerAndSubmitScoreFlowWorks() throws Exception {
                                String playerResponse = mockMvc.perform(multipart("/api/players/register")
                                                                                                .file(jsonPart("""
                                                                                                                                {
                                                                                                                                        \"playerName\": \"Avery\"
                                                                                                                                }
                                                                                                                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName").value("Avery"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String playerId = playerResponse.replaceAll(".*\"playerId\":\"([^\"]+)\".*", "$1");

                                mockMvc.perform(multipart("/api/scores")
                                                                                                .file(jsonPart("""
                                                                                                                                {
                                                                                                                                        \"playerId\": \"%s\",
                                                                                                                                        \"score\": 4200,
                                                                                                                                        \"level\": 3
                                                                                                                                }
                                                                                                                                """.formatted(playerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(4200))
                .andExpect(jsonPath("$.level").value(3));

        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[?(@.playerName=='Avery' && @.score==4200)]").value(hasSize(greaterThan(0))));
    }

    @Test
    void frontendFlowWorksWithPlayerNameOnly() throws Exception {
                                mockMvc.perform(multipart("/api/frontend/players")
                                                                                                .file(jsonPart("""
                                                                                                                                {
                                                                                                                                        \"playerName\": \"Jules\"
                                                                                                                                }
                                                                                                                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName").value("Jules"));

                                mockMvc.perform(multipart("/api/frontend/scores")
                                                                                                .file(jsonPart("""
                                                                                                                                {
                                                                                                                                        \"playerName\": \"Jules\",
                                                                                                                                        \"score\": 9001,
                                                                                                                                        \"level\": 6
                                                                                                                                }
                                                                                                                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName").value("Jules"))
                .andExpect(jsonPath("$.score").value(9001))
                .andExpect(jsonPath("$.level").value(6));

        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[?(@.playerName=='Jules' && @.score==9001)]").value(hasSize(greaterThan(0))));
    }

    @Test
    void taskEndpointsWorkThroughApiPrefix() throws Exception {
        String taskName = "task-" + UUID.randomUUID();

        mockMvc.perform(post("/api/tasks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                                "userName": "Mia",
                                                "taskName": "%s",
                                                "completed": false
                                        }
                                        """.formatted(taskName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("Mia"))
                .andExpect(jsonPath("$.name").value(taskName))
                .andExpect(jsonPath("$.taskName").value(taskName))
                .andExpect(jsonPath("$.completed").value(false));

        mockMvc.perform(post("/api/tasks/complete")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                                "userName": "Mia",
                                                "taskName": "%s"
                                        }
                                        """.formatted(taskName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("Mia"))
                .andExpect(jsonPath("$.name").value(taskName))
                .andExpect(jsonPath("$.completed").value(true));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userName=='Mia' && @.taskName=='" + taskName + "' && @.completed==true)]").value(hasSize(greaterThan(0))));
    }

    @Test
    void taskEndpointsSupportMultipartRoomAndTaskIdPayload() throws Exception {
        mockMvc.perform(multipart("/api/tasks")
                                .file(jsonPart("""
                                        {
                                                "userName": "Noah",
                                                "room": 2,
                                                "taskId": 7,
                                                "completed": false
                                        }
                                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("Noah"))
                .andExpect(jsonPath("$.name").value("room:2:task:7"))
                .andExpect(jsonPath("$.completed").value(false));

        mockMvc.perform(multipart("/api/tasks/complete")
                                .file(jsonPart("""
                                        {
                                                "userName": "Noah",
                                                "room": 2,
                                                "taskId": 7
                                        }
                                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("Noah"))
                .andExpect(jsonPath("$.name").value("room:2:task:7"))
                .andExpect(jsonPath("$.completed").value(true));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userName=='Noah' && @.name=='room:2:task:7' && @.completed==true)]").value(hasSize(greaterThan(0))));
    }

    @Test
    void taskEndpointsAcceptGameStyleDisplayNameAndRoomAliases() throws Exception {
        mockMvc.perform(post("/api/tasks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                                "characterDisplayName": "Luna",
                                                "taskDisplayName": "wash dishes",
                                                "roomNumber": 4,
                                                "taskNumber": 9,
                                                "completed": false
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("Luna"))
                .andExpect(jsonPath("$.name").value("wash dishes"))
                .andExpect(jsonPath("$.taskName").value("wash dishes"))
                .andExpect(jsonPath("$.room").value(4))
                .andExpect(jsonPath("$.completed").value(false));

        mockMvc.perform(post("/api/tasks/complete")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                                "displayName": "Luna",
                                                "taskLabel": "wash dishes",
                                                "currentRoom": 4
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("Luna"))
                .andExpect(jsonPath("$.name").value("wash dishes"))
                .andExpect(jsonPath("$.room").value(4))
                .andExpect(jsonPath("$.completed").value(true));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userName=='Luna' && @.taskName=='wash dishes' && @.room==4 && @.completed==true)]")
                        .value(hasSize(greaterThan(0))));
    }

    @Test
    void multiplayerRoomLifecycleWorks() throws Exception {
                                String createRoomResponse = mockMvc.perform(multipart("/api/multiplayer/rooms")
                                                                                                .file(jsonPart("""
                                                                                                                                {
                                                                                                                                        "playerName": "Mia"
                                                                                                                                }
                                                                                                                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").exists())
                .andExpect(jsonPath("$.members[0].playerName").value("Mia"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String roomCode = createRoomResponse.replaceAll(".*\\\"roomCode\\\":\\\"([^\\\"]+)\\\".*", "$1");

                                mockMvc.perform(multipart("/api/multiplayer/rooms/{roomCode}/join", roomCode)
                                                                                                .file(jsonPart("""
                                                                                                                                {
                                                                                                                                        "playerName": "Noah"
                                                                                                                                }
                                                                                                                                """)))
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

                                mockMvc.perform(multipart("/api/multiplayer/rooms/{roomCode}/leave", roomCode)
                                                                                                .file(jsonPart("""
                                                                                                                                {
                                                                                                                                        "playerName": "Noah"
                                                                                                                                }
                                                                                                                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value(roomCode))
                .andExpect(jsonPath("$.roomClosed").value(false))
                .andExpect(jsonPath("$.playerCount").value(1));

                                mockMvc.perform(multipart("/api/multiplayer/rooms/{roomCode}/leave", roomCode)
                                                                                                .file(jsonPart("""
                                                                                                                                {
                                                                                                                                        "playerName": "Mia"
                                                                                                                                }
                                                                                                                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value(roomCode))
                .andExpect(jsonPath("$.roomClosed").value(true))
                .andExpect(jsonPath("$.playerCount").value(0));

        mockMvc.perform(get("/api/multiplayer/rooms/{roomCode}", roomCode))
                .andExpect(status().isNotFound());
    }

        private MockMultipartFile jsonPart(String json) {
                return new MockMultipartFile(
                                "requestFile",
                                "request.json",
                                MediaType.APPLICATION_JSON_VALUE,
                                json.getBytes(StandardCharsets.UTF_8)
                );
        }
}
