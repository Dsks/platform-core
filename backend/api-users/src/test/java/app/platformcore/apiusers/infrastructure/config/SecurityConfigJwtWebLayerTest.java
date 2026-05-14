package app.platformcore.apiusers.infrastructure.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.platformcore.apiusers.application.port.in.CreateUserUseCase;
import app.platformcore.apiusers.application.port.in.DeleteUserUseCase;
import app.platformcore.apiusers.application.port.in.GetCurrentUserUseCase;
import app.platformcore.apiusers.application.port.in.GetUserUseCase;
import app.platformcore.apiusers.application.port.in.LoginUseCase;
import app.platformcore.apiusers.application.port.in.RegisterUserUseCase;
import app.platformcore.apiusers.application.port.in.ResendEmailVerificationUseCase;
import app.platformcore.apiusers.application.port.in.VerifyEmailUseCase;
import app.platformcore.apiusers.application.port.out.ClockPort;
import app.platformcore.apiusers.application.port.out.JwtTokenProviderPort;
import app.platformcore.apiusers.domain.model.Email;
import app.platformcore.apiusers.domain.model.PasswordHash;
import app.platformcore.apiusers.domain.model.Role;
import app.platformcore.apiusers.domain.model.RoleId;
import app.platformcore.apiusers.domain.model.User;
import app.platformcore.apiusers.domain.model.UserId;
import app.platformcore.apiusers.infrastructure.adapter.in.web.ApiExceptionHandler;
import app.platformcore.apiusers.infrastructure.adapter.in.web.AuthController;
import app.platformcore.apiusers.infrastructure.adapter.in.web.UserController;
import app.platformcore.apiusers.infrastructure.adapter.in.web.VerifyEmailController;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = SecurityConfigJwtWebLayerTest.TestApplication.class)
@AutoConfigureMockMvc
class SecurityConfigJwtWebLayerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationContext applicationContext;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean private LoginUseCase loginUseCase;
  @MockitoBean private RegisterUserUseCase registerUserUseCase;
  @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;
  @MockitoBean private CreateUserUseCase createUserUseCase;
  @MockitoBean private GetUserUseCase getUserUseCase;
  @MockitoBean private DeleteUserUseCase deleteUserUseCase;
  @MockitoBean private VerifyEmailUseCase verifyEmailUseCase;
  @MockitoBean private ResendEmailVerificationUseCase resendEmailVerificationUseCase;

  @MockitoBean private JwtTokenProviderPort jwtTokenProviderPort;
  @MockitoBean private ClockPort clockPort;

  @Test
  void csrfTokenRepositoryBeanIsCookieBacked() {
    Assertions.assertInstanceOf(
        CookieCsrfTokenRepository.class, applicationContext.getBean(CsrfTokenRepository.class));
  }

  @Test
  void securityFilterChainComesFromSecurityConfig() {
    var chains = applicationContext.getBeansOfType(SecurityFilterChain.class);

    Assertions.assertEquals(1, chains.size());
    Assertions.assertTrue(chains.containsKey("securityFilterChain"));
  }

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
    verify(getUserUseCase, never()).getByIdForAdmin(any(), any());
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
        .andExpect(
            header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("PLATFORMCORE_AUTH=")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("Max-Age=0")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("Path=/")));
  }

  @Test
  @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
  void logoutRouteAcceptsTokenReturnedByCsrfEndpoint() throws Exception {
    when(clockPort.now()).thenReturn(Instant.parse("2026-03-26T10:15:30Z"));
    when(jwtTokenProviderPort.validate(eq("logout-jwt"), any(Instant.class))).thenReturn(true);
    when(jwtTokenProviderPort.subject("logout-jwt"))
        .thenReturn("33333333-3333-4333-8333-333333333333");
    when(jwtTokenProviderPort.roles("logout-jwt")).thenReturn(Set.of("USER"));

    var csrfResult =
        mockMvc
            .perform(get("/v1/auth/csrf").secure(true))
            .andExpect(status().isOk())
            .andExpect(header().exists(HttpHeaders.SET_COOKIE))
            .andExpect(
                header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("XSRF-TOKEN=")))
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
            .andExpect(jsonPath("$.parameterName").value("_csrf"))
            .andExpect(jsonPath("$.token", Matchers.not(Matchers.isEmptyOrNullString())))
            .andReturn();

    var csrfResponse = csrfResult.getResponse();
    var csrfJson = objectMapper.readTree(csrfResponse.getContentAsString());
    Cookie csrfCookie = csrfResponse.getCookie("XSRF-TOKEN");
    var session = csrfResult.getRequest().getSession(false);

    Assertions.assertNotNull(csrfCookie);
    if (session != null) {
      Assertions.assertNull(
          session.getAttribute(
              "org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN"));
    }

    Cookie postCsrfCookie = new Cookie("XSRF-TOKEN", csrfJson.get("token").asText());
    postCsrfCookie.setPath("/");
    postCsrfCookie.setSecure(csrfCookie.getSecure());

    Cookie authCookie = new Cookie(AuthController.AUTH_COOKIE_NAME, "logout-jwt");
    authCookie.setPath("/");
    authCookie.setHttpOnly(true);
    authCookie.setSecure(true);

    var logoutResponse =
        mockMvc
            .perform(
                post("/v1/auth/logout")
                    .secure(true)
                    .header(csrfJson.get("headerName").asText(), csrfJson.get("token").asText())
                    .cookie(postCsrfCookie, authCookie))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();

    var setCookies = logoutResponse.getHeaders(HttpHeaders.SET_COOKIE);

    Assertions.assertTrue(
        setCookies.stream()
            .anyMatch(
                cookie ->
                    cookie.startsWith("PLATFORMCORE_AUTH=")
                        && cookie.contains("Max-Age=0")
                        && cookie.contains("Path=/")));

    Assertions.assertTrue(
        setCookies.stream()
            .anyMatch(
                cookie ->
                    cookie.startsWith("XSRF-TOKEN=")
                        && cookie.contains("Max-Age=0")
                        && cookie.contains("Path=/")));

    verify(jwtTokenProviderPort).validate(eq("logout-jwt"), any(Instant.class));
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

    verify(getUserUseCase, never()).getByIdForAdmin(any(), any());
  }

  @Test
  void protectedUserRouteForbidsAuthenticatedUserRole() throws Exception {
    when(clockPort.now()).thenReturn(Instant.parse("2026-03-26T10:15:30Z"));
    when(jwtTokenProviderPort.validate(eq("user-jwt"), any(Instant.class))).thenReturn(true);
    when(jwtTokenProviderPort.subject("user-jwt"))
        .thenReturn("8ad3b073-0d96-4f2e-b9e7-c93fa89075bf");
    when(jwtTokenProviderPort.roles("user-jwt")).thenReturn(java.util.Set.of("USER"));

    mockMvc
        .perform(
            get("/v1/users/2fa8b8e9-3090-404e-a6e8-d95dd8e3b0ec")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "user-jwt")))
        .andExpect(status().isForbidden());

    verify(getUserUseCase, never()).getByIdForAdmin(any(), any());
  }

  @Test
  void protectedUserRouteAllowsAdminWithoutCsrfAndReturnsNotFoundWhenMissing() throws Exception {
    when(clockPort.now()).thenReturn(Instant.parse("2026-03-26T10:15:30Z"));
    when(jwtTokenProviderPort.validate(eq("admin-read-jwt"), any(Instant.class))).thenReturn(true);
    when(jwtTokenProviderPort.subject("admin-read-jwt"))
        .thenReturn("8ad3b073-0d96-4f2e-b9e7-c93fa89075bf");
    when(jwtTokenProviderPort.roles("admin-read-jwt")).thenReturn(java.util.Set.of("ADMIN"));
    when(getUserUseCase.getByIdForAdmin(any(), any())).thenReturn(Optional.empty());

    mockMvc
        .perform(
            get("/v1/users/2fa8b8e9-3090-404e-a6e8-d95dd8e3b0ec")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-read-jwt")))
        .andExpect(status().isNotFound());

    verify(getUserUseCase)
        .getByIdForAdmin(UserId.of("2fa8b8e9-3090-404e-a6e8-d95dd8e3b0ec"), Set.of("ADMIN"));
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
                          "email": "new-user@platformcore.app",
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
                          "email": "new-admin-created-user@platformcore.app",
                          "password": "StrongPass123!",
                          "roles": ["USER"]
                        }
                        """)
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
        .andExpect(status().isAccepted());
  }

  @Test
  void updateUserEndpointForbidsAnonymousRequest() throws Exception {
    mockMvc
        .perform(
            patch("/v1/users/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isForbidden());

    verify(getUserUseCase, never()).update(any());
  }

  @Test
  void updateUserEndpointForbidsAuthenticatedUserRole() throws Exception {
    authenticateAs("patch-user-jwt", "USER");

    mockMvc
        .perform(
            patch("/v1/users/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "patch-user-jwt")))
        .andExpect(status().isForbidden());

    verify(getUserUseCase, never()).update(any());
  }

  @Test
  void updateUserEndpointRequiresCsrfForAdmin() throws Exception {
    authenticateAs("patch-admin-missing-csrf-jwt", "ADMIN");

    mockMvc
        .perform(
            patch("/v1/users/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")
                .cookie(
                    new Cookie(AuthController.AUTH_COOKIE_NAME, "patch-admin-missing-csrf-jwt")))
        .andExpect(status().isForbidden());

    verify(getUserUseCase, never()).update(any());
  }

  @Test
  void updateUserEndpointAllowsAdminAndReturnsSafeUserResponse() throws Exception {
    authenticateAs("patch-admin-jwt", "ADMIN");
    when(getUserUseCase.update(any()))
        .thenReturn(
            user(
                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                false,
                Role.user(roleId("99999999-9999-4999-8999-999999999999"))));

    mockMvc
        .perform(
            patch("/v1/users/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "patch-admin-jwt")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))
        .andExpect(jsonPath("$.isActive").value(false))
        .andExpect(jsonPath("$.roles", Matchers.containsInAnyOrder("USER")))
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.token").doesNotExist())
        .andExpect(jsonPath("$.tokens").doesNotExist())
        .andExpect(jsonPath("$.secret").doesNotExist())
        .andExpect(jsonPath("$.secrets").doesNotExist());
  }

  @Test
  void deleteUserEndpointForbidsAnonymousRequest() throws Exception {
    mockMvc
        .perform(delete("/v1/users/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa").with(csrf()))
        .andExpect(status().isForbidden());

    verify(deleteUserUseCase, never()).delete(any());
  }

  @Test
  void deleteUserEndpointForbidsAuthenticatedUserRole() throws Exception {
    authenticateAs("delete-user-jwt", "USER");

    mockMvc
        .perform(
            delete("/v1/users/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .with(csrf())
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "delete-user-jwt")))
        .andExpect(status().isForbidden());

    verify(deleteUserUseCase, never()).delete(any());
  }

  @Test
  void deleteUserEndpointAllowsAdminWithCsrf() throws Exception {
    authenticateAs("delete-admin-jwt", "ADMIN");

    mockMvc
        .perform(
            delete("/v1/users/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .with(csrf())
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "delete-admin-jwt")))
        .andExpect(status().isNoContent());

    verify(deleteUserUseCase)
        .delete(
            new DeleteUserUseCase.Command(
                UserId.of("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                UserId.of("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                Set.of("ADMIN")));
  }

  @Test
  void deleteUserEndpointRequiresCsrfForAdmin() throws Exception {
    authenticateAs("delete-admin-missing-csrf-jwt", "ADMIN");

    mockMvc
        .perform(
            delete("/v1/users/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .cookie(
                    new Cookie(AuthController.AUTH_COOKIE_NAME, "delete-admin-missing-csrf-jwt")))
        .andExpect(status().isForbidden());

    verify(deleteUserUseCase, never()).delete(any());
  }

  private void authenticateAs(String token, String role) {
    when(clockPort.now()).thenReturn(Instant.parse("2026-03-26T10:15:30Z"));
    when(jwtTokenProviderPort.validate(eq(token), any(Instant.class))).thenReturn(true);
    when(jwtTokenProviderPort.subject(token)).thenReturn("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    when(jwtTokenProviderPort.roles(token)).thenReturn(Set.of(role));
  }

  private User user(String id, boolean active, Role role) {
    return User.restore(
        UserId.of(id),
        Email.of("target@example.com"),
        new PasswordHash("hashed-password"),
        active,
        true,
        null,
        Instant.parse("2026-03-25T12:30:00Z"),
        Instant.parse("2026-03-26T12:30:00Z"),
        null,
        Set.of(role));
  }

  private RoleId roleId(String raw) {
    return new RoleId(UUID.fromString(raw));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
      })
  @Import({
    SecurityConfig.class,
    ApiExceptionHandler.class,
    AuthController.class,
    UserController.class,
    VerifyEmailController.class
  })
  static class TestApplication {}
}
