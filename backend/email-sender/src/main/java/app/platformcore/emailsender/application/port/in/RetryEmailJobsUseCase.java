package app.platformcore.emailsender.application.port.in;

/**
 * Coordinates a retry sweep for email jobs that were previously left in a failed or recoverable
 * state.
 *
 * <p>This use case is normally called by an application trigger inside {@code email-sender}, such
 * as a scheduler or operational retry entry point. It owns the application-level decision to retry
 * pending failed work, while persistence details, SQL selection rules, and transport-specific
 * delivery mechanics remain behind the corresponding outbound ports and services.
 */
public interface RetryEmailJobsUseCase {

  /**
   * Attempts one retry pass for eligible failed email jobs.
   *
   * <p>The call is synchronous at this boundary: when it returns, the retry pass has been
   * attempted. The contract does not guarantee that every eligible job was delivered successfully.
   * Observable effects may include delivery attempts, persisted state changes, retry bookkeeping,
   * and application logs, depending on the jobs selected by the implementation.
   */
  void retryFailedJobs();
}
