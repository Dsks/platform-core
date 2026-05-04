package app.qomo.apiusers.application.port.out;

import app.qomo.apiusers.domain.model.PasswordHash;

public interface PasswordHasherPort {

  PasswordHash hash(String rawPassword);
}
