package app.platformcore.apiusers.application.port.out;

import java.time.Instant;

/**
 * Abstracts the source of application time for use cases and infrastructure coordination.
 *
 * <p>The application expects callers to pass this time explicitly into time-dependent operations so
 * expiration, throttling, audit timestamps, and retry windows can be evaluated consistently.
 * Implementations may use system time or a controlled clock for tests.
 */
public interface ClockPort {

  /**
   * Provides the current application instant.
   *
   * @return current instant according to the configured clock source
   */
  Instant now();
}
