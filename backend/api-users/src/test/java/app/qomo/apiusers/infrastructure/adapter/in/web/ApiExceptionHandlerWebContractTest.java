package app.qomo.apiusers.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.qomo.apiusers.application.exception.ApplicationException;
import app.qomo.apiusers.application.exception.EmailAlreadyInUseException;
import app.qomo.apiusers.application.exception.ForbiddenOperationException;
import app.qomo.apiusers.application.exception.InvalidCommandException;
import app.qomo.apiusers.application.exception.UserNotFoundException;
import app.qomo.apiusers.application.port.in.CreateUserUseCase;
import app.qomo.apiusers.application.port.in.GetUserUseCase;
import app.qomo.apiusers.application.port.in.LoginUseCase;
import app.qomo.apiusers.application.port.in.RegisterUserUseCase;
import app.qomo.apiusers.domain.port.out.ClockPort;
import app.qomo.apiusers.domain.port.out.JwtTokenProviderPort;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AuthController.class, UserController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class ApiExceptionHandlerWebContractTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private LoginUseCase loginUseCase;

  @MockitoBean
  private RegisterUserUseCase registerUserUseCase;

  @MockitoBean
  private CreateUserUseCase createUserUseCase;

  @MockitoBean
  private GetUserUseCase getUserUseCase;

  @MockitoBean
  private JwtTokenProviderPort jwtTokenProvider;

  @MockitoBean
  private ClockPort clock;

  @Test
  void register_withInvalidBody_shouldReturnValidationProblemDetail() throws Exception {
    mockMvc
        .perform(
            post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"bad-email","password":""}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Request validation failed"))
        .andExpect(jsonPath("$.type").value("https://qomo.app/problems/VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.errors[0]").isString());
  }

  @Test
  void register_withMalformedJson_shouldReturnMalformedRequestProblemDetail() throws Exception {
    mockMvc
        .perform(
            post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("MALFORMED_REQUEST"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Malformed request body"))
        .andExpect(jsonPath("$.type").value("https://qomo.app/problems/MALFORMED_REQUEST"));
  }

  @Test
  void getUser_withInvalidPathVariable_shouldReturnValidationProblemDetail() throws Exception {
    mockMvc
        .perform(get("/v1/users/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Request validation failed"))
        .andExpect(jsonPath("$.type").value("https://qomo.app/problems/VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.errors[0]").isString());
  }

  @Test
  void createUser_whenEmailAlreadyInUse_shouldReturnConflictProblemWithoutParamsLeak()
      throws Exception {
    when(createUserUseCase.create(any()))
        .thenThrow(new EmailAlreadyInUseException("used@example.com"));

    mockMvc
        .perform(
            post("/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "used@example.com",
                      "password": "StrongPass123!",
                      "roles": ["USER"]
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("USER_EMAIL_ALREADY_IN_USE"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.detail").value("Email already in use"))
        .andExpect(jsonPath("$.type").value("https://qomo.app/problems/USER_EMAIL_ALREADY_IN_USE"))
        .andExpect(jsonPath("$.params").isMap())
        .andExpect(jsonPath("$.params").isEmpty());
  }

  @Test
  void register_whenEmailAlreadyInUse_shouldReturnAcceptedGenericResponse() throws Exception {
    when(registerUserUseCase.register(any()))
        .thenThrow(new EmailAlreadyInUseException("used@example.com"));

    mockMvc
        .perform(
            post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"used@example.com","password":"StrongPass123!"}
                    """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.requestId").isString())
        .andExpect(jsonPath("$.message").value("If the email is valid, you'll receive next steps."))
        .andExpect(jsonPath("$.title").doesNotExist());
  }

  @Test
  void login_whenApplicationExceptionMapped_shouldReturnExpectedStatusAndProblemFormat()
      throws Exception {
    when(loginUseCase.login(any()))
        .thenThrow(new ForbiddenOperationException("admin only"));

    mockMvc
        .perform(
            post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"user@example.com","password":"StrongPass123!"}
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("FORBIDDEN_OPERATION"))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.detail").value("admin only"))
        .andExpect(jsonPath("$.type").value("https://qomo.app/problems/FORBIDDEN_OPERATION"))
        .andExpect(jsonPath("$.params.reason").value("admin only"));
  }

  @Test
  void login_whenApplicationExceptionIsUnmapped_shouldFallbackToBadRequest() throws Exception {
    when(loginUseCase.login(any()))
        .thenThrow(new UnknownApplicationException());

    mockMvc
        .perform(
            post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"user@example.com","password":"StrongPass123!"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("SOME_UNKNOWN_CODE"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Unknown application error"))
        .andExpect(jsonPath("$.type").value("https://qomo.app/problems/SOME_UNKNOWN_CODE"))
        .andExpect(jsonPath("$.params.traceId").value("abc-123"));
  }

  @Test
  void getUser_whenUserNotFoundExceptionIsThrown_shouldReturnNotFoundProblem() throws Exception {
    when(getUserUseCase.getById(any()))
        .thenThrow(new UserNotFoundException("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"));

    mockMvc
        .perform(get("/v1/users/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("USER_NOT_FOUND"));
  }

  @Test
  void getUser_whenInvalidCommandExceptionIsThrown_shouldReturnBadRequestProblem()
      throws Exception {
    when(getUserUseCase.getById(any())).thenThrow(InvalidCommandException.blank("id"));

    mockMvc
        .perform(get("/v1/users/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("INVALID_COMMAND"));
  }

  private static final class UnknownApplicationException extends ApplicationException {

    private UnknownApplicationException() {
      super("SOME_UNKNOWN_CODE", "Unknown application error", Map.of("traceId", "abc-123"));
    }
  }
}