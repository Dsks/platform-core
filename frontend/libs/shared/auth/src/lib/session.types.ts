import type { CurrentUser } from '@qomo/shared-models';

export type SessionStatus = 'unknown' | 'authenticated' | 'guest';

export interface SessionState {
  status: SessionStatus;
  currentUser: CurrentUser | null;
}
