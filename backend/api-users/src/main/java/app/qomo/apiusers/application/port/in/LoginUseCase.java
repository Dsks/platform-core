package app.qomo.apiusers.application.port.in;

import app.qomo.apiusers.domain.model.User;
import java.util.UUID;

public interface LoginUseCase {

  record Command(String email, String password) {}

  record Result(
      User user,
      boolean emailNotVerified,
      UUID verificationSessionId,
      long verificationTtlSeconds) {}

  Result login(Command command);
}
