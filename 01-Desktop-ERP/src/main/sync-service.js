'use strict';

class RemoteHttpError extends Error {
  constructor(status, code, message) {
    super(message || `HTTP ${status}`);
    this.name = 'RemoteHttpError';
    this.status = Number(status) || 0;
    this.code = String(code || '');
  }
}

class LicenseRevokedError extends Error {
  constructor(message) {
    super(message || 'Shop access was removed by the platform administrator.');
    this.name = 'LicenseRevokedError';
  }
}

class DesktopSyncService {
  constructor(store, onRevoked = () => {}, onComplete = () => {}) {
    this.store = store;
    this.onRevoked = onRevoked;
    this.onComplete = onComplete;
    this.running = false;
    this.timer = null;
    this.startupTimer = null;
  }

  start() {
    this.stop();
    this.startupTimer = setTimeout(() => this.run().catch(() => {}), 1_500);
    this.timer = setInterval(() => this.run().catch(() => {}), 15_000);
  }

  stop() {
    if (this.timer) clearInterval(this.timer);
    if (this.startupTimer) clearTimeout(this.startupTimer);
    this.timer = null;
    this.startupTimer = null;
  }

  run() {
    if (this.inFlight) return this.inFlight;
    this.inFlight = this.execute().finally(() => { this.inFlight = null; });
    return this.inFlight;
  }

  async execute() {
    if (this.running) return { skipped: true, reason: 'already_running' };
    let settings = this.store.settings(true);
    if (!settings.apiBaseUrl || !settings.accessToken) return { skipped: true, reason: 'not_configured', status: this.store.status() };
    const rows = this.store.pending(100);
    const ids = rows.map((row) => row.operation_id);
    this.running = true;
    if (ids.length) this.store.markSyncing(ids);
    try {
      settings = await this.ensureEntitled(settings);
      await this.post(`${settings.apiBaseUrl}/v1/devices/register`, settings, {
        device_id: settings.deviceId,
        platform: 'desktop',
      });
      let uploaded = 0;
      if (rows.length) {
        const data = await this.post(`${settings.apiBaseUrl}/v1/sync/push`, settings, { operations: rows.map((row) => ({
          operation_id: row.operation_id,
          entity_type: row.entity_type,
          entity_id: row.entity_id,
          action: row.action_name,
          payload: JSON.parse(row.payload_json),
          device_id: settings.deviceId,
        })) });
        if (!data.success || !Array.isArray(data.results)) throw new Error('Invalid sync response.');
        const acknowledged = new Set(data.results.map(r => r.operation_id));
        const results = [...data.results, ...ids.filter(id => !acknowledged.has(id)).map(id => ({operation_id:id,status:'failed',error:'Server did not acknowledge this change.'}))];
        this.store.markResults(results);
        uploaded = results.filter(r => ['applied','duplicate'].includes(r.status)).length;
      }
      let downloaded = 0;
      let cursor = settings.pullCursor || 0;
      let pulled;
      do {
        pulled = await this.pull(`${settings.apiBaseUrl}/v1/sync/pull?cursor=${encodeURIComponent(cursor)}&limit=500`, settings);
        if (!pulled.success || !Array.isArray(pulled.records) || !Number.isFinite(Number(pulled.next_cursor))) throw new Error('Invalid pull response.');
        const next = Number(pulled.next_cursor);
        if (next < cursor || (pulled.records.length && next <= cursor)) throw new Error('Server sync cursor did not advance.');
        this.store.applyRemote(pulled.records, next);
        cursor = next;
        downloaded += pulled.records.length;
      } while (pulled.records.length === 500);
      this.store.syncSucceeded();
      const status = this.store.status();
      const result = { success: !status.failed, uploaded, downloaded, status,
        ...(status.failed ? {error:`${status.failed} change(s) could not be uploaded. Your local records are retained.`} : {}) };
      this.onComplete(result);
      return result;
    } catch (error) {
      if (error instanceof LicenseRevokedError || (error instanceof RemoteHttpError && ['device_blocked','shop_inactive','shop_expired','user_inactive','session_invalid'].includes(error.code))) {
        const status = this.store.markRevoked(error.message);
        this.onRevoked({ reason: error.message, status, code: error.code || 'access_revoked' });
        return { success: false, revoked: true, error: error.message, status };
      }
      const code = error.cause?.code || error.code;
      const message = ['ENOTFOUND','EAI_AGAIN'].includes(code)
        ? 'Cannot resolve the server address. Check internet/DNS and the configured API domain. Saved changes will retry.'
        : error.message || 'Network sync failed';
      const activeIds = new Set(this.store.pending(100).map(row => row.operation_id));
      this.store.markTransportFailure(ids.filter(id => activeIds.has(id)), message);
      const result = { success: false, error: message, status: this.store.status() };
      this.onComplete(result);
      return result;
    } finally {
      this.running = false;
    }
  }

