import { Directive, Input, OnInit, TemplateRef, ViewContainerRef, inject } from '@angular/core';
import { SessionStore } from '../../core/auth/session.store';

@Directive({
  selector: '[appHasPermission]',
  standalone: true,
})
export class HasPermissionDirective implements OnInit {
  @Input('appHasPermission') permission = '';
  @Input('appHasPermissionMode') mode: 'any' | 'all' = 'any';

  private readonly session = inject(SessionStore);
  private readonly tpl = inject(TemplateRef<unknown>);
  private readonly vcr = inject(ViewContainerRef);

  ngOnInit(): void {
    const codes = this.permission.split(',').map((s) => s.trim()).filter(Boolean);
    const allowed =
      this.mode === 'all'
        ? this.session.hasAllPermissions(codes)
        : this.session.hasAnyPermission(codes);
    if (allowed) {
      this.vcr.createEmbeddedView(this.tpl);
    }
  }
}
