package com.example.taskapi.agent;

import java.util.List;

/**
 * @param reply the agent's natural-language answer
 * @param actions every change the agent made while handling the request, in order
 */
public record AgentChatResponse(String reply, List<AgentAction> actions) {
}
