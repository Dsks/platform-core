package app.platformcore.emailsender.infrastructure.adapter.in.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the minimal HTTP health probe for the email-sender service.
 *
 * <p>This controller is operational rather than user-facing. It does not call an application use
 * case because the probe only confirms that the web adapter can answer requests, and it
 * deliberately avoids exposing dependency state, queue details, mail provider configuration, or
 * other internal diagnostics.
 */
@RestController
public class HealthController {

  /**
   * Provides a lightweight readiness/liveness response for infrastructure checks.
   *
   * @return a plain-text {@code ok} marker with no service internals or tenant-specific data
   */
  @GetMapping(value = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
  public String health() {
    return "ok\n";
  }
}
