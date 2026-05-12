import { Route } from '@angular/router';
import { adminGuard, guestGuard } from '@platformcore/shared-auth';

export const appRoutes: Route[] = [
  {
    path: 'iniciar-sesion',
    loadComponent: () =>
      import('./features/login/admin-login.component').then(
        (component) => component.AdminLoginComponent,
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
    path: 'dashboard',
    pathMatch: 'full',
    redirectTo: 'panel',
  },
  {
    path: 'profile',
    pathMatch: 'full',
    redirectTo: 'perfil',
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'panel',
  },
  {
    path: '',
    loadComponent: () =>
      import('./layout/admin-layout.component').then(
        (component) => component.AdminLayoutComponent,
      ),
    canActivate: [adminGuard],
    data: {
      loginPath: '/iniciar-sesion',
      notAuthorizedPath: '/panel',
      externalNotAuthorizedRedirect: true,
    },
    children: [
      {
        path: 'panel',
        loadComponent: () =>
          import('./features/dashboard/admin-dashboard.component').then(
            (component) => component.AdminDashboardComponent,
          ),
      },
      {
        path: 'perfil',
        loadComponent: () =>
          import('./features/profile/admin-profile.component').then(
            (component) => component.AdminProfileComponent,
          ),
      },
      {
        path: 'usuarios/nuevo',
        loadComponent: () =>
          import('./features/users/user-form.component').then(
            (component) => component.UserFormComponent,
          ),
      },
      {
        path: 'usuarios/:id/editar',
        loadComponent: () =>
          import('./features/users/user-form.component').then(
            (component) => component.UserFormComponent,
          ),
      },
      {
        path: 'usuarios',
        loadComponent: () =>
          import('./features/users/users-list.component').then(
            (component) => component.UsersListComponent,
          ),
      },
    ],
  },
  {
    path: '**',
    redirectTo: 'panel',
  },
];
