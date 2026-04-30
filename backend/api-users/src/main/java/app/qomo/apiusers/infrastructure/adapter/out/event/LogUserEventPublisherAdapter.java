package app.qomo.apiusers.infrastructure.adapter.out.event;

import app.qomo.apiusers.application.observability.PiiUtil;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.port.out.UserEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LogUserEventPublisherAdapter implements UserEventPublisherPort {

  private static final Logger log = LoggerFactory.getLogger(LogUserEventPublisherAdapter.class);

  @Override
  public void userCreated(User user) {
    var email_fp = PiiUtil.emailFingerprint(String.valueOf(user.email().value()));
    log.info("event=user_created userId={} email={}", user.id(), email_fp);
  }
}
