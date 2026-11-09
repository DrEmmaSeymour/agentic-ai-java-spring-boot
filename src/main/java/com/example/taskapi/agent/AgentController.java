package com.example.taskapi.agent;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Natural-language endpoint for the task agent. HTTP concerns only; the agent logic is in
 * {@link TaskAgentService}.
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

	private final TaskAgentService agentService;

	public AgentController(TaskAgentService agentService) {
		this.agentService = agentService;
	}

	@PostMapping("/chat")
	public AgentChatResponse chat(@Valid @RequestBody AgentChatRequest request) {
		return agentService.chat(request.message());
	}
}
