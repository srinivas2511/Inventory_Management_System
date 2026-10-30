import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { PartyLedgerComponent } from './party-ledger.component';

describe('PartyLedgerComponent', () => {
  let fixture: ComponentFixture<PartyLedgerComponent>;
  let component: PartyLedgerComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PartyLedgerComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(PartyLedgerComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have partyType and partyId controls', () => {
    expect(component.form.contains('partyType')).toBeTrue();
    expect(component.form.contains('partyId')).toBeTrue();
  });

  it('should start with CUSTOMER as default party type', () => {
    expect(component.form.value.partyType).toBe('CUSTOMER');
  });

  it('should start with no ledger data', () => {
    expect(component.ledger()).toBeNull();
  });
});
