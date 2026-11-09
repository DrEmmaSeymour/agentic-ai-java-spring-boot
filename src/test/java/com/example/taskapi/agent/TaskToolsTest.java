package com.example.taskapi.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.taskapi.agent.TaskTools.ToolRejectedException;
import com.example.taskapi.task.CreateTaskRequest;
import com.example.taskapi.task.Task;
import com.example.taskapi.task.TaskService;
import com.example.taskapi.task.TaskStatus;

/**
 * Unit tests for the agent's tools: no Spring context and no AI model involved.
 */
class TaskToolsTest {

	private static final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
	private static final Validator validator = validatorFactory.getValidator();

	private TaskService taskService;
	private TaskTools tools;

	@BeforeEach
	void setUp() {
		taskService = new TaskService();
		tools = new TaskTools(taskService, validator, 2, 5);
	}

	@AfterAll
	static void closeValidator() {
		validatorFactory.close();
	}

	@Test
	void createTaskDelegatesToServiceAndRecordsAction() {
		Task created = tools.createTask("Write release notes", "For version 1.1");

		assertThat(taskService.findAll()).containsExactly(created);
		assertThat(created.status()).isEqualTo(TaskStatus.TODO);
		assertThat(tools.actions()).containsExactly(AgentAction.of(AgentAction.Type.CREATED, created));
	}

	@Test
	void listTasksFiltersByStatus() {
		Task todo = taskService.create(new CreateTaskRequest("A", null));
		Task done = taskService.updateStatus(taskService.create(new CreateTaskRequest("B", null)).id(), TaskStatus.DONE);

		assertThat(tools.listTasks(null)).containsExactlyInAnyOrder(todo, done);
		assertThat(tools.listTasks(TaskStatus.DONE)).containsExactly(done);
		assertThat(tools.actions()).as("reads are not recorded as changes").isEmpty();
	}

	@Test
	void updateTaskStatusChangesTaskAndRecordsAction() {
		Task task = taskService.create(new CreateTaskRequest("Pay invoice", null));

		Task updated = tools.updateTaskStatus(task.id().toString(), TaskStatus.IN_PROGRESS);

		assertThat(updated.status()).isEqualTo(TaskStatus.IN_PROGRESS);
		assertThat(tools.actions()).containsExactly(AgentAction.of(AgentAction.Type.STATUS_UPDATED, updated));
	}

	@Test
	void invalidCreateInputIsRejectedWithoutCallingService() {
		TaskService mockService = mock(TaskService.class);
		TaskTools mockedTools = new TaskTools(mockService, validator, 2, 5);

		assertThatThrownBy(() -> mockedTools.createTask(" ", null))
				.isInstanceOf(ToolRejectedException.class)
				.hasMessageContaining("title must not be blank");
		assertThatThrownBy(() -> mockedTools.createTask("x".repeat(101), null))
				.hasMessageContaining("title must be at most 100 characters");

		verify(mockService, never()).create(any());
		assertThat(mockedTools.actions()).isEmpty();
	}

	@Test
	void malformedIdIsRejected() {
		assertThatThrownBy(() -> tools.updateTaskStatus("task-1", TaskStatus.DONE))
				.isInstanceOf(ToolRejectedException.class)
				.hasMessageContaining("not a valid task id");
	}

	@Test
	void unknownIdIsRejectedAndDoesNotConsumeWriteBudget() {
		String unknown = UUID.randomUUID().toString();

		assertThatThrownBy(() -> tools.updateTaskStatus(unknown, TaskStatus.DONE))
				.isInstanceOf(ToolRejectedException.class)
				.hasMessageContaining("No task exists with id " + unknown);

		// Both writes in the budget of 2 are still available.
		tools.createTask("First", null);
		tools.createTask("Second", null);
		assertThat(taskService.findAll()).hasSize(2);
	}

	@Test
	void missingStatusIsRejected() {
		Task task = taskService.create(new CreateTaskRequest("Pay invoice", null));

		assertThatThrownBy(() -> tools.updateTaskStatus(task.id().toString(), null))
				.isInstanceOf(ToolRejectedException.class)
				.hasMessageContaining("status is required");
	}

	@Test
	void writeLimitBlocksFurtherChanges() {
		tools.createTask("One", null);
		tools.createTask("Two", null);

		assertThatThrownBy(() -> tools.createTask("Three", null))
				.isInstanceOf(ToolRejectedException.class)
				.hasMessageContaining("Change limit of 2 per request reached");
		// findAll() sorts by createdAt; tasks created in the same clock tick may tie, so ignore order.
		assertThat(taskService.findAll()).extracting(Task::title).containsExactlyInAnyOrder("One", "Two");
		assertThat(tools.actions()).hasSize(2);
	}

	@Test
	void toolCallLimitStopsRunawayLoops() {
		for (int i = 0; i < 5; i++) {
			tools.listTasks(null);
		}

		assertThatThrownBy(() -> tools.listTasks(null))
				.isInstanceOf(ToolRejectedException.class)
				.hasMessageContaining("Tool call limit of 5 reached");
	}

	@Test
	void eachInstanceHasItsOwnBudget() {
		tools.createTask("One", null);
		tools.createTask("Two", null);

		TaskTools nextRequest = new TaskTools(taskService, validator, 2, 5);
		nextRequest.createTask("Three", null);

		assertThat(nextRequest.actions()).hasSize(1);
		assertThat(taskService.findAll()).hasSize(3);
	}
}
