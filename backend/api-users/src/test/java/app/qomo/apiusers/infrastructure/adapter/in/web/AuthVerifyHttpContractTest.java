package app.qomo.apiusers.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.qomo.apiusers.application.exception.EmailAlreadyInUseException;
import app.qomo.apiusers.application.exception.InvalidCredentialsException;
import app.qomo.apiusers.application.port.in.GetCurrentUserUseCase;
import app.qomo.apiusers.application.port.in.LoginUseCase;
import app.qomo.apiusers.application.port.in.RegisterUserUseCase;
import app.qomo.apiusers.application.port.in.RegisterUserUseCase.RegistrationStatus;
import app.qomo.apiusers.application.port.in.ResendEmailVerificationUseCase;
import app.qomo.apiusers.application.port.in.VerifyEmailUseCase;
import app.qomo.apiusers.application.port.out.ClockPort;
import app.qomo.apiusers.application.port.out.JwtTokenProviderPort;
import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.model.PasswordHash;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AuthController.class, VerifyEmailController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
@TestPropertySource(
    properties = {
      "qomo.security.cookie.secure=true",
      "qomo.security.cookie.same-site=Strict",
      "qomo.security.verification.cookie.name=QOMO_VERIF_TEST",
      "qomo.security.verification.cookie.secure=true",
      "qomo.security.verification.cookie.same-site=None",
      "qomo.security.jwt.expiration-ms=120000"
    })
class AuthVerifyHttpContractTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private LoginUseCase loginUseCase;

  @MockitoBean private RegisterUserUseCase registerUserUseCase;

  @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;

  @MockitoBean private VerifyEmailUseCase verifyEmailUseCase;

  @MockitoBean private ResendEmailVerificationUseCase resendEmailVerificationUseCase;

  @MockitoBean private JwtTokenProviderPort jwtTokenProvider;

  @MockitoBean private ClockPort clock;

  @Test
  void login_shouldReturn204AndEmitAuthCookie_whenCredentialsAreValid() throws Exception {
    var user =
        User.createNew(
            new UserId(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")),
            new Email("user@example.com"),
            new PasswordHash("hash"),
            Instant.parse("2026-03-01T00:00:00Z"));
    when(loginUseCase.login(any())).thenReturn(new LoginUseCase.Result(user, false, null, 0));
    when(clock.now()).thenReturn(Instant.parse("2026-03-26T10:00:00Z"));
    when(jwtTokenProvider.generate(eq(user), any())).thenReturn("jwt-token-123");

    var response =
        mockMvc
            .perform(
                post("/v1/auth/login")
                    .contentType("application/json")
                    .content(
                        """
                            {"email":"user@example.com","password":"s3cret"}
                            """))
            .andExpect(status().isNoContent())
            .andExpect(header().exists(HttpHeaders.SET_COOKIE))
            .andReturn()
            .getResponse();

    String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
    Assertions.assertNotNull(setCookie);
    Assertions.assertTrue(setCookie.contains("QOMO_AUTH=jwt-token-123"));
    Assertions.assertTrue(setCookie.contains("HttpOnly"));
    Assertions.assertTrue(setCookie.contains("Secure"));
    Assertions.assertTrue(setCookie.contains("Path=/"));
    Assertions.assertTrue(setCookie.contains("SameSite=Strict"));
    Assertions.assertTrue(setCookie.contains("Max-Age=120"));
  }

  @Test
  void login_shouldReturnUnauthorizedProblemDetail_whenCredentialsAreInvalid() throws Exception {
    when(loginUseCase.login(any())).thenThrow(new InvalidCredentialsException());

    mockMvc
        .perform(
            post("/v1/auth/login")
                .contentType("application/json")
                .content(
                    """
                        {"email":"user@example.com","password":"wrong"}
                        """))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
        .andExpect(jsonPath("$.title").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.detail").value("Invalid credentials"))
        .andExpect(jsonPath("$.type").value("https://qomo.app/problems/INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.params").isMap());
  }

  @Test
  void logout_shouldReturn204AndExpireAuthCookie() throws Exception {
    var response =
        mockMvc
            .perform(post("/v1/auth/logout"))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""))
            .andExpect(header().exists(HttpHeaders.SET_COOKIE))
            .andReturn()
            .getResponse();

    String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
    Assertions.assertNotNull(setCookie);
    Assertions.assertTrue(setCookie.contains("QOMO_AUTH="));
    Assertions.assertTrue(setCookie.contains("HttpOnly"));
    Assertions.assertTrue(setCookie.contains("Secure"));
    Assertions.assertTrue(setCookie.contains("Path=/"));
    Assertions.assertTrue(setCookie.contains("SameSite=Strict"));
    Assertions.assertTrue(setCookie.contains("Max-Age=0"));
  }

  @Test
  void login_shouldReturnForbiddenAndSetVerificationCookie_whenEmailIsNotVerifiedWithSession()
      throws Exception {
    UUID verificationSessionId = UUID.fromString("11111111-2222-4333-8444-555555555555");
    when(loginUseCase.login(any()))
        .thenReturn(new LoginUseCase.Result(null, true, verificationSessionId, 900));

    var response =
        mockMvc
            .perform(
                post("/v1/auth/login")
                    .contentType("application/json")
                    .content(
                        """
                            {"email":"user@example.com","password":"s3cret"}
                            """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("EMAIL_NOT_VERIFIED"))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.detail").value("Login not completed"))
            .andReturn()
            .getResponse();

    String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
    Assertions.assertNotNull(setCookie);
    Assertions.assertTrue(
        setCookie.contains("QOMO_VERIF_TEST=11111111-2222-4333-8444-555555555555"));
    Assertions.assertTrue(setCookie.contains("Max-Age=900"));
    Assertions.assertTrue(setCookie.contains("SameSite=None"));
  }

  @Test
  void login_shouldReturnForbiddenWithoutCookie_whenEmailIsNotVerifiedAndNoSessionWasIssued()
      throws Exception {
    when(loginUseCase.login(any())).thenReturn(new LoginUseCase.Result(null, true, null, 120));

    mockMvc
        .perform(
            post("/v1/auth/login")
                .contentType("application/json")
                .content(
                    """
                        {"email":"user@example.com","password":"s3cret"}
                        """))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
        .andExpect(jsonPath("$.title").value("EMAIL_NOT_VERIFIED"))
        .andExpect(jsonPath("$.detail").value("Login not completed"));
  }

  @Test
  void register_shouldReturnAcceptedAndSetVerificationCookie_whenUseCaseIssuesSession()
      throws Exception {
    when(registerUserUseCase.register(any()))
        .thenReturn(
            new RegisterUserUseCase.Result(
                "req-123",
                RegistrationStatus.VERIFICATION_REQUIRED,
                UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                300));

    var response =
        mockMvc
            .perform(
                post("/v1/auth/register")
                    .contentType("application/json")
                    .content(
                        """
                            {"email":"new@example.com","password":"s3cret"}
                            """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.requestId").value("req-123"))
            .andExpect(jsonPath("$.status").value("VERIFICATION_REQUIRED"))
            .andExpect(
                jsonPath("$.message").value("If the email is valid, you'll receive next steps."))
            .andReturn()
            .getResponse();

    String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
    Assertions.assertNotNull(setCookie);
    Assertions.assertTrue(
        setCookie.contains("QOMO_VERIF_TEST=bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"));
    Assertions.assertTrue(setCookie.contains("Max-Age=300"));
  }

  @Test
  void register_shouldReturnConflict_whenExistingUserIsAlreadyVerified() throws Exception {
    when(registerUserUseCase.register(any()))
        .thenReturn(
            new RegisterUserUseCase.Result(
                "req-registered", RegistrationStatus.ALREADY_REGISTERED, null, 0));

    mockMvc
        .perform(
            post("/v1/auth/register")
                .contentType("application/json")
                .content(
                    """
                        {"email":"used@example.com","password":"s3cret"}
                        """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.requestId").value("req-registered"))
        .andExpect(jsonPath("$.status").value("ALREADY_REGISTERED"))
        .andExpect(jsonPath("$.message").value("Account already registered. Please sign in."))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
  }

  @Test
  void register_shouldReturnConflictAlreadyRegistered_whenEmailAlreadyInUseIsThrown()
      throws Exception {
    when(registerUserUseCase.register(any()))
        .thenThrow(new EmailAlreadyInUseException("used@example.com"));

    mockMvc
        .perform(
            post("/v1/auth/register")
                .contentType("application/json")
                .content(
                    """
                        {"email":"used@example.com","password":"s3cret"}
                        """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.requestId").isString())
        .andExpect(jsonPath("$.status").value("ALREADY_REGISTERED"))
        .andExpect(jsonPath("$.message").value("Account already registered. Please sign in."))
        .andExpect(jsonPath("$.title").doesNotExist());
  }

  @Test
  void verifyEmail_shouldReturnAcceptedGenericResponse_whenVerificationCookieIsMissing()
      throws Exception {
    mockMvc
        .perform(
            post("/v1/users/verify-email")
                .contentType("application/json")
                .content(
                    """
                        {"code":"123456"}
                        """))
        .andExpect(status().isAccepted())
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
        .andExpect(jsonPath("$.message").value("Code Not Found"));

    verify(verifyEmailUseCase, never()).verify(any());
  }

  @Test
  void verifyEmail_shouldReturnAcceptedGenericResponse_whenVerificationCookieIsMalformed()
      throws Exception {
    mockMvc
        .perform(
            post("/v1/users/verify-email")
                .cookie(new Cookie("QOMO_VERIF_TEST", "not-a-uuid"))
                .contentType("application/json")
                .content(
                    """
                        {"code":"123456"}
                        """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.message").value("Code Not Found"));

    verify(verifyEmailUseCase, never()).verify(any());
  }

  @Test
  void verifyEmail_shouldReturnNoContentAndClearVerificationCookie_whenCodeIsValid()
      throws Exception {
    UUID verificationSessionId = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    when(verifyEmailUseCase.verify(new VerifyEmailUseCase.Command(verificationSessionId, "123456")))
        .thenReturn(true);

    var response =
        mockMvc
            .perform(
                post("/v1/users/verify-email")
                    .cookie(new Cookie("QOMO_VERIF_TEST", verificationSessionId.toString()))
                    .contentType("application/json")
                    .content(
                        """
                            {"code":"123456"}
                            """))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();

    String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
    Assertions.assertNotNull(setCookie);
    Assertions.assertTrue(setCookie.contains("QOMO_VERIF_TEST="));
    Assertions.assertTrue(setCookie.contains("Max-Age=0"));
    Assertions.assertTrue(setCookie.contains("SameSite=None"));
  }

  @Test
  void verifyEmail_shouldReturnAcceptedGenericResponse_whenCodeIsInvalidForSession()
      throws Exception {
    UUID verificationSessionId = UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");
    when(verifyEmailUseCase.verify(new VerifyEmailUseCase.Command(verificationSessionId, "000000")))
        .thenReturn(false);

    mockMvc
        .perform(
            post("/v1/users/verify-email")
                .cookie(new Cookie("QOMO_VERIF_TEST", verificationSessionId.toString()))
                .contentType("application/json")
                .content(
                    """
                        {"code":"000000"}
                        """))
        .andExpect(status().isAccepted())
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
        .andExpect(jsonPath("$.message").value("Code Not Found"));
  }

  @Test
  void resendVerification_shouldReturnAcceptedGenericResponseAndSetCookie_whenSessionIsIssued()
      throws Exception {
    when(resendEmailVerificationUseCase.resend(any()))
        .thenReturn(
            new ResendEmailVerificationUseCase.Result(
                UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd"), 60));

    var response =
        mockMvc
            .perform(
                post("/v1/users/verification/resend")
                    .contentType("application/json")
                    .content(
                        """
                            {"email":"user@example.com"}
                            """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.message").value("Forwarded Code"))
            .andReturn()
            .getResponse();

    String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
    Assertions.assertNotNull(setCookie);
    Assertions.assertTrue(
        setCookie.contains("QOMO_VERIF_TEST=dddddddd-dddd-4ddd-8ddd-dddddddddddd"));
    Assertions.assertTrue(setCookie.contains("Max-Age=60"));
  }

  @Test
  void resendVerification_shouldReturnAcceptedGenericResponseWithoutCookie_whenNoSessionIsIssued()
      throws Exception {
    when(resendEmailVerificationUseCase.resend(any()))
        .thenReturn(new ResendEmailVerificationUseCase.Result(null, 0));

    mockMvc
        .perform(
            post("/v1/users/verification/resend")
                .contentType("application/json")
                .content(
                    """
                        {"email":"user@example.com"}
                        """))
        .andExpect(status().isAccepted())
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
        .andExpect(jsonPath("$.message").value("Forwarded Code"));
  }

  @Test
  void login_shouldReturnMalformedRequestProblemDetail_whenBodyIsInvalidJson() throws Exception {
    mockMvc
        .perform(
            post("/v1/auth/login")
                .contentType("application/json")
                .content(
                    """
                        {"email":"user@example.com",
                        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("MALFORMED_REQUEST"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Malformed request body"));
  }
}
