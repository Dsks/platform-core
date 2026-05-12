import { expect, type Page, type Route, test } from '@playwright/test';

type CurrentUser = {
  id: string;
  email: string;
  active: boolean;
  emailVerified: boolean;
  roles: string[];
};

type UserSummary = CurrentUser & {
  lastLogin?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  deletedAt?: string | null;
};

type UsersPage = {
  content: UserSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

type ApiMockOptions = {
  authMe?: CurrentUser | CurrentUser[] | null;
  usersPage?: UsersPage;
  userDetails?: Record<string, UserSummary>;
  onLoginRequest?: (body: unknown) => void;
  onUsersListRequest?: (url: URL) => void;
};

const adminPath = (path: string): string =>
  path === '/' ? '/admin/' : `/admin${path}`;

const adminUser: CurrentUser = {
  id: 'admin-1',
  email: 'admin@platformcore.test',
  active: true,
  emailVerified: true,
  roles: ['ADMIN'],
};

const usersPage: UsersPage = {
  content: [
    {
      id: 'user-1',
      email: 'ana@platformcore.test',
      active: true,
      emailVerified: true,
      roles: ['USER'],
      lastLogin: '2026-05-11T10:30:00.000Z',
      createdAt: '2026-05-01T08:00:00.000Z',
      updatedAt: '2026-05-10T12:00:00.000Z',
      deletedAt: null,
    },
    {
      id: 'user-2',
      email: 'beto@platformcore.test',
      active: false,
      emailVerified: false,
      roles: ['ADMIN'],
      lastLogin: null,
      createdAt: '2026-04-20T08:00:00.000Z',
      updatedAt: '2026-05-02T12:00:00.000Z',
      deletedAt: null,
    },
  ],
  page: 0,
  size: 10,
  totalElements: 2,
  totalPages: 1,
};

const userDetails: Record<string, UserSummary> = Object.fromEntries(
  usersPage.content.map((user) => [user.id, user]),
);

test.describe('platformcore-admin smoke tests', () => {
  test('renders the admin login page', async ({ page }) => {
    await mockApi(page, { authMe: null });

    await page.goto(adminPath('/iniciar-sesion'));

    await expect(
      page.getByRole('heading', { name: /acceso de administraci/i }),
    ).toBeVisible();
    await expect(page.getByLabel(/^email$/i)).toBeVisible();
    await expect(page.getByLabel(/contrase/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /^entrar$/i })).toBeVisible();
  });

  for (const protectedPath of [
    '/',
    '/panel',
    '/perfil',
    '/usuarios',
    '/usuarios/nuevo',
    '/dashboard',
    '/profile',
  ] as const) {
    test(`redirects unauthenticated users from ${protectedPath} to login`, async ({
      page,
    }) => {
      await mockApi(page, { authMe: null });

      await page.goto(adminPath(protectedPath));

      await expect(page).toHaveURL(/\/admin\/iniciar-sesion$/);
    });
  }

  test('logs in an admin and renders the admin shell', async ({ page }) => {
    const plainPassword = 'secret-admin-password';
    let loginBody: unknown;

    await mockApi(page, {
      authMe: [null, adminUser],
      onLoginRequest: (body) => {
        loginBody = body;
      },
    });

    await page.goto(adminPath('/iniciar-sesion'));
    await page.getByLabel(/^email$/i).fill(adminUser.email);
    await page.getByLabel(/contrase/i).fill(plainPassword);
    await page.getByRole('button', { name: /^entrar$/i }).click();

    await expect(page).toHaveURL(/\/admin\/panel$/);
    await expect(page.getByRole('heading', { name: /^panel$/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /usuarios/i })).toBeVisible();
    await expect(page.getByText(adminUser.email)).toBeVisible();
    await expect(
      page.getByRole('button', { name: /cerrar sesi/i }),
    ).toBeVisible();

    expect(loginBody).toMatchObject({
      email: adminUser.email,
    });
    expect((loginBody as { password?: string }).password).toBeTruthy();
    expect((loginBody as { password?: string }).password).not.toBe(
      plainPassword,
    );
  });

  test('renders the authenticated admin profile page', async ({ page }) => {
    await mockApi(page, { authMe: adminUser });

    await page.goto(adminPath('/perfil'));

    await expect(page.getByRole('heading', { name: /^perfil$/i })).toBeVisible();
    await expect(
      page.getByRole('heading', { name: /sesi.n actual/i }),
    ).toBeVisible();
    const sessionCard = page.getByRole('region', { name: /sesi.n actual/i });

    await expect(
      sessionCard.getByText(adminUser.email, { exact: true }),
    ).toBeVisible();
    await expect(sessionCard.getByText('ADMIN', { exact: true })).toBeVisible();
  });

  test('renders the authenticated users list with the initial query', async ({
    page,
  }) => {
    let usersListUrl: URL | null = null;

    await mockApi(page, {
      authMe: adminUser,
      usersPage,
      onUsersListRequest: (url) => {
        usersListUrl = url;
      },
    });

    await page.goto(adminPath('/usuarios'));

    await expect(
      page.getByRole('heading', { name: /^usuarios$/i }),
    ).toBeVisible();
    await expect(
      page.getByRole('heading', { name: /listado de usuarios/i }),
    ).toBeVisible();
    await expect(
      page.getByRole('cell', { name: 'ana@platformcore.test' }),
    ).toBeVisible();
    await expect(
      page.getByRole('cell', { name: 'beto@platformcore.test' }),
    ).toBeVisible();

    expect(usersListUrl).not.toBeNull();
    expect(usersListUrl?.searchParams.get('page')).toBe('0');
    expect(usersListUrl?.searchParams.get('size')).toBe('10');
    expect(usersListUrl?.searchParams.get('deleted')).toBe('false');
    expect(usersListUrl?.searchParams.get('sortBy')).toBe('createdAt');
    expect(usersListUrl?.searchParams.get('sortDirection')).toBe('desc');
  });

  test('renders the authenticated create user form', async ({ page }) => {
    await mockApi(page, { authMe: adminUser });

    await page.goto(adminPath('/usuarios/nuevo'));

    await expect(
      page.getByRole('heading', { name: /crear usuario/i }).first(),
    ).toBeVisible();
    await expect(page.getByLabel(/^email$/i)).toBeVisible();
    await expect(page.getByLabel(/^contrase/i)).toBeVisible();
    await expect(page.getByLabel(/confirmar contrase/i)).toBeVisible();
    await expect(
      page.getByRole('combobox', { name: /roles/i }),
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: /crear usuario/i }),
    ).toBeVisible();
  });

  test('renders the authenticated edit user form', async ({ page }) => {
    await mockApi(page, {
      authMe: adminUser,
      userDetails,
    });

    await page.goto(adminPath('/usuarios/user-1/editar'));

    await expect(
      page.getByRole('heading', { name: /editar usuario/i }).first(),
    ).toBeVisible();
    await expect(page.getByLabel(/^email$/i)).toHaveValue('ana@platformcore.test');
    await expect(page.getByText('USER')).toBeVisible();
    await expect(page.getByRole('checkbox', { name: /activo/i })).toBeChecked();
    await expect(
      page.getByRole('button', { name: /guardar cambios/i }),
    ).toBeVisible();
  });
});

