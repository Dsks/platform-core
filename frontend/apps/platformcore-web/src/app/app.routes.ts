import { Route } from '@angular/router';
import { authGuard, guestGuard } from '@platformcore/shared-auth';

export const appRoutes: Route[] = [
  {
    path: 'iniciar-sesion',
    loadComponent: () =>
      import('./features/login/client-login.component').then(
        (component) => component.ClientLoginComponent,
      ),
    canActivate: [guestGuard],
    data: {
      authenticatedPath: '/panel',
    },
  },
  {
    path: 'login',
    pathMatch: 'full',
    redirectTo: 'iniciar-sesion',
  },
  {
    path: 'registro',
    loadComponent: () =>
      import('./features/register/client-register.component').then(
        (component) => component.ClientRegisterComponent,
      ),
    canActivate: [guestGuard],
    data: {
      authenticatedPath: '/panel',
    },
  },
  {
    path: 'register',
    pathMatch: 'full',
    redirectTo: 'registro',
  },
  {
    path: 'verificar-cuenta',
    loadComponent: () =>
      import('./features/verify-email/client-verify-email.component').then(
        (component) => component.ClientVerifyEmailComponent,
      ),
  },
  {
    path: 'verify-email',
    pathMatch: 'full',
    redirectTo: 'verificar-cuenta',
  },
  {
    path: 'profile',
    pathMatch: 'full',
    redirectTo: 'perfil',
  },
  {
    path: 'dashboard',
    pathMatch: 'full',
    redirectTo: 'panel',
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'panel',
  },
  {
    path: '',
    loadComponent: () =>
      import('./layout/client-layout.component').then(
        (component) => component.ClientLayoutComponent,
      ),
    canActivate: [authGuard],
    data: {
      loginPath: '/iniciar-sesion',
    },
    children: [
      {
        path: 'panel',
        loadComponent: () =>
          import('./features/dashboard/client-dashboard.component').then(
            (component) => component.ClientDashboardComponent,
          ),
      },
      {
        path: 'perfil',
        loadComponent: () =>
          import('./features/profile/client-profile.component').then(
            (component) => component.ClientProfileComponent,
          ),
      },
    ],
  },
  {
    path: '**',
    redirectTo: 'panel',
  },
];
