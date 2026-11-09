package com.example.taskapi.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.taskapi.task.TaskStatus;

/**
 * Web-layer slice test for the agent endpoint, with the agent service mocked.
 */
@WebMvcTest(AgentController.class)
class AgentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TaskAgentService agentService;

	@Test
	void chatReturnsReplyAndActions() throws Exception {
		UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
		when(agentService.chat("Add a task to water the plants")).thenReturn(new AgentChatResponse(
				"Created 'Water the plants'.",
				List.of(new AgentAction(AgentAction.Type.CREATED, id, "Water the plants", TaskStatus.TODO))));

		mockMvc.perform(post("/agent/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message": "Add a task to water the plants"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reply").value("Created 'Water the plants'."))
				.andExpect(jsonPath("$.actions[0].type").value("CREATED"))
				.andExpect(jsonPath("$.actions[0].taskId").value(id.toString()));
	}

	@Test
	void blankMessageReturns400() throws Exception {
		mockMvc.perform(post("/agent/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message": "  "}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.message").value("message must not be blank"));

		verify(agentService, never()).chat(any());
	}

	@Test
	void tooLongMessageReturns400() throws Exception {
		String longMessage = "a".repeat(2001);

		mockMvc.perform(post("/agent/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"message\": \"" + longMessage + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.message").value("message must be at most 2000 characters"));

		verify(agentService, never()).chat(any());
	}

	@Test
	void agentUnavailableReturns503() throws Exception {
		when(agentService.chat(any())).thenThrow(new AgentUnavailableException(
				"The AI agent is not configured. Set the ANTHROPIC_API_KEY environment variable.", null));

		mockMvc.perform(post("/agent/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message": "List my tasks"}
								"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.title").value("AI agent unavailable"))
				.andExpect(jsonPath("$.detail")
						.value("The AI agent is not configured. Set the ANTHROPIC_API_KEY environment variable."));
	}
}
