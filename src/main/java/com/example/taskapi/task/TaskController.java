package com.example.taskapi.task;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP layer only: maps requests to service calls. No business logic lives here.
 */
@RestController
@RequestMapping("/tasks")
public class TaskController {

	private final TaskService taskService;

	public TaskController(TaskService taskService) {
		this.taskService = taskService;
	}

	@PostMapping
	public ResponseEntity<Task> create(@Valid @RequestBody CreateTaskRequest request) {
		Task created = taskService.create(request);
		return ResponseEntity.created(URI.create("/tasks/" + created.id())).body(created);
	}

	@GetMapping
	public List<Task> list() {
		return taskService.findAll();
	}

	@PatchMapping("/{id}")
	public Task updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
		return taskService.updateStatus(id, request.status());
	}
}
