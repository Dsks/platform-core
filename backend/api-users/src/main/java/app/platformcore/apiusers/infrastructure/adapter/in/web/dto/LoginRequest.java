package app.platformcore.apiusers.infrastructure.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Credentials submitted to create a browser authentication session.
 *
 * @param email account email used for credential lookup; must be non-blank and syntactically valid
 * @param password raw password from the request body; must be non-blank and is never echoed
 */
@Schema(description = "Credentials used to create a browser authentication session.")
public record LoginRequest(
    @NotBlank
        @Email
        @Schema(
            description = "Account email used for credential lookup.",
            format = "email",
            example = "user@example.com")
        String email,
    @NotBlank
        @Schema(
            description = "Raw account password. The value is accepted only in requests.",
            writeOnly = true,
            example = "StrongPassw0rd!")
        String password) {}
