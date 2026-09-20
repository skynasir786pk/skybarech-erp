'use strict';

const Database = require('better-sqlite3');
const crypto = require('crypto');
const { diffRecords } = require('./snapshot-diff');

class LocalFirstStore {
  constructor(filePath, tokenCodec) {
    this.tokenCodec = tokenCodec;
    this.db = new Database(filePath);
    this.db.pragma('journal_mode = WAL');
    this.db.pragma('foreign_keys = ON');
    this.db.pragma('busy_timeout = 5000');
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS app_state (
        id INTEGER PRIMARY KEY CHECK (id = 1),
        snapshot_json TEXT NOT NULL,
        updated_at TEXT NOT NULL
      );
      CREATE TABLE IF NOT EXISTS sync_outbox (
        operation_id TEXT PRIMARY KEY,
        entity_type TEXT NOT NULL,
        entity_id TEXT NOT NULL,
        action_name TEXT NOT NULL CHECK (action_name IN ('upsert','delete')),
        payload_json TEXT NOT NULL,
        status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','syncing','failed','synced')),
        attempts INTEGER NOT NULL DEFAULT 0,
        last_error TEXT NOT NULL DEFAULT '',
        next_retry_at TEXT NULL,
        created_at TEXT NOT NULL,
        synced_at TEXT NULL
      );
      CREATE INDEX IF NOT EXISTS idx_outbox_ready ON sync_outbox(status, next_retry_at, created_at);
      CREATE TABLE IF NOT EXISTS sync_settings (
        id INTEGER PRIMARY KEY CHECK (id = 1),
        api_base_url TEXT NOT NULL DEFAULT '',
        access_token TEXT NOT NULL DEFAULT '',
        refresh_token TEXT NOT NULL DEFAULT '',
        pull_cursor INTEGER NOT NULL DEFAULT 0,
        device_id TEXT NOT NULL,
        last_sync_at TEXT NULL,
        last_sync_error TEXT NOT NULL DEFAULT '',
        license_status TEXT NOT NULL DEFAULT 'unknown',
        last_entitlement_at TEXT NULL,
        revoked_reason TEXT NOT NULL DEFAULT ''
      );
    `);
    const settingColumns = new Set(this.db.prepare('PRAGMA table_info(sync_settings)').all().map((column) => column.name));
    if (!settingColumns.has('license_status')) this.db.exec("ALTER TABLE sync_settings ADD COLUMN license_status TEXT NOT NULL DEFAULT 'unknown'");
    if (!settingColumns.has('last_entitlement_at')) this.db.exec('ALTER TABLE sync_settings ADD COLUMN last_entitlement_at TEXT NULL');
    if (!settingColumns.has('revoked_reason')) this.db.exec("ALTER TABLE sync_settings ADD COLUMN revoked_reason TEXT NOT NULL DEFAULT ''");
    this.db.prepare(`
      INSERT OR IGNORE INTO sync_settings (id, device_id)
      VALUES (1, ?)
    `).run(`desktop-${crypto.randomUUID()}`);
  }

  loadSnapshot() {
    const row = this.db.prepare('SELECT snapshot_json, updated_at FROM app_state WHERE id = 1').get();
    if (!row) return null;
    return { snapshot: JSON.parse(row.snapshot_json), updatedAt: row.updated_at };
  }

  saveSnapshot(snapshot, change = {}) {
    const previous = this.loadSnapshot()?.snapshot;
    const beforeId = previous?.shop?.id;
    if (beforeId && snapshot?.shop?.id && String(beforeId) !== String(snapshot.shop.id)) {
      throw new Error('Shop cache mismatch. Sign in to the correct shop before saving.');
    }
    const changes = change.entityType && change.entityType !== 'device_snapshot'
      ? [change] : diffRecords(previous, snapshot);
    const now = new Date().toISOString();
    const snapshotJson = JSON.stringify(snapshot);
    const entityType = String(change.entityType || 'device_snapshot');
    const entityId = String(change.entityId || snapshot?.shop?.id || 'desktop-local');
    const action = change.action === 'delete' ? 'delete' : 'upsert';
    const payload = change.payload && typeof change.payload === 'object' ? change.payload : snapshot;
    const operationId = String(change.operationId || `desktop-${crypto.randomUUID()}`);
    const transaction = this.db.transaction(() => {
      this.db.prepare(`
        INSERT INTO app_state (id, snapshot_json, updated_at) VALUES (1, ?, ?)
        ON CONFLICT(id) DO UPDATE SET snapshot_json = excluded.snapshot_json, updated_at = excluded.updated_at
      `).run(snapshotJson, now);
      const insert = this.db.prepare(`
        INSERT OR IGNORE INTO sync_outbox
          (operation_id, entity_type, entity_id, action_name, payload_json, status, created_at)
        VALUES (?, ?, ?, ?, ?, 'pending', ?)
      `);
      for (const item of changes) insert.run(item.operationId || `desktop-${crypto.randomUUID()}`,
        item.entityType, String(item.entityId), item.action || 'upsert', JSON.stringify(item.payload), now);
    });
    transaction();
    return { operationId, savedAt: now };
  }

  applyShopProfile(shop) {
    if (!shop || !shop.id) return;
    const saved = this.loadSnapshot();
    if (!saved || String(saved.snapshot.shop?.id) !== String(shop.id)) return;
    const profile = { ...saved.snapshot.shop, id: String(shop.id), shopName: shop.name || saved.snapshot.shop.shopName,
      plan: shop.plan, status: shop.status, expires_at: shop.expires_at, shopCode: shop.code };
    if (shop.owner_name !== undefined) profile.ownerName = shop.owner_name;
    if (shop.owner_mobile !== undefined) profile.ownerMobile = shop.owner_mobile;
    if (shop.address !== undefined) profile.address = shop.address;
    if (shop.city !== undefined) profile.city = shop.city;
    if (shop.appearance !== undefined) profile.appearance = shop.appearance;
    this.db.prepare('UPDATE app_state SET snapshot_json = ?, updated_at = ? WHERE id = 1')
      .run(JSON.stringify({ ...saved.snapshot, shop: profile }), new Date().toISOString());
  }

  pending(limit = 100) {
    return this.db.prepare(`
      SELECT operation_id, entity_type, entity_id, action_name, payload_json, attempts
      FROM sync_outbox
      WHERE status IN ('pending','failed','syncing')
        AND (next_retry_at IS NULL OR next_retry_at <= ?)
      ORDER BY created_at ASC, rowid ASC LIMIT ?
    `).all(new Date().toISOString(), Math.max(1, Math.min(Number(limit) || 100, 100)));
  }

  markSyncing(ids) {
    if (!ids.length) return;
    const update = this.db.prepare("UPDATE sync_outbox SET status = 'syncing', attempts = attempts + 1 WHERE operation_id = ?");
    this.db.transaction(() => ids.forEach((id) => update.run(id)))();
  }

  markResults(results) {
    const synced = this.db.prepare("UPDATE sync_outbox SET status = 'synced', synced_at = ?, last_error = '', next_retry_at = NULL WHERE operation_id = ?");
    const failed = this.db.prepare("UPDATE sync_outbox SET status = 'failed', last_error = ?, next_retry_at = ? WHERE operation_id = ?");
    const now = new Date().toISOString();
    this.db.transaction(() => {
      for (const result of results) {
        if (['applied', 'duplicate'].includes(result.status)) synced.run(now, result.operation_id);
        else failed.run(String(result.error || 'server_rejected'), new Date(Date.now() + 60_000).toISOString(), result.operation_id);
      }
    })();
  }

  retryFailuresNow() {
    this.db.prepare("UPDATE sync_outbox SET next_retry_at = NULL WHERE status = 'failed'").run();
  }

  preserveLegacySnapshots() {
    // Keep old full-cache uploads for recovery, but never replay them over newer
    // Android records. Only explicit record edits are uploaded from now on.
    this.db.exec('CREATE TABLE IF NOT EXISTS legacy_sync_archive AS SELECT * FROM sync_outbox WHERE 0');
    this.db.transaction(() => {
      this.db.exec("INSERT INTO legacy_sync_archive SELECT * FROM sync_outbox WHERE entity_type = 'device_snapshot'");
      this.db.exec("DELETE FROM sync_outbox WHERE entity_type = 'device_snapshot'");
    })();
  }

  markTransportFailure(ids, message) {
    const update = this.db.prepare(`
      UPDATE sync_outbox SET status = 'failed', last_error = ?,
        next_retry_at = datetime('now', '+' || MIN(3600, (30 * (1 << MIN(attempts, 7)))) || ' seconds')
      WHERE operation_id = ?
    `);
    this.db.transaction(() => ids.forEach((id) => update.run(String(message).slice(0, 500), id)))();
    this.db.prepare('UPDATE sync_settings SET last_sync_error = ? WHERE id = 1').run(String(message).slice(0, 500));
  }

  status() {
    const counts = this.db.prepare(`
      SELECT
        SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) AS pending,
        SUM(CASE WHEN status = 'syncing' THEN 1 ELSE 0 END) AS syncing,
        SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) AS failed,
        SUM(CASE WHEN status = 'synced' THEN 1 ELSE 0 END) AS synced
      FROM sync_outbox
    `).get();
    const settings = this.settings(false);
    return { pending: counts.pending || 0, syncing: counts.syncing || 0, failed: counts.failed || 0, synced: counts.synced || 0, ...settings };
  }

  describeShopConflict(shopId) {
    const saved = this.loadSnapshot()?.snapshot;
    const profileShopId = saved?.shop?.id;
    const currentShopId = profileShopId && profileShopId !== 'SHOP-LOCAL-001' ? profileShopId : saved?.session?.shop_id;
    const targetShopId = String(shopId || '');
    if (!currentShopId || currentShopId === 'SHOP-LOCAL-001' || String(currentShopId) === targetShopId) return null;
    const status = this.status();
    const collections = ['products', 'invoices', 'repairs', 'customers', 'suppliers', 'installments', 'ewallets', 'expenses', 'cashSessions', 'supportRequests'];
    const localRecords = collections.reduce((total, key) => total + (Array.isArray(saved?.[key]) ? saved[key].length : 0), 0);
    return {
      currentShopId: String(currentShopId),
      currentShopName: String(saved?.shop?.shopName || saved?.shop?.name || 'Existing shop'),
      targetShopId,
      localRecords,
      unsyncedChanges: Number(status.pending || 0) + Number(status.syncing || 0) + Number(status.failed || 0),
    };
  }

  shopSwitchBackup() {
    const saved = this.loadSnapshot();
    return {
      format: 'skybarech-shop-switch-backup',
      version: 1,
      createdAt: new Date().toISOString(),
      shopId: String(saved?.snapshot?.shop?.id || saved?.snapshot?.session?.shop_id || ''),
      snapshot: saved?.snapshot || {},
      outbox: this.db.prepare('SELECT * FROM sync_outbox ORDER BY created_at, rowid').all(),
      sync: this.status(),
    };
  }

  settings(includeSecrets = false) {
    const row = this.db.prepare(`
      SELECT api_base_url AS apiBaseUrl, access_token AS accessToken,
        refresh_token AS refreshToken, pull_cursor AS pullCursor,
        device_id AS deviceId, last_sync_at AS lastSyncAt, last_sync_error AS lastSyncError,
        license_status AS licenseStatus, last_entitlement_at AS lastEntitlementAt,
        revoked_reason AS revokedReason
      FROM sync_settings WHERE id = 1
    `).get();
    const base = {
      apiBaseUrl: row.apiBaseUrl,
      pullCursor: row.pullCursor,
      deviceId: row.deviceId,
      lastSyncAt: row.lastSyncAt,
      lastSyncError: row.lastSyncError,
      licenseStatus: row.licenseStatus || 'unknown',
      lastEntitlementAt: row.lastEntitlementAt,
      revokedReason: row.revokedReason || '',
      configured: Boolean(row.apiBaseUrl && row.accessToken),
    };
    if (!includeSecrets) return base;
    return {
      ...base,
      accessToken: row.accessToken ? this.tokenCodec.decrypt(row.accessToken) : '',
      refreshToken: row.refreshToken ? this.tokenCodec.decrypt(row.refreshToken) : '',
    };
  }

  configure({ apiBaseUrl = '', accessToken = '', refreshToken = '' }) {
    const base = String(apiBaseUrl).trim().replace(/\/+$/, '');
    if (base && !/^https:\/\//i.test(base)) throw new Error('HTTPS API URL is required.');
    if ((accessToken || refreshToken) && !this.tokenCodec?.available()) throw new Error('Secure operating-system token storage is unavailable.');
    const hasSession = Boolean(accessToken && refreshToken);
    this.db.prepare(`
      UPDATE sync_settings
      SET api_base_url = ?, access_token = ?, refresh_token = ?, last_sync_error = '',
          license_status = ?, last_entitlement_at = ?, revoked_reason = ''
      WHERE id = 1
    `).run(
      base,
      accessToken ? this.tokenCodec.encrypt(String(accessToken)) : '',
      refreshToken ? this.tokenCodec.encrypt(String(refreshToken)) : '',
      hasSession ? 'active' : 'unknown',
      hasSession ? new Date().toISOString() : null,
    );
    return this.status();
  }

  /**
   * A device may only hold one shop cache.  This is called before a fresh
   * remote activation/login when the stored cache belongs to a different (or
   * demo) shop, so stale records can never be pushed into the new shop.
   */
  resetForRemoteShop(shopId) {
    if (!shopId) throw new Error('A remote shop ID is required to reset local cache.');
    const saved = this.loadSnapshot()?.snapshot;
    if (String(saved?.shop?.id || '') === String(shopId)) return { cleared: false, shopId: String(shopId) };
    this.archiveLocalData();
    const transaction = this.db.transaction(() => {
      this.db.prepare('DELETE FROM app_state').run();
      this.db.prepare('INSERT INTO app_state (id, snapshot_json, updated_at) VALUES (1, ?, ?)')
        .run(JSON.stringify({shop:{id:String(shopId)},session:{shop_id:String(shopId)}}), new Date().toISOString());
      this.db.prepare('DELETE FROM sync_outbox').run();
      this.db.prepare(`
        UPDATE sync_settings
        SET api_base_url = '', access_token = '', refresh_token = '', pull_cursor = 0,
            last_sync_at = NULL, last_sync_error = '', license_status = 'unknown',
            last_entitlement_at = NULL, revoked_reason = ''
        WHERE id = 1
      `).run();
    });
    transaction();
    return { cleared: true, shopId: String(shopId) };
  }

  clearAllLocalData() {
    if (this.status().pending || this.status().syncing || this.status().failed) throw new Error('Unsynced changes exist. Reconnect and sync before clearing the cache.');
    this.archiveLocalData();
    const transaction = this.db.transaction(() => {
      this.db.prepare('DELETE FROM app_state').run();
      this.db.prepare('DELETE FROM sync_outbox').run();
      this.db.prepare("UPDATE sync_settings SET pull_cursor = 0, last_sync_at = NULL, last_sync_error = '' WHERE id = 1").run();
    });
    transaction();
    return { cleared: true };
  }

  archiveLocalData() {
    this.db.exec('CREATE TABLE IF NOT EXISTS local_recovery (id INTEGER PRIMARY KEY, saved_at TEXT, snapshot_json TEXT, outbox_json TEXT)');
    this.db.prepare('INSERT INTO local_recovery (saved_at, snapshot_json, outbox_json) VALUES (?, ?, ?)')
      .run(new Date().toISOString(), JSON.stringify(this.loadSnapshot()?.snapshot || {}), JSON.stringify(this.db.prepare('SELECT * FROM sync_outbox').all()));
  }

  markEntitled() {
    this.db.prepare(`
      UPDATE sync_settings
      SET license_status = 'active', last_entitlement_at = ?, revoked_reason = '', last_sync_error = ''
      WHERE id = 1
    `).run(new Date().toISOString());
  }

  markRevoked(reason) {
    const message = String(reason || 'Shop access was removed by the platform administrator.').slice(0, 500);
    this.db.prepare(`
      UPDATE sync_settings
      SET access_token = '', refresh_token = '', license_status = 'revoked',
          last_entitlement_at = ?, revoked_reason = ?, last_sync_error = ?
      WHERE id = 1
    `).run(new Date().toISOString(), message, message);
    return this.status();
  }

  syncSucceeded() {
    this.db.prepare("UPDATE sync_settings SET last_sync_at = ?, last_sync_error = '' WHERE id = 1").run(new Date().toISOString());
  }

  applyRemote(records, nextCursor) {
    if (!Array.isArray(records)) throw new Error('Remote records must be an array.');
    const current = this.loadSnapshot()?.snapshot || {};
    const collectionMap = {
      product: 'products', sale: 'invoices', repair: 'repairs', customer: 'customers',
      supplier: 'suppliers', installment: 'installments', wallet: 'ewallets',
      expense: 'expenses', cash_session: 'cashSessions', support_request: 'supportRequests',
    };
    const mergeOne = (collection, id, payload, deleted = false) => {
      if (!Array.isArray(current[collection])) current[collection] = [];
      const index = current[collection].findIndex((item) => String(item.id) === String(id));
      if (deleted) {
        if (index >= 0) current[collection].splice(index, 1);
        return;
      }
      const normalized = { ...payload, id };
      if (normalized.dateLabel && !normalized.date) normalized.date = normalized.dateLabel;
      if (normalized.timeLabel && !normalized.time) normalized.time = normalized.timeLabel;
      if (collection === 'products') {
        if (normalized.salePrice !== undefined) normalized.price = Number(normalized.salePrice || 0);
        if (normalized.purchasePrice !== undefined) normalized.cost = Number(normalized.purchasePrice || 0);
      }
      if (collection === 'repairs' && normalized.amount !== undefined && normalized.cost === undefined) normalized.cost = Number(normalized.amount || 0);
      if (collection === 'invoices') {
        if (!Array.isArray(normalized.items) || !normalized.items.length) {
          normalized.items = [{ name: normalized.item || 'Sale', qty: 1, price: Number(normalized.total || 0) }];
        }
        if (!normalized.date) normalized.date = normalized.time || normalized.timeLabel || '';
      }
      if (collection === 'ewallets') {
        if (normalized.party && !normalized.customer) normalized.customer = normalized.party;
        if (normalized.reference && !normalized.note) normalized.note = normalized.reference;
      }
      if (collection === 'cashSessions') {
        if (normalized.openingCash !== undefined && normalized.opening === undefined) normalized.opening = Number(normalized.openingCash || 0);
        if (normalized.cashSales !== undefined && normalized.sales === undefined) normalized.sales = Number(normalized.cashSales || 0);
        if (normalized.actualCash !== undefined && normalized.closing === undefined) normalized.closing = Number(normalized.actualCash || 0);
      }
      if (collection === 'installments') {
        if (normalized.amount !== undefined && normalized.due === undefined) normalized.due = Number(normalized.amount || 0);
        if (normalized.dueLabel && !normalized.date) normalized.date = normalized.dueLabel;
      }
      if (index >= 0) current[collection][index] = { ...current[collection][index], ...normalized };
      else current[collection].unshift(normalized);
    };
    const mergeSnapshot = (snapshot) => {
      const aliases = {
        products: 'products', sales: 'invoices', invoices: 'invoices', repairs: 'repairs', customers: 'customers',
        suppliers: 'suppliers', installments: 'installments', ewallets: 'ewallets', expenses: 'expenses',
        cashClosings: 'cashSessions', cashSessions: 'cashSessions', supportRequests: 'supportRequests'
      };
      for (const [sourceName, collection] of Object.entries(aliases)) {
        if (!Array.isArray(snapshot?.[sourceName])) continue;
        for (const item of snapshot[sourceName]) if (item?.id) mergeOne(collection, item.id, item, false);
      }
    };
    for (const record of records) {
      const payload = record?.payload && typeof record.payload === 'object' ? record.payload : {};
      if (record.entity_type === 'device_snapshot') {
        mergeSnapshot(payload.snapshot || payload);
        continue;
      }
      const collection = collectionMap[record.entity_type];
      if (collection) mergeOne(collection, record.entity_id, payload, Boolean(record.is_deleted));
    }
    for (const row of this.db.prepare("SELECT * FROM sync_outbox WHERE status IN ('pending','failed','syncing') ORDER BY created_at, rowid").all()) {
      const collection = collectionMap[row.entity_type];
      if (collection) mergeOne(collection, row.entity_id, JSON.parse(row.payload_json), row.action_name === 'delete');
    }
    const now = new Date().toISOString();
    const transaction = this.db.transaction(() => {
      this.db.prepare(`
        INSERT INTO app_state (id, snapshot_json, updated_at) VALUES (1, ?, ?)
        ON CONFLICT(id) DO UPDATE SET snapshot_json = excluded.snapshot_json, updated_at = excluded.updated_at
      `).run(JSON.stringify(current), now);
      this.db.prepare('UPDATE sync_settings SET pull_cursor = ?, last_sync_at = ?, last_sync_error = ? WHERE id = 1')
        .run(Math.max(0, Number(nextCursor) || 0), now, '');
    });
    transaction();
    return { applied: records.length, nextCursor: Math.max(0, Number(nextCursor) || 0) };
  }
}

module.exports = { LocalFirstStore };
