package app.qomo.emailsender.application.port.out;

import java.time.Instant;

public interface ClockPort {

  Instant now();
}
