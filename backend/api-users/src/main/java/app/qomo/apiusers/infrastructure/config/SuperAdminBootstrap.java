package app.qomo.apiusers.infrastructure.config;

import app.qomo.apiusers.application.exception.RoleNotFoundException;
import app.qomo.apiusers.application.observability.PiiUtil;
import app.qomo.apiusers.application.port.out.ClockPort;
import app.qomo.apiusers.application.port.out.PasswordHasherPort;
import app.qomo.apiusers.application.port.out.RoleRepositoryPort;
import app.qomo.apiusers.application.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public class SuperAdminBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);
  private static final String SUPERADMIN_ROLE = "SUPERADMIN";
  private static final String SUPERADMIN_USER_ID = "00000000-0000-0000-0000-000000000010";

  private final UserRepositoryPort userRepository;
  private final RoleRepositoryPort roleRepository;
  private final PasswordHasherPort passwordHasher;
  private final ClockPort clock;
  private final String superAdminInitialPassword;
  private final String superAdminEmail;

  public SuperAdminBootstrap(
      UserRepositoryPort userRepository,
      RoleRepositoryPort roleRepository,
      PasswordHasherPort passwordHasher,
      ClockPort clock,
      String superAdminInitialPassword,
      String superAdminEmail) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.passwordHasher = passwordHasher;
    this.clock = clock;
    this.superAdminInitialPassword = superAdminInitialPassword;
    this.superAdminEmail = superAdminEmail;
  }

  @Override
  public void run(ApplicationArguments args) {
    var email = new Email(superAdminEmail);
    var emailFingerprint = PiiUtil.emailFingerprint(email.value());

    if (superAdminInitialPassword == null || superAdminInitialPassword.isBlank()) {
      log.warn(
          "superadmin_bootstrap_skipped reason=missing_initial_password email_fp={}",
          emailFingerprint);
      return;
    }

    if (userRepository.existsByEmail(email.value())) {
      return;
    }

    var now = clock.now();
    var id = UserId.of(SUPERADMIN_USER_ID);
    var hash = passwordHasher.hash(superAdminInitialPassword);

    var user = User.createNew(id, email, hash, now);

    var role =
        roleRepository
            .findByName(SUPERADMIN_ROLE)
            .orElseThrow(() -> new RoleNotFoundException(SUPERADMIN_ROLE));

    user.addRole(role, now);
    user.verify(now);
    userRepository.save(user);

    log.info("superadmin_bootstrapped email_fp={}", emailFingerprint);
  }
}
