package app.qomo.apiusers.infrastructure.adapter.out.clock;

import app.qomo.apiusers.application.port.out.ClockPort;
import java.time.Instant;

/**
 * Outbound adapter for {@link ClockPort} backed by the JVM system clock.
 *
 * <p>It gives application services a technical time source without coupling them to {@link
 * Instant#now()}. The adapter has no storage or broker side effects; it only reads the current
 * wall-clock instant from the runtime.
 */
public final class SystemClockAdapter implements ClockPort {

  /**
   * Reads the current wall-clock instant from the JVM runtime.
   *
   * @return the current instant according to the system clock
   */
  @Override
  public Instant now() {
    return Instant.now();
  }
}
