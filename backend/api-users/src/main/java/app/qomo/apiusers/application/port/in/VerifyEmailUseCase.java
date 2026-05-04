package app.qomo.apiusers.application.port.in;

import java.util.UUID;

public interface VerifyEmailUseCase {

  record Command(UUID sessionId, String code) {}

  boolean verify(Command command);
}
