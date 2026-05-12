import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, Router } from '@angular/router';
import { AuthService, SessionStore } from '@qomo/shared-auth';
import { CurrentUser } from '@qomo/shared-models';
import { of } from 'rxjs';
import { ClientLayoutComponent } from './client-layout.component';

const user: CurrentUser = {
  id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  email: 'user@example.com',
  active: true,
  emailVerified: true,
  roles: ['CLIENT'],
};

describe('ClientLayoutComponent', () => {
  let fixture: ComponentFixture<ClientLayoutComponent>;
  let authService: {
    logout: ReturnType<typeof vi.fn>;
  };
  let navigateByUrlSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    authService = {
      logout: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [ClientLayoutComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: authService },
      ],
    }).compileComponents();

    const router = TestBed.inject(Router);

    navigateByUrlSpy = vi
      .spyOn(router, 'navigateByUrl')
      .mockResolvedValue(true);

    TestBed.inject(SessionStore).setAuthenticated(user);
    fixture = TestBed.createComponent(ClientLayoutComponent);
  });

  it('renders navigation, the current user email, logo, and the outlet', () => {
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('router-outlet')).not.toBeNull();
    expect(compiled.querySelector('.brand-logo')).not.toBeNull();
    expect(compiled.textContent).toContain('Panel');
    expect(compiled.textContent).toContain('user@example.com');
  });

  it('uses the user email as the profile link', () => {
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const emailLink = compiled.querySelector<HTMLAnchorElement>(
      '.desktop-user-email',
    );

    expect(emailLink?.getAttribute('href')).toBe('/perfil');
  });

  it('logs out and navigates to login', () => {

    authService.logout.mockReturnValue(of(undefined));

    fixture.componentInstance.logout();

    expect(authService.logout).toHaveBeenCalledTimes(1);
    expect(navigateByUrlSpy).toHaveBeenCalledWith('/iniciar-sesion');
  });

  it('opens the mobile menu and closes it when logging out', () => {
    authService.logout.mockReturnValue(of(undefined));

    fixture.componentInstance.toggleMobileMenu();
    fixture.detectChanges();

    let compiled = fixture.nativeElement as HTMLElement;

    expect(
      compiled
        .querySelector('.client-layout')
        ?.classList.contains('client-layout-menu-open'),
    ).toBe(true);

    fixture.componentInstance.logout();
    fixture.detectChanges();

    compiled = fixture.nativeElement as HTMLElement;

    expect(
      compiled
        .querySelector('.client-layout')
        ?.classList.contains('client-layout-menu-open'),
    ).toBe(false);
  });
});
