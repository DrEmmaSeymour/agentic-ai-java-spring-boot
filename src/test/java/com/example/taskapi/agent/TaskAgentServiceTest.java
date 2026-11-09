package com.example.taskapi.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.NoCredentialsException;
import com.example.taskapi.task.TaskService;

/**
 * Unit tests for how provider failures are translated, using a mocked ChatClient.
 */
class TaskAgentServiceTest {

	private final ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
	private final TaskAgentService service = new TaskAgentService(chatClient, new TaskService(),
			Validation.buildDefaultValidatorFactory().getValidator(),
			new AgentProperties("claude-haiku-5-5", AgentProperties.Effort.LOW, 16000, 3, 10));

	private void failWith(RuntimeException ex) {
		when(chatClient.prompt().user(any(String.class)).tools(any()).options(any()).call().chatResponse())
				.thenThrow(ex);
	}

	@Test
	void missingApiKeyBecomesAgentUnavailable() {
		failWith(new NoCredentialsException(List.of()));

		assertThatThrownBy(() -> service.chat("List my tasks"))
				.isInstanceOf(AgentUnavailableException.class)
				.hasMessage("The AI agent is not configured. Set the ANTHROPIC_API_KEY environment variable.");
	}

	@Test
	void wrappedNetworkErrorBecomesAgentUnavailable() {
		failWith(new IllegalStateException("wrapper", new AnthropicIoException("connection refused")));

		assertThatThrownBy(() -> service.chat("List my tasks"))
				.isInstanceOf(AgentUnavailableException.class)
				.hasMessage("The AI provider could not be reached.");
	}

	@Test
	void nonProviderErrorsAreNotHidden() {
		IllegalStateException bug = new IllegalStateException("programming error");
		failWith(bug);

		assertThatThrownBy(() -> service.chat("List my tasks")).isSameAs(bug);
	}

	@Test
	void nullResponseFallsBackToSafeReply() {
		when(chatClient.prompt().user(any(String.class)).tools(any()).options(any()).call().chatResponse())
				.thenReturn(null);

		assertThat(service.chat("List my tasks").reply()).isEqualTo(TaskAgentService.REFUSAL_REPLY);
	}
}
