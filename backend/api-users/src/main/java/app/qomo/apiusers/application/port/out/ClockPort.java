package app.qomo.apiusers.application.port.out;

import java.time.Instant;

public interface ClockPort {

  Instant now();
}
