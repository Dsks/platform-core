package app.qomo.emailsender.infrastructure.adapter.out.clock;

import app.qomo.emailsender.application.port.out.ClockPort;
import java.time.Instant;

/**
 * {@link ClockPort} implementation backed by the JVM system clock.
 *
 * <p>This adapter keeps direct wall-clock access at the infrastructure boundary. Application
 * services receive an explicit {@link Instant} through the port instead of depending on {@link
 * Instant#now()} directly, which keeps retry decisions and persisted timestamps driven by a
 * replaceable time source.
 */
public final class SystemClockAdapter implements ClockPort {

  /**
   * Reads the current wall-clock instant from the running JVM.
   *
   * @return current system timestamp
   */
  @Override
  public Instant now() {
    return Instant.now();
  }
}
