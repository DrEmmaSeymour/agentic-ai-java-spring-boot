package com.example.taskapi.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Minimal stand-in for the Anthropic Messages API, built on the JDK's HTTP server.
 * Tests queue scripted responses (for example a {@code tool_use} turn followed by a final
 * answer) and then inspect the exact JSON requests Spring AI sent. No network, no API key, no cost.
 */
final class FakeAnthropicServer implements AutoCloseable {

	private final HttpServer server;
	private final Deque<Reply> replies = new ConcurrentLinkedDeque<>();
	private final List<String> requestBodies = Collections.synchronizedList(new ArrayList<>());

	private record Reply(int status, String body) {
	}

	FakeAnthropicServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", this::handle);
		server.start();
	}

	String baseUrl() {
		return "http://localhost:" + server.getAddress().getPort();
	}

	/** Queue a successful Messages API response. */
	FakeAnthropicServer reply(String messageJson) {
		replies.add(new Reply(200, messageJson));
		return this;
	}

	/** Queue an HTTP error response. */
	FakeAnthropicServer error(int status, String errorType, String message) {
		replies.add(new Reply(status, """
				{"type":"error","error":{"type":"%s","message":"%s"}}""".formatted(errorType, message)));
		return this;
	}

	List<String> requests() {
		synchronized (requestBodies) {
			return List.copyOf(requestBodies);
		}
	}

	void reset() {
		replies.clear();
		requestBodies.clear();
	}

	private void handle(HttpExchange exchange) throws IOException {
		try (InputStream in = exchange.getRequestBody()) {
			requestBodies.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
		Reply reply = replies.poll();
		if (reply == null) {
			reply = new Reply(500, """
					{"type":"error","error":{"type":"api_error","message":"FakeAnthropicServer: no reply queued"}}""");
		}
		byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(reply.status(), bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	@Override
	public void close() {
		server.stop(0);
	}

	// ---- helpers that build Messages API response bodies ----

	static String toolUse(String... toolUseBlocks) {
		return message("tool_use", String.join(",", toolUseBlocks));
	}

	static String toolUseBlock(String id, String name, String inputJson) {
		return """
				{"type":"tool_use","id":"%s","name":"%s","input":%s}""".formatted(id, name, inputJson);
	}

	static String finalText(String text) {
		return message("end_turn", """
				{"type":"text","text":"%s"}""".formatted(text));
	}

	static String refusal() {
		return message("refusal", "");
	}

	private static String message(String stopReason, String contentBlocks) {
		return """
				{"id":"msg_test","type":"message","role":"assistant","model":"claude-haiku-5-5",
				 "content":[%s],"stop_reason":"%s","stop_sequence":null,
				 "usage":{"input_tokens":10,"output_tokens":5}}""".formatted(contentBlocks, stopReason);
	}
}
