package com.example.taskapi.task;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable task model. Status changes produce a new instance via {@link #withStatus}.
 */
public record Task(UUID id, String title, String description, TaskStatus status, Instant createdAt) {

	public Task withStatus(TaskStatus newStatus) {
		return new Task(id, title, description, newStatus, createdAt);
	}
}
