package app.platformcore.apiusers.infrastructure.config;

import app.platformcore.apiusers.application.exception.RoleNotFoundException;
import app.platformcore.apiusers.application.observability.PiiUtil;
import app.platformcore.apiusers.application.port.out.ClockPort;
import app.platformcore.apiusers.application.port.out.PasswordHasherPort;
import app.platformcore.apiusers.application.port.out.RoleRepositoryPort;
import app.platformcore.apiusers.application.port.out.UserRepositoryPort;
import app.platformcore.apiusers.domain.model.Email;
import app.platformcore.apiusers.domain.model.User;
import app.platformcore.apiusers.domain.model.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Startup runner that optionally provisions the first SUPERADMIN account through application
 * outbound ports.
 *
 * <p>The runner connects the bootstrap properties provided by {@link UsersBeansConfig} to user
 * persistence, role lookup, password hashing, and clock access. The initial password is sensitive
 * bootstrap material: when it is absent or blank, provisioning is skipped instead of creating an
 * unusable or weak account. This component only handles the seed account; ongoing administrator
 * management remains in the application use cases and HTTP adapters.
 */
public class SuperAdminBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);
  private static final String SUPERADMIN_ROLE = "SUPERADMIN";
  private static final String SUPERADMIN_USER_ID = "00000000-0000-4000-8000-00000000001";

  private final UserRepositoryPort userRepository;
  private final RoleRepositoryPort roleRepository;
  private final PasswordHasherPort passwordHasher;
  private final ClockPort clock;
  private final String superAdminInitialPassword;
  private final String superAdminEmail;

  /**
   * Receives the ports and externally supplied bootstrap values required for startup provisioning.
   *
   * @param userRepository user persistence port used to detect and save the bootstrap account
   * @param roleRepository role lookup port used to attach the SUPERADMIN role
   * @param passwordHasher outbound port used to hash the sensitive initial password
   * @param clock shared time port for account creation and verification timestamps
   * @param superAdminInitialPassword sensitive value of {@code
   *     platformcore.bootstrap.superadmin.initial-password}; blank disables provisioning
   * @param superAdminEmail value of {@code platformcore.bootstrap.superadmin.email}
   */
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

  /**
   * Creates and verifies the seed SUPERADMIN account when configured and not already present.
   *
   * <p>Logs use the email fingerprint rather than the raw address so startup diagnostics avoid
   * exposing personally identifiable information.
   */
  @Override
  public void run(ApplicationArguments args) {
    var email = new Email(superAdminEmail);
    var emailFingerprint = PiiUtil.emailFingerprint(email.value());

    // A blank bootstrap password disables privileged account creation by configuration.
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
