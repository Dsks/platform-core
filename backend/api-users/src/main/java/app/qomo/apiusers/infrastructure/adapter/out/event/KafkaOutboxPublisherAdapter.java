package app.qomo.apiusers.infrastructure.adapter.out.event;

import app.qomo.apiusers.domain.model.OutboxEvent;
import app.qomo.apiusers.domain.port.out.OutboxEventPublisherPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaOutboxPublisherAdapter implements OutboxEventPublisherPort {

  private static final Logger log = LoggerFactory.getLogger(KafkaOutboxPublisherAdapter.class);

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  public KafkaOutboxPublisherAdapter(KafkaTemplate<String, String> kafkaTemplate,
      ObjectMapper objectMapper) {
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public void publish(OutboxEvent event) {
    try {
      kafkaTemplate.send(event.topic(), event.key(), event.payloadJson()).get();
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