package app.qomo.emailsender.infrastructure.adapter.out.template;

import app.qomo.emailsender.application.exception.TemplateNotFoundException;
import app.qomo.emailsender.application.port.out.EmailTemplateRendererPort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * {@link EmailTemplateRendererPort} implementation backed by HTML files packaged on the application
 * classpath.
 *
 * <p>This adapter is the infrastructure boundary for template loading. It hides the physical
 * classpath locations, UTF-8 resource access, the current set of supported template identifiers,
 * and the placeholder replacement mechanism from the application layer. Rendering reads a template
 * resource and substitutes application-provided command values verbatim; missing values used by the
 * supported template are rendered as empty strings.
 *
 * <p>The adapter performs no persistence or network I/O. Unknown, missing, or unreadable templates
 * are reported as {@link TemplateNotFoundException} so the use case does not depend on Spring
 * resource exceptions.
 */
public class ClasspathEmailTemplateRendererAdapter implements EmailTemplateRendererPort {

  private static final String EMAIL_VERIFICATION_TEMPLATE = "EMAIL_VERIFICATION";

  private final ResourceLoader resourceLoader;

  public ClasspathEmailTemplateRendererAdapter(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  /**
   * Renders the application-level template name by resolving it to a classpath resource.
   *
   * @param templateName template identifier selected by the application layer
   * @param model values already prepared by the use case for the selected template
   * @return HTML with the supported placeholders replaced
   * @throws TemplateNotFoundException when the template is unsupported, absent, or unreadable
   */
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

  /**
   * Keeps the mapping from application template identifiers to packaged resource paths local to the
   * infrastructure adapter.
   */
  private String resolveTemplateLocation(String templateName) {
    if (EMAIL_VERIFICATION_TEMPLATE.equals(templateName)) {
      return "classpath:templates/email_verification.html";
    }
    throw new TemplateNotFoundException("Template not found: " + templateName);
  }
}
