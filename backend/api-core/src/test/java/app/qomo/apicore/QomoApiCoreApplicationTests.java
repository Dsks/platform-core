package app.qomo.apicore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestContainersConfig.class)
class QomoApiCoreApplicationTests {

  @Test
  void contextLoads() {}
}
