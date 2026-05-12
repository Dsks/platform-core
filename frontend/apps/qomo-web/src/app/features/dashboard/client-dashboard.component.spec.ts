import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ClientDashboardComponent } from './client-dashboard.component';

describe('ClientDashboardComponent', () => {
  let fixture: ComponentFixture<ClientDashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClientDashboardComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ClientDashboardComponent);
  });

  it('renders the client panel placeholder', () => {
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Panel');
  });
});
