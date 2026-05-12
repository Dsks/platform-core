import { TestBed } from '@angular/core/testing';
import { CurrentUser } from '@platformcore/shared-models';
import { SessionStore } from './session-store';

const clientUser: CurrentUser = {
  id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  email: 'client@example.com',
  active: true,
  emailVerified: true,
  roles: ['CLIENT'],
};

const adminUser: CurrentUser = {
  id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  email: 'admin@example.com',
  active: true,
  emailVerified: true,
  roles: ['ADMIN'],
};

describe('SessionStore', () => {
  let store: SessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    store = TestBed.inject(SessionStore);
  });

  it('starts in unknown state without a user', () => {
    expect(store.status()).toBe('unknown');
    expect(store.currentUser()).toBeNull();
    expect(store.isAuthenticated()).toBe(false);
    expect(store.isGuest()).toBe(false);
  });

  it('stores authenticated users only in memory', () => {
    store.setAuthenticated(clientUser);

    expect(store.status()).toBe('authenticated');
    expect(store.currentUser()).toEqual(clientUser);
    expect(store.roles()).toEqual(['CLIENT']);
    expect(store.isAuthenticated()).toBe(true);
    expect(store.isClient()).toBe(true);
    expect(store.isAdmin()).toBe(false);
  });

  it('computes admin and superadmin role helpers', () => {
    store.setAuthenticated(adminUser);

    expect(store.hasAnyRole('ADMIN')).toBe(true);
    expect(store.isAdmin()).toBe(true);
    expect(store.isSuperAdmin()).toBe(false);

    store.setAuthenticated({
      ...adminUser,
      roles: ['SUPERADMIN'],
    });

    expect(store.isAdmin()).toBe(true);
    expect(store.isSuperAdmin()).toBe(true);
  });

  it('can switch to guest and clear back to unknown', () => {
    store.setAuthenticated(clientUser);
    store.setGuest();

    expect(store.status()).toBe('guest');
    expect(store.currentUser()).toBeNull();
    expect(store.isGuest()).toBe(true);

    store.clear();

    expect(store.status()).toBe('unknown');
    expect(store.currentUser()).toBeNull();
  });
});
