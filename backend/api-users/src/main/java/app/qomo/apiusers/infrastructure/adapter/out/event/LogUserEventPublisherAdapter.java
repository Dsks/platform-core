package app.qomo.apiusers.infrastructure.adapter.out.event;

import app.qomo.apiusers.application.observability.PiiUtil;
import app.qomo.apiusers.application.port.out.UserEventPublisherPort;
import app.qomo.apiusers.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Outbound adapter for {@link UserEventPublisherPort} that exposes user-domain events as structured
 * application logs.
 *
 * <p>This implementation is a logging-based publisher rather than a broker publisher. It records a
 * user-created event with a non-reversible email fingerprint so the raw email address is not
 * emitted to logs.
 */
public final class LogUserEventPublisherAdapter implements UserEventPublisherPort {

  private static final Logger log = LoggerFactory.getLogger(LogUserEventPublisherAdapter.class);

  /**
   * Logs the creation of a user using stable identifiers safe for operational correlation.
   *
   * @param user user aggregate that has been created; its email is fingerprinted before logging
   */
  @Override
  public void userCreated(User user) {
    var email_fp = PiiUtil.emailFingerprint(String.valueOf(user.email().value()));
    log.info("event=user_created userId={} email={}", user.id(), email_fp);
  }
}