  async ensureEntitled(settings) {
    try {
      const bootstrap = await this.pull(`${settings.apiBaseUrl}/v1/bootstrap`, settings);
      const localShopId = this.store.loadSnapshot()?.snapshot?.shop?.id;
      if (bootstrap.shop?.id && localShopId && String(bootstrap.shop.id) !== String(localShopId)) throw new Error("Server shop does not match this installation. Sign in again.");
      this.store.applyShopProfile(bootstrap.shop);
      this.store.markEntitled();
      return settings;
    } catch (error) {
      if (error instanceof RemoteHttpError && ['shop_inactive','shop_expired','user_inactive','device_blocked'].includes(error.code)) {
        throw new LicenseRevokedError(error.message);
      }
      if (!(error instanceof RemoteHttpError) || error.status !== 401) throw error;
    }

    if (!settings.refreshToken) throw new LicenseRevokedError();
    try {
      const refreshed = await this.postPublic(`${settings.apiBaseUrl}/v1/auth/refresh`, {
        refresh_token: settings.refreshToken,
      });
      const tokens = refreshed?.tokens || {};
      if (!tokens.access_token || !tokens.refresh_token) throw new Error('Server returned invalid refresh credentials.');
      this.store.configure({
        apiBaseUrl: settings.apiBaseUrl,
        accessToken: tokens.access_token,
        refreshToken: tokens.refresh_token,
      });
      return this.store.settings(true);
    } catch (error) {
      if (error instanceof RemoteHttpError && [401, 403].includes(error.status)) {
        throw new LicenseRevokedError(error.message);
      }
      throw error;
    }
  }

  async post(url, settings, body) {
    const response = await fetch(url, {
    signal: AbortSignal.timeout(30000),
      method: 'POST',
        headers: { Authorization: `Bearer ${settings.accessToken}`, 'Content-Type': 'application/json', 'X-Client-Version': 'desktop-1.3.28', 'X-Device-ID': settings.deviceId },
      body: JSON.stringify(body),
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok || data.success === false) {
      throw new RemoteHttpError(response.status, data?.error?.code, data?.error?.message || `HTTP ${response.status}`);
    }
    return data;
  }

  async pull(url, settings) {
    const response = await fetch(url, {
    signal: AbortSignal.timeout(30000),
      headers: { Authorization: `Bearer ${settings.accessToken}`, Accept: 'application/json', 'X-Client-Version': 'desktop-1.3.28', 'X-Device-ID': settings.deviceId },
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok || data.success === false) {
      throw new RemoteHttpError(response.status, data?.error?.code, data?.error?.message || `HTTP ${response.status}`);
    }
    return data;
  }

  async postPublic(url, body) {
    const response = await fetch(url, {
    signal: AbortSignal.timeout(30000),
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'X-Client-Version': 'desktop-1.3.28' },
      body: JSON.stringify(body),
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok || data.success === false) {
      throw new RemoteHttpError(response.status, data?.error?.code, data?.error?.message || `HTTP ${response.status}`);
    }
    return data;
  }
}

module.exports = { DesktopSyncService };
