'use strict';
const collections = { products:'product', invoices:'sale', repairs:'repair', customers:'customer',
  suppliers:'supplier', installments:'installment', ewallets:'wallet', expenses:'expense',
  cashSessions:'cash_session', supportRequests:'support_request' };
function diffRecords(previous, next) {
  const changes = [];
  for (const [collection, entityType] of Object.entries(collections)) {
    const oldRows = new Map((previous?.[collection] || []).map(row => [String(row.id), row]));
    const newRows = new Map((next?.[collection] || []).map(row => [String(row.id), row]));
    for (const [id, row] of newRows) {
      if (!row.id || JSON.stringify(oldRows.get(id)) === JSON.stringify(row)) continue;
      const payload = { ...row };
      if (entityType === 'product') {
        payload.salePrice = Number(row.price ?? row.salePrice ?? 0);
        payload.purchasePrice = Number(row.cost ?? row.purchasePrice ?? 0);
      }
      changes.push({entityType, entityId:id, action:'upsert', payload});
    }
    for (const [id] of oldRows) if (!newRows.has(id)) changes.push({entityType,entityId:id,action:'delete',payload:{}});
  }
  return changes;
}
module.exports = { diffRecords };