async function mockApi(page: Page, options: ApiMockOptions): Promise<void> {
  const authMeQueue = Array.isArray(options.authMe)
    ? [...options.authMe]
    : [options.authMe ?? null];

  await page.route('**/v1/**', async (route) => {
    const request = route.request();
    const method = request.method();
    const url = new URL(request.url());

    if (method === 'GET' && url.pathname === '/v1/auth/me') {
      const user =
        authMeQueue.length > 1 ? authMeQueue.shift() : authMeQueue[0];

      if (user) {
        await fulfillJson(route, 200, user);

        return;
      }

      await fulfillJson(route, 401, { title: 'Unauthorized' });

      return;
    }

    if (method === 'POST' && url.pathname === '/v1/auth/login') {
      options.onLoginRequest?.(request.postDataJSON());
      await route.fulfill({ status: 204 });

      return;
    }

    if (method === 'GET' && url.pathname === '/v1/users') {
      options.onUsersListRequest?.(url);
      await fulfillJson(route, 200, options.usersPage ?? usersPage);

      return;
    }

    const userDetailMatch = url.pathname.match(/^\/v1\/users\/([^/]+)$/);
    if (method === 'GET' && userDetailMatch) {
      const id = userDetailMatch[1];
      const user = options.userDetails?.[id] ?? userDetails[id];

      if (user) {
        await fulfillJson(route, 200, user);

        return;
      }

      await fulfillJson(route, 404, { title: 'User not found' });

      return;
    }

    await fulfillJson(route, 404, {
      title: 'Unexpected API call in platformcore-admin e2e smoke test',
      detail: `${method} ${url.pathname}`,
    });
  });
}

async function fulfillJson(
  route: Route,
  status: number,
  body: unknown,
): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}
