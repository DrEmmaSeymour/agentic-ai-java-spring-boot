package com.example.taskapi.agent;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Agent settings, bound from {@code task-agent.*}. All of them can be overridden with
 * environment variables (for example {@code TASK_AGENT_MODEL}).
 *
 * @param model Claude model ID sent with every request
 * @param effort how much reasoning the model applies: low, medium, high, xhigh or max
 * @param maxTokens upper bound on tokens generated per model response
 * @param maxWritesPerRequest maximum creates/updates the agent may perform for one chat message
 * @param maxToolCallsPerRequest maximum tool invocations (reads and writes) for one chat message
 */
@Validated
@ConfigurationProperties("task-agent")
public record AgentProperties(
		@NotBlank String model,
		@NotNull Effort effort,
		@Min(256) int maxTokens,
		@Min(0) @Max(50) int maxWritesPerRequest,
		@Min(1) @Max(100) int maxToolCallsPerRequest) {

	public enum Effort {
		LOW, MEDIUM, HIGH, XHIGH, MAX
	}
}
