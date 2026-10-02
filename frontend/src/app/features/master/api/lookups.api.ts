import { HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { PartnerRef } from '../models';
import { MaterialsApi } from './materials.api';

/** Typeahead sources for pickers: they return only id, code and name of active records. */
@Injectable({ providedIn: 'root' })
export class LookupsApi {
  private readonly api = inject(ApiService);
  private readonly materials = inject(MaterialsApi);

  customers(q: string): Observable<PartnerRef[]> {
    return this.api.get<PartnerRef[]>('/lookups/customers', new HttpParams().set('q', q));
  }

  materialsMatching(q: string): Observable<PartnerRef[]> {
    return this.materials
      .list({ q, page: 0, size: 20, sort: 'name,asc', filters: { active: 'true' } })
      .pipe(map((page) => page.content.map(({ id, code, name }) => ({ id, code, name }))));
  }
}
