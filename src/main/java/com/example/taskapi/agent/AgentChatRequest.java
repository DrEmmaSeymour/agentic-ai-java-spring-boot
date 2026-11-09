package com.example.taskapi.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentChatRequest(
		@NotBlank(message = "message must not be blank")
		@Size(max = 2000, message = "message must be at most 2000 characters")
		String message) {
}
