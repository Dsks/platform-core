package app.qomo.apiusers.domain.constant;

import app.qomo.apiusers.domain.model.RoleId;
import java.util.UUID;

/** Stable identifiers for built-in system roles used across the users domain. */
public final class SystemRoleIds {

  private SystemRoleIds() {}

  public static final RoleId SUPERADMIN =
      new RoleId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  public static final RoleId ADMIN =
      new RoleId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  public static final RoleId USER =
      new RoleId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
}
