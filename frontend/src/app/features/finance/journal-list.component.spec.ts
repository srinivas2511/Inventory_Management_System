import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { JournalListComponent } from './journal-list.component';

describe('JournalListComponent', () => {
  let fixture: ComponentFixture<JournalListComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[]): void {
    TestBed.configureTestingModule({
      imports: [JournalListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'acc',
      fullName: 'Accountant',
      email: 'a@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(JournalListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend
      .expectOne((r) => r.url === '/api/journals')
      .flush({
        content: [
          {
            id: 5,
            journalNumber: 'JNL/26/00005',
            journalType: 'GL',
            postingDate: '2026-10-07',
            periodId: 7,
            description: 'Opening balance correction',
            currencyCode: 'INR',
            status: 'DRAFT',
            lines: [],
          },
        ],
        totalElements: 1,
        totalPages: 1,
        size: 20,
        number: 0,
      });
    fixture.detectChanges();
  }

  const newJournalLink = () => (fixture.nativeElement as HTMLElement).querySelector('a[href="/finance/journals/new"]');

  it('offers New journal to a GL_POST holder', () => {
    setup(['GL_VIEW', 'GL_POST']);

    expect(newJournalLink()).not.toBeNull();
  });

  it('hides New journal from a read-only user', () => {
    setup(['GL_VIEW']);

    expect(newJournalLink()).toBeNull();
  });

  it('links each journal number to its detail screen', () => {
    setup(['GL_VIEW']);

    expect((fixture.nativeElement as HTMLElement).querySelector('a[href="/finance/journals/5"]')).not.toBeNull();
    backend.verify();
  });
});
