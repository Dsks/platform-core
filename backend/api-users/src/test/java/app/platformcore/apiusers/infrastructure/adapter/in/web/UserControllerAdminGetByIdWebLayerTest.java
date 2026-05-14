package app.platformcore.apiusers.infrastructure.adapter.in.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.platformcore.apiusers.application.exception.ForbiddenOperationException;
import app.platformcore.apiusers.application.port.in.CreateUserUseCase;
import app.platformcore.apiusers.application.port.in.DeleteUserUseCase;
import app.platformcore.apiusers.application.port.in.GetUserUseCase;
import app.platformcore.apiusers.application.port.out.ClockPort;
import app.platformcore.apiusers.application.port.out.JwtTokenProviderPort;
import app.platformcore.apiusers.domain.model.Email;
import app.platformcore.apiusers.domain.model.PasswordHash;
import app.platformcore.apiusers.domain.model.Role;
import app.platformcore.apiusers.domain.model.RoleId;
import app.platformcore.apiusers.domain.model.User;
import app.platformcore.apiusers.domain.model.UserId;
import app.platformcore.apiusers.infrastructure.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class UserControllerAdminGetByIdWebLayerTest {

  private static final Instant NOW = Instant.parse("2026-03-26T10:15:30Z");
  private static final String TARGET_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CreateUserUseCase createUserUseCase;

  @MockitoBean private GetUserUseCase getUserUseCase;

  @MockitoBean private DeleteUserUseCase deleteUserUseCase;

  @MockitoBean private JwtTokenProviderPort jwtTokenProviderPort;

  @MockitoBean private ClockPort clockPort;

  @Test
  void getByIdForAdmin_shouldForbidAuthenticatedUserRole() throws Exception {
    mockJwt("user-jwt", "USER");

    mockMvc
        .perform(
            get("/v1/users/" + TARGET_ID)
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "user-jwt")))
        .andExpect(status().isForbidden());

    verify(getUserUseCase, never()).getByIdForAdmin(any(), any());
  }

  @Test
  void getByIdForAdmin_shouldAllowAdminAndReturnSafeUserResponse() throws Exception {
    mockJwt("admin-jwt", "ADMIN");
    when(getUserUseCase.getByIdForAdmin(any(), any()))
        .thenReturn(
            Optional.of(
                user(TARGET_ID, Role.user(roleId("11111111-1111-4111-8111-111111111111")))));

    var response =
        mockMvc
            .perform(
                get("/v1/users/" + TARGET_ID)
                    .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(TARGET_ID))
            .andExpect(jsonPath("$.email").value("target@example.com"))
            .andExpect(jsonPath("$.isActive").value(true))
            .andExpect(jsonPath("$.isVerified").value(true))
            .andExpect(jsonPath("$.roles", containsInAnyOrder("USER")))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.token").doesNotExist())
            .andReturn()
            .getResponse();

    String body = response.getContentAsString();
    assertFalse(body.contains("password"));
    assertFalse(body.contains("passwordHash"));
    verify(getUserUseCase).getByIdForAdmin(eq(UserId.of(TARGET_ID)), eq(Set.of("ADMIN")));
  }

  @Test
  void getByIdForAdmin_shouldAllowSuperAdminAndPassRoles() throws Exception {
    mockJwt("superadmin-jwt", "SUPERADMIN");
    when(getUserUseCase.getByIdForAdmin(any(), any()))
        .thenReturn(
            Optional.of(
                user(
                    TARGET_ID,
                    Role.of(roleId("22222222-2222-4222-8222-222222222222"), "SUPERADMIN"))));

    mockMvc
        .perform(
            get("/v1/users/" + TARGET_ID)
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "superadmin-jwt")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles", containsInAnyOrder("SUPERADMIN")));

    verify(getUserUseCase).getByIdForAdmin(eq(UserId.of(TARGET_ID)), eq(Set.of("SUPERADMIN")));
  }

  @Test
  void getByIdForAdmin_shouldReturnNotFoundWhenUseCaseReturnsEmpty() throws Exception {
    mockJwt("admin-jwt", "ADMIN");
    when(getUserUseCase.getByIdForAdmin(any(), any())).thenReturn(Optional.empty());

    mockMvc
        .perform(
            get("/v1/users/" + TARGET_ID)
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
        .andExpect(status().isNotFound());
  }

  @Test
  void getByIdForAdmin_shouldReturnForbiddenWhenUseCaseRejectsTarget() throws Exception {
    mockJwt("admin-jwt", "ADMIN");
    when(getUserUseCase.getByIdForAdmin(any(), any()))
        .thenThrow(new ForbiddenOperationException("Actor cannot read the target user"));

    mockMvc
        .perform(
            get("/v1/users/" + TARGET_ID)
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("FORBIDDEN_OPERATION"));
  }

  @Test
  void getByIdForAdmin_shouldRejectInvalidUuid() throws Exception {
    mockJwt("admin-jwt", "ADMIN");

    mockMvc
        .perform(
            get("/v1/users/not-a-uuid")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("VALIDATION_ERROR"));

    verify(getUserUseCase, never()).getByIdForAdmin(any(), any());
  }

  private void mockJwt(String token, String role) {
    when(clockPort.now()).thenReturn(NOW);
    when(jwtTokenProviderPort.validate(eq(token), any(Instant.class))).thenReturn(true);
    when(jwtTokenProviderPort.subject(token)).thenReturn("11111111-1111-4111-8111-111111111111");
    when(jwtTokenProviderPort.roles(token)).thenReturn(Set.of(role));
  }

  private User user(String id, Role role) {
    return User.restore(
        UserId.of(id),
        Email.of("target@example.com"),
        new PasswordHash("hashed-password"),
        true,
        true,
        null,
        NOW,
        NOW,
        null,
        Set.of(role));
  }

  private RoleId roleId(String raw) {
    return new RoleId(UUID.fromString(raw));
  }
}
