package app.qomo.emailsender.application.port.out;

import java.time.Instant;

/**
 * Time source used by application services when a use case needs a consistent timestamp.
 *
 * <p>Implementations normally live in infrastructure and hide the concrete system clock, time-zone,
 * or test clock mechanism. The application core depends on this boundary so persistence updates,
 * retry decisions, and emitted timestamps can be driven by an explicit {@link Instant} provider.
 */
public interface ClockPort {

  /**
   * Obtains the current instant according to the configured application time source.
   *
   * @return current timestamp to pass into use cases and secondary ports
   */
  Instant now();
}
