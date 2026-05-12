import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionStore } from '@qomo/shared-auth';
import { CurrentUser } from '@qomo/shared-models';
import { AdminProfileComponent } from './admin-profile.component';

const adminUser: CurrentUser = {
  id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  email: 'admin@example.com',
  active: true,
  emailVerified: true,
  roles: ['ADMIN'],
};

describe('AdminProfileComponent', () => {
  let fixture: ComponentFixture<AdminProfileComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminProfileComponent],
    }).compileComponents();

    TestBed.inject(SessionStore).setAuthenticated(adminUser);
    fixture = TestBed.createComponent(AdminProfileComponent);
  });

  it('renders the current admin identity without logout controls', () => {
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('admin@example.com');
    expect(compiled.textContent).toContain('ADMIN');
    expect(compiled.textContent).not.toContain('Cerrar sesi');
  });
});
