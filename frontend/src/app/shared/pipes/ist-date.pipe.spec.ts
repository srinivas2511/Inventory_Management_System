import { TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeEnIn from '@angular/common/locales/en-IN';
import { IstDatePipe } from './ist-date.pipe';
import { AppConfigService } from '../../core/config/app-config.service';

registerLocaleData(localeEnIn);

describe('IstDatePipe', () => {
  let pipe: IstDatePipe;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [IstDatePipe, { provide: LOCALE_ID, useValue: 'en-IN' }, AppConfigService],
    });
    pipe = TestBed.inject(IstDatePipe);
  });

  it('transforms a UTC ISO timestamp to IST display', () => {
    // 2026-10-22T00:00:00Z is 2026-10-22T05:30:00+05:30 in IST
    const result = pipe.transform('2026-10-22T00:00:00Z', 'dd MMM yyyy HH:mm');
    expect(result).toBe('22 Oct 2026 05:30');
  });

  it('formats a date-only string', () => {
    const result = pipe.transform('2026-10-22', 'dd/MM/yyyy');
    expect(result).toBeTruthy();
  });

  it('returns null for null input', () => {
    expect(pipe.transform(null)).toBeNull();
  });

  it('returns null for undefined input', () => {
    expect(pipe.transform(undefined)).toBeNull();
  });

  it('allows overriding the timezone', () => {
    const utcResult = pipe.transform('2026-10-22T00:00:00Z', 'HH:mm', 'UTC');
    expect(utcResult).toBe('00:00');
  });
});
