package app.qomo.emailsender.infrastructure.adapter.out.clock;

import app.qomo.emailsender.application.port.out.ClockPort;
import java.time.Instant;

public final class SystemClockAdapter implements ClockPort {

  @Override
  public Instant now() {
    return Instant.now();
  }
}
