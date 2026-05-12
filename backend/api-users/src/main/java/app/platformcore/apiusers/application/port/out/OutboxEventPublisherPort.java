package app.platformcore.apiusers.application.port.out;

import app.platformcore.apiusers.domain.model.OutboxEvent;

/**
 * Abstracts delivery of already-claimed outbox events to external messaging infrastructure.
 *
 * <p>The application expects implementations to either complete publication or raise a runtime
 * failure so the outbox dispatcher can mark the event for retry or terminal failure. Payloads may
 * contain sensitive data such as emails or verification codes and must not be logged in clear text.
 *
 * <p>This contract publishes a prepared outbox event; it does not create payloads, choose retry
 * policy, or decide business eligibility for publication.
 */
public interface OutboxEventPublisherPort {

  /**
   * Publishes one outbox event to its configured destination.
   *
   * @param event claimed event to deliver, including routing key, destination topic, and payload
   * @throws RuntimeException when publication cannot be completed and should be handled by the
   *     outbox retry flow
   */
  void publish(OutboxEvent event);
}
