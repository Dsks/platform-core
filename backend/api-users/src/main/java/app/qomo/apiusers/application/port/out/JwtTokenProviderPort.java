package app.qomo.apiusers.application.port.out;

import app.qomo.apiusers.domain.model.User;
import java.time.Instant;
import java.util.Set;

public interface JwtTokenProviderPort {

  String generate(User user, Instant now);

  boolean validate(String token, Instant now);

  String subject(String token);

  Set<String> roles(String token);
}
