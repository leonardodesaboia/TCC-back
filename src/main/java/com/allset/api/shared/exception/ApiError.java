package com.allset.api.shared.exception;

import java.time.Instant;
import java.util.Map;

public record ApiError(
    int status,
    String message,
    Map<String, String> fields,
    Instant timestamp
) {}
