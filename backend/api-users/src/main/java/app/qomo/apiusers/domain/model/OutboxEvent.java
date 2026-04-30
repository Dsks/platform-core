package app.qomo.apiusers.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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