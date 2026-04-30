package app.qomo.apiusers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QomoApiUsersApplication {

  public static void main(String[] args) {
    SpringApplication.run(QomoApiUsersApplication.class, args);
  }
}
