package com.example.taskapi.agent;

import java.util.Locale;

import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.NoCredentialsException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.OutputConfig;
import com.example.taskapi.task.TaskService;

/**
 * Runs one natural-language request through Claude with the task tools attached.
 * Spring AI drives the tool-calling loop: Claude asks for a tool, Spring AI invokes the
 * matching {@link TaskTools} method, sends the result back, and repeats until Claude answers.
 */
@Service
public class TaskAgentService {

	static final String REFUSAL_REPLY = "I can't help with that request. I can list, create, and update tasks.";

	private static final Logger log = LoggerFactory.getLogger(TaskAgentService.class);

	private final ChatClient chatClient;
	private final TaskService taskService;
	private final Validator validator;
	private final AgentProperties properties;

	public TaskAgentService(ChatClient taskAgentChatClient, TaskService taskService, Validator validator,
			AgentProperties properties) {
		this.chatClient = taskAgentChatClient;
		this.taskService = taskService;
		this.validator = validator;
		this.properties = properties;
	}

	public AgentChatResponse chat(String message) {
		TaskTools tools = new TaskTools(taskService, validator, properties.maxWritesPerRequest(),
				properties.maxToolCallsPerRequest());

		ChatResponse response;
		try {
			response = chatClient.prompt()
					.user(message)
					.tools(tools)
					// Spring AI uses per-request options as-is, so set every option explicitly.
					.options(AnthropicChatOptions.builder()
							.model(properties.model())
							.maxTokens(properties.maxTokens())
							.effort(OutputConfig.Effort.of(properties.effort().name().toLowerCase(Locale.ROOT))))
					.call()
					.chatResponse();
		}
		catch (RuntimeException ex) {
			throw translate(ex, tools);
		}

		return new AgentChatResponse(replyText(response), tools.actions());
	}

	private static String replyText(ChatResponse response) {
		Generation result = response == null ? null : response.getResult();
		if (result == null) {
			return REFUSAL_REPLY;
		}
		String finishReason = result.getMetadata().getFinishReason();
		String text = result.getOutput().getText();
		if ("refusal".equalsIgnoreCase(finishReason) || text == null || text.isBlank()) {
			return REFUSAL_REPLY;
		}
		return text.strip();
	}

	private static RuntimeException translate(RuntimeException ex, TaskTools tools) {
		AnthropicException anthropic = findCause(ex, AnthropicException.class);
		if (anthropic == null) {
			return ex; // not a provider failure: let it surface as a 500 with the real stack trace
		}
		if (!tools.actions().isEmpty()) {
			log.warn("AI request failed after {} change(s) were applied: {}", tools.actions().size(), tools.actions());
		}
		String message = switch (anthropic) {
			case NoCredentialsException e -> "The AI agent is not configured. Set the ANTHROPIC_API_KEY environment variable.";
			// Also the result when no key is set: the SDK sends the request without credentials.
			case UnauthorizedException e ->
				"The AI provider rejected the credentials. Set ANTHROPIC_API_KEY to a valid API key.";
			case PermissionDeniedException e -> "The configured API key is not allowed to use this model.";
			case RateLimitException e -> "The AI provider is rate limiting requests. Try again shortly.";
			case AnthropicIoException e -> "The AI provider could not be reached.";
			default -> "The AI provider returned an error.";
		};
		log.warn("AI request failed: {}", anthropic.toString());
		return new AgentUnavailableException(message, ex);
	}

	private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (type.isInstance(t)) {
				return type.cast(t);
			}
		}
		return null;
	}
}
