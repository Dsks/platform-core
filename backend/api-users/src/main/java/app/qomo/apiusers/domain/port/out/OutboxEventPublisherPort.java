package app.qomo.apiusers.domain.port.out;

import app.qomo.apiusers.domain.model.OutboxEvent;

public interface OutboxEventPublisherPort {

  void publish(OutboxEvent event);
}