import { isPlatformBrowser } from '@angular/common';
import {
  EnvironmentProviders,
  PLATFORM_ID,
  inject,
  makeEnvironmentProviders,
  provideAppInitializer,
} from '@angular/core';
import { catchError, of, take, timeout } from 'rxjs';
import { AuthService } from './auth.service';
import { SessionStore } from './session-store';

export interface SessionBootstrapConfig {
  enabled?: boolean;
  timeoutMs?: number;
}

const DEFAULT_BOOTSTRAP_TIMEOUT_MS = 5000;

export function provideSessionBootstrap(
  config: SessionBootstrapConfig = {},
): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideAppInitializer(() => {
      if (config.enabled === false) {
        return;
      }

      const platformId = inject(PLATFORM_ID);

      if (!isPlatformBrowser(platformId)) {
        return;
      }

      const authService = inject(AuthService);
      const sessionStore = inject(SessionStore);

      return authService.bootstrapSession().pipe(
        timeout({
          first: config.timeoutMs ?? DEFAULT_BOOTSTRAP_TIMEOUT_MS,
        }),
        catchError(() => {
          sessionStore.setGuest();

          return of(null);
        }),
        take(1),
      );
    }),
  ]);
}
