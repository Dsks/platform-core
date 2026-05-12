import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class EmailVerificationFlowStore {
  private readonly emailSignal = signal<string | null>(null);

  readonly email = this.emailSignal.asReadonly();

  setEmail(email: string): void {
    const normalizedEmail = email.trim();

    this.emailSignal.set(normalizedEmail || null);
  }

  clear(): void {
    this.emailSignal.set(null);
  }
}
