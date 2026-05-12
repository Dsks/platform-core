import { expect, type Page, test } from '@playwright/test';

test.describe('qomo-web smoke tests', () => {
  test.beforeEach(async ({ page }) => {
    await mockGuestSession(page);
  });

  test('renders the login page', async ({ page }) => {
    await page.goto('/iniciar-sesion');

    await expect(
      page.getByRole('heading', { name: /iniciar sesi/i }),
    ).toBeVisible();
    await expect(page.getByLabel(/^email$/i)).toBeVisible();
    await expect(page.getByLabel(/contrase/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /^entrar$/i })).toBeVisible();
    await expect(
      page.getByRole('link', { name: /crear cuenta/i }),
    ).toHaveAttribute('href', '/registro');
  });

  test('renders the register page', async ({ page }) => {
    await page.goto('/registro');

    await expect(
      page.getByRole('heading', { name: /crear cuenta/i }),
    ).toBeVisible();
    await expect(page.getByLabel(/^email$/i)).toBeVisible();
    await expect(page.getByLabel(/^contrase/i)).toBeVisible();
    await expect(page.getByLabel(/confirmar contrase/i)).toBeVisible();
    await expect(
      page.getByRole('button', { name: /crear cuenta/i }),
    ).toBeVisible();
    await expect(
      page.getByRole('link', { name: /iniciar sesi/i }),
    ).toHaveAttribute('href', '/iniciar-sesion');
  });

  test('renders the account verification page', async ({ page }) => {
    await page.goto('/verificar-cuenta');

    await expect(
      page.getByRole('heading', { name: /verificar cuenta/i }),
    ).toBeVisible();
    await expect(page.getByLabel(/c.*digo/i)).toBeVisible();
    await expect(
      page.getByRole('button', { name: /verificar cuenta/i }),
    ).toBeVisible();
  });

  for (const protectedPath of ['/panel', '/perfil'] as const) {
    test(`redirects unauthenticated users from ${protectedPath} to login`, async ({
      page,
    }) => {
      await page.goto(protectedPath);

      await expect(page).toHaveURL(/\/iniciar-sesion$/);
    });
  }

  for (const legacyPath of ['/dashboard', '/profile'] as const) {
    test(`redirects unauthenticated legacy route ${legacyPath} to login`, async ({
      page,
    }) => {
      await page.goto(legacyPath);

      await expect(page).toHaveURL(/\/iniciar-sesion$/);
    });
  }
});

async function mockGuestSession(page: Page): Promise<void> {
  await page.route('**/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());

    if (request.method() === 'GET' && url.pathname === '/v1/auth/me') {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Unauthorized',
        }),
      });

      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({
        title: 'Unexpected API call in qomo-web e2e smoke test',
        detail: `${request.method()} ${url.pathname}`,
      }),
    });
  });
}
