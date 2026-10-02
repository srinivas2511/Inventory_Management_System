/** Shapes of {@code /api/auth/*} (DESIGN.md section 6.3). */
export interface UserProfile {
  id: number;
  username: string;
  fullName: string;
  roles: string[];
  permissions: string[];
  primaryDashboard: string;
}

/**
 * Response of login, change-password and refresh. When {@code mustChangePassword} is true no session was opened
 * (no token, no user): the client must call change-password first.
 */
export interface LoginResponse {
  accessToken?: string;
  expiresIn?: number;
  mustChangePassword: boolean;
  user?: UserProfile;
}

export type LoginOutcome = 'authenticated' | 'mustChangePassword';
