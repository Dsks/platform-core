package app.qomo.apiusers.application.port.in;

import java.util.UUID;

public interface ResendEmailVerificationUseCase {

  record Command(String email) {}

  record Result(UUID verificationSessionId, long verificationTtlSeconds) {}

  Result resend(Command command);
}
