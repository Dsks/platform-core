package app.qomo.apiusers.application.port.in;

import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import java.util.Optional;

public interface GetUserUseCase {

  Optional<User> getById(UserId id);
}
