package app.qomo.emailsender.infrastructure.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

import app.qomo.emailsender.application.port.in.RetryEmailJobsUseCase;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class EmailRetryWorkerTest {

  @Mock
  private RetryEmailJobsUseCase retryEmailJobsUseCase;

  @Test
  void retryFailedJobs_whenTriggered_delegatesToUseCase() {
    EmailRetryWorker worker = new EmailRetryWorker(retryEmailJobsUseCase);

    worker.retryFailedJobs();

    verify(retryEmailJobsUseCase).retryFailedJobs();
  }

  @Test
  void retryFailedJobs_hasExpectedSchedulingConfiguration() throws NoSuchMethodException {
    Method method = EmailRetryWorker.class.getMethod("retryFailedJobs");

    Scheduled scheduled = method.getAnnotation(Scheduled.class);

    assertNotNull(scheduled);
    assertEquals("${qomo.email.retry.fixed-delay-ms:30000}", scheduled.fixedDelayString());
  }
}