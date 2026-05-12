import { Injectable } from '@angular/core';
import type { CsrfResponse } from '@platformcore/shared-models';

@Injectable({ providedIn: 'root' })
export class CsrfTokenStore {
  private token: CsrfResponse | null = null;

  snapshot(): CsrfResponse | null {
    return this.token;
  }

  set(token: CsrfResponse): void {
    this.token = token;
  }

  clear(): void {
    this.token = null;
  }
}
