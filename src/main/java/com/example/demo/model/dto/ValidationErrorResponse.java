package com.example.demo.model.dto;

import java.time.Instant;
import java.util.List;

public record ValidationErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String code,
    String message,
    String path,
    List<FieldErrorDto> fieldErrors
) {
}


