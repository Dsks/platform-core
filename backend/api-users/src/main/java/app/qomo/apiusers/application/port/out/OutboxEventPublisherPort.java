package app.qomo.apiusers.application.port.out;

import app.qomo.apiusers.domain.model.OutboxEvent;

public interface OutboxEventPublisherPort {

  void publish(OutboxEvent event);
}
