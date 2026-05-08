package app.qomo.apiusers.infrastructure.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.qomo.apiusers.application.port.in.CreateUserUseCase;
import app.qomo.apiusers.application.port.in.GetCurrentUserUseCase;
import app.qomo.apiusers.application.port.in.GetUserUseCase;
import app.qomo.apiusers.application.port.in.LoginUseCase;
import app.qomo.apiusers.application.port.in.RegisterUserUseCase;
import app.qomo.apiusers.application.port.in.ResendEmailVerificationUseCase;
import app.qomo.apiusers.application.port.in.VerifyEmailUseCase;
import app.qomo.apiusers.application.port.out.ClockPort;
import app.qomo.apiusers.application.port.out.JwtTokenProviderPort;
import app.qomo.apiusers.domain.model.UserId;
import app.qomo.apiusers.infrastructure.adapter.in.web.AuthController;
import app.qomo.apiusers.infrastructure.adapter.in.web.UserController;
import app.qomo.apiusers.infrastructure.adapter.in.web.VerifyEmailController;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AuthController.class, UserController.class, VerifyEmailController.class})
@Import(SecurityConfig.class)
class SecurityConfigJwtWebLayerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private LoginUseCase loginUseCase;
  @MockitoBean private RegisterUserUseCase registerUserUseCase;
  @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;
  @MockitoBean private CreateUserUseCase createUserUseCase;
  @MockitoBean private GetUserUseCase getUserUseCase;
  @MockitoBean private VerifyEmailUseCase verifyEmailUseCase;
  @MockitoBean private ResendEmailVerificationUseCase resendEmailVerificationUseCase;

  @MockitoBean private JwtTokenProviderPort jwtTokenProviderPort;
  @MockitoBean private ClockPort clockPort;

  @Test
  void csrfEndpointIsPublic() throws Exception {
    mockMvc.perform(get("/v1/auth/csrf")).andExpect(status().isOk());
  }

  @Test
  void loginRouteDoesNotRequireCsrf() throws Exception {
    mockMvc
        .perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void protectedUserRouteReturnsForbiddenWhenJwtCookieIsMissing() throws Exception {
    mockMvc
        .perform(get("/v1/users/2fa8b8e9-3090-404e-a6e8-d95dd8e3b0ec"))
        .andExpect(status().isForbidden());
    verify(getUserUseCase, never()).getById(any());
  }

  @Test
  void currentUserRouteReturnsForbiddenWhenJwtCookieIsMissing() throws Exception {
    mockMvc.perform(get("/v1/auth/me")).andExpect(status().isForbidden());

    verify(getCurrentUserUseCase, never()).getCurrentUser();
  }

  @Test
  void logoutRouteReturnsForbiddenWhenJwtCookieIsMissing() throws Exception {
    mockMvc.perform(post("/v1/auth/logout").with(csrf())).andExpect(status().isForbidden());
  }

  @Test
  void logoutRouteReturnsForbiddenWhenCsrfTokenIsMissing() throws Exception {
    when(clockPort.now()).thenReturn(Instant.parse("2026-03-26T10:15:30Z"));
    when(jwtTokenProviderPort.validate(eq("logout-jwt"), any(Instant.class))).thenReturn(true);
    when(jwtTokenProviderPort.subject("logout-jwt"))
        .thenReturn("33333333-3333-4333-8333-333333333333");
    when(jwtTokenProviderPort.roles("logout-jwt")).thenReturn(Set.of("USER"));

    mockMvc
        .perform(
            post("/v1/auth/logout")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "logout-jwt")))
        .andExpect(status().isForbidden());
  }

  @Test
  void logoutRouteAllowsAuthenticatedUserWithCsrfAndExpiresAuthCookie() throws Exception {
    when(clockPort.now()).thenReturn(Instant.parse("2026-03-26T10:15:30Z"));
    when(jwtTokenProviderPort.validate(eq("logout-jwt"), any(Instant.class))).thenReturn(true);
    when(jwtTokenProviderPort.subject("logout-jwt"))
        .thenReturn("33333333-3333-4333-8333-333333333333");
    when(jwtTokenProviderPort.roles("logout-jwt")).thenReturn(Set.of("USER"));

    mockMvc
        .perform(
            post("/v1/auth/logout")
                .with(csrf())
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "logout-jwt")))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("QOMO_AUTH=")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("Max-Age=0")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("Path=/")));
  }

  @Test
  void logoutRouteAcceptsTokenReturnedByCsrfEndpoint() throws Exception {
    when(clockPort.now()).thenReturn(Instant.parse("2026-03-26T10:15:30Z"));
    when(jwtTokenProviderPort.validate(eq("logout-jwt"), any(Instant.class))).thenReturn(true);
    when(jwtTokenProviderPort.subject("logout-jwt"))
        .thenReturn("33333333-3333-4333-8333-333333333333");
    when(jwtTokenProviderPort.roles("logout-jwt")).thenReturn(Set.of("USER"));

    var csrfResponse =
        mockMvc
            .perform(get("/v1/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(header().exists(HttpHeaders.SET_COOKIE))
            .andReturn()
            .getResponse();
    var csrfJson = objectMapper.readTree(csrfResponse.getContentAsString());
    Cookie csrfCookie = csrfResponse.getCookie("XSRF-TOKEN");

    Assertions.assertNotNull(csrfCookie);

    mockMvc
        .perform(
            post("/v1/auth/logout")
                .header(csrfJson.get("headerName").asText(), csrfJson.get("token").asText())
                .cookie(csrfCookie)
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "logout-jwt")))
        .andExpect(status().isNoContent())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("QOMO_AUTH=")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("Max-Age=0")));
  }

  @Test
  void currentUserRouteAllowsAuthenticatedUserRoleWithoutCsrf() throws Exception {
    when(clockPort.now()).thenReturn(Instant.parse("2026-03-26T10:15:30Z"));
    when(jwtTokenProviderPort.validate(eq("current-user-jwt"), any(Instant.class)))
        .thenReturn(true);
    when(jwtTokenProviderPort.subject("current-user-jwt"))
        .thenReturn("11111111-1111-4111-8111-111111111111");
    when(jwtTokenProviderPort.roles("current-user-jwt")).thenReturn(Set.of("USER"));
    when(getCurrentUserUseCase.getCurrentUser())
        .thenReturn(
            new GetCurrentUserUseCase.Result(
                "11111111-1111-4111-8111-111111111111",
                "current.user@example.com",
                true,
                true,
                Set.of("USER")));

    mockMvc
        .perform(
            get("/v1/auth/me")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "current-user-jwt")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("11111111-1111-4111-8111-111111111111"))
        .andExpect(jsonPath("$.email").value("current.user@example.com"))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.emailVerified").value(true))
        .andExpect(jsonPath("$.roles", Matchers.containsInAnyOrder("USER")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ADMIN", "SUPERADMIN"})
  void currentUserRouteAllowsAdministrativeRolesWithoutCsrf(String role) throws Exception {
    String token = role.toLowerCase(java.util.Locale.ROOT) + "-me-jwt";
    when(clockPort.now()).thenReturn(Instant.parse("2026-03-26T10:15:30Z"));
    when(jwtTokenProviderPort.validate(eq(token), any(Instant.class))).thenReturn(true);
    when(jwtTokenProviderPort.subject(token)).thenReturn("22222222-2222-4222-8222-222222222222");
    when(jwtTokenProviderPort.roles(token)).thenReturn(Set.of(role));
    when(getCurrentUserUseCase.getCurrentUser())
        .thenReturn(
            new GetCurrentUserUseCase.Result(
                "22222222-2222-4222-8222-222222222222",
                "admin.user@example.com",
                true,
                true,
                Set.of(role)));

    mockMvc
        .perform(get("/v1/auth/me").cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles", Matchers.containsInAnyOrder(role)));
  }

  @Test
  void protectedUserRouteReturnsForbiddenWhenJwtCookieIsInvalid() throws Exception {
    when(clockPort.now()).thenReturn(Instant.parse("2026-03-26T10:15:30Z"));
    when(jwtTokenProviderPort.validate(eq("invalid-jwt"), any(Instant.class))).thenReturn(false);

    mockMvc
        .perform(
            get("/v1/users/2fa8b8e9-3090-404e-a6e8-d95dd8e3b0ec")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "invalid-jwt")))
        .andExpect(status().isForbidden());

    verify(getUserUseCase, never()).getById(any());
  }

  @Test
  void protectedUserRouteAllowsAccessWhenJwtCookieIsValid() throws Exception {
    when(clockPort.now()).thenReturn(Instant.parse("2026-03-26T10:15:30Z"));
    when(jwtTokenProviderPort.validate(eq("valid-jwt"), any(Instant.class))).thenReturn(true);
    when(jwtTokenProviderPort.subject("valid-jwt"))
        .thenReturn("8ad3b073-0d96-4f2e-b9e7-c93fa89075bf");
    when(jwtTokenProviderPort.roles("valid-jwt")).thenReturn(java.util.Set.of("USER"));
    when(getUserUseCase.getById(any())).thenReturn(Optional.empty());

    mockMvc
        .perform(
            get("/v1/users/2fa8b8e9-3090-404e-a6e8-d95dd8e3b0ec")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "valid-jwt")))
        .andExpect(status().isNotFound());
  }

  @Test
  void createUserEndpointForbidsAuthenticatedUserWithoutAdminRole() throws Exception {
    when(clockPort.now()).thenReturn(Instant.parse("2026-03-26T10:15:30Z"));
    when(jwtTokenProviderPort.validate(eq("user-jwt"), any(Instant.class))).thenReturn(true);
    when(jwtTokenProviderPort.subject("user-jwt"))
        .thenReturn("bce24716-5337-4713-b8f1-8cab7c4f8a0f");
    when(jwtTokenProviderPort.roles("user-jwt")).thenReturn(java.util.Set.of("USER"));

    mockMvc
        .perform(
            post("/v1/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "email": "new-user@qomo.app",
                          "password": "StrongPass123!",
                          "roles": ["USER"]
                        }
                        """)
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "user-jwt")))
        .andExpect(status().isForbidden());
  }

  @Test
  void createUserEndpointAllowsAuthenticatedAdminRole() throws Exception {
    when(clockPort.now()).thenReturn(Instant.parse("2026-03-26T10:15:30Z"));
    when(jwtTokenProviderPort.validate(eq("admin-jwt"), any(Instant.class))).thenReturn(true);
    when(jwtTokenProviderPort.subject("admin-jwt"))
        .thenReturn("5a9ddd91-9220-4ac8-9157-8563facc4ef3");
    when(jwtTokenProviderPort.roles("admin-jwt")).thenReturn(java.util.Set.of("ADMIN"));
    when(createUserUseCase.create(any())).thenReturn(new CreateUserUseCase.Result(UserId.newId()));

    mockMvc
        .perform(
            post("/v1/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "email": "new-admin-created-user@qomo.app",
                          "password": "StrongPass123!",
                          "roles": ["USER"]
                        }
                        """)
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
        .andExpect(status().isAccepted());
  }
}
