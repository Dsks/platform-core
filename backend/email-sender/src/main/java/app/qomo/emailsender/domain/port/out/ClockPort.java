package app.qomo.emailsender.domain.port.out;

import java.time.Instant;

public interface ClockPort {

  Instant now();
}
