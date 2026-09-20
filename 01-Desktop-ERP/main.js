const { app, BrowserWindow, ipcMain, safeStorage } = require('electron');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { LocalFirstStore } = require('./src/main/local-store');
const { DesktopSyncService } = require('./src/main/sync-service');

let mainWindow;
let localStore;
let syncService;
let saveSyncTimer;
const pendingActivationSwitches = new Map();

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 920,
    minWidth: 900,
    minHeight: 600,
    backgroundColor: '#f5f8ff',
    icon: path.join(__dirname, 'build', 'icon.ico'),
    show: false,
    autoHideMenuBar: true,
    title: 'SkyBarech Mobile Shop ERP',
    ...(process.platform === 'darwin' ? { titleBarStyle: 'hiddenInset' } : {}),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  mainWindow.loadFile(path.join(__dirname, 'src', 'index.html'));
  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.webContents.setZoomFactor(1);
  mainWindow.webContents.setVisualZoomLevelLimits(1, 1).catch(() => {});

  mainWindow.webContents.on('before-input-event', (event, input) => {
    const blockedZoom = (input.control || input.meta) && ['+', '-', '0'].includes(input.key);
    if (blockedZoom) event.preventDefault();
  });

  mainWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (!url.startsWith('file://')) event.preventDefault();
  });
}

app.whenReady().then(() => {
  const tokenCodec = {
    available: () => safeStorage.isEncryptionAvailable(),
    encrypt: (value) => `v1:${safeStorage.encryptString(value).toString('base64')}`,
    decrypt: (value) => {
      if (!String(value).startsWith('v1:')) throw new Error('Unencrypted sync token rejected. Please configure sync again.');
      return safeStorage.decryptString(Buffer.from(String(value).slice(3), 'base64'));
    },
  };
  localStore = new LocalFirstStore(path.join(app.getPath('userData'), 'skybarech-local.sqlite'), tokenCodec);
  localStore.preserveLegacySnapshots();
  syncService = new DesktopSyncService(
    localStore,
    (payload) => {
      if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send('license:revoked', payload);
    },
    (payload) => {
      if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send('sync:completed', payload);
    }
  );
  syncService.start();
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('before-quit', () => syncService?.stop());

ipcMain.handle('app:version', () => app.getVersion());
ipcMain.handle('store:load-snapshot', () => localStore?.loadSnapshot() || null);
ipcMain.handle('store:save-snapshot', (_event, snapshot, change) => {
  const saved = localStore.saveSnapshot(snapshot, change);
  clearTimeout(saveSyncTimer);
  if (localStore.status().pending) saveSyncTimer = setTimeout(() => syncService.run().catch(() => {}), 750);
  return saved;
});
ipcMain.handle('store:clear-local-data', () => localStore.clearAllLocalData());
ipcMain.handle('sync:status', () => localStore.status());
ipcMain.handle('sync:configure', (_event, settings) => localStore.configure(settings || {}));
ipcMain.handle('sync:run', () => { localStore.retryFailuresNow(); return syncService.run(); });
ipcMain.handle('license:check', async () => {
  const result = await syncService.run();
  return { ...result, status: localStore.status() };
});

function assertSameShop(shopId) {
  if (localStore.describeShopConflict(shopId)) {
    const error = new Error('This installation contains another shop. Use a separate OS profile/device to protect unsynced data.');
    error.status = 409; error.code = 'shop_mismatch'; throw error;
  }
}

function validApiBase(value) {
  const base = String(value || '').trim().replace(/\/+$/, '');
  if (!/^https:\/\//i.test(base) || base.includes('YOUR-DOMAIN')) throw new Error('Valid Cloud HTTPS API URL is required.');
  return base;
}

async function apiPost(apiBaseUrl, route, body, accessToken = '') {
  const base = validApiBase(apiBaseUrl);
  const response = await fetch(`${base}${route}`, {
    signal: AbortSignal.timeout(30000),
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}) },
    body: JSON.stringify(body),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload.success === false) {
    const error = new Error(payload?.error?.message || `Server request failed (HTTP ${response.status}).`);
    error.status = response.status;
    error.code = payload?.error?.code || `http_${response.status}`;
    throw error;
  }
  return payload;
}

async function apiGet(apiBaseUrl, route, accessToken = '') {
  const base = validApiBase(apiBaseUrl);
  const response = await fetch(`${base}${route}`, {
    signal: AbortSignal.timeout(30000),
    headers: { Accept: 'application/json', ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}) },
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload.success === false) {
    const error = new Error(payload?.error?.message || `Server request failed (HTTP ${response.status}).`);
    error.status = response.status;
    error.code = payload?.error?.code || `http_${response.status}`;
    throw error;
  }
  return payload;
}

ipcMain.handle('auth:login', async (_event, input = {}) => {
  try {
    const apiBaseUrl = validApiBase(input.apiBaseUrl);
    const result = await apiPost(apiBaseUrl, '/v1/auth/login', { identity: input.identity, password: input.password });
    if (result.user?.role !== 'super_admin') {
      const error = new Error('This offline installation requires a shop owner account. Staff cache isolation is not yet available.');
      error.status = 403; error.code = 'owner_client_required'; throw error;
    }
    const bootstrap = await apiGet(apiBaseUrl, '/v1/bootstrap', result.tokens.access_token);
    assertSameShop(bootstrap.shop?.id);
    const savedId = localStore.loadSnapshot()?.snapshot?.shop?.id || localStore.loadSnapshot()?.snapshot?.session?.shop_id;
    const cacheReset = String(savedId || '') !== String(bootstrap.shop?.id);
    if (cacheReset) localStore.resetForRemoteShop(bootstrap.shop?.id);
    localStore.configure({ apiBaseUrl, accessToken: result.tokens.access_token, refreshToken: result.tokens.refresh_token });
    return { success: true, cacheReset, user: result.user, shop: bootstrap.shop || null, branches: bootstrap.branches || [] };
  } catch (error) {
    const status = Number(error?.status) || 0;
    return {
      success: false,
      authoritative: [401, 403, 404].includes(status),
      offline: status === 0,
      status,
      code: String(error?.code || (status ? `http_${status}` : 'network_unavailable')),
      message: error?.message || 'Online login is unavailable.',
    };
  }
});

