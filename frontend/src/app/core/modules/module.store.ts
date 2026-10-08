import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

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
  private readonly http = inject(HttpClient);
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
    this.http.get<ModuleDto[]>('/api/modules').subscribe({
      next: (list) => this._modules.set(list),
      error: () => this._modules.set([]),
    });
  }

  setActive(code: string, active: boolean): Observable<ModuleDto> {
    return this.http
      .put<ModuleDto>(`/api/modules/${code}`, { active })
      .pipe(tap((updated) => this._modules.update((list) => list.map((m) => (m.code === updated.code ? updated : m)))));
  }
}
