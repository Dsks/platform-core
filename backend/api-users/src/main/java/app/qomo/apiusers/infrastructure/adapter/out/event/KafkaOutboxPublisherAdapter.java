package app.qomo.apiusers.infrastructure.adapter.out.event;

import app.qomo.apiusers.application.port.out.OutboxEventPublisherPort;
import app.qomo.apiusers.domain.model.OutboxEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Outbound adapter for {@link OutboxEventPublisherPort} that publishes claimed outbox records to
 * Kafka.
 *
 * <p>The adapter sends the persisted JSON payload unchanged to the topic and key stored on the
 * {@link OutboxEvent}. It waits for the {@link KafkaTemplate} send future to complete before
 * returning, so broker or serialization failures visible through that future are propagated as
 * {@link IllegalStateException}. This class does not add exactly-once guarantees; delivery
 * semantics remain the responsibility of Kafka, the producer configuration, and the outbox state
 * transitions around this port.
 */
public class KafkaOutboxPublisherAdapter implements OutboxEventPublisherPort {

  private static final Logger log = LoggerFactory.getLogger(KafkaOutboxPublisherAdapter.class);

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  /**
   * Creates a Kafka publisher for outbox events.
   *
   * @param kafkaTemplate Kafka producer abstraction configured for string keys and JSON string
   *     payloads
   * @param objectMapper mapper used only to inspect the already-persisted JSON payload for
   *     diagnostic fields after a successful send
   */
  public KafkaOutboxPublisherAdapter(
      KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
  }

  /**
   * Publishes one outbox event to Kafka and waits for the producer acknowledgement.
   *
   * @param event claimed outbox record carrying the Kafka topic, key, and JSON payload
   * @throws IllegalStateException when the thread is interrupted, Kafka reports a send failure, or
   *     the persisted payload cannot be parsed for post-send diagnostics
   */
  @Override
  public void publish(OutboxEvent event) {
    try {
      kafkaTemplate.send(event.topic(), event.key(), event.payloadJson()).get();
      /*
       * The payload is parsed after Kafka acknowledges the send so debug logs can include stable
       * correlation fields without logging the full JSON payload.
       */
      JsonNode payload = objectMapper.readTree(event.payloadJson());
      log.debug(
          "outbox_event_published outboxId={} eventId={} correlationId={} aggregateId={} fingerprint={}",
          event.id(),
          payload.path("eventId").asText("n/a"),
          payload.path("correlationId").asText("n/a"),
          event.aggregateId(),
          payload.path("fingerprint").asText("n/a"));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while publishing outbox event", e);
    } catch (ExecutionException | IOException e) {
      throw new IllegalStateException("Unable to publish outbox event", e);
    }
  }
}
