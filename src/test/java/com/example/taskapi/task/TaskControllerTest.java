package com.example.taskapi.task;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer slice test: loads only the controller + advice, with the service mocked.
 */
@WebMvcTest(TaskController.class)
class TaskControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TaskService taskService;

	private static Task sampleTask(TaskStatus status) {
		return new Task(UUID.fromString("11111111-1111-1111-1111-111111111111"), "Write API docs", "Document the REST endpoints",
				status, Instant.parse("2026-01-01T10:00:00Z"));
	}

	@Test
	void postCreatesTaskAndReturns201WithLocation() throws Exception {
		Task task = sampleTask(TaskStatus.TODO);
		when(taskService.create(any(CreateTaskRequest.class))).thenReturn(task);

		mockMvc.perform(post("/tasks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title": "Write API docs", "description": "Document the REST endpoints"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/tasks/" + task.id()))
				.andExpect(jsonPath("$.id").value(task.id().toString()))
				.andExpect(jsonPath("$.title").value("Write API docs"))
				.andExpect(jsonPath("$.status").value("TODO"));
	}

	@Test
	void postWithBlankTitleReturns400WithFieldError() throws Exception {
		mockMvc.perform(post("/tasks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title": "   "}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Validation error"))
				.andExpect(jsonPath("$.errors.title").value("title must not be blank"));

		verify(taskService, never()).create(any());
	}

	@Test
	void getReturnsAllTasks() throws Exception {
		when(taskService.findAll()).thenReturn(List.of(sampleTask(TaskStatus.TODO)));

		mockMvc.perform(get("/tasks"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].title").value("Write API docs"));
	}

	@Test
	void patchUpdatesStatus() throws Exception {
		Task done = sampleTask(TaskStatus.DONE);
		when(taskService.updateStatus(eq(done.id()), eq(TaskStatus.DONE))).thenReturn(done);

		mockMvc.perform(patch("/tasks/{id}", done.id())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "DONE"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DONE"));
	}

	@Test
	void patchUnknownTaskReturns404() throws Exception {
		UUID unknown = UUID.randomUUID();
		when(taskService.updateStatus(unknown, TaskStatus.DONE)).thenThrow(new TaskNotFoundException(unknown));

		mockMvc.perform(patch("/tasks/{id}", unknown)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "DONE"}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Task not found"));
	}

	@Test
	void patchWithMissingStatusReturns400() throws Exception {
		mockMvc.perform(patch("/tasks/{id}", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.status").value("status is required"));
	}

	@Test
	void patchWithInvalidStatusValueReturns400() throws Exception {
		mockMvc.perform(patch("/tasks/{id}", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "FINISHED"}
								"""))
				.andExpect(status().isBadRequest());

		verify(taskService, never()).updateStatus(any(), any());
	}
}
