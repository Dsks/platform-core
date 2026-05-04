package app.qomo.apiusers.application.port.out;

import app.qomo.apiusers.domain.model.User;

public interface UserEventPublisherPort {

  void userCreated(User user);
}
