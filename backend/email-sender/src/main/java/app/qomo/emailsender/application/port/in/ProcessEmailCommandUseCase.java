package app.qomo.emailsender.application.port.in;

import app.qomo.emailsender.application.model.EmailCommandMessage;

public interface ProcessEmailCommandUseCase {

  EmailCommandProcessingOutcome process(EmailCommandMessage message, String rawPayload);

  enum EmailCommandProcessingOutcome {
    COMPLETED,
    RECOVERABLE_STATE_PERSISTED
  }
}