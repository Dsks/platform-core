package app.qomo.apiusers.infrastructure.adapter.in.web.dto;

/**
 * Generic acknowledgement returned by registration-style HTTP flows.
 *
 * @param requestId opaque request identifier for client-side correlation; not a user identifier
 * @param message generic client-facing message that does not confirm account existence
 */
public record RegistrationAcceptedResponse(String requestId, String message) {}
