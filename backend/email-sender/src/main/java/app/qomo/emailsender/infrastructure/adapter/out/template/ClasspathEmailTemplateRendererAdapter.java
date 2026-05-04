package app.qomo.emailsender.infrastructure.adapter.out.template;

import app.qomo.emailsender.application.exception.TemplateNotFoundException;
import app.qomo.emailsender.application.port.out.EmailTemplateRendererPort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public class ClasspathEmailTemplateRendererAdapter implements EmailTemplateRendererPort {

  private static final String EMAIL_VERIFICATION_TEMPLATE = "EMAIL_VERIFICATION";

  private final ResourceLoader resourceLoader;

  public ClasspathEmailTemplateRendererAdapter(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  @Override
  public String render(String templateName, Map<String, Object> model) {
    String classpathLocation = resolveTemplateLocation(templateName);
    Resource resource = resourceLoader.getResource(classpathLocation);

    if (!resource.exists()) {
      throw new TemplateNotFoundException("Template not found: " + templateName);
    }

    try {
      String template = resource.getContentAsString(StandardCharsets.UTF_8);
      return template
          .replace("{{code}}", String.valueOf(model.getOrDefault("verificationCode", "")))
          .replace("{{appName}}", String.valueOf(model.getOrDefault("appName", "")));
    } catch (IOException exception) {
      throw new TemplateNotFoundException("Template could not be read: " + templateName, exception);
    }
  }

  private String resolveTemplateLocation(String templateName) {
    if (EMAIL_VERIFICATION_TEMPLATE.equals(templateName)) {
      return "classpath:templates/email_verification.html";
    }
    throw new TemplateNotFoundException("Template not found: " + templateName);
  }
}
