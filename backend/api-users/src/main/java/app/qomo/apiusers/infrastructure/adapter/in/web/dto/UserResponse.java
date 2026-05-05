package app.qomo.apiusers.infrastructure.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "User account representation returned by read endpoints.")
public record UserResponse(
    @Schema(
            description = "Stable user resource identifier.",
            format = "uuid",
            example = "2fa8b8e9-3090-404e-a6e8-d95dd8e3b0ec")
        String id,
    @Schema(
            description = "Email address associated with the account.",
            format = "email",
            example = "user@example.com")
        String email,
    @Schema(description = "Whether the account is currently usable by application policy.")
        boolean isActive,
    @Schema(description = "Whether the account has completed email verification.")
        boolean isVerified,
    @Schema(
            description = "Last recorded successful login time, when available.",
            format = "date-time",
            nullable = true,
            example = "2026-03-26T10:15:30Z")
        Instant lastLogin,
    @Schema(
            description = "Time when the account was created.",
            format = "date-time",
            example = "2026-03-26T10:15:30Z")
        Instant createdAt,
    @Schema(
            description = "Time when the account was last updated.",
            format = "date-time",
            example = "2026-03-26T10:15:30Z")
        Instant updatedAt,
    @Schema(description = "Role names currently assigned to the account.", example = "[\"USER\"]")
        Set<String> roles) {}
