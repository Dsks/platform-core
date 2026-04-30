package app.qomo.apiusers.domain.port.out;

import app.qomo.apiusers.domain.model.User;

public interface UserEventPublisherPort {

  void userCreated(User user);
}
