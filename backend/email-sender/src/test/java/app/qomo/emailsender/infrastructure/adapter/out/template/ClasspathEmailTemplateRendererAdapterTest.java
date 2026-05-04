package app.qomo.emailsender.infrastructure.adapter.out.template;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import app.qomo.emailsender.application.exception.TemplateNotFoundException;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@ExtendWith(MockitoExtension.class)
class ClasspathEmailTemplateRendererAdapterTest {

  @Mock private ResourceLoader resourceLoader;

  @Mock private Resource resource;

  @Test
  void render_supportedTemplate_replacesPlaceholdersFromModel() {
    ClasspathEmailTemplateRendererAdapter adapter =
        new ClasspathEmailTemplateRendererAdapter(new DefaultResourceLoader());

    String rendered =
        adapter.render(
            "EMAIL_VERIFICATION", Map.of("verificationCode", "123456", "appName", "Qomo"));

    assertNotEquals(-1, rendered.indexOf("<strong>123456</strong>"));
    assertNotEquals(-1, rendered.indexOf("Qomo - Email Verification"));
    assertTrue(!rendered.contains("{{code}}"));
    assertTrue(!rendered.contains("{{appName}}"));
  }

  @Test
  void render_supportedTemplate_withMissingModelValues_usesEmptyFallbacks() {
    ClasspathEmailTemplateRendererAdapter adapter =
        new ClasspathEmailTemplateRendererAdapter(new DefaultResourceLoader());

    String rendered = adapter.render("EMAIL_VERIFICATION", Map.of());

    assertNotEquals(-1, rendered.indexOf("<strong></strong>"));
    assertNotEquals(-1, rendered.indexOf(" - Email Verification"));
  }

  @Test
  void render_unsupportedTemplate_throwsTemplateNotFoundException() {
    ClasspathEmailTemplateRendererAdapter adapter =
        new ClasspathEmailTemplateRendererAdapter(resourceLoader);

    TemplateNotFoundException exception =
        assertThrows(
            TemplateNotFoundException.class, () -> adapter.render("WELCOME_EMAIL", Map.of()));

    assertNotEquals(-1, exception.getMessage().indexOf("Template not found: WELCOME_EMAIL"));
  }

  @Test
  void render_supportedTemplate_whenResourceDoesNotExist_throwsTemplateNotFoundException() {
    ClasspathEmailTemplateRendererAdapter adapter =
        new ClasspathEmailTemplateRendererAdapter(resourceLoader);
    when(resourceLoader.getResource("classpath:templates/email_verification.html"))
        .thenReturn(resource);
    when(resource.exists()).thenReturn(false);

    TemplateNotFoundException exception =
        assertThrows(
            TemplateNotFoundException.class, () -> adapter.render("EMAIL_VERIFICATION", Map.of()));

    assertNotEquals(-1, exception.getMessage().indexOf("Template not found: EMAIL_VERIFICATION"));
  }

  @Test
  void render_supportedTemplate_whenResourceReadFails_wrapsAsTemplateNotFoundException()
      throws IOException {
    ClasspathEmailTemplateRendererAdapter adapter =
        new ClasspathEmailTemplateRendererAdapter(resourceLoader);
    when(resourceLoader.getResource("classpath:templates/email_verification.html"))
        .thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    when(resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
        .thenThrow(new IOException("disk broken"));

    TemplateNotFoundException exception =
        assertThrows(
            TemplateNotFoundException.class,
            () -> adapter.render("EMAIL_VERIFICATION", Map.of("verificationCode", "123456")));

    assertNotEquals(
        -1, exception.getMessage().indexOf("Template could not be read: EMAIL_VERIFICATION"));
    assertTrue(exception.getCause() instanceof IOException);
  }
}
