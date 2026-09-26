const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const { LocalFirstStore } = require('../01-Desktop-ERP/src/main/local-store');

const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'skybarech-android-sale-'));
const database = path.join(directory, 'local.sqlite');
const tokenCodec = { available: () => true, encrypt: value => value, decrypt: value => value };

try {
  const store = new LocalFirstStore(database, tokenCodec);
  store.db.prepare('INSERT INTO app_state (id, snapshot_json, updated_at) VALUES (1, ?, ?)')
    .run(JSON.stringify({ shop: { id: 'SHOP-1' }, session: { shop_id: 'SHOP-1' }, invoices: [] }), new Date().toISOString());

  store.applyRemote([{
    entity_type: 'sale',
    entity_id: 'INV-ANDROID-1',
    payload: {
      customer: 'Walk-in Customer',
      item: 'Android sale',
      total: 2450,
      payment: 'Cash',
      timeLabel: '2026-09-26T00:15:00',
      time: '2026-09-26T00:15:00',
    },
  }], 12);

  const invoice = store.loadSnapshot().snapshot.invoices[0];
  assert.equal(invoice.id, 'INV-ANDROID-1');
  assert.equal(invoice.total, 2450);
  assert.equal(invoice.date.slice(0, 10), '2026-09-26', 'Android local sale date must remain available to Today Sales');
  assert.deepEqual(invoice.items, [{ name: 'Android sale', qty: 1, price: 2450 }]);

  process.env.TZ = 'Asia/Karachi';
  const appSource = fs.readFileSync(path.join(__dirname, '../01-Desktop-ERP/src/app.js'), 'utf8');
  const helpers = appSource.match(/const localDateKey[\s\S]*?const today = \(\) => localDateKey\(\);/)?.[0];
  assert.ok(helpers, 'desktop date helpers must remain available');
  const dateContext = {};
  vm.runInNewContext(`${helpers}\nresult = localDateKey(new Date('2026-09-25T21:15:00Z'));`, dateContext);
  assert.equal(dateContext.result, '2026-09-26', 'Today Sales must use Pakistan local date after midnight');

  store.db.close();
  console.log('Android-to-desktop sale sync and local-date regressions passed.');
} finally {
  fs.rmSync(directory, { recursive: true, force: true });
}
