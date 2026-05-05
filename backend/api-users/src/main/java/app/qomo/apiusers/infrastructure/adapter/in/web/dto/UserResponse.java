package app.qomo.apiusers.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.Set;

/**
 * User representation returned by HTTP read endpoints.
 *
 * <p>The response exposes account state and role names, but never credential material.
 *
 * @param id stable UUID string for the user resource
 * @param email email address associated with the account
 * @param isActive whether the account is currently usable by application policy
 * @param isVerified whether the email-verification flow has completed
 * @param lastLogin last recorded successful login time, when available
 * @param createdAt time when the account was created
 * @param updatedAt time when the account was last updated
 * @param roles role names currently assigned to the account
 */
public record UserResponse(
    String id,
    String email,
    boolean isActive,
    boolean isVerified,
    Instant lastLogin,
    Instant createdAt,
    Instant updatedAt,
    Set<String> roles) {}
