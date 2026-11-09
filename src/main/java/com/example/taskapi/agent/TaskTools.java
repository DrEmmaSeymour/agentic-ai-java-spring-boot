package com.example.taskapi.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.example.taskapi.task.CreateTaskRequest;
import com.example.taskapi.task.Task;
import com.example.taskapi.task.TaskNotFoundException;
import com.example.taskapi.task.TaskService;
import com.example.taskapi.task.TaskStatus;
import com.example.taskapi.task.UpdateStatusRequest;

/**
 * The tools Claude may call. A new instance is created for every chat request, so the
 * write budget, tool-call budget, and action log are per request and never shared.
 *
 * <p>All business logic stays in {@link TaskService}; input is validated with the same
 * Jakarta constraints as the REST API. Rejections are thrown as {@link ToolRejectedException},
 * which Spring AI returns to the model as the tool result so it can explain or recover.
 */
public class TaskTools {

	private final TaskService taskService;
	private final Validator validator;
	private final int maxWrites;
	private final int maxToolCalls;

	private final AtomicInteger writes = new AtomicInteger();
	private final AtomicInteger toolCalls = new AtomicInteger();
	private final List<AgentAction> actions = Collections.synchronizedList(new ArrayList<>());

	public TaskTools(TaskService taskService, Validator validator, int maxWrites, int maxToolCalls) {
		this.taskService = taskService;
		this.validator = validator;
		this.maxWrites = maxWrites;
		this.maxToolCalls = maxToolCalls;
	}

	@Tool(name = "list_tasks", description = """
			List existing tasks, oldest first. Each task has an id, title, description, status and createdAt. \
			Call this to look up a task's exact id before updating it.""")
	public List<Task> listTasks(
			@ToolParam(required = false, description = "Only return tasks with this status. Omit to return all tasks.")
			@Nullable TaskStatus status) {
		countToolCall();
		return taskService.findAll().stream()
				.filter(task -> status == null || task.status() == status)
				.toList();
	}

	@Tool(name = "create_task", description = """
			Create one new task with status TODO. Only call this when the user explicitly asks to create or add a task.""")
	public Task createTask(
			@ToolParam(description = "Short task title, 1-100 characters.") String title,
			@ToolParam(required = false, description = "Optional longer description, at most 500 characters.")
			@Nullable String description) {
		countToolCall();
		CreateTaskRequest request = new CreateTaskRequest(title, description);
		validate(request);
		reserveWrite();
		Task created = taskService.create(request);
		actions.add(AgentAction.of(AgentAction.Type.CREATED, created));
		return created;
	}

	@Tool(name = "update_task_status", description = """
			Change the status of one existing task. Requires the task's exact id from list_tasks; never guess an id. \
			Only call this when the user explicitly asks to change that task.""")
	public Task updateTaskStatus(
			@ToolParam(description = "Exact task id (UUID) as returned by list_tasks.") String taskId,
			@ToolParam(description = "New status.") TaskStatus status) {
		countToolCall();
		UUID id = parseId(taskId);
		validate(new UpdateStatusRequest(status));
		reserveWrite();
		try {
			Task updated = taskService.updateStatus(id, status);
			actions.add(AgentAction.of(AgentAction.Type.STATUS_UPDATED, updated));
			return updated;
		}
		catch (TaskNotFoundException ex) {
			writes.decrementAndGet(); // nothing changed, so give the write back
			throw new ToolRejectedException("No task exists with id " + id + ". Call list_tasks to find the correct id.");
		}
	}

	/** Changes made so far, in order. */
	public List<AgentAction> actions() {
		synchronized (actions) {
			return List.copyOf(actions);
		}
	}

	private void countToolCall() {
		if (toolCalls.incrementAndGet() > maxToolCalls) {
			throw new ToolRejectedException("Tool call limit of " + maxToolCalls
					+ " reached for this request. Stop calling tools and answer the user with what you have.");
		}
	}

	private void reserveWrite() {
		if (writes.incrementAndGet() > maxWrites) {
			writes.decrementAndGet();
			throw new ToolRejectedException("Change limit of " + maxWrites
					+ " per request reached, so this change was NOT made. Tell the user which changes were skipped.");
		}
	}

	private UUID parseId(String taskId) {
		try {
			return UUID.fromString(taskId == null ? "" : taskId.strip());
		}
		catch (IllegalArgumentException ex) {
			throw new ToolRejectedException("'" + taskId + "' is not a valid task id. Call list_tasks to get the exact id.");
		}
	}

	private <T> void validate(T request) {
		Set<ConstraintViolation<T>> violations = validator.validate(request);
		if (!violations.isEmpty()) {
			String details = violations.stream()
					.map(ConstraintViolation::getMessage)
					.sorted()
					.collect(Collectors.joining("; "));
			throw new ToolRejectedException("Invalid input, nothing was changed: " + details);
		}
	}

	/** Thrown for input the tool refuses; its message is sent back to the model. */
	public static class ToolRejectedException extends RuntimeException {

		public ToolRejectedException(String message) {
			super(message);
		}
	}
}
