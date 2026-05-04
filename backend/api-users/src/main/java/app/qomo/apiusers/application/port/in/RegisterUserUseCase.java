package app.qomo.apiusers.application.port.in;

import java.util.UUID;

public interface RegisterUserUseCase {

  record Command(String email, String rawPassword) {}

  record Result(String requestId, UUID verificationSessionId, long verificationTtlSeconds) {}

  Result register(Command command);
}
