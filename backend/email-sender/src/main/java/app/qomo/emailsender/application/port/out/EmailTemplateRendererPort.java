package app.qomo.emailsender.application.port.out;

import java.util.Map;

/**
 * Boundary for rendering application-selected email templates into HTML content.
 *
 * <p>Implementations normally live in infrastructure and encapsulate the concrete template engine,
 * template loading strategy, escaping rules, and rendering configuration. Application services
 * provide the template name and command already chosen for the use case; the returned HTML is ready
 * to pass to the email delivery boundary.
 */
public interface EmailTemplateRendererPort {

  /**
   * Renders a template using the supplied command.
   *
   * @param templateName application-level identifier of the template to render
   * @param model values made available to the template; keys are interpreted by the selected
   *     template
   * @return rendered HTML body for the outgoing email
   */
  String render(String templateName, Map<String, Object> model);
}
