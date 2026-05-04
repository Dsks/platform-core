package app.qomo.emailsender.domain.port.out;

import java.util.Map;

public interface EmailTemplateRendererPort {

  String render(String templateName, Map<String, Object> model);
}
