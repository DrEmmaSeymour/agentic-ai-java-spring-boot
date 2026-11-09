package com.example.taskapi.agent;

/**
 * The AI provider could not be reached or rejected the request (missing key, rate limit, outage).
 * The message is safe to show to API clients; the cause holds the provider details for logs.
 */
public class AgentUnavailableException extends RuntimeException {

	public AgentUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
