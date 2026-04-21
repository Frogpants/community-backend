package com.frogpants.communitybackend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.frogpants.communitybackend.model.TaskDataEntry;
import com.frogpants.communitybackend.model.TaskDataRequest;
import com.frogpants.communitybackend.service.GameService;

import jakarta.validation.Valid;

@RestController
@CrossOrigin(origins = "${app.cors.allowed-origins:*}")
public class TaskController {

    private final GameService gameService;

    public TaskController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/tasks")
    public List<TaskDataEntry> getTasks() {
        return gameService.getTaskData();
    }

    @PostMapping("/tasks")
    public TaskDataEntry saveTask(@Valid @RequestBody TaskDataRequest request) {
        return gameService.saveTaskData(request);
    }

    @PostMapping("/tasks/complete")
    public TaskDataEntry completeTask(@Valid @RequestBody TaskDataRequest request) {
        return gameService.completeTaskData(request);
    }
}
