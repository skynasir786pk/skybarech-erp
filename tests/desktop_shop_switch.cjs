const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { LocalFirstStore } = require('../01-Desktop-ERP/src/main/local-store');

const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'skybarech-shop-switch-'));
const database = path.join(directory, 'local.sqlite');
const tokenCodec = {
  available: () => true,
  encrypt: value => value,
  decrypt: value => value,
};

try {
  const store = new LocalFirstStore(database, tokenCodec);
  const oldSnapshot = {
    shop: { id: 'SHOP-OLD', shopName: 'Old local cache' },
    session: { shop_id: 'SHOP-OLD' },
    products: [{ id: 'P-1', name: 'Saved phone' }],
  };
  store.db.prepare('INSERT INTO app_state (id, snapshot_json, updated_at) VALUES (1, ?, ?)')
    .run(JSON.stringify(oldSnapshot), new Date().toISOString());

  const cleanConflict = store.describeShopConflict('SHOP-ANDROID');
  assert.equal(cleanConflict.currentShopId, 'SHOP-OLD');
  assert.equal(cleanConflict.targetShopId, 'SHOP-ANDROID');
  assert.equal(cleanConflict.localRecords, 1);
  assert.equal(cleanConflict.unsyncedChanges, 0, 'synced stale cache can be backed up and switched automatically');

  store.saveSnapshot({ ...oldSnapshot, products: [...oldSnapshot.products, { id: 'P-2', name: 'Unsynced phone' }] }, {
    entityType: 'products', entityId: 'P-2', payload: { id: 'P-2', name: 'Unsynced phone' },
  });
  const protectedConflict = store.describeShopConflict('SHOP-ANDROID');
  assert.equal(protectedConflict.unsyncedChanges, 1, 'unsynced records require explicit backup confirmation');

  store.resetForRemoteShop('SHOP-ANDROID');
  assert.equal(store.describeShopConflict('SHOP-ANDROID'), null);
  assert.equal(store.loadSnapshot().snapshot.shop.id, 'SHOP-ANDROID');
  store.db.close();
  console.log('Desktop shop-switch regression passed: clean cache auto-switches; unsynced data requires confirmation.');
} finally {
  fs.rmSync(directory, { recursive: true, force: true });
}
