import type { CurrentUser } from './current-user.model';

export type UsersDeletedFilter = 'false' | 'true' | 'all';

export type UsersRoleFilter = 'USER' | 'ADMIN' | 'SUPERADMIN';

export type UsersSortBy =
  | 'email'
  | 'createdAt'
  | 'updatedAt'
  | 'lastLoginAt'
  | 'deletedAt'
  | 'role';

export type UsersSortDirection = 'asc' | 'desc';

export interface UsersListFilters {
  search?: string | null;
  deleted: UsersDeletedFilter;
  active?: boolean | null;
  verified?: boolean | null;
  role?: UsersRoleFilter | null;
}

export interface UsersListQuery extends UsersListFilters {
  sortBy: UsersSortBy;
  sortDirection: UsersSortDirection;
}

export interface UserSummary
  extends Pick<
    CurrentUser,
    'id' | 'email' | 'active' | 'emailVerified' | 'roles'
  > {
  lastLogin?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  deletedAt?: string | null;
}

export interface UserDetail
  extends Pick<
    CurrentUser,
    'id' | 'email' | 'active' | 'emailVerified' | 'roles'
  > {
  lastLogin?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CreateUserRequest {
  email: string;
  password: string;
  roles: string[];
}

export interface UpdateUserRequest {
  active: boolean;
}

export interface UsersPage {
  content: UserSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
