import { Directive, EmbeddedViewRef, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';
import { PermissionMode, PermissionService } from './permission.service';

/**
 * Renders its element only if the signed-in user holds the permission:
 * <pre>
 * &lt;button *appHasPermission="'USER_CREATE'"&gt;New user&lt;/button&gt;
 * &lt;li *appHasPermission="['ROLE_MANAGE', 'USER_VIEW']"&gt;...&lt;/li&gt;            (any of)
 * &lt;li *appHasPermission="['A', 'B']; mode: 'all'"&gt;...&lt;/li&gt;                (all of)
 * </pre>
 * Hiding a control is a courtesy: the backend refuses the call regardless.
 */
@Directive({ selector: '[appHasPermission]', standalone: true })
export class HasPermissionDirective {
  readonly appHasPermission = input.required<string | readonly string[]>();
  readonly appHasPermissionMode = input<PermissionMode>('any');

  private readonly template = inject(TemplateRef<unknown>);
  private readonly container = inject(ViewContainerRef);
  private readonly permissions = inject(PermissionService);
  private view: EmbeddedViewRef<unknown> | null = null;

  constructor() {
    effect(() => {
      const allowed = this.permissions.check(this.appHasPermission(), this.appHasPermissionMode());
      if (allowed && !this.view) {
        this.view = this.container.createEmbeddedView(this.template);
      } else if (!allowed && this.view) {
        this.container.clear();
        this.view = null;
      }
    });
  }
}
