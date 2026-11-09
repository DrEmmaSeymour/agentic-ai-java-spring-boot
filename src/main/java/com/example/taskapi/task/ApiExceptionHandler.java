package com.example.taskapi.task;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.example.taskapi.agent.AgentUnavailableException;

/**
 * Translates exceptions into RFC 9457 ProblemDetail responses.
 * The base class already maps malformed JSON / bad enum values / bad UUIDs to 400.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(TaskNotFoundException.class)
	public ProblemDetail handleNotFound(TaskNotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Task not found");
		return problem;
	}

	@ExceptionHandler(AgentUnavailableException.class)
	public ProblemDetail handleAgentUnavailable(AgentUnavailableException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
		problem.setTitle("AI agent unavailable");
		return problem;
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
				.collect(Collectors.toMap(FieldError::getField,
						fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
						(first, second) -> first));
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
		problem.setTitle("Validation error");
		problem.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(problem);
	}
}
