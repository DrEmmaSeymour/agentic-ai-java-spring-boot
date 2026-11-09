package com.example.taskapi.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plain unit tests: no Spring context, so they run in milliseconds.
 */
class TaskServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

	private TaskService service;

	@BeforeEach
	void setUp() {
		service = new TaskService(Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void createAssignsIdDefaultStatusAndTimestamp() {
		Task task = service.create(new CreateTaskRequest("  Write tests  ", "JUnit 5"));

		assertThat(task.id()).isNotNull();
		assertThat(task.title()).isEqualTo("Write tests");
		assertThat(task.description()).isEqualTo("JUnit 5");
		assertThat(task.status()).isEqualTo(TaskStatus.TODO);
		assertThat(task.createdAt()).isEqualTo(NOW);
	}

	@Test
	void findAllReturnsEveryCreatedTask() {
		Task a = service.create(new CreateTaskRequest("A", null));
		Task b = service.create(new CreateTaskRequest("B", null));

		assertThat(service.findAll()).containsExactlyInAnyOrder(a, b);
	}

	@Test
	void findAllIsEmptyInitially() {
		assertThat(service.findAll()).isEmpty();
	}

	@Test
	void updateStatusChangesOnlyTheStatus() {
		Task original = service.create(new CreateTaskRequest("Review pull request", "Check validation rules"));

		Task updated = service.updateStatus(original.id(), TaskStatus.DONE);

		assertThat(updated.status()).isEqualTo(TaskStatus.DONE);
		assertThat(updated).usingRecursiveComparison().ignoringFields("status").isEqualTo(original);
		assertThat(service.findAll()).containsExactly(updated);
	}

	@Test
	void updateStatusOfUnknownTaskThrows() {
		UUID unknown = UUID.randomUUID();

		assertThatThrownBy(() -> service.updateStatus(unknown, TaskStatus.DONE))
				.isInstanceOf(TaskNotFoundException.class)
				.hasMessageContaining(unknown.toString());
	}

	@Test
	void concurrentCreatesAreAllStored() throws InterruptedException {
		int count = 1_000;
		try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
			for (int i = 0; i < count; i++) {
				int n = i;
				pool.submit(() -> service.create(new CreateTaskRequest("Task " + n, null)));
			}
			pool.shutdown();
			assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}

		assertThat(service.findAll()).hasSize(count);
	}
}
