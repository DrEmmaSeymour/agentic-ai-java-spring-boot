package com.example.taskapi.agent;

import java.util.UUID;

import com.example.taskapi.task.Task;
import com.example.taskapi.task.TaskStatus;

/**
 * Audit record of one change the agent made, returned to the caller so nothing changes silently.
 */
public record AgentAction(Type type, UUID taskId, String title, TaskStatus status) {

	public enum Type {
		CREATED, STATUS_UPDATED
	}

	static AgentAction of(Type type, Task task) {
		return new AgentAction(type, task.id(), task.title(), task.status());
	}
}
