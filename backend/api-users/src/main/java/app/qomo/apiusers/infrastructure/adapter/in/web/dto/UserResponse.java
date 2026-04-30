package app.qomo.apiusers.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
    String id,
    String email,
    boolean isActive,
    boolean isVerified,
    Instant lastLogin,
    Instant createdAt,
    Instant updatedAt,
    Set<String> roles) {}
