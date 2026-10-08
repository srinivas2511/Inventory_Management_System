import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { ApiService } from '../api/api.service';

export interface ModuleDto {
  code: string;
  name: string;
  active: boolean;
}

/**
 * Which optional modules an administrator has switched off. The navigation hides their screens; the server refuses
 * their API with MODULE_INACTIVE whatever the browser shows. Until the list is loaded nothing is hidden.
 */
@Injectable({ providedIn: 'root' })
export class ModuleStore {
  private readonly api = inject(ApiService);
  private readonly _modules = signal<ModuleDto[]>([]);

  readonly modules = this._modules.asReadonly();
  readonly inactive = computed(
    () =>
      new Set(
        this._modules()
          .filter((m) => !m.active)
          .map((m) => m.code),
      ),
  );

  load(): void {
    this.api.get<ModuleDto[]>('/modules').subscribe({
      next: (list) => this._modules.set(list),
      error: () => this._modules.set([]),
    });
  }

  setActive(code: string, active: boolean): Observable<ModuleDto> {
    return this.api
      .put<ModuleDto>(`/modules/${code}`, { active })
      .pipe(tap((updated) => this._modules.update((list) => list.map((m) => (m.code === updated.code ? updated : m)))));
  }
}
