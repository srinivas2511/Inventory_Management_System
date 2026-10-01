import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';
import { LoadingService } from '../http/loading.service';
import { visibleGroups } from './nav.config';

/** Application shell: top bar, collapsible left sidebar, progress bar, routed content (desktop and tablet). */
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
    MatProgressBarModule,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  protected readonly appName = inject(AppConfigService).appName;
  protected readonly loading = inject(LoadingService).isLoading;
  protected readonly groups = visibleGroups();

  /** Below 1024px (tablet portrait and smaller) the sidebar becomes an overlay drawer. */
  protected readonly isCompact = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 1023.98px)')
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
}
