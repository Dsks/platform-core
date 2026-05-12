package app.platformcore.apiusers.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import app.platformcore.apiusers.TestContainersConfig;
import app.platformcore.apiusers.application.port.out.RoleRepositoryPort;
import app.platformcore.apiusers.domain.constant.SystemRoleIds;
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
