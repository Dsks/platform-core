package app.platformcore.apiusers.infrastructure.config;

import app.platformcore.apiusers.infrastructure.adapter.in.web.AuthController;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the base OpenAPI document metadata and reusable authentication schemes. */
@Configuration
public class OpenApiConfig {

  public static final String PLATFORMCORE_AUTH_COOKIE_SCHEME = "platformcoreAuthCookie";

  @Bean
  public OpenAPI platformcoreUsersOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("PlatformCore Users API")
                .description(
                    "API for authentication, email verification and user management in PlatformCore")
                .version("0.0.1-SNAPSHOT"))
        .components(
            new Components()
                .addSecuritySchemes(
                    PLATFORMCORE_AUTH_COOKIE_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name(AuthController.AUTH_COOKIE_NAME)));
  }
}