async function shopAdminRequest(method, route, body = null) {
  let settings = localStore.settings(true);
  if (!settings.apiBaseUrl || !settings.accessToken) throw new Error('Online shop session is not configured. Login online first.');
  const execute = () => method === 'GET'
    ? apiGet(settings.apiBaseUrl, route, settings.accessToken)
    : apiPost(settings.apiBaseUrl, route, body || {}, settings.accessToken);
  try {
    return await execute();
  } catch (error) {
    if (Number(error?.status) !== 401) throw error;
    await syncService.run();
    settings = localStore.settings(true);
    if (!settings.accessToken) throw error;
    return execute();
  }
}

ipcMain.handle('shop-admin:control', async () => {
  const result = await shopAdminRequest('GET', '/v1/admin/control');
  return result.control || {};
});

ipcMain.handle('shop-admin:user-save', async (_event, input = {}) => {
  const result = await shopAdminRequest('POST', '/v1/admin/users', input);
  return result.user || null;
});

ipcMain.handle('shop-admin:user-status', async (_event, input = {}) => {
  await shopAdminRequest('POST', '/v1/admin/users/status', input);
  return { success: true };
});

ipcMain.handle('activation:verify', async (_event, input = {}) => {
  const result = await apiPost(input.apiBaseUrl, '/v1/activation/verify', {
    code: input.code, mobile: input.mobile, temporary_password: input.temporaryPassword
  });
  const conflict = localStore.describeShopConflict(result.activation?.shop_id);
  if (conflict) {
    const switchToken = crypto.randomUUID();
    const now = Date.now();
    for (const [token, pending] of pendingActivationSwitches) if (pending.expiresAt <= now) pendingActivationSwitches.delete(token);
    pendingActivationSwitches.set(switchToken, { targetShopId: String(result.activation.shop_id), expiresAt: now + (5 * 60 * 1000) });
    result.deviceConflict = { ...conflict, switchToken };
  }
  return result;
});

ipcMain.handle('activation:switch-shop', async (_event, input = {}) => {
  const token = String(input.switchToken || '');
  const pending = pendingActivationSwitches.get(token);
  if (!pending || pending.expiresAt <= Date.now()) {
    pendingActivationSwitches.delete(token);
    throw new Error('Shop switch approval expired. Verify the activation again.');
  }
  if (String(input.shopId || '') !== pending.targetShopId) throw new Error('Verified shop does not match the switch request.');

  syncService.stop();
  try {
    if (syncService.inFlight) await syncService.inFlight.catch(() => {});
    const backup = localStore.shopSwitchBackup();
    const backupDirectory = path.join(app.getPath('userData'), 'shop-switch-backups');
    fs.mkdirSync(backupDirectory, { recursive: true });
    const stamp = new Date().toISOString().replace(/[:.]/g, '-');
    const safeShopId = String(backup.shopId || 'shop').replace(/[^a-z0-9_-]/gi, '-').slice(0, 60);
    const backupPath = path.join(backupDirectory, `${safeShopId}-${stamp}.json`);
    fs.writeFileSync(backupPath, JSON.stringify(backup, null, 2), { encoding: 'utf8', mode: 0o600, flag: 'wx' });
    localStore.resetForRemoteShop(pending.targetShopId);
    pendingActivationSwitches.clear();
    return { success: true, shopId: pending.targetShopId, backupPath, unsyncedChanges: backup.sync.pending + backup.sync.syncing + backup.sync.failed };
  } finally {
    syncService.start();
  }
});

ipcMain.handle('activation:complete', async (_event, input = {}) => {
  const apiBaseUrl = validApiBase(input.apiBaseUrl);
  const activation = input.activation || {};
  assertSameShop(activation.shopId);
  const result = await apiPost(apiBaseUrl, '/v1/activation/complete', {
    activation_id: activation.activationId,
    code: activation.code,
    mobile: activation.mobile,
    temporary_password: activation.temporaryPassword,
    new_password: input.newPassword,
    full_name: 'Shop Owner',
  });
  localStore.resetForRemoteShop(result.user?.shop_id || activation.shopId);
  localStore.configure({ apiBaseUrl, accessToken: result.tokens.access_token, refreshToken: result.tokens.refresh_token });
  const bootstrap = await apiGet(apiBaseUrl, '/v1/bootstrap', result.tokens.access_token);
  localStore.applyShopProfile(bootstrap.shop);
  return { user: result.user };
});

ipcMain.handle('auth:password', async (_event, input = {}) => {
  const settings = await syncService.ensureEntitled(localStore.settings(true));
  const result = await apiPost(settings.apiBaseUrl, '/v1/auth/password', {
    current_password: input.currentPassword, new_password: input.newPassword
  }, settings.accessToken);
  localStore.configure({ apiBaseUrl: settings.apiBaseUrl, accessToken: result.tokens.access_token, refreshToken: result.tokens.refresh_token });
  return { success: true };
});
