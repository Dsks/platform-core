package app.platformcore.apiusers.infrastructure.adapter.in.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.platformcore.apiusers.application.port.in.CreateUserUseCase;
import app.platformcore.apiusers.application.port.in.DeleteUserUseCase;
import app.platformcore.apiusers.application.port.in.GetUserUseCase;
import app.platformcore.apiusers.application.port.in.GetUserUseCase.DeletedFilter;
import app.platformcore.apiusers.application.port.in.GetUserUseCase.SortBy;
import app.platformcore.apiusers.application.port.in.GetUserUseCase.SortDirection;
import app.platformcore.apiusers.application.port.out.ClockPort;
import app.platformcore.apiusers.application.port.out.JwtTokenProviderPort;
import app.platformcore.apiusers.infrastructure.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class UserControllerAdminListWebLayerTest {

  private static final Instant NOW = Instant.parse("2026-03-26T10:15:30Z");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CreateUserUseCase createUserUseCase;

  @MockitoBean private GetUserUseCase getUserUseCase;

  @MockitoBean private DeleteUserUseCase deleteUserUseCase;

  @MockitoBean private JwtTokenProviderPort jwtTokenProviderPort;

  @MockitoBean private ClockPort clockPort;

  @Test
  void listForAdmin_shouldForbidAnonymousRequests() throws Exception {
    mockMvc.perform(get("/v1/users")).andExpect(status().isForbidden());

    verify(getUserUseCase, never()).listForAdmin(any(), any());
  }

  @Test
  void listForAdmin_shouldForbidAuthenticatedUserRole() throws Exception {
    mockJwt("user-jwt", "USER");

    mockMvc
        .perform(get("/v1/users").cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "user-jwt")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("FORBIDDEN_OPERATION"));

    verify(getUserUseCase, never()).listForAdmin(any(), any());
  }

  @Test
  void listForAdmin_shouldAllowAdminAndReturnSafePagedResponse() throws Exception {
    mockJwt("admin-jwt", "ADMIN");
    when(getUserUseCase.listForAdmin(any(), any()))
        .thenReturn(
            new GetUserUseCase.PageResult(
                List.of(
                    new GetUserUseCase.UserSummary(
                        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                        "admin@example.com",
                        true,
                        true,
                        Set.of("ADMIN"),
                        NOW,
                        NOW,
                        NOW,
                        null)),
                1,
                2,
                3,
                2));

    var response =
        mockMvc
            .perform(
                get("/v1/users?page=1&size=2")
                    .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.content[0].id").value("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))
            .andExpect(jsonPath("$.content[0].email").value("admin@example.com"))
            .andExpect(jsonPath("$.content[0].active").value(true))
            .andExpect(jsonPath("$.content[0].emailVerified").value(true))
            .andExpect(jsonPath("$.content[0].roles", containsInAnyOrder("ADMIN")))
            .andExpect(jsonPath("$.content[0].deletedAt").doesNotExist())
            .andExpect(jsonPath("$.content[0].password").doesNotExist())
            .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
            .andReturn()
            .getResponse();

    String body = response.getContentAsString();
    assertFalse(body.contains("password"));
    assertFalse(body.contains("passwordHash"));
    assertFalse(body.contains("deleted_at"));
    assertFalse(body.toLowerCase(java.util.Locale.ROOT).contains("secret"));

    ArgumentCaptor<GetUserUseCase.Query> queryCaptor =
        ArgumentCaptor.forClass(GetUserUseCase.Query.class);
    verify(getUserUseCase).listForAdmin(queryCaptor.capture(), eq(Set.of("ADMIN")));
    assertEquals(1, queryCaptor.getValue().page());
    assertEquals(2, queryCaptor.getValue().size());
    assertNull(queryCaptor.getValue().search());
    assertEquals(DeletedFilter.EXCLUDE, queryCaptor.getValue().deleted());
    assertNull(queryCaptor.getValue().active());
    assertNull(queryCaptor.getValue().verified());
    assertNull(queryCaptor.getValue().role());
    assertEquals(SortBy.CREATED_AT, queryCaptor.getValue().sortBy());
    assertEquals(SortDirection.DESC, queryCaptor.getValue().sortDirection());
  }

  @Test
  void listForAdmin_shouldPassSearchAndFilterQueryParameters() throws Exception {
    mockJwt("admin-jwt", "ADMIN");
    when(getUserUseCase.listForAdmin(any(), any()))
        .thenReturn(new GetUserUseCase.PageResult(List.of(), 0, 20, 0, 0));

    mockMvc
        .perform(
            get("/v1/users?page=0&size=20&search=Foo&deleted=true&active=false&verified=true")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
        .andExpect(status().isOk());

    ArgumentCaptor<GetUserUseCase.Query> queryCaptor =
        ArgumentCaptor.forClass(GetUserUseCase.Query.class);
    verify(getUserUseCase).listForAdmin(queryCaptor.capture(), eq(Set.of("ADMIN")));
    assertEquals(0, queryCaptor.getValue().page());
    assertEquals(20, queryCaptor.getValue().size());
    assertEquals("Foo", queryCaptor.getValue().search());
    assertEquals(DeletedFilter.ONLY, queryCaptor.getValue().deleted());
    assertEquals(Boolean.FALSE, queryCaptor.getValue().active());
    assertEquals(Boolean.TRUE, queryCaptor.getValue().verified());
    assertNull(queryCaptor.getValue().role());
    assertEquals(SortBy.CREATED_AT, queryCaptor.getValue().sortBy());
    assertEquals(SortDirection.DESC, queryCaptor.getValue().sortDirection());
  }

  @Test
  void listForAdmin_shouldTranslateDeletedAllQueryParameter() throws Exception {
    mockJwt("admin-jwt", "ADMIN");
    when(getUserUseCase.listForAdmin(any(), any()))
        .thenReturn(new GetUserUseCase.PageResult(List.of(), 0, 20, 0, 0));

    mockMvc
        .perform(
            get("/v1/users?deleted=all")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
        .andExpect(status().isOk());

    ArgumentCaptor<GetUserUseCase.Query> queryCaptor =
        ArgumentCaptor.forClass(GetUserUseCase.Query.class);
    verify(getUserUseCase).listForAdmin(queryCaptor.capture(), eq(Set.of("ADMIN")));
    assertEquals(DeletedFilter.ALL, queryCaptor.getValue().deleted());
    assertNull(queryCaptor.getValue().active());
    assertNull(queryCaptor.getValue().verified());
    assertNull(queryCaptor.getValue().role());
  }

  @Test
  void listForAdmin_shouldPassRoleQueryParameter() throws Exception {
    mockJwt("admin-jwt", "ADMIN");
    when(getUserUseCase.listForAdmin(any(), any()))
        .thenReturn(new GetUserUseCase.PageResult(List.of(), 0, 20, 0, 0));

    mockMvc
        .perform(
            get("/v1/users?role=USER")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
        .andExpect(status().isOk());

    ArgumentCaptor<GetUserUseCase.Query> queryCaptor =
        ArgumentCaptor.forClass(GetUserUseCase.Query.class);
    verify(getUserUseCase).listForAdmin(queryCaptor.capture(), eq(Set.of("ADMIN")));
    assertEquals("USER", queryCaptor.getValue().role());
  }

  @Test
  void listForAdmin_shouldPassSortQueryParameters() throws Exception {
    mockJwt("admin-jwt", "ADMIN");
    when(getUserUseCase.listForAdmin(any(), any()))
        .thenReturn(new GetUserUseCase.PageResult(List.of(), 0, 20, 0, 0));

    mockMvc
        .perform(
            get("/v1/users?sortBy=email&sortDirection=asc")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
        .andExpect(status().isOk());

    ArgumentCaptor<GetUserUseCase.Query> queryCaptor =
        ArgumentCaptor.forClass(GetUserUseCase.Query.class);
    verify(getUserUseCase).listForAdmin(queryCaptor.capture(), eq(Set.of("ADMIN")));
    assertEquals(SortBy.EMAIL, queryCaptor.getValue().sortBy());
    assertEquals(SortDirection.ASC, queryCaptor.getValue().sortDirection());
  }

  @Test
  void listForAdmin_shouldNormalizeSortQueryParameters() throws Exception {
    mockJwt("admin-jwt", "ADMIN");
    when(getUserUseCase.listForAdmin(any(), any()))
        .thenReturn(new GetUserUseCase.PageResult(List.of(), 0, 20, 0, 0));

    mockMvc
        .perform(
            get("/v1/users?sortBy=LASTLOGINAT&sortDirection=DESC")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
        .andExpect(status().isOk());

    ArgumentCaptor<GetUserUseCase.Query> queryCaptor =
        ArgumentCaptor.forClass(GetUserUseCase.Query.class);
    verify(getUserUseCase).listForAdmin(queryCaptor.capture(), eq(Set.of("ADMIN")));
    assertEquals(SortBy.LAST_LOGIN_AT, queryCaptor.getValue().sortBy());
    assertEquals(SortDirection.DESC, queryCaptor.getValue().sortDirection());
  }

  @Test
  void listForAdmin_shouldNormalizeRoleQueryParameter() throws Exception {
    mockJwt("admin-jwt", "ADMIN");
    when(getUserUseCase.listForAdmin(any(), any()))
        .thenReturn(new GetUserUseCase.PageResult(List.of(), 0, 20, 0, 0));

    mockMvc
        .perform(
            get("/v1/users?role=admin")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
        .andExpect(status().isOk());

    ArgumentCaptor<GetUserUseCase.Query> queryCaptor =
        ArgumentCaptor.forClass(GetUserUseCase.Query.class);
    verify(getUserUseCase).listForAdmin(queryCaptor.capture(), eq(Set.of("ADMIN")));
    assertEquals("ADMIN", queryCaptor.getValue().role());
  }

  @Test
  void listForAdmin_shouldRejectInvalidDeletedQueryParameter() throws Exception {
    mockJwt("admin-jwt", "ADMIN");

    mockMvc
        .perform(
            get("/v1/users?deleted=maybe")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("VALIDATION_ERROR"));

    verify(getUserUseCase, never()).listForAdmin(any(), any());
  }

  @Test
  void listForAdmin_shouldRejectInvalidRoleQueryParameter() throws Exception {
    mockJwt("admin-jwt", "ADMIN");

    mockMvc
        .perform(
            get("/v1/users?role=MANAGER")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("VALIDATION_ERROR"));

    verify(getUserUseCase, never()).listForAdmin(any(), any());
  }

  @Test
  void listForAdmin_shouldRejectInvalidSortByQueryParameter() throws Exception {
    mockJwt("admin-jwt", "ADMIN");

    mockMvc
        .perform(
            get("/v1/users?sortBy=passwordHash")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("VALIDATION_ERROR"));

    verify(getUserUseCase, never()).listForAdmin(any(), any());
  }

  @Test
  void listForAdmin_shouldRejectInvalidSortDirectionQueryParameter() throws Exception {
    mockJwt("admin-jwt", "ADMIN");

    mockMvc
        .perform(
            get("/v1/users?sortDirection=up")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("VALIDATION_ERROR"));

    verify(getUserUseCase, never()).listForAdmin(any(), any());
  }

  @Test
  void listForAdmin_shouldAllowSuperAdminAndReturnSuperAdminUsers() throws Exception {
    mockJwt("superadmin-jwt", "SUPERADMIN");
    when(getUserUseCase.listForAdmin(any(), any()))
        .thenReturn(
            new GetUserUseCase.PageResult(
                List.of(
                    new GetUserUseCase.UserSummary(
                        "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
                        "superadmin@example.com",
                        true,
                        true,
                        Set.of("SUPERADMIN"),
                        NOW,
                        NOW,
                        NOW,
                        null)),
                0,
                20,
                1,
                1));

    mockMvc
        .perform(
            get("/v1/users").cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "superadmin-jwt")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].roles", containsInAnyOrder("SUPERADMIN")));
  }

  @Test
  void listForAdmin_shouldRejectSizeGreaterThanOneHundred() throws Exception {
    mockJwt("admin-jwt", "ADMIN");

    mockMvc
        .perform(
            get("/v1/users?size=101")
                .cookie(new Cookie(AuthController.AUTH_COOKIE_NAME, "admin-jwt")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("VALIDATION_ERROR"));

    verify(getUserUseCase, never()).listForAdmin(any(), any());
  }

  private void mockJwt(String token, String role) {
    when(clockPort.now()).thenReturn(NOW);
    when(jwtTokenProviderPort.validate(eq(token), any(Instant.class))).thenReturn(true);
    when(jwtTokenProviderPort.subject(token)).thenReturn("11111111-1111-4111-8111-111111111111");
    when(jwtTokenProviderPort.roles(token)).thenReturn(Set.of(role));
  }
}
