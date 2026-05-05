package app.qomo.apiusers.infrastructure.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for completing email verification.
 *
 * <p>The verification session is intentionally taken from the browser cookie, not from this body.
 *
 * @param code one-time verification code supplied by the user; must not be blank
 */
@Schema(description = "Request for completing email verification with a one-time code.")
public record VerifyEmailRequest(
    @NotBlank
        @Schema(
            description =
                "Sensitive one-time email verification code supplied by the user. The response"
                    + " remains generic when verification cannot be completed.",
            writeOnly = true)
        String code) {}
