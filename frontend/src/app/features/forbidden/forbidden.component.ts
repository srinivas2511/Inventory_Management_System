import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

/** Shown when a route needs a permission the user does not hold (the server would answer 403 as well). */
@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatIconModule],
  template: `
    <section class="box" data-testid="forbidden">
      <mat-icon class="icon" aria-hidden="true">lock</mat-icon>
      <h1>Not permitted</h1>
      <p>Your role does not allow you to open this page. If you think it should, ask an administrator.</p>
      <a mat-flat-button color="primary" routerLink="/dashboard">Back to the dashboard</a>
    </section>
  `,
  styles: `
    .box {
      max-width: 480px;
      margin: 48px auto;
      text-align: center;
    }
    .icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      opacity: 0.6;
    }
  `,
})
export class ForbiddenComponent {}
