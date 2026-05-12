export const QOMO_ROLES = {
  CLIENT: 'CLIENT',
  USER: 'USER',
  ADMIN: 'ADMIN',
  SUPERADMIN: 'SUPERADMIN',
} as const;

export type KnownRole = (typeof QOMO_ROLES)[keyof typeof QOMO_ROLES];

export type Role = KnownRole | (string & {});

export interface CurrentUser {
  id: string;
  email: string;
  active: boolean;
  emailVerified: boolean;
  roles: Role[];
}
