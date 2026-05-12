import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, Router } from '@angular/router';
import { AuthService, SessionStore } from '@platformcore/shared-auth';
import { CurrentUser } from '@platformcore/shared-models';
import { of } from 'rxjs';
import { AdminLayoutComponent } from './admin-layout.component';

const adminUser: CurrentUser = {
  id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  email: 'admin@example.com',
  active: true,
  emailVerified: true,
  roles: ['ADMIN'],
};

const adminSidebarCollapsedKey = 'platformcore.admin.sidebar.collapsed';

describe('AdminLayoutComponent', () => {
  let fixture: ComponentFixture<AdminLayoutComponent>;
  let authService: {
    logout: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    localStorage.removeItem(adminSidebarCollapsedKey);

    authService = {
      logout: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [AdminLayoutComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: authService },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.removeItem(adminSidebarCollapsedKey);
  });

  function createComponent(): ComponentFixture<AdminLayoutComponent> {
    TestBed.inject(SessionStore).setAuthenticated(adminUser);
    fixture = TestBed.createComponent(AdminLayoutComponent);
    return fixture;
  }

  it('renders navigation, the current user email, and the outlet', () => {
    fixture = createComponent();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('router-outlet')).not.toBeNull();
    expect(compiled.textContent).toContain('Panel');
    expect(compiled.textContent).toContain('Usuarios');
    expect(compiled.textContent).toContain('admin@example.com');
  });

  it('starts expanded and initializes localStorage to false when there is no saved value', () => {
    fixture = createComponent();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const layout = compiled.querySelector('.admin-layout');

    expect(layout?.classList.contains('admin-layout-collapsed')).toBe(false);
    expect(localStorage.getItem(adminSidebarCollapsedKey)).toBe('false');
  });

  it('starts collapsed when localStorage has true', () => {
    localStorage.setItem(adminSidebarCollapsedKey, 'true');

    fixture = createComponent();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const layout = compiled.querySelector('.admin-layout');

    expect(layout?.classList.contains('admin-layout-collapsed')).toBe(true);
    expect(localStorage.getItem(adminSidebarCollapsedKey)).toBe('true');
  });

  it.each(['false', 'invalid'])(
    'starts expanded and stores false when localStorage has %s',
    (savedValue) => {
      localStorage.setItem(adminSidebarCollapsedKey, savedValue);

      fixture = createComponent();
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const layout = compiled.querySelector('.admin-layout');

      expect(layout?.classList.contains('admin-layout-collapsed')).toBe(false);
      expect(localStorage.getItem(adminSidebarCollapsedKey)).toBe('false');
    },
  );

  it('stores the new collapsed value when toggling the sidebar', () => {
    fixture = createComponent();
    fixture.detectChanges();

    fixture.componentInstance.toggleSidebar();
    fixture.detectChanges();

    let compiled = fixture.nativeElement as HTMLElement;
    let layout = compiled.querySelector('.admin-layout');

    expect(layout?.classList.contains('admin-layout-collapsed')).toBe(true);
    expect(localStorage.getItem(adminSidebarCollapsedKey)).toBe('true');

    fixture.componentInstance.toggleSidebar();
    fixture.detectChanges();

    compiled = fixture.nativeElement as HTMLElement;
    layout = compiled.querySelector('.admin-layout');

    expect(layout?.classList.contains('admin-layout-collapsed')).toBe(false);
    expect(localStorage.getItem(adminSidebarCollapsedKey)).toBe('false');
  });

  it('logs out and navigates to login', () => {
    fixture = createComponent();
    const router = TestBed.inject(Router);
    const navigateByUrl = vi
      .spyOn(router, 'navigateByUrl')
      .mockResolvedValue(true);

    authService.logout.mockReturnValue(of(undefined));

    fixture.componentInstance.logout();

    expect(authService.logout).toHaveBeenCalledTimes(1);
    expect(navigateByUrl).toHaveBeenCalledWith('/iniciar-sesion');
  });
});
