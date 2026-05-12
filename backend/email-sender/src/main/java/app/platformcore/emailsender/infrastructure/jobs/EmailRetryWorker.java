package app.platformcore.emailsender.infrastructure.jobs;

import app.platformcore.emailsender.application.port.in.RetryEmailJobsUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Infrastructure worker that periodically asks the email retry use case to process eligible
 * delivery jobs.
 *
 * <p>The worker does not choose records, enforce retry limits, or transition job state itself.
 * Those operational rules are owned by {@link RetryEmailJobsUseCase}, which keeps this scheduled
 * component as a thin trigger around the application boundary.
 */
@Component
public class EmailRetryWorker {

  private final RetryEmailJobsUseCase retryEmailJobsUseCase;

  /**
   * Creates the scheduler-facing worker with the application use case that owns retry processing.
   */
  public EmailRetryWorker(RetryEmailJobsUseCase retryEmailJobsUseCase) {
    this.retryEmailJobsUseCase = retryEmailJobsUseCase;
  }

  /**
   * Triggers the configured retry pass for failed email delivery jobs.
   *
   * <p>Spring invokes this method on a fixed delay. Repeated executions must remain safe because
   * the method may run again after partial progress or after a previous execution found no eligible
   * work. Any eligibility rules, batching, locking, attempt limits, minimum age checks, and state
   * transitions are delegated to {@link RetryEmailJobsUseCase}; this method only starts the
   * operational pass.
   */
  @Scheduled(fixedDelayString = "${platformcore.email.retry.fixed-delay-ms:30000}")
  public void retryFailedJobs() {
    // Fixed delay spaces retry passes from completion time, including slow SMTP/database work.
    retryEmailJobsUseCase.retryFailedJobs();
  }
}
