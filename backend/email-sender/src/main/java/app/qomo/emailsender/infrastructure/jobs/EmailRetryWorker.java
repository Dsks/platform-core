package app.qomo.emailsender.infrastructure.jobs;

import app.qomo.emailsender.application.port.in.RetryEmailJobsUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailRetryWorker {

  private final RetryEmailJobsUseCase retryEmailJobsUseCase;

  public EmailRetryWorker(RetryEmailJobsUseCase retryEmailJobsUseCase) {
    this.retryEmailJobsUseCase = retryEmailJobsUseCase;
  }

  @Scheduled(fixedDelayString = "${qomo.email.retry.fixed-delay-ms:30000}")
  public void retryFailedJobs() {
    retryEmailJobsUseCase.retryFailedJobs();
  }
}