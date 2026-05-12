import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { SessionStore } from '@qomo/shared-auth';

@Component({
  selector: 'app-client-profile',
  templateUrl: './client-profile.component.html',
  styleUrl: './client-profile.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientProfileComponent {
  protected readonly currentUser = inject(SessionStore).currentUser;
}
