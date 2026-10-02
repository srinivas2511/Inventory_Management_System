import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { SessionStore } from '../auth/session.store';
import { AppConfigService } from '../config/app-config.service';
import { LoadingService } from '../http/loading.service';
import { PermissionService } from '../permission/permission.service';
import { NAV_GROUPS, visibleGroups } from './nav.config';

/**
 * Application shell: top bar with the signed-in user, collapsible sidebar showing only what the user's
 * permissions allow, progress bar, routed content (desktop and tablet).
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatProgressBarModule,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  private readonly session = inject(SessionStore);
  private readonly permissions = inject(PermissionService);
  private readonly router = inject(Router);

  protected readonly appName = inject(AppConfigService).appName;
  protected readonly loading = inject(LoadingService).isLoading;
  protected readonly user = this.session.user;
  protected readonly groups = computed(() => visibleGroups(NAV_GROUPS, (required) => this.permissions.check(required)));

  /** Below 1024px (tablet portrait and smaller) the sidebar becomes an overlay drawer. */
  protected readonly isCompact = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 1023.98px)')
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  protected signOut(): void {
    this.session.logout().subscribe(() => void this.router.navigate(['/login']));
  }
}
