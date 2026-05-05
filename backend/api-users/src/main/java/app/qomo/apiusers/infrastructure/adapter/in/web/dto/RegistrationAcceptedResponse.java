package app.qomo.apiusers.infrastructure.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Generic acknowledgement returned by registration-style HTTP flows.
 *
 * @param requestId opaque request identifier for client-side correlation; not a user identifier
 * @param message generic client-facing message that does not confirm account existence
 */
@Schema(
    description =
        "Generic acknowledgement for registration-style flows. The response does not confirm"
            + " whether an account exists.")
public record RegistrationAcceptedResponse(
    @Schema(
            description =
                "Opaque request identifier for client-side correlation. This is not a user"
                    + " identifier.",
            example = "req-123")
        String requestId,
    @Schema(
            description =
                "Generic client-facing message that avoids revealing account existence or"
                    + " verification state.",
            example = "If the email is valid, you'll receive next steps.")
        String message) {}
