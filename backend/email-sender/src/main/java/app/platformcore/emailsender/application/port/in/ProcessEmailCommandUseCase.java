package app.platformcore.emailsender.application.port.in;

import app.platformcore.emailsender.application.command.EmailCommandMessage;

/**
 * Handles a parsed request to process an email command.
 *
 * <p>This use case is normally invoked by an inbound adapter after an external payload has been
 * translated into an {@link EmailCommandMessage}. The port represents the application contract for
 * deciding whether the message can be completed immediately or must be persisted for later
 * recovery. Parsing transport records, acknowledging external brokers, and choosing infrastructure
 * delivery mechanisms are outside this contract.
 */
public interface ProcessEmailCommandUseCase {

  /**
   * Processes one email command and reports the application-visible outcome.
   *
   * <p>The call is synchronous at this boundary: the returned value describes the work completed or
   * made recoverable before the method returns. Observable effects may include an email delivery
   * attempt, persisted recovery state, and application logs, but broker acknowledgement and
   * transport offset handling are not part of this port.
   *
   * @param message parsed command data to process; callers are expected to provide the application
   *     payload rather than a raw transport record
   * @param rawPayload original payload associated with the command, kept for observability,
   *     diagnostics, or recoverable persistence when processing cannot complete normally
   * @return the outcome reached by the use case; {@link EmailCommandProcessingOutcome#COMPLETED}
   *     means processing completed at this boundary, while {@link
   *     EmailCommandProcessingOutcome#RECOVERABLE_STATE_PERSISTED} means recoverable work was
   *     stored for later handling
   */
  EmailCommandProcessingOutcome process(EmailCommandMessage message, String rawPayload);

  /**
   * Application outcome exposed to inbound callers so they can react without depending on internal
   * delivery or persistence details.
   */
  enum EmailCommandProcessingOutcome {
    /** The command was processed to completion during this call. */
    COMPLETED,
    /** Processing could not complete immediately, and recoverable state was persisted. */
    RECOVERABLE_STATE_PERSISTED
  }
}
