package app.qomo.apiusers.infrastructure.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.qomo.apiusers.TestContainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class OpenApiSecurityIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void openApiDocsArePublicAndExposeBaseMetadata() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("Qomo Users API"))
        .andExpect(jsonPath("$.info.version").value("0.0.1-SNAPSHOT"))
        .andExpect(jsonPath("$.components.securitySchemes.qomoAuthCookie.type").value("apiKey"))
        .andExpect(jsonPath("$.components.securitySchemes.qomoAuthCookie.in").value("cookie"))
        .andExpect(jsonPath("$.components.securitySchemes.qomoAuthCookie.name").value("QOMO_AUTH"));
  }

  @Test
  void swaggerUiIsPublic() throws Exception {
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }

  @Test
  void protectedUserRouteStillRequiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/v1/users/2fa8b8e9-3090-404e-a6e8-d95dd8e3b0ec"))
        .andExpect(status().isForbidden());
  }
}
