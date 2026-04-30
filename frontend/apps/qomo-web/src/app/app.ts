import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NxWelcome } from './nx-welcome';
import { HttpClient } from '@angular/common/http';

@Component({
  imports: [NxWelcome, RouterModule],
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected title = 'qomo-web';
  private http = inject(HttpClient);
  private cdr = inject(ChangeDetectorRef);
  out = '';

  pingUsers() {
    this.http.get('/v1/users/ping', { responseType: 'text' }).subscribe((t) => {
      this.out = t;
      this.cdr.detectChanges();
    });
  }

  pingCore() {
    this.http.get('/v1/core/ping', { responseType: 'text' }).subscribe((t) => {
      this.out = t;
      this.cdr.detectChanges();
    });
  }
}
