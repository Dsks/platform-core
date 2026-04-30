package app.qomo.apiusers.domain.port.out;

import java.time.Instant;

public interface ClockPort {

  Instant now();
}
