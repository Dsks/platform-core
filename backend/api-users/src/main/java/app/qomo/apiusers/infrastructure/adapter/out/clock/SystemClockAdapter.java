package app.qomo.apiusers.infrastructure.adapter.out.clock;

import app.qomo.apiusers.domain.port.out.ClockPort;
import java.time.Instant;

public final class SystemClockAdapter implements ClockPort {

  @Override
  public Instant now() {
    return Instant.now();
  }
}
