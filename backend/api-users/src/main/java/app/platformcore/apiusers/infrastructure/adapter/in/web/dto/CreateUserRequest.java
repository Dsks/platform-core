package app.platformcore.apiusers.infrastructure.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;

/**
 * Request body for creating a user through the users HTTP API.
 *
 * @param email address for the new account; must be non-blank and syntactically valid
 * @param password raw password from the request body; must be non-blank and is never returned by
 *     web responses
 * @param roles role names supplied by the caller for application-level validation and assignment
 */
@Schema(description = "Administrative request for creating a user account.")
public record CreateUserRequest(
    @Email
        @NotBlank
        @Schema(
            description = "Email address for the new account.",
            format = "email",
            example = "user@example.com")
        String email,
    @NotBlank
        @Schema(
            description = "Raw account password. The value is accepted only in requests.",
            writeOnly = true,
            example = "StrongPassw0rd!")
        String password,
    @Schema(
            description =
                "Requested role names for the new account. Role assignment is subject to"
                    + " server-side authorization policy.",
            example = "[\"USER\"]")
        Set<String> roles) {}
