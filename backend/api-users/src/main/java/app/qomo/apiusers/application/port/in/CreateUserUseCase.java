package app.qomo.apiusers.application.port.in;

import app.qomo.apiusers.domain.model.UserId;
import java.util.Set;

public interface CreateUserUseCase {

  record Command(String email, String rawPassword, Set<String> roles) {

  }

  record Result(UserId id) {

  }

  Result create(Command command);
}
