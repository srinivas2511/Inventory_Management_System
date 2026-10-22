import { Pipe, PipeTransform, inject, LOCALE_ID } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AppConfigService } from '../../core/config/app-config.service';

/**
 * Wraps Angular's DatePipe and applies the user's configured timezone (default Asia/Kolkata / IST +05:30).
 * Components should use this pipe instead of DatePipe directly so all dates display in the local timezone.
 */
@Pipe({ name: 'istDate', standalone: true, pure: true })
export class IstDatePipe implements PipeTransform {
  private readonly locale = inject(LOCALE_ID);
  private readonly timezone = inject(AppConfigService).timezone;
  private readonly dp = new DatePipe(this.locale);

  transform(
    value: Date | string | number | null | undefined,
    format: string = 'medium',
    timezone: string = this.timezone,
  ): string | null {
    return this.dp.transform(value, format, timezone);
  }
}
