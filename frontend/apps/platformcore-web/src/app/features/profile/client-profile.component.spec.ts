import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionStore } from '@platformcore/shared-auth';
import { CurrentUser } from '@platformcore/shared-models';
import { ClientProfileComponent } from './client-profile.component';

const user: CurrentUser = {
  id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  email: 'user@example.com',
  active: true,
  emailVerified: true,
  roles: ['CLIENT'],
};

describe('ClientProfileComponent', () => {
  let fixture: ComponentFixture<ClientProfileComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClientProfileComponent],
    }).compileComponents();

    TestBed.inject(SessionStore).setAuthenticated(user);
    fixture = TestBed.createComponent(ClientProfileComponent);
  });

  it('renders the current user identity', () => {
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('user@example.com');
    expect(compiled.textContent).toContain('CLIENT');
  });
});
