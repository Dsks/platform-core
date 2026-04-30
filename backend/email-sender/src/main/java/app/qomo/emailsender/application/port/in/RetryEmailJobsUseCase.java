package app.qomo.emailsender.application.port.in;

public interface RetryEmailJobsUseCase {

  void retryFailedJobs();
}