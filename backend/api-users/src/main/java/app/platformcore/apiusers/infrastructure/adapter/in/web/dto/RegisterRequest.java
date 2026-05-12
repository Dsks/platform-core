package app.platformcore.apiusers.infrastructure.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Public registration request body accepted by the authentication API.
 *
 * @param email address to register; must be non-blank and syntactically valid
 * @param password raw password from the request body; must be non-blank and is never returned by
 *     web responses
 */
@Schema(description = "Public registration request for a new browser account.")
public record RegisterRequest(
    @Email
        @NotBlank
        @Schema(
            description = "Email address to register.",
            format = "email",
            example = "user@example.com")
        String email,
    @NotBlank
        @Schema(
            description = "Raw account password. The value is accepted only in requests.",
            writeOnly = true,
            example = "StrongPassw0rd!")
        String password) {}
