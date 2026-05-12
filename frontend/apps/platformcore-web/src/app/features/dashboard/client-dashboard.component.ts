import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-client-dashboard',
  templateUrl: './client-dashboard.component.html',
  styleUrl: './client-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientDashboardComponent {}
