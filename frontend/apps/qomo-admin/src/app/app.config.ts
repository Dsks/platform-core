import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { APP_BASE_HREF } from '@angular/common';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { provideRouter } from '@angular/router';
import { provideSharedApi } from '@qomo/shared-api';
import { provideAuthRedirects, provideSessionBootstrap } from '@qomo/shared-auth';
import { appRoutes } from './app.routes';
import { SpanishPaginatorIntl } from './core/material/spanish-paginator-intl';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: APP_BASE_HREF, useValue: '/admin/' },
    provideRouter(appRoutes),
    { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl },
    provideSharedApi(),
    provideAuthRedirects({
      loginPath: '/iniciar-sesion',
      authenticatedPath: '/panel',
      notAuthorizedPath: '/panel',
    }),
    provideSessionBootstrap(),
  ],
};
