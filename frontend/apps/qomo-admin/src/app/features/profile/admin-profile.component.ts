import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { SessionStore } from '@qomo/shared-auth';

@Component({
  selector: 'admin-profile',
  templateUrl: './admin-profile.component.html',
  styleUrl: './admin-profile.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminProfileComponent {
  protected readonly currentUser = inject(SessionStore).currentUser;
}
