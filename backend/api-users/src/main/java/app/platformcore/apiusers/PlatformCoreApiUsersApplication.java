package app.platformcore.apiusers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point for the api-users service.
 *
 * <p>This service owns the user-facing identity and account lifecycle concerns within PlatformCore.
 * Runtime wiring is delegated to the module's configuration, adapters, and application services, so
 * this class should remain minimal and free of business logic.
 */
@SpringBootApplication
@EnableScheduling
public class PlatformCoreApiUsersApplication {

  /**
   * Starts the Spring Boot application context.
   *
   * <p>Startup loads configuration, beans, the embedded web server, and scheduled jobs according to
   * the active profiles and properties.
   *
   * @param args command-line arguments forwarded to Spring Boot
   */
  public static void main(String[] args) {
    SpringApplication.run(PlatformCoreApiUsersApplication.class, args);
  }
}
