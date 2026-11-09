package com.example.taskapi.task;

import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull(message = "status is required") TaskStatus status) {
}
