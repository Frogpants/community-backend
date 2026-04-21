package com.frogpants.communitybackend.controller;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frogpants.communitybackend.model.TaskDataEntry;
import com.frogpants.communitybackend.model.TaskDataRequest;
import com.frogpants.communitybackend.service.GameService;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;

@RestController
@RequestMapping({"/api", ""})
@CrossOrigin(origins = "${app.cors.allowed-origins:*}")
public class TaskController {

    private final GameService gameService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public TaskController(GameService gameService, ObjectMapper objectMapper, Validator validator) {
        this.gameService = gameService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @GetMapping("/tasks")
    public List<TaskDataEntry> getTasks() {
        return gameService.getTaskData();
    }

    @PostMapping(value = "/tasks", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TaskDataEntry saveTask(@Valid @RequestBody TaskDataRequest request) {
        return gameService.saveTaskData(request);
    }

    @PostMapping(value = "/tasks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TaskDataEntry saveTask(@RequestPart("requestFile") MultipartFile requestFile) {
        TaskDataRequest request = readJsonRequest(requestFile, TaskDataRequest.class);
        return gameService.saveTaskData(request);
    }

    @PostMapping(value = "/tasks/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TaskDataEntry completeTask(@Valid @RequestBody TaskDataRequest request) {
        return gameService.completeTaskData(request);
    }

    @PostMapping(value = "/tasks/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TaskDataEntry completeTask(@RequestPart("requestFile") MultipartFile requestFile) {
        TaskDataRequest request = readJsonRequest(requestFile, TaskDataRequest.class);
        return gameService.completeTaskData(request);
    }

    @PostMapping(value = "/frontend/tasks", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TaskDataEntry saveFrontendTask(@Valid @RequestBody TaskDataRequest request) {
        return gameService.saveTaskData(request);
    }

    @PostMapping(value = "/frontend/tasks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TaskDataEntry saveFrontendTask(@RequestPart("requestFile") MultipartFile requestFile) {
        TaskDataRequest request = readJsonRequest(requestFile, TaskDataRequest.class);
        return gameService.saveTaskData(request);
    }

    @PostMapping(value = "/frontend/tasks/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TaskDataEntry completeFrontendTask(@Valid @RequestBody TaskDataRequest request) {
        return gameService.completeTaskData(request);
    }

    @PostMapping(value = "/frontend/tasks/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TaskDataEntry completeFrontendTask(@RequestPart("requestFile") MultipartFile requestFile) {
        TaskDataRequest request = readJsonRequest(requestFile, TaskDataRequest.class);
        return gameService.completeTaskData(request);
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
