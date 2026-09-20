const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('skybarechDesktop', {
  version: () => ipcRenderer.invoke('app:version'),
  loadSnapshot: () => ipcRenderer.invoke('store:load-snapshot'),
  saveSnapshot: (snapshot, change = {}) => ipcRenderer.invoke('store:save-snapshot', snapshot, change),
  clearLocalData: () => ipcRenderer.invoke('store:clear-local-data'),
  syncStatus: () => ipcRenderer.invoke('sync:status'),
  configureSync: (settings) => ipcRenderer.invoke('sync:configure', settings),
  syncNow: () => ipcRenderer.invoke('sync:run'),
  checkLicense: () => ipcRenderer.invoke('license:check'),
  onLicenseRevoked: (callback) => {
    const listener = (_event, payload) => callback(payload || {});
    ipcRenderer.on('license:revoked', listener);
    return () => ipcRenderer.removeListener('license:revoked', listener);
  },
  onSyncCompleted: (callback) => {
    const listener = (_event, payload) => callback(payload || {});
    ipcRenderer.on('sync:completed', listener);
    return () => ipcRenderer.removeListener('sync:completed', listener);
  },
  changePassword: (currentPassword, newPassword) => ipcRenderer.invoke('auth:password', { currentPassword, newPassword }),
  onlineLogin: (apiBaseUrl, identity, password) => ipcRenderer.invoke('auth:login', { apiBaseUrl, identity, password }),
  shopAdminControl: () => ipcRenderer.invoke('shop-admin:control'),
  saveShopUser: (input) => ipcRenderer.invoke('shop-admin:user-save', input || {}),
  setShopUserStatus: (input) => ipcRenderer.invoke('shop-admin:user-status', input || {}),
  verifyActivation: (apiBaseUrl, code, mobile, temporaryPassword) => ipcRenderer.invoke('activation:verify', { apiBaseUrl, code, mobile, temporaryPassword }),
  switchActivationShop: (shopId, switchToken) => ipcRenderer.invoke('activation:switch-shop', { shopId, switchToken }),
  completeActivation: (apiBaseUrl, activation, newPassword) => ipcRenderer.invoke('activation:complete', { apiBaseUrl, activation, newPassword })
});
