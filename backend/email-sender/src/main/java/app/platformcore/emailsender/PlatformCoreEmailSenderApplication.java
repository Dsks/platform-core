package app.platformcore.emailsender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point for the email-sender service.
 *
 * <p>The service consumes email commands and events, then coordinates their processing through the
 * module's configured adapters, application services, and infrastructure wiring. Keep this class
 * minimal: startup concerns belong here, while business behavior should remain in the dedicated
 * services and adapters.
 */
@SpringBootApplication
@EnableScheduling
public class PlatformCoreEmailSenderApplication {

  /**
   * Starts the Spring Boot context, loading configuration, beans, the web server, listeners, and
   * scheduled jobs according to the active profiles and properties.
   *
   * @param args command-line arguments passed to Spring Boot
   */
  public static void main(String[] args) {
    SpringApplication.run(PlatformCoreEmailSenderApplication.class, args);
  }
}
