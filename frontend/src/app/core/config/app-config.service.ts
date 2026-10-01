import { Injectable } from '@angular/core';

export interface AppConfig {
  apiBaseUrl: string;
  appName: string;
}

const DEFAULTS: AppConfig = { apiBaseUrl: '/api', appName: 'Spring IMS' };

/**
 * Runtime configuration loaded from /config.json before the app starts, so the same build can be deployed to
 * any environment (the file is replaced or mounted at deploy time) without rebuilding.
 */
@Injectable({ providedIn: 'root' })
export class AppConfigService {
  private config: AppConfig = DEFAULTS;

  async load(): Promise<void> {
    try {
      const response = await fetch('config.json', { cache: 'no-store' });
      if (response.ok) {
        this.config = { ...DEFAULTS, ...(await response.json()) };
      }
    } catch {
      this.config = DEFAULTS; // fall back to defaults; the app must still start
    }
  }

  get apiBaseUrl(): string {
    return this.config.apiBaseUrl.replace(/\/+$/, '');
  }

  get appName(): string {
    return this.config.appName;
  }
}
