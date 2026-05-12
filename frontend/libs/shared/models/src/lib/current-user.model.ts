export const PLATFORMCORE_ROLES = {
  CLIENT: 'CLIENT',
  USER: 'USER',
  ADMIN: 'ADMIN',
  SUPERADMIN: 'SUPERADMIN',
} as const;

export type KnownRole = (typeof PLATFORMCORE_ROLES)[keyof typeof PLATFORMCORE_ROLES];

export type Role = KnownRole | (string & {});

export interface CurrentUser {
  id: string;
  email: string;
  active: boolean;
  emailVerified: boolean;
  roles: Role[];
}
