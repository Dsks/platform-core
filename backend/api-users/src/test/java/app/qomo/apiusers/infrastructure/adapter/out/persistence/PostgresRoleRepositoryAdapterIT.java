package app.qomo.apiusers.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import app.qomo.apiusers.TestContainersConfig;
import app.qomo.apiusers.domain.constant.SystemRoleIds;
import app.qomo.apiusers.domain.port.out.RoleRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class PostgresRoleRepositoryAdapterIT {

  @Autowired private RoleRepositoryPort roleRepository;

  @Test
  void findByName_shouldNormalizeCaseAndWhitespaceBeforeQuerying() {
    var role = roleRepository.findByName("  user  ").orElseThrow();

    assertThat(role.id()).isEqualTo(SystemRoleIds.USER);
    assertThat(role.name()).isEqualTo("USER");
  }

  @Test
  void findByName_shouldReturnEmptyWhenRoleIsMissing() {
    assertThat(roleRepository.findByName("unknown-role")).isEmpty();
  }
}
