package app.qomo.apiusers.domain.port.out;

import app.qomo.apiusers.domain.model.PasswordHash;

public interface PasswordHasherPort {

  PasswordHash hash(String rawPassword);
}
