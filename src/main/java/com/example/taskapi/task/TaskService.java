package com.example.taskapi.task;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Business logic and in-memory storage for tasks.
 * ConcurrentHashMap makes concurrent requests safe without explicit locking.
 */
@Service
public class TaskService {

	private final Map<UUID, Task> tasks = new ConcurrentHashMap<>();
	private final Clock clock;

	@Autowired
	public TaskService() {
		this(Clock.systemUTC());
	}

	// Package-private: lets tests supply a fixed clock.
	TaskService(Clock clock) {
		this.clock = clock;
	}

	public Task create(CreateTaskRequest request) {
		Task task = new Task(UUID.randomUUID(), request.title().strip(), request.description(),
				TaskStatus.TODO, Instant.now(clock));
		tasks.put(task.id(), task);
		return task;
	}

	public List<Task> findAll() {
		return tasks.values().stream()
				.sorted(Comparator.comparing(Task::createdAt))
				.toList();
	}

	public Task updateStatus(UUID id, TaskStatus status) {
		// computeIfPresent is atomic: no lost updates under concurrent PATCH requests.
		Task updated = tasks.computeIfPresent(id, (key, existing) -> existing.withStatus(status));
		if (updated == null) {
			throw new TaskNotFoundException(id);
		}
		return updated;
	}
}
