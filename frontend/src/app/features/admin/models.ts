/** Shapes of {@code /api/users}, {@code /api/roles} and {@code /api/permissions}. */
export interface UserSummary {
  id: number;
  username: string;
  employeeCode?: string;
  fullName: string;
  email: string;
  active: boolean;
  roles: string[];
  lastLoginAt?: string;
  lockedUntil?: string;
}

export interface UserResponse extends UserSummary {
  phone?: string;
  mustChangePassword: boolean;
  passwordChangedAt?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreateUserRequest {
  username: string;
  employeeCode?: string;
  fullName: string;
  email: string;
  phone?: string;
  roles: string[];
  temporaryPassword: string;
}

export interface UpdateUserRequest {
  fullName: string;
  email: string;
  phone?: string;
  employeeCode?: string;
  version: number;
}

export interface RoleSummary {
  id: number;
  code: string;
  name: string;
  description?: string;
  systemRole: boolean;
  permissionCount: number;
  userCount: number;
}

export interface RoleResponse {
  id: number;
  code: string;
  name: string;
  description?: string;
  systemRole: boolean;
  permissions: string[];
  userCount: number;
  version: number;
}

export interface PermissionResponse {
  id: number;
  code: string;
  module: string;
  description?: string;
}

export interface UserListQuery {
  q?: string;
  role?: string;
  active?: boolean | null;
  page: number;
  size: number;
  sort: string;
}
