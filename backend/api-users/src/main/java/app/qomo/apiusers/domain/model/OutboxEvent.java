package app.qomo.apiusers.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain representation of an event pending publication through the outbox pattern.
 *
 * <p>Stores immutable event identity plus operational metadata used by dispatchers (attempt count
 * and timestamps).
 */
public record OutboxEvent(
    UUID id,
    String aggregateType,
    UUID aggregateId,
    String eventType,
    String topic,
    String key,
    String payloadJson,
    int attempts,
    Instant createdAt,
    Instant updatedAt) {

  /** Creates an outbox event with mandatory routing and payload information. */
  public OutboxEvent {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(aggregateType, "aggregateType cannot be null");
    Objects.requireNonNull(aggregateId, "aggregateId cannot be null");
    Objects.requireNonNull(eventType, "eventType cannot be null");
    Objects.requireNonNull(topic, "topic cannot be null");
    Objects.requireNonNull(key, "key cannot be null");
    Objects.requireNonNull(payloadJson, "payloadJson cannot be null");
    Objects.requireNonNull(createdAt, "createdAt cannot be null");
    Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
  }
}
