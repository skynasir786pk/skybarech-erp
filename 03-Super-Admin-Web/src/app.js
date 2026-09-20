const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

const api = window.SkyBarechApi;
const LIVE_REFRESH_MS = 10_000;
let liveRefreshTimer = null;
let liveRefreshRunning = false;
let shopsRevision = "";

const state = {
  user: null,
  page: "dashboard",
  shopFilter: "all",
  shopSearch: "",
  shops: [],
  users: [{ id: "local-admin", name: "Local Admin", email: "", role: "super_admin", status: "active" }],
  rules: [],
  activations: [],
  support: [],
  activity: [],
  permissionShopId: "",
  permissions: null,
  dataShopId: "",
  shopData: [],
  dataLoading: false
};

const pageMeta = {
  dashboard: ["Platform", "Dashboard"],
  shops: ["Operations", "Shop Management"],
  activations: ["Local Access", "Activations"],
  users: ["Access Control", "Users & Roles"],
  rules: ["Policy Engine", "Rules System"],
  payments: ["Commercial", "Payments & Plans"],
  readonly: ["Read-only", "Shop Data View"],
  ewallets: ["Wallet", "Easypaisa/Jazz"],
  imei: ["Inventory", "IMEI & Stock Trace"],
  documents: ["Documents", "CNIC / Files"],
  featureMatrix: ["Parity", "Feature Matrix"],
  subscriptions: ["Commercial", "Subscriptions"],
  support: ["Service Desk", "Support Center"],
  reports: ["Business Intelligence", "Reports"],
  settings: ["System", "Settings"]
};

function escapeHtml(value = "") {
  return String(value).replace(/[&<>'"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#039;", '"': "&quot;" }[char]));
}
function initials(value = "") { return String(value).split(/\s+/).filter(Boolean).slice(0, 2).map((word) => word[0]).join("").toUpperCase() || "SB"; }
function money(value = 0) { return `PKR ${new Intl.NumberFormat("en-PK").format(Number(value || 0))}`; }
function compactNumber(value = 0) { return new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 }).format(Number(value || 0)); }
function todayIso() { return new Date().toISOString().slice(0, 10); }
function timestamp(value, withTime = false) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) return "—";
  return new Intl.DateTimeFormat("en-PK", { day: "2-digit", month: "short", year: "numeric", ...(withTime ? { hour: "2-digit", minute: "2-digit" } : {}) }).format(date);
}
function badge(status) {
  const text = String(status || "unknown").replace(/_/g, " ").replace(/([A-Z])/g, " $1").replace(/^./, (s) => s.toUpperCase());
  const key = String(status || "unknown").toLowerCase().replace(/\s+/g, "");
  return `<span class="badge ${escapeHtml(key)}">${escapeHtml(text)}</span>`;
}
function toast(message, type = "") {
  const root = $("#toastRoot");
  const item = document.createElement("div");
  item.className = `toast ${type}`;
  item.textContent = message;
  root.append(item);
  setTimeout(() => item.remove(), 3600);
}
function id(prefix) { return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 7)}`; }
function addActivity(title, text, icon = "•") {
  state.activity.unshift({ id: id("act"), title, text, icon, at: new Date().toISOString() });
  state.activity = state.activity.slice(0, 40);
}
function normalizeShop(shop = {}) {
  return {
    id: shop.id || id("shop"),
    shopName: shop.shopName || "Untitled Shop",
    ownerName: shop.ownerName || "",
    ownerMobile: shop.ownerMobile || "",
    city: shop.city || "",
    address: shop.address || "",
    plan: shop.plan || "Local",
    monthlyFee: Number(shop.monthlyFee || 0),
    expiryDate: shop.expiryDate || "",
    status: shop.status || "active",
    shopCode: shop.shopCode || `SB-${String(state.shops.length + 1).padStart(4, "0")}`,
    activationCode: shop.activationCode || "",
    androidPassword: shop.androidPassword || "",
    allowedUsers: Number(shop.allowedUsers || 1),
    notes: shop.notes || "",
    createdAt: shop.createdAt || new Date().toISOString(),
    updatedAt: shop.updatedAt || new Date().toISOString()
  };
}
const defaultRules = [
  { id: "rule-pos", title: "POS / Sales", description: "Sale billing, invoices, cart and payment collection.", enabled: true, level: "allow" },
  { id: "rule-inventory", title: "Inventory", description: "Products, stock, purchase entry, barcode/SKU management.", enabled: true, level: "allow" },
  { id: "rule-repairs", title: "Repair Jobs", description: "Repair tickets, technician status and delivery tracking.", enabled: true, level: "allow" },
  { id: "rule-installments", title: "Installments", description: "Installment plans, due dates and customer collection tracking.", enabled: true, level: "allow" },
  { id: "rule-customers", title: "Customers & Suppliers", description: "Customer ledger, supplier payable and contact records.", enabled: true, level: "allow" },
  { id: "rule-accessories", title: "Mobile Accessories", description: "Covers, chargers, cables, handsfree and accessory stock.", enabled: true, level: "allow" },
  { id: "rule-spare-parts", title: "Mobile Spare Parts", description: "LCD, touch, battery, charging board and repair parts stock.", enabled: true, level: "allow" },
  { id: "rule-laptop", title: "Laptop Module", description: "Laptop purchase, sale and inventory workflow.", enabled: true, level: "allow" },
  { id: "rule-wallet", title: "Easypaisa/JazzCash", description: "Wallet in/out ledger, fee and thermal wallet receipt.", enabled: true, level: "allow" },
  { id: "rule-imei", title: "IMEI Tracking", description: "Purchase IMEI scan, duplicate check and stock trace.", enabled: true, level: "allow" },
  { id: "rule-cnic", title: "CNIC / Invoice Documents", description: "CNIC front/back and purchase invoice document attachment.", enabled: true, level: "allow" },
  { id: "rule-thermal", title: "Thermal Printing", description: "80mm receipts for sales, purchase, wallet and repair records.", enabled: true, level: "allow" },
  { id: "rule-staff", title: "Staff & Audit", description: "Staff activity, permissions, audit trail and deleted-record view.", enabled: true, level: "allow" },
  { id: "rule-reports", title: "Reports", description: "Dashboard, sales report, profit snapshot and local backup export.", enabled: true, level: "allow" },
  { id: "rule-delete", title: "Delete Permission", description: "Allow delete buttons for sensitive shop records.", enabled: false, level: "approval" },
  { id: "rule-backup", title: "Backup / Import", description: "Allow shop-side JSON backup export and restore.", enabled: true, level: "allow" }
];
function normalizeRule(rule = {}) {
  const found = defaultRules.find((item) => item.id === rule.id) || {};
  return { ...found, ...rule, enabled: rule.enabled !== undefined ? Boolean(rule.enabled) : found.enabled !== false, level: rule.level || found.level || "allow" };
}
function loadLocal() {
  state.rules = defaultRules.map(normalizeRule);
}
function saveLocal() {
  // Super Admin is deliberately online-only; no business state is persisted in the browser.
}

function shopRevision(shops) {
  return JSON.stringify(shops.map((shop) => [shop.id, shop.shopName, shop.ownerName, shop.ownerMobile, shop.city, shop.address, shop.plan, shop.monthlyFee, shop.expiryDate, shop.status, shop.shopCode, shop.allowedUsers, shop.notes, shop.updatedAt]));
}
async function refreshShops(showToast = false, forceRender = true) {
  const fresh = (await api.shops()).map(normalizeShop);
  const revision = shopRevision(fresh);
  const changed = revision !== shopsRevision;
  state.shops = fresh;
  shopsRevision = revision;
  if (forceRender || changed) render();
  if (showToast) toast(`${state.shops.length} shops Hostinger se refresh ho gaye.`, "success");
  return changed;
}
async function liveRefresh() {
  if (liveRefreshRunning || document.hidden || !state.user || !api.hasSession()) return;
  liveRefreshRunning = true;
  try {
    const changed = await refreshShops(false, false);
    if (state.page === "rules" && !document.activeElement?.matches(".permission-select")) await refreshPermissions(state.permissionShopId, true);
    if (["readonly", "ewallets", "imei", "documents", "support"].includes(state.page)) await refreshShopData(state.dataShopId, true);
    const indicator = $("#sideConnection");
    if (indicator) indicator.textContent = `Live · ${new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
  } catch (error) {
    const indicator = $("#sideConnection");
    if (indicator) indicator.textContent = navigator.onLine ? "Reconnecting…" : "Offline";
    if (error.status === 401 || ["platform_session_invalid", "platform_authentication_required"].includes(error.code)) showLogin();
  } finally {
    liveRefreshRunning = false;
  }
}
function startLiveRefresh() {
  if (liveRefreshTimer) clearInterval(liveRefreshTimer);
  liveRefreshTimer = setInterval(liveRefresh, LIVE_REFRESH_MS);
}
function activationPayload(shop = null) {
  const selected = shop ? [normalizeShop(shop)] : state.shops.map(normalizeShop);
  const selectedIds = new Set(selected.map((item) => item.id));
  return {
    skybarechLinkVersion: 2,
    exportedAt: new Date().toISOString(),
    source: "super-admin-local",
    shops: selected,
    activations: state.activations.filter((item) => selectedIds.has(item.shopId)),
    rules: state.rules
  };
}
function downloadActivationFile(shop = null) {
  const payload = activationPayload(shop);
  const first = payload.shops[0] || {};
  const safeName = String(first.shopCode || first.shopName || "all-shops").replace(/[^a-z0-9_-]+/gi, "-").toLowerCase();
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = `skybarech-activation-${safeName}.json`;
  link.click();
  URL.revokeObjectURL(link.href);
  toast(shop ? "Shop activation file downloaded." : "All shop activation file downloaded.", "success");
}
function copyActivationText(shop = null) {
  const payload = activationPayload(shop);
  const text = btoa(unescape(encodeURIComponent(JSON.stringify(payload))));
  navigator.clipboard?.writeText(text);
  toast("SkyLink copied. Paste/import this on Desktop/Mobile local mode.", "success");
}
function closeModal() { $("#modalRoot").innerHTML = ""; }
function mountModal({ title, body, size = "" }) {
  $("#modalRoot").innerHTML = `<div class="modal-backdrop" data-modal-backdrop><section class="modal ${escapeHtml(size)}" role="dialog" aria-modal="true"><header class="modal-header"><h3>${escapeHtml(title)}</h3><button class="icon-button" type="button" data-close-modal aria-label="Close">✕</button></header><div class="modal-body">${body}</div></section></div>`;
}
function metricCard(label, value, icon, delta, tone = "") {
  return `<article class="metric-card ${tone}"><div class="metric-head"><span>${escapeHtml(label)}</span><span class="metric-icon">${icon}</span></div><h4>${escapeHtml(value)}</h4><small>${escapeHtml(delta)}</small></article>`;
}
function heading(title, description, actions = "") {
  return `<section class="page-heading"><div><h3>${escapeHtml(title)}</h3><p>${escapeHtml(description)}</p></div><div class="page-heading-actions">${actions}</div></section>`;
}
function setPage(page) {
  state.page = page;
  $$(".nav-item").forEach((button) => button.classList.toggle("active", button.dataset.page === page));
  const [kicker, title] = pageMeta[page] || pageMeta.dashboard;
  $("#pageKicker").textContent = kicker;
  $("#pageTitle").textContent = title;
  $("#sidebar").classList.remove("open");
  $(".nav-item.active")?.scrollIntoView({block:"nearest", inline:"nearest"});
  render();
  if (page === "rules") refreshPermissions();
  if (["readonly", "ewallets", "imei", "documents", "support"].includes(page)) refreshShopData();
}
function filteredShops() {
  const search = state.shopSearch.trim().toLowerCase();
  return state.shops.filter((shop) => {
    const matchStatus = state.shopFilter === "all" || String(shop.status).toLowerCase() === state.shopFilter;
    const haystack = [shop.shopName, shop.ownerName, shop.ownerMobile, shop.city, shop.shopCode, shop.plan].join(" ").toLowerCase();
    return matchStatus && (!search || haystack.includes(search));
  });
}
function statusCounts() {
  return state.shops.reduce((acc, shop) => { const key = String(shop.status || "active").toLowerCase(); acc[key] = (acc[key] || 0) + 1; return acc; }, {});
}
function renderDashboard() {
  const counts = statusCounts();
  const active = (counts.active || 0) + (counts.trial || 0);
  const suspended = (counts.suspended || 0) + (counts.blocked || 0) + (counts.expired || 0);
  const revenue = state.shops.reduce((sum, shop) => sum + Number(shop.monthlyFee || 0), 0);
  const recent = state.activity.length ? state.activity.slice(0, 7) : state.shops.slice(0, 7).map((shop) => ({ title: shop.shopName, text: `${shop.city || "Local"} · ${shop.plan || "Local"}`, icon: initials(shop.shopName), at: shop.createdAt }));
  const max = Math.max(...Object.values(counts), 1);
  return `${heading("Shop overview", "Manage your shops, access and appearance.", `<button class="outline-button" type="button" data-action="go-colors">Shop colors</button><button class="primary-button" type="button" data-action="new-shop">+ Add Shop</button>`)}
    <section class="metric-grid">
      ${metricCard("Total Shops", compactNumber(state.shops.length), "▣", "Registered shops")}
      ${metricCard("Active / Trial", compactNumber(active), "✓", "Ready shops")}
      ${metricCard("Stopped", compactNumber(suspended), "!", "Suspended / blocked", suspended ? "danger" : "")}
      ${metricCard("Monthly Ref.", money(revenue), "₨", "From plan fees")}
    </section>
    <section class="dashboard-grid"><section class="panel"><header class="panel-header"><div><h4>Shop status</h4><p>Current Cloud records.</p></div>${badge("online")}</header><div class="status-pipeline">
      ${["active", "trial", "suspended", "blocked", "expired"].map((s) => `<div class="pipe-row"><span>${s}</span><div><i style="width:${((counts[s] || 0) / max) * 100}%"></i></div><b>${counts[s] || 0}</b></div>`).join("")}
    </div></section><section class="panel"><header class="panel-header"><div><h4>Recent Activity</h4><p>Recent shops and actions in this session.</p></div></header><div class="activity-list">
      ${recent.length ? recent.map((item) => `<div class="activity-row"><div class="activity-icon">${escapeHtml(item.icon || "•")}</div><div><strong>${escapeHtml(item.title || "Activity")}</strong><span>${escapeHtml(item.text || "")}</span></div><time>${timestamp(item.at, true)}</time></div>`).join("") : `<div class="empty-state"><b>No activity yet</b>Add a shop to start.</div>`}
    </div></section></section>`;
}
function renderShops() {
  const shops = filteredShops();
  return `${heading("Shop Management", "Add, view, edit, suspend and activate central shop records.", `<button class="outline-button" type="button" data-action="refresh">↻ Refresh</button><button class="primary-button" type="button" data-action="new-shop">+ New Shop</button>`)}
    <section class="toolbar"><div class="search"><span>⌕</span><input id="shopSearch" value="${escapeHtml(state.shopSearch)}" placeholder="Search shop, owner, mobile, city, code" /></div><div class="filter-row">${["all", "active", "trial", "suspended", "blocked"].map((status) => `<button class="filter-chip ${state.shopFilter === status ? "active" : ""}" data-filter="${status}">${status}</button>`).join("")}</div></section>
    <section class="table-card"><table><thead><tr><th>Shop</th><th>Owner</th><th>Plan</th><th>Status</th><th>Expiry</th><th>Local Code</th><th>Action</th></tr></thead><tbody>
    ${shops.length ? shops.map((shop) => `<tr><td><button class="shop-link" type="button" data-action="view-shop" data-id="${escapeHtml(shop.id)}"><div class="avatar">${initials(shop.shopName)}</div><div><strong>${escapeHtml(shop.shopName)}</strong><span>${escapeHtml(shop.city || "—")} · click for details</span></div></button></td><td>${escapeHtml(shop.ownerName || "—")}<br><small>${escapeHtml(shop.ownerMobile || "—")}</small></td><td>${escapeHtml(shop.plan || "Trial")}<br><small>${money(shop.monthlyFee || 0)}</small></td><td>${badge(shop.status || "active")}</td><td>${escapeHtml(shop.expiryDate || "—")}</td><td><code>${escapeHtml(shop.shopCode)}</code></td><td><div class="row-actions"><button title="View" data-action="view-shop" data-id="${escapeHtml(shop.id)}">👁</button><button title="Shop colors" aria-label="Shop colors" data-action="shop-colors" data-id="${escapeHtml(shop.id)}">◈</button><button title="Edit" data-action="edit-shop" data-id="${escapeHtml(shop.id)}">✎</button><button title="Status" data-action="status" data-id="${escapeHtml(shop.id)}">⚡</button><button title="Delete" data-action="delete-shop" data-id="${escapeHtml(shop.id)}">🗑</button></div></td></tr>`).join("") : `<tr><td colspan="7"><div class="empty-state"><b>No shops found</b>Create the first online shop record.</div></td></tr>`}
    </tbody></table></section>`;
}
function renderActivations() {
  return `${heading("Activations", "One-time credentials generated by Hostinger when a shop is created.", `<button class="primary-button" type="button" data-action="new-shop">+ Add Shop</button>`)}
    <section class="table-card"><table><thead><tr><th>Shop</th><th>Owner Mobile</th><th>Activation Code</th><th>Temp PIN</th><th>Created</th></tr></thead><tbody>
      ${state.activations.length ? state.activations.map((a) => `<tr><td><strong>${escapeHtml(a.shopName || a.shopId)}</strong></td><td>${escapeHtml(a.ownerMobile)}</td><td><code>${escapeHtml(a.code)}</code></td><td><code>${escapeHtml(a.password || "—")}</code></td><td>${timestamp(a.createdAt)}</td></tr>`).join("") : `<tr><td colspan="5"><div class="empty-state"><b>No credentials in this session</b>Create a shop to generate one-time activation credentials.</div></td></tr>`}
    </tbody></table></section>`;
}
function renderUsers() {
  return `${heading("Users, Branches & Devices", "Open a shop to manage its real Hostinger users, branches, sessions and registered Android/Desktop devices.")}
    <section class="table-card"><table><thead><tr><th>Shop</th><th>Owner</th><th>Status</th><th>Plan</th><th>Control</th></tr></thead><tbody>${state.shops.length ? state.shops.map((shop) => `<tr><td><strong>${escapeHtml(shop.shopName)}</strong><br><small>${escapeHtml(shop.shopCode)}</small></td><td>${escapeHtml(shop.ownerName || "—")}<br><small>${escapeHtml(shop.ownerMobile || "—")}</small></td><td>${badge(shop.status)}</td><td>${badge(shop.plan)}</td><td><button class="primary-button" type="button" data-action="view-shop" data-id="${escapeHtml(shop.id)}">Open Control</button></td></tr>`).join("") : `<tr><td colspan="5"><div class="empty-state"><b>No shops found</b>Create a shop first.</div></td></tr>`}</tbody></table></section>`;
}

function selectedPermissionShop() {
  const id = String(state.permissionShopId || state.shops[0]?.id || "");
  return state.shops.find((shop) => String(shop.id) === id) || state.shops[0] || null;
}
async function refreshPermissions(shopId = "", background = false) {
  const target = String(shopId || state.permissionShopId || state.shops[0]?.id || "");
  if (!target) { state.permissions = null; render(); return; }
  state.permissionShopId = target;
  if (!background) {
    state.permissions = null;
    if (state.page === "rules") render();
  }
  try {
    state.permissions = await api.permissions(target);
    if (state.page === "rules") render();
  } catch (error) { toast(error.message, "error"); }
}
function renderRules() {
  if (!state.shops.length) return `${heading("Role Permissions", "Create a shop before configuring server-side roles.")}<div class="empty-state"><b>No shops found</b></div>`;
  const shop = selectedPermissionShop();
  const picker = `<select id="permissionShopSelect" class="select-control">${state.shops.map((item) => `<option value="${escapeHtml(item.id)}" ${String(item.id) === String(shop?.id) ? "selected" : ""}>${escapeHtml(item.shopName)}</option>`).join("")}</select>`;
  if (!state.permissions) return `${heading("Role Permissions", "Live policy from Hostinger role_permissions. Blocked operations are rejected by the sync API.", picker)}<div class="empty-state"><b>Loading server permissions…</b></div>`;
  const definitions = state.permissions.definitions || {};
  const rows = Array.isArray(state.permissions.rows) ? state.permissions.rows : [];
  const roles = [
    ["super_admin", "Super Admin"], ["branch_manager", "Branch Manager"], ["cashier", "Cashier"], ["technician", "Technician"], ["accountant", "Accountant"]
  ];
  const levelFor = (role, key) => rows.find((r) => r.roleName === role && r.permissionKey === key)?.accessLevel || (role === "super_admin" ? "allow" : "block");
  const body = Object.entries(definitions).map(([key, label]) => `<tr><td><strong>${escapeHtml(label)}</strong><br><small>${escapeHtml(key)}</small></td>${roles.map(([role]) => { const level = levelFor(role, key); const owner = role === "super_admin"; return `<td><select class="permission-select" data-permission-role="${escapeHtml(role)}" data-permission-key="${escapeHtml(key)}" ${owner ? "disabled" : ""}><option value="allow" ${level === "allow" ? "selected" : ""}>Allow</option><option value="approval" ${level === "approval" ? "selected" : ""}>Approval</option><option value="block" ${level === "block" ? "selected" : ""}>Block</option></select></td>`; }).join("")}</tr>`).join("");
  return `${heading("Role Permissions", "These settings are live. Server sync enforces them even if a client UI is modified.", picker)}
    <section class="metric-grid">${metricCard("Selected Shop", shop?.shopName || "—", "▣", "Hostinger policy")}${metricCard("Roles", "5", "◉", "Owner + staff")}${metricCard("Permissions", String(Object.keys(definitions).length), "⚖", "Server enforced")}${metricCard("Device Snapshot", "Owner only", "🔒", "Prevents permission bypass")}</section>
    <section class="table-card"><table><thead><tr><th>Permission</th>${roles.map(([, label]) => `<th>${escapeHtml(label)}</th>`).join("")}</tr></thead><tbody>${body}</tbody></table></section>
    <section class="panel rules-note"><h4>Enforcement</h4><p>Allow permits writes. Approval rejects the write with an approval-required response until a future approval workflow is added. Block rejects it. Read sync also hides blocked modules from staff roles.</p></section>`;
}

function selectedDataShop() {
  const id = String(state.dataShopId || state.shops[0]?.id || "");
  return state.shops.find((shop) => String(shop.id) === id) || state.shops[0] || null;
}
async function refreshShopData(shopId = "", background = false) {
  const target = String(shopId || state.dataShopId || state.shops[0]?.id || "");
  if (!target) { state.shopData = []; state.dataLoading = false; render(); return; }
  state.dataShopId = target;
  state.dataLoading = !background;
  if (!background && ["readonly", "ewallets", "imei", "documents", "support"].includes(state.page)) render();
  try {
    state.shopData = await api.shopData(target, "", 500);
  } catch (error) {
    if (!background) {
      state.shopData = [];
      toast(error.message, "error");
    }
  } finally {
    state.dataLoading = false;
    if (["readonly", "ewallets", "imei", "documents", "support"].includes(state.page)) render();
  }
}
function dataShopPicker() {
  const shop = selectedDataShop();
  return `<select id="dataShopSelect" class="select-control">${state.shops.map((item) => `<option value="${escapeHtml(item.id)}" ${String(item.id) === String(shop?.id) ? "selected" : ""}>${escapeHtml(item.shopName)}</option>`).join("")}</select>`;
}
function recordSummary(payload = {}) {
  const preferred = ["name","customer","item","device","brand","model","category","party","reference","issue","status","payment","phone","sku"];
  const parts = preferred.filter((key) => payload[key] !== undefined && payload[key] !== "").slice(0, 4).map((key) => `${key}: ${payload[key]}`);
  return parts.join(" · ") || Object.entries(payload).slice(0, 4).map(([k,v]) => `${k}: ${typeof v === "object" ? "…" : v}`).join(" · ") || "No details";
}

function renderReadOnly() {
  const shop = selectedDataShop();
  if (!shop) return `${heading("Read-only Shop Data", "Select a shop after creating it.")}<div class="empty-state"><b>No shops found</b></div>`;
  if (state.dataLoading) return `${heading("Read-only Shop Data", "Live current records from sync_records.", dataShopPicker())}<div class="empty-state"><b>Loading live business records…</b></div>`;
  const rows = state.shopData || [];
  return `${heading("Read-only Shop Data", "Live records from Hostinger. Super Admin can inspect but cannot edit/delete shop business data.", dataShopPicker())}
    <section class="metric-grid">${metricCard("Current Records", String(rows.length), "▣", shop.shopName)}${metricCard("Entity Types", String(new Set(rows.map((r) => r.entityType)).size), "⌁", "Central sync data")}${metricCard("Edit/Delete", "Disabled", "🔒", "Business records protected")}${metricCard("Mode", "Live", "✓", "sync_records")}</section>
    <section class="table-card"><table><thead><tr><th>Record</th><th>Type</th><th>Branch</th><th>Details</th><th>Version</th><th>Updated</th></tr></thead><tbody>${rows.length ? rows.map((r) => `<tr><td><strong>${escapeHtml(r.entityId)}</strong></td><td>${badge(r.entityType)}</td><td>${escapeHtml(r.branchId ?? "All")}</td><td>${escapeHtml(recordSummary(r.payload))}</td><td>${escapeHtml(r.version)}</td><td>${timestamp(r.updatedAt, true)}</td></tr>`).join("") : `<tr><td colspan="6"><div class="empty-state"><b>No synced business records yet</b>Open Desktop/Android and run sync.</div></td></tr>`}</tbody></table></section>`;
}

function renderEWallets() {
  const shop = selectedDataShop();
  const rows = (state.shopData || []).filter((r) => r.entityType === "wallet");
  const amount = (r) => Number(r.payload?.amount || 0) + Number(r.payload?.fee || 0);
  const type = (r) => String(r.payload?.type || r.payload?.direction || "").toLowerCase();
  const inTotal = rows.filter((r) => type(r) === "in").reduce((s,r) => s + amount(r), 0);
  const outTotal = rows.filter((r) => type(r) === "out").reduce((s,r) => s + amount(r), 0);
  if (state.dataLoading) return `${heading("Easypaisa/Jazz", "Live wallet records from Hostinger.", dataShopPicker())}<div class="empty-state"><b>Loading wallet records…</b></div>`;
  return `${heading("Easypaisa/Jazz", `Live read-only wallet data for ${shop?.shopName || "selected shop"}.`, dataShopPicker())}
    <section class="metric-grid">${metricCard("Wallet In", money(inTotal), "↙", "Synced")}${metricCard("Wallet Out", money(outTotal), "↗", "Synced")}${metricCard("Balance", money(inTotal-outTotal), "₨", "Calculated")}${metricCard("Records", String(rows.length), "▣", "Hostinger")}</section>
    <section class="table-card"><table><thead><tr><th>ID</th><th>Wallet</th><th>Type</th><th>Party</th><th>Amount</th><th>Reference</th><th>Updated</th></tr></thead><tbody>${rows.length ? rows.map((r) => `<tr><td><strong>${escapeHtml(r.entityId)}</strong></td><td>${badge(r.payload?.wallet || r.payload?.provider || "Wallet")}</td><td>${badge(r.payload?.type || r.payload?.direction || "—")}</td><td>${escapeHtml(r.payload?.party || r.payload?.customer || "—")}</td><td>${money(amount(r))}</td><td>${escapeHtml(r.payload?.reference || r.payload?.note || "—")}</td><td>${timestamp(r.updatedAt, true)}</td></tr>`).join("") : `<tr><td colspan="7"><div class="empty-state"><b>No wallet records synced</b></div></td></tr>`}</tbody></table></section>`;
}
function renderImeiTrace() {
  const shop = selectedDataShop();
  const rows = (state.shopData || []).filter((r) => r.entityType === "imei" || (r.entityType === "product" && (r.payload?.imei || r.payload?.imei1 || r.payload?.imei2)));
  if (state.dataLoading) return `${heading("IMEI & Stock Trace", "Live IMEI records from Hostinger.", dataShopPicker())}<div class="empty-state"><b>Loading IMEI records…</b></div>`;
  return `${heading("IMEI & Stock Trace", `Read-only synced IMEI/stock trace for ${shop?.shopName || "selected shop"}.`, dataShopPicker())}
    <section class="metric-grid">${metricCard("Tracked", String(rows.length), "⌁", "Central records")}${metricCard("Mode", "Read-only", "🔒", "No business edits")}${metricCard("Source", "Hostinger", "✓", "sync_records")}${metricCard("Shop", shop?.shopName || "—", "▣", "Selected")}</section>
    <section class="table-card"><table><thead><tr><th>ID</th><th>Device/Product</th><th>IMEI</th><th>Status</th><th>Branch</th><th>Updated</th></tr></thead><tbody>${rows.length ? rows.map((r) => `<tr><td><strong>${escapeHtml(r.entityId)}</strong></td><td>${escapeHtml(r.payload?.name || r.payload?.device || [r.payload?.brand,r.payload?.model].filter(Boolean).join(" ") || "—")}</td><td><code>${escapeHtml(r.payload?.imei || r.payload?.imei1 || r.payload?.number || "—")}</code></td><td>${badge(r.payload?.status || r.payload?.state || "Synced")}</td><td>${escapeHtml(r.branchId ?? "All")}</td><td>${timestamp(r.updatedAt, true)}</td></tr>`).join("") : `<tr><td colspan="6"><div class="empty-state"><b>No IMEI records synced</b></div></td></tr>`}</tbody></table></section>`;
}
function renderDocuments() {
  const shop = selectedDataShop();
  const keys = ["cnicFront","cnicBack","cnic_front","cnic_back","invoiceImage","invoice_image","purchaseInvoice","document","documents","attachments"];
  const rows = (state.shopData || []).filter((r) => r.entityType === "purchase" && keys.some((key) => r.payload && r.payload[key]));
  const present = (payload, names) => names.some((name) => Boolean(payload?.[name]));
  if (state.dataLoading) return `${heading("CNIC / Files", "Live purchase document references from Hostinger.", dataShopPicker())}<div class="empty-state"><b>Loading document records…</b></div>`;
  return `${heading("CNIC / Files", `Read-only synced document checklist for ${shop?.shopName || "selected shop"}.`, dataShopPicker())}
    <section class="metric-grid">${metricCard("Purchase Records", String(rows.length), "▤", "With document fields")}${metricCard("Control", "Read-only", "🔒", "No delete/edit")}${metricCard("Source", "Hostinger", "✓", "Synced payload")}${metricCard("Shop", shop?.shopName || "—", "▣", "Selected")}</section>
    <section class="table-card"><table><thead><tr><th>Purchase</th><th>CNIC Front</th><th>CNIC Back</th><th>Invoice/File</th><th>Branch</th><th>Updated</th></tr></thead><tbody>${rows.length ? rows.map((r) => `<tr><td><strong>${escapeHtml(r.entityId)}</strong></td><td>${badge(present(r.payload,["cnicFront","cnic_front"]) ? "Attached" : "Missing")}</td><td>${badge(present(r.payload,["cnicBack","cnic_back"]) ? "Attached" : "Missing")}</td><td>${badge(present(r.payload,["invoiceImage","invoice_image","purchaseInvoice","document","documents","attachments"]) ? "Attached" : "Missing")}</td><td>${escapeHtml(r.branchId ?? "All")}</td><td>${timestamp(r.updatedAt, true)}</td></tr>`).join("") : `<tr><td colspan="6"><div class="empty-state"><b>No synced purchase document references</b></div></td></tr>`}</tbody></table></section>`;
}

function renderFeatureMatrix() {
  const modules = [
    ["Dashboard", "Yes", "Yes", "Yes", "Overview and metrics"],
    ["POS / Sales", "Yes", "Yes", "View", "Sales, invoices, payments"],
    ["Inventory / Stock", "Yes", "Yes", "View", "Products and stock"],
    ["Mobile Accessories", "Yes", "Yes", "Rule", "Separate accessory stock"],
    ["Mobile Spare Parts", "Yes", "Yes", "Rule", "Repair parts stock"],
    ["Laptop", "Yes", "Yes", "Rule", "Laptop purchase/sale"],
    ["Mobile Purchase + IMEI", "Yes", "Yes", "View", "IMEI scan/manual"],
    ["CNIC Front/Back", "Yes", "Yes", "View", "Document checklist"],
    ["Easypaisa/Jazz", "Yes", "Yes", "View", "Wallet in/out"],
    ["Thermal Print", "Yes", "Button", "View", "80mm receipts"],
    ["Reports", "Yes", "Yes", "View", "Local reporting"],
    ["Staff / Audit", "Yes", "Activity", "Rule", "Audit controls"],
    ["Backup / Restore", "Yes", "Local data", "Backup", "Manual JSON backup"]
  ];
  return `${heading("Feature Matrix", "Desktop, Android and Super Admin deployment parity checklist for the central Hostinger sync build.", `<button class="outline-button" type="button" data-action="export-data">⇩ Export Backup</button>`)}
    <section class="table-card"><table><thead><tr><th>Module</th><th>Desktop</th><th>Android</th><th>Super Admin</th><th>Notes</th></tr></thead><tbody>${modules.map((r) => `<tr><td><strong>${escapeHtml(r[0])}</strong></td><td>${badge(r[1])}</td><td>${badge(r[2])}</td><td>${badge(r[3])}</td><td>${escapeHtml(r[4])}</td></tr>`).join("")}</tbody></table></section>`;
}

function renderSubscriptions() {
  const rows = state.shops;
  const active = rows.filter((s) => ["active", "trial"].includes(String(s.status).toLowerCase())).length;
  const revenue = rows.reduce((sum, shop) => sum + Number(shop.monthlyFee || 0), 0);
  const body = rows.length
    ? rows.map((shop) => `<tr><td><strong>${escapeHtml(shop.shopName)}</strong><br><small>${escapeHtml(shop.shopCode)}</small></td><td>${escapeHtml(shop.ownerName || "—")}</td><td>${badge(shop.plan)}</td><td>${money(shop.monthlyFee)}</td><td>${escapeHtml(shop.expiryDate || "—")}</td><td>${badge(shop.status)}</td><td><button data-action="status" data-id="${escapeHtml(shop.id)}">Change Status</button></td></tr>`).join("")
    : `<tr><td colspan="7"><div class="empty-state"><b>No shops registered yet</b><br><small>Create a shop to start central activation and sync.</small></div></td></tr>`;
  return `${heading("Subscriptions", "Manage real shop activation, expiry and package status from the central API.", `<button class="primary-button" type="button" data-action="new-shop">+ Add Shop</button>`)}
    <section class="metric-grid">${metricCard("Active/Trial", String(active), "✓", "Allowed shops")}${metricCard("Monthly Revenue", money(revenue), "₨", "Package reference")}${metricCard("Expired/Blocked", String(rows.length-active), "⚠", "Needs follow-up")}${metricCard("Central Sync", "Connected", "✓", "Hostinger API active")}</section>
    <section class="table-card"><table><thead><tr><th>Shop</th><th>Owner</th><th>Plan</th><th>Fee</th><th>Expiry</th><th>Status</th><th>Control</th></tr></thead><tbody>${body}</tbody></table></section>`;
}
function renderPayments() { return `${heading("Payments & Plans", "Local package reference only. Edit monthly fee inside each shop.")}<section class="plan-grid"><article class="plan-card"><h4>Local</h4><strong>Offline</strong><span>No subscription check</span><ul><li>Device-only records</li><li>No remote sync</li><li>Manual backup</li></ul></article><article class="plan-card featured"><h4>Premium</h4><strong>PKR 5,000</strong><span>Reference</span><ul><li>POS</li><li>Repairs</li><li>Installments</li></ul></article><article class="plan-card"><h4>Pro</h4><strong>PKR 8,000</strong><span>Reference</span><ul><li>Multi-user</li><li>Reports</li><li>Backups</li></ul></article></section>`; }
function renderSupport() { const shop = selectedDataShop(); const rows = (state.shopData || []).filter((r) => r.entityType === "support_request"); if (state.dataLoading) return `${heading("Support Center", "Live shop support requests.", dataShopPicker())}<div class="empty-state"><b>Loading support requests…</b></div>`; return `${heading("Support Center", `Live read-only support requests for ${shop?.shopName || "selected shop"}.`, dataShopPicker())}<section class="table-card"><table><thead><tr><th>Ticket</th><th>Issue</th><th>Status</th><th>Branch</th><th>Updated</th></tr></thead><tbody>${rows.length ? rows.map((r) => `<tr><td>${escapeHtml(r.entityId)}</td><td>${escapeHtml(r.payload?.title || r.payload?.issue || r.payload?.message || "—")}</td><td>${badge(r.payload?.status || "open")}</td><td>${escapeHtml(r.branchId ?? "All")}</td><td>${timestamp(r.updatedAt, true)}</td></tr>`).join("") : `<tr><td colspan="5"><div class="empty-state"><b>No synced support requests</b></div></td></tr>`}</tbody></table></section>`; }
function renderReports() { const revenue = state.shops.reduce((s, x) => s + Number(x.monthlyFee || 0), 0); return `${heading("Reports", "Current central shop overview from Hostinger.", `<button class="outline-button" type="button" data-action="refresh">↻ Refresh</button>`)}<section class="metric-grid">${metricCard("Registered Shops", String(state.shops.length), "▣", "Online total")}${metricCard("Premium Shops", String(state.shops.filter((s) => s.plan === "Premium").length), "★", "Central plans")}${metricCard("Monthly Ref.", money(revenue), "₨", "Total fees")}${metricCard("Mode", "Online", "⌁", "Hostinger MySQL")}</section>`; }
function renderSettings() {
  return `${heading("Settings", "Shop appearance and Cloud connection.")}
    <section class="info-card appearance-entry"><div><h4>Shop colors</h4><p>Choose a shop to manage its desktop and Android colors.</p></div>
    <label>Shop<select id="appearanceShop">${state.shops.map(shop => `<option value="${escapeHtml(shop.id)}">${escapeHtml(shop.shopName)}</option>`).join('')}</select></label>
    <button type="button" class="primary-button" data-action="shop-colors" ${state.shops.length ? '' : 'disabled'}>Edit colors</button></section>
    <section class="info-card"><h4>Cloud connection</h4><p>${escapeHtml(api.baseUrl)}</p></section>`;
}
const appearancePresets = {
  mint:{accent:'#17604B', background:'#F3F7F5', surface:'#FFFFFF', sidebar:'#112B24'},
  blue:{accent:'#245BD6', background:'#F2F5FA', surface:'#FFFFFF', sidebar:'#142B4B'},
  violet:{accent:'#6B42BD', background:'#F5F2F9', surface:'#FFFFFF', sidebar:'#302342'},
  midnight:{accent:'#9DBEFF', background:'#0C1525', surface:'#19263C', sidebar:'#101A2A'}
};
async function openAppearance(shopId) {
  const shop = state.shops.find(shop => String(shop.id) === String(shopId));
  if (!shop) return;
  try {
    const settings = await api.appearance(shop.id);
    if (!state.user) return;
    const platformCard = platform => {
      const colors = settings[platform] || {...appearancePresets.mint, enabled:false};
      return `<fieldset class="appearance-platform" data-platform="${platform}"><legend>${platform === 'desktop' ? 'Desktop' : 'Android'}</legend>
        <label class="appearance-enable"><input type="checkbox" name="${platform}-enabled" ${colors.enabled ? 'checked' : ''}> Manage colors from Super Admin</label>
        <div class="appearance-presets">${Object.keys(appearancePresets).map(key => `<button type="button" class="outline-button" data-palette="${key}">${key[0].toUpperCase()+key.slice(1)}</button>`).join('')}</div>
        <div class="appearance-fields">${(platform === 'desktop' ? ['accent','background','surface','sidebar'] : ['accent','background','surface']).map(key => `<label>${({accent:'Accent',background:'Background',surface:'Cards',sidebar:'Sidebar'})[key]}<input type="color" name="${platform}-${key}" value="${/^#[0-9a-f]{6}$/i.test(colors[key]) ? colors[key] : appearancePresets.mint[key]}" required></label>`).join('')}</div>
        <div class="appearance-preview" aria-label="${platform} color preview"><span class="appearance-preview-nav"></span><div><strong>Shop workspace</strong><span class="appearance-preview-card">POS Billing <b>+</b></span><button type="button" tabindex="-1">Continue</button></div></div>
      </fieldset>`;
    };
    mountModal({title:`Colors · ${shop.shopName}`, body:`<form id="appearanceForm" class="modal-form"><p>Colors update through Cloud sync. Text contrast is checked when you save.</p>${platformCard('desktop')}${platformCard('android')}<p class="appearance-error" role="alert" hidden></p><button type="submit" class="primary-button large">Save shop colors</button></form>`});
    const form = $('#appearanceForm');
    const preview = () => form.querySelectorAll('[data-platform]').forEach(card => {
      const platform = card.dataset.platform;
      const color = key => form.elements[`${platform}-${key}`]?.value || '#112B24';
      const light = hex => {
        const rgb = [1,3,5].map(i => parseInt(hex.slice(i,i+2),16)/255).map(v => v<=.04045 ? v/12.92 : ((v+.055)/1.055)**2.4);
        return .2126*rgb[0]+.7152*rgb[1]+.0722*rgb[2]>.179;
      };
      const box = card.querySelector('.appearance-preview');
      ['accent','background','surface','sidebar'].forEach(key => box.style.setProperty('--'+key, color(key)));
      box.style.setProperty('--preview-ink', light(color('surface')) ? '#101010' : '#ffffff');
      box.style.setProperty('--preview-on-accent', light(color('accent')) ? '#101010' : '#ffffff');
    });
    preview(); form.addEventListener('input', preview);
    form.addEventListener('click', event => {
      const button = event.target.closest('[data-palette]'); if (!button) return;
      const platform = button.closest('[data-platform]').dataset.platform;
      Object.entries(appearancePresets[button.dataset.palette]).forEach(([key,value]) => { const input = form.elements[`${platform}-${key}`]; if (input) input.value=value; });
      form.elements[`${platform}-enabled`].checked = true; preview();
    });
    form.addEventListener('submit', async event => {
      event.preventDefault(); const button = form.querySelector('[type=submit]'); if (button.disabled) return;
      button.disabled = true; const errorBox = form.querySelector('.appearance-error'); errorBox.hidden = true;
      const body = {shop_id:shop.id};
      ['desktop','android'].forEach(platform => {
        body[platform] = {enabled:form.elements[`${platform}-enabled`].checked};
        ['accent','background','surface',...(platform === 'desktop' ? ['sidebar'] : [])].forEach(key => body[platform][key] = form.elements[`${platform}-${key}`].value);
      });
      try { await api.saveAppearance(body); closeModal(); toast('Colors saved. Connected clients will update on their next Cloud sync.', 'success'); }
      catch (error) { errorBox.textContent = error.message; errorBox.hidden = false; }
      finally { button.disabled = false; }
    });
  } catch (error) { toast(error.message, 'error'); }
}

function render() {
  if (!state.user) return;
  const map = { dashboard: renderDashboard, shops: renderShops, activations: renderActivations, users: renderUsers, rules: renderRules, payments: renderPayments, readonly: renderReadOnly, ewallets: renderEWallets, imei: renderImeiTrace, documents: renderDocuments, featureMatrix: renderFeatureMatrix, subscriptions: renderSubscriptions, support: renderSupport, reports: renderReports, settings: renderSettings };
  $("#pageContent").innerHTML = (map[state.page] || renderDashboard)();
}
function shopForm(shop = null) {
  const s = shop || {};
  return `<form id="shopForm" class="modal-form" novalidate data-id="${escapeHtml(s.id || "")}">
    <div class="two-col"><label>Shop Name<input name="shopName" required placeholder="e.g. Al Raza Mobiles" value="${escapeHtml(s.shopName || "")}" /></label><label>Owner Name<input name="ownerName" required placeholder="Owner full name" value="${escapeHtml(s.ownerName || "")}" /></label></div>
    <div class="two-col"><label>Owner Mobile<input name="ownerMobile" required placeholder="03001234567" value="${escapeHtml(s.ownerMobile || "")}" /></label><label>City<input name="city" placeholder="Quetta" value="${escapeHtml(s.city || "")}" /></label></div>
    <label>Address<input name="address" placeholder="Shop address" value="${escapeHtml(s.address || "")}" /></label>
    <div class="two-col"><label>Package<select name="plan"><option ${s.plan === "Local" ? "selected" : ""}>Local</option><option ${s.plan === "Trial" ? "selected" : ""}>Trial</option><option ${s.plan === "Starter" ? "selected" : ""}>Starter</option><option ${!s.plan || s.plan === "Premium" ? "selected" : ""}>Premium</option><option ${s.plan === "Pro" ? "selected" : ""}>Pro</option></select></label><label>Monthly Fee<input name="monthlyFee" type="number" min="0" value="${escapeHtml(s.monthlyFee || 0)}" /></label></div>
    <div class="two-col"><label>Expiry Date<input name="expiryDate" type="date" value="${escapeHtml(s.expiryDate || "")}" /></label><label>Status<select name="status"><option value="active" ${s.status === "active" ? "selected" : ""}>Active</option><option value="trial" ${s.status === "trial" ? "selected" : ""}>Trial</option><option value="suspended" ${s.status === "suspended" ? "selected" : ""}>Suspended</option><option value="blocked" ${s.status === "blocked" ? "selected" : ""}>Blocked</option></select></label></div>
    ${shop ? "" : `<div class="two-col"><label>Activation Code (optional)<input name="activationCode" placeholder="Auto-generated if blank" /></label><label>Temp PIN (optional)<input name="temporaryPassword" type="password" autocomplete="new-password" placeholder="Auto-generated securely" /></label></div>`}
    <label>Notes<textarea name="notes" placeholder="Internal note">${escapeHtml(s.notes || "")}</textarea></label>
    <button class="primary-button large" type="submit"><span>${shop ? "Save shop changes" : "Create online shop"}</span><span>→</span></button>
  </form>`;
}
function bindShopForm() {
  $("#shopForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const input = Object.fromEntries(new FormData(form).entries());
    if (!input.shopName.trim() || !input.ownerName.trim() || !input.ownerMobile.trim()) { toast("Shop name, owner and mobile are required.", "error"); return; }
    const existing = state.shops.find((item) => item.id === form.dataset.id);
    const payload = { ...input, id: existing?.id, monthlyFee: Number(input.monthlyFee || 0) };
    const button = form.querySelector("button[type='submit']");
    button.disabled = true;
    try {
      if (existing) {
        await api.updateShop(payload);
        toast("Shop Hostinger par update ho gaya.", "success");
      } else {
        const created = await api.createShop(payload);
        const activation = {
          shopId: created.shop_id, shopName: input.shopName, ownerMobile: input.ownerMobile,
          code: created.activation_code, password: created.temporary_password, createdAt: new Date().toISOString()
        };
        state.activations.unshift(activation);
        closeModal();
        mountModal({ title: "Shop Created — Save Credentials", body: `<div class="credential-card"><p>Ye credentials sirf ab dikhaye ja rahe hain. Shop owner ko secure tareeqe se dein.</p><div class="credential-value"><code>Activation: ${escapeHtml(activation.code)}</code><button type="button" data-copy="${escapeHtml(activation.code)}">Copy</button></div><div class="credential-value"><code>Temporary PIN: ${escapeHtml(activation.password)}</code><button type="button" data-copy="${escapeHtml(activation.password)}">Copy</button></div></div>` });
      }
      await refreshShops();
      if (existing) closeModal();
    } catch (error) {
      toast(error.message, "error");
      button.disabled = false;
    }
  });
}
function openNewShopModal() { mountModal({ title: "Create Online Shop", body: shopForm(), size: "wide" }); bindShopForm(); }
function openEditShopModal(id) { const shop = state.shops.find((item) => item.id === id); if (!shop) return; mountModal({ title: "Edit Shop", body: shopForm(shop), size: "wide" }); bindShopForm(); }
async function openShopDetails(id) {
  const shop = state.shops.find((item) => item.id === id); if (!shop) return;
  mountModal({ title: shop.shopName || "Shop Control", body: `<div class="empty-state"><b>Loading live shop control…</b>Reading branches, users, devices and sync status from Hostinger.</div>`, size: "wide" });
  try {
    const control = await api.shopControl(id);
    const branches = Array.isArray(control.branches) ? control.branches : [];
    const users = Array.isArray(control.users) ? control.users : [];
    const devices = Array.isArray(control.devices) ? control.devices : [];
    const sync = control.sync || {};
    const activation = state.activations.find((item) => String(item.shopId) === String(shop.id));
    const credentialCard = activation ? `<hr class="divider"><div class="credential-card"><strong>Latest one-time credentials in this browser session</strong><div class="credential-value"><code>Activation: ${escapeHtml(activation.code)}</code><button type="button" data-copy="${escapeHtml(activation.code)}">Copy</button></div><div class="credential-value"><code>Password: ${escapeHtml(activation.password || "—")}</code><button type="button" data-copy="${escapeHtml(activation.password || "")}">Copy</button></div></div>` : "";
    const branchRows = branches.length ? branches.map((b) => `<tr><td><strong>${escapeHtml(b.name)}</strong><br><small>${escapeHtml(b.code)}</small></td><td>${escapeHtml(b.address || "—")}</td><td>${escapeHtml([b.contact1,b.contact2,b.contact3].filter(Boolean).join(" · ") || "—")}</td><td>${badge(b.status)}</td></tr>`).join("") : `<tr><td colspan="4"><div class="empty-state"><b>No branch records</b></div></td></tr>`;
    const userRows = users.length ? users.map((u) => `<tr><td><strong>${escapeHtml(u.fullName)}</strong><br><small>${escapeHtml(u.mobile || "—")}</small></td><td>${badge(u.role)}</td><td>${escapeHtml(branches.find((b) => String(b.id) === String(u.branchId))?.name || (u.role === "super_admin" ? "All Branches" : "—"))}</td><td>${badge(u.status)}</td><td>${u.role === "super_admin" ? `<span class="muted-text">Owner protected</span>` : `<button type="button" data-action="user-status" data-shop-id="${escapeHtml(id)}" data-id="${escapeHtml(u.id)}" data-status="${u.status === "active" ? "blocked" : "active"}">${u.status === "active" ? "Block" : "Unblock"}</button>`}</td></tr>`).join("") : `<tr><td colspan="5"><div class="empty-state"><b>No users yet</b>Add a staff login.</div></td></tr>`;
    const deviceRows = devices.length ? devices.map((d) => `<tr><td><strong>${escapeHtml(d.platform)}</strong><br><small>${escapeHtml(d.deviceId)}</small></td><td>${escapeHtml(users.find((u) => String(u.id) === String(d.userId))?.fullName || "—")}</td><td>${timestamp(d.lastSeenAt, true)}</td><td>${badge(d.status)}</td><td><button type="button" data-action="device-status" data-shop-id="${escapeHtml(id)}" data-id="${escapeHtml(d.id)}" data-status="${d.status === "active" ? "blocked" : "active"}">${d.status === "active" ? "Block" : "Unblock"}</button></td></tr>`).join("") : `<tr><td colspan="5"><div class="empty-state"><b>No registered devices</b>Android/Desktop appears here after online login and sync.</div></td></tr>`;
    mountModal({ title: `${shop.shopName} — Live Control`, size: "wide", body: `
      <div class="detail-grid"><div class="detail-item"><span>Owner</span><strong>${escapeHtml(shop.ownerName)}</strong></div><div class="detail-item"><span>Mobile</span><strong>${escapeHtml(shop.ownerMobile)}</strong></div><div class="detail-item"><span>Status</span><strong>${badge(shop.status)}</strong></div><div class="detail-item"><span>Plan / Expiry</span><strong>${escapeHtml(shop.plan)} · ${escapeHtml(shop.expiryDate || "No expiry")}</strong></div></div>
      <section class="metric-grid" style="margin-top:16px"><article class="metric-card"><div class="metric-head"><span>Active Sessions</span></div><h4>${Number(control.activeSessions || 0)}</h4><small>Current login sessions</small></article><article class="metric-card"><div class="metric-head"><span>Sync Operations</span></div><h4>${Number(sync.operations || 0)}</h4><small>${Number(sync.applied || 0)} applied</small></article><article class="metric-card ${Number(sync.openConflicts || 0) ? "danger" : ""}"><div class="metric-head"><span>Open Conflicts</span></div><h4>${Number(sync.openConflicts || 0)}</h4><small>Needs attention</small></article><article class="metric-card"><div class="metric-head"><span>Last Change</span></div><h4 style="font-size:15px">${escapeHtml(timestamp(sync.lastChangeAt, true))}</h4><small>Central sync change</small></article></section>
      <hr class="divider"><div class="modal-footer" style="justify-content:flex-start"><button class="primary-button" type="button" data-action="shop-add-branch" data-id="${escapeHtml(id)}">+ Branch</button><button class="primary-button" type="button" data-action="shop-add-user" data-id="${escapeHtml(id)}">+ Staff User</button><button class="secondary-button" type="button" data-action="revoke-shop-sessions" data-id="${escapeHtml(id)}">Revoke Sessions</button><button class="secondary-button" type="button" data-action="reset-shop-activation" data-id="${escapeHtml(id)}">Reset Activation</button></div>
      <hr class="divider"><h4>Branches</h4><section class="table-card"><table><thead><tr><th>Branch</th><th>Address</th><th>Contacts</th><th>Status</th></tr></thead><tbody>${branchRows}</tbody></table></section>
      <h4 style="margin-top:18px">Users & Roles</h4><section class="table-card"><table><thead><tr><th>User</th><th>Role</th><th>Branch</th><th>Status</th><th>Control</th></tr></thead><tbody>${userRows}</tbody></table></section>
      <h4 style="margin-top:18px">Registered Devices</h4><section class="table-card"><table><thead><tr><th>Device</th><th>User</th><th>Last Seen</th><th>Status</th><th>Control</th></tr></thead><tbody>${deviceRows}</tbody></table></section>
      ${credentialCard}<hr class="divider"><p class="muted-text">${escapeHtml(shop.notes || "No notes")}</p><div class="modal-footer"><button class="secondary-button" type="button" data-action="edit-shop" data-id="${escapeHtml(shop.id)}">Edit Shop</button><button class="status-button" type="button" data-action="status" data-id="${escapeHtml(shop.id)}">Change Status</button></div>` });
  } catch (error) {
    closeModal(); toast(error.message, "error");
  }
}

function openBranchModal(shopId) {
  mountModal({ title: "Add Branch", body: `<form id="branchControlForm" class="modal-form" data-shop-id="${escapeHtml(shopId)}"><div class="two-col"><label>Branch Name<input name="name" required placeholder="Main Market Branch"></label><label>Branch Code<input name="code" required placeholder="BR02"></label></div><label>Address<input name="address" placeholder="Complete branch address"></label><div class="two-col"><label>Contact 1<input name="contact1"></label><label>Contact 2<input name="contact2"></label></div><label>Contact 3<input name="contact3"></label><button class="primary-button large" type="submit">Save Branch</button></form>` });
  $("#branchControlForm").addEventListener("submit", async (event) => {
    event.preventDefault(); const input = Object.fromEntries(new FormData(event.currentTarget).entries());
    try { await api.saveBranch({ ...input, shopId }); toast("Branch Hostinger par save ho gaya.", "success"); await openShopDetails(shopId); } catch (error) { toast(error.message, "error"); }
  });
}

async function openUserModal(shopId) {
  try {
    const control = await api.shopControl(shopId); const branches = Array.isArray(control.branches) ? control.branches : [];
    mountModal({ title: "Add Staff User", body: `<form id="userControlForm" class="modal-form" data-shop-id="${escapeHtml(shopId)}"><div class="two-col"><label>Full Name<input name="fullName" required></label><label>Mobile<input name="mobile" required></label></div><label>Email (optional)<input name="email" type="email"></label><div class="two-col"><label>Role<select name="role"><option value="branch_manager">Branch Manager</option><option value="cashier">Cashier</option><option value="technician">Technician</option><option value="accountant">Accountant</option></select></label><label>Branch<select name="branchId" required><option value="">Select branch</option>${branches.map((b) => `<option value="${escapeHtml(b.id)}">${escapeHtml(b.name)}</option>`).join("")}</select></label></div><label>Temporary PIN<input name="password" type="password" inputmode="numeric" pattern="[0-9]{4}" minlength="4" maxlength="4" required placeholder="4-digit PIN"></label><button class="primary-button large" type="submit">Create Staff Login</button></form>` });
    $("#userControlForm").addEventListener("submit", async (event) => {
      event.preventDefault(); const input = Object.fromEntries(new FormData(event.currentTarget).entries());
      try { await api.saveUser({ ...input, shopId }); toast("Staff login Hostinger par create ho gaya.", "success"); await openShopDetails(shopId); } catch (error) { toast(error.message, "error"); }
    });
  } catch (error) { toast(error.message, "error"); }
}

function openStatusModal(id) {
  const shop = state.shops.find((item) => item.id === id); if (!shop) return;
  mountModal({ title: "Change Online Status", body: `<p class="muted-text">Current status for <strong>${escapeHtml(shop.shopName)}</strong>: ${badge(shop.status)}</p><div class="status-actions">${["active", "trial", "suspended", "blocked"].map((status) => `<button class="status-button" data-set-status="${status}" data-id="${escapeHtml(id)}">${status}</button>`).join("")}</div>` });
}
function openSupportModal() {
  mountModal({ title: "Add Support Note", body: `<form id="supportForm" class="modal-form"><label>Issue / Note<input name="title" required placeholder="e.g. Shop asked for activation reset" /></label><label>Status<select name="status"><option>open</option><option>inprogress</option><option>resolved</option></select></label><button class="primary-button large" type="submit"><span>Save note</span><span>→</span></button></form>` });
  $("#supportForm").addEventListener("submit", (e) => { e.preventDefault(); const input = Object.fromEntries(new FormData(e.currentTarget).entries()); state.support.unshift({ id: `T-${String(state.support.length + 1).padStart(3, "0")}`, ...input, createdAt: new Date().toISOString() }); addActivity("Support note", input.title, "◌"); saveLocal(); closeModal(); render(); toast("Support note saved.", "success"); });
}
function exportData() {
  const data = JSON.stringify({ format: "skybarech-super-admin-backup", version: 2, exportedAt: new Date().toISOString(), data: { shops: state.shops, activations: state.activations, support: state.support, users: state.users, rules: state.rules, activity: state.activity } }, null, 2);
  const blob = new Blob([data], { type: "application/json" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = `skybarech-super-admin-backup-${todayIso()}.json`;
  link.click();
  URL.revokeObjectURL(link.href);
  toast("Backup exported.", "success");
}
function importData() {
  const input = document.createElement("input");
  input.type = "file"; input.accept = "application/json";
  input.onchange = () => {
    const file = input.files && input.files[0]; if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const backup = JSON.parse(String(reader.result || "{}"));
        if (backup.format !== "skybarech-super-admin-backup" || backup.version !== 2 || !backup.data) throw new Error("Unsupported backup");
        const data = backup.data;
        if (!["shops", "activations", "support", "users", "rules", "activity"].every((key) => !data[key] || Array.isArray(data[key]))) throw new Error("Invalid data structure");
        state.shops = Array.isArray(data.shops) ? data.shops.map(normalizeShop) : state.shops;
        state.activations = Array.isArray(data.activations) ? data.activations : state.activations;
        state.support = Array.isArray(data.support) ? data.support : state.support;
        state.users = Array.isArray(data.users) ? data.users : state.users;
        state.rules = Array.isArray(data.rules) && data.rules.length ? data.rules.map(normalizeRule) : state.rules;
        state.activity = Array.isArray(data.activity) ? data.activity : state.activity;
        addActivity("Backup imported", `${state.shops.length} shops loaded`, "⇧");
        saveLocal(); render(); toast("Backup imported.", "success");
      } catch (_) { toast("Invalid backup file.", "error"); }
    };
    reader.readAsText(file);
  };
  input.click();
}
function showLogin() {
  clearInterval(liveRefreshTimer);
  liveRefreshTimer = null;
  state.user = null;
  state.shops = []; state.activations = []; state.shopData = []; state.permissions = null;
  state.support = []; state.activity = []; state.permissionShopId = ''; state.dataShopId = '';
  state.page = 'dashboard';
  $("#pageContent").innerHTML = '';
  shopsRevision = "";
  closeModal();
  $("#adminView").classList.add("hidden");
  $("#loginView").classList.remove("hidden");
  $("#loginPassword").value = "";
  $("#loginPassword").type = "password";
  $("#loginNote").textContent = "Sign in with your administrator email and 4-digit PIN.";
}
async function handleAuth() {
  // Do not reveal the workspace until a protected request succeeds.
  await refreshShops();
  $("#loginView").classList.add("hidden");
  $("#adminView").classList.remove("hidden");
  $("#loginPassword").value = "";
  $("#profileInitial").textContent = initials(state.user.email);
  $("#sideConnection").textContent = "Online";
  startLiveRefresh();
}
function boot() {
  loadLocal();
  window.addEventListener("skybarech:session-expired", showLogin);
  if (api.configured && api.hasSession()) {
    const button = $("#loginForm button[type='submit']");
    button.disabled = true;
    api.me().then(admin => { state.user = admin; return handleAuth(); }).catch(error => {
      showLogin(); toast(error.message, "error");
    }).finally(() => { button.disabled = false; });
  }
  if (!api.configured) {
    $("#setupNotice").classList.remove("hidden");
    $("#setupNotice").textContent = "Setup required: configure your HTTPS Hostinger API URL.";
    $("#loginForm button[type='submit']").disabled = true;
  }
  $("#loginForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const email = $("#loginEmail").value.trim();
    const password = $("#loginPassword").value;
    if (password.length !== 4 || !/^[0-9]{4}$/.test(password)) { toast("PIN sirf 4 digits ka hona chahiye.", "error"); return; }
    const submit = event.currentTarget.querySelector("button[type=submit]");
    if (submit.disabled) return;
    submit.disabled = true;
    try {
      state.user = await api.login(email, password);
      await handleAuth();
    } catch (error) {
      toast(error.message, "error");
    } finally { submit.disabled = false; }
  });
  document.addEventListener("click", async (event) => {
    if (event.target.closest("#logoutButton")) {
      const button = $("#logoutButton"); button.disabled = true;
      try { await api.logout(); } catch (error) { toast(error.message, "error"); }
      finally { showLogin(); button.disabled = false; }
      return;
    }
    const togglePassword = event.target.closest("[data-toggle-password]");
    if (togglePassword) {
      const input = document.getElementById(togglePassword.dataset.togglePassword);
      if (input) {
        const show = input.type === "password";
        input.type = show ? "text" : "password";
        togglePassword.textContent = show ? "Hide" : "Show";
        togglePassword.setAttribute("aria-label", show ? "Hide PIN" : "Show PIN");
      }
      return;
    }
    if (event.target.matches("[data-close-modal]") || event.target.matches("[data-modal-backdrop]")) { closeModal(); return; }
    const copy = event.target.closest("[data-copy]");
    if (copy) { navigator.clipboard?.writeText(copy.dataset.copy); toast("Copied.", "success"); return; }
    const nav = event.target.closest(".nav-item"); if (nav) { setPage(nav.dataset.page); return; }
    if (event.target.closest("#menuToggle")) { const open = $("#sidebar").classList.toggle("open"); $("#menuToggle").setAttribute("aria-expanded", String(open)); return; }
    if (event.target.closest("#newShopButton") || event.target.closest("[data-action='new-shop']")) { openNewShopModal(); return; }
    if (event.target.closest("#syncButton") || event.target.closest("[data-action='refresh']")) { try { await refreshShops(true); } catch (error) { toast(error.message, "error"); } return; }
    if (event.target.closest("[data-action='go-colors']")) { setPage("settings"); return; }
    if (event.target.closest("[data-action='go-shops']")) { setPage("shops"); return; }
    if (event.target.closest("[data-action='view-only-note']")) { toast("Super Admin is view-only for shop business records.", "success"); return; }
    if (event.target.closest("[data-action='export-data']")) { exportData(); return; }
    if (event.target.closest("[data-action='export-activation-all']")) { downloadActivationFile(); return; }
    const dlLink = event.target.closest("[data-action='download-shop-link']"); if (dlLink) { const shop = state.shops.find((s) => s.id === dlLink.dataset.id); if (shop) downloadActivationFile(shop); return; }
    const cpLink = event.target.closest("[data-action='copy-shop-link']"); if (cpLink) { const shop = state.shops.find((s) => s.id === cpLink.dataset.id); if (shop) copyActivationText(shop); return; }
    if (event.target.closest("[data-action='import-data']")) { importData(); return; }
    if (event.target.closest("[data-action='new-ticket']")) { toast("Support requests are created from shop clients and are read-only here.", "success"); return; }

    const filter = event.target.closest("[data-filter]"); if (filter) { state.shopFilter = filter.dataset.filter; render(); return; }
    const colors = event.target.closest('[data-action="shop-colors"]'); if (colors) { await openAppearance(colors.dataset.id || $('#appearanceShop')?.value); return; }
    const view = event.target.closest("[data-action='view-shop']"); if (view) { await openShopDetails(view.dataset.id); return; }
    const addBranch = event.target.closest("[data-action='shop-add-branch']"); if (addBranch) { openBranchModal(addBranch.dataset.id); return; }
    const addUser = event.target.closest("[data-action='shop-add-user']"); if (addUser) { await openUserModal(addUser.dataset.id); return; }
    const userStatus = event.target.closest("[data-action='user-status']"); if (userStatus) { try { await api.setUserStatus(userStatus.dataset.shopId, userStatus.dataset.id, userStatus.dataset.status); toast("User status updated.", "success"); await openShopDetails(userStatus.dataset.shopId); } catch (error) { toast(error.message, "error"); } return; }
    const deviceStatus = event.target.closest("[data-action='device-status']"); if (deviceStatus) { try { await api.setDeviceStatus(deviceStatus.dataset.shopId, deviceStatus.dataset.id, deviceStatus.dataset.status); toast("Device status updated.", "success"); await openShopDetails(deviceStatus.dataset.shopId); } catch (error) { toast(error.message, "error"); } return; }
    const revokeSessions = event.target.closest("[data-action='revoke-shop-sessions']"); if (revokeSessions && confirm("Is shop ke tamam Android/Desktop login sessions revoke karne hain?")) { try { const result = await api.revokeSessions(revokeSessions.dataset.id); toast(`${Number(result.revoked || 0)} session(s) revoked.`, "success"); await openShopDetails(revokeSessions.dataset.id); } catch (error) { toast(error.message, "error"); } return; }
    const resetActivation = event.target.closest("[data-action='reset-shop-activation']"); if (resetActivation && confirm("New activation code/password generate karke existing sessions revoke karne hain?")) { try { const created = await api.resetActivation(resetActivation.dataset.id); const shop = state.shops.find((x) => x.id === resetActivation.dataset.id); const activation = { shopId: created.shop_id, shopName: shop?.shopName || created.shop_id, ownerMobile: created.owner_mobile, code: created.activation_code, password: created.temporary_password, createdAt: new Date().toISOString() }; state.activations = state.activations.filter((x) => String(x.shopId) !== String(created.shop_id)); state.activations.unshift(activation); mountModal({ title: "Activation Reset — Save Credentials", body: `<div class="credential-card"><p>Existing sessions revoke ho gaye. Ye new credentials owner ko dein.</p><div class="credential-value"><code>Activation: ${escapeHtml(activation.code)}</code><button type="button" data-copy="${escapeHtml(activation.code)}">Copy</button></div><div class="credential-value"><code>Temporary PIN: ${escapeHtml(activation.password)}</code><button type="button" data-copy="${escapeHtml(activation.password)}">Copy</button></div></div>` }); } catch (error) { toast(error.message, "error"); } return; }
    const edit = event.target.closest("[data-action='edit-shop']"); if (edit) { openEditShopModal(edit.dataset.id); return; }
    const status = event.target.closest("[data-action='status']"); if (status) { openStatusModal(status.dataset.id); return; }
    const setStatus = event.target.closest("[data-set-status]"); if (setStatus) { try { await api.setShopStatus(setStatus.dataset.id, setStatus.dataset.setStatus); closeModal(); await refreshShops(); toast("Status Hostinger par update ho gaya.", "success"); } catch (error) { toast(error.message, "error"); } return; }
    const del = event.target.closest("[data-action='delete-shop']"); if (del && confirm("Is shop aur is se linked tamam server data permanently delete karna hai?")) { try { await api.deleteShop(del.dataset.id); await refreshShops(); toast("Shop server se delete ho gaya.", "success"); } catch (error) { toast(error.message, "error"); } return; }
  });
  document.addEventListener("input", (event) => {
    const toggle = event.target.closest("[data-rule-toggle]");
    if (toggle) { const rule = state.rules.find((item) => item.id === toggle.dataset.ruleToggle); if (rule) { rule.enabled = toggle.checked; if (!rule.enabled && rule.level === "allow") rule.level = "block"; if (rule.enabled && rule.level === "block") rule.level = "allow"; saveLocal(); render(); } return; }
    if (event.target.id === "shopSearch") { state.shopSearch = event.target.value; render(); const input = $("#shopSearch"); if (input) { input.focus(); input.setSelectionRange(input.value.length, input.value.length); } }
  });
  document.addEventListener("change", async (event) => {
    if (event.target.id === "permissionShopSelect") { await refreshPermissions(event.target.value); return; }
    if (event.target.id === "dataShopSelect") { await refreshShopData(event.target.value); return; }
    const permission = event.target.closest("[data-permission-role][data-permission-key]");
    if (permission) {
      try {
        const saved = await api.savePermission({ shopId: state.permissionShopId, roleName: permission.dataset.permissionRole, permissionKey: permission.dataset.permissionKey, accessLevel: permission.value });
        const rows = state.permissions?.rows || [];
        const found = rows.find((r) => r.roleName === saved.roleName && r.permissionKey === saved.permissionKey);
        if (found) found.accessLevel = saved.accessLevel; else rows.push(saved);
        toast("Role permission Hostinger par save ho gayi.", "success");
      } catch (error) { toast(error.message, "error"); await refreshPermissions(state.permissionShopId); }
      return;
    }
    const level = event.target.closest("[data-rule-level]");
    if (level) { const rule = state.rules.find((item) => item.id === level.dataset.ruleLevel); if (rule) { rule.level = level.value; rule.enabled = level.value !== "block"; saveLocal(); render(); } }
  });
  document.addEventListener("visibilitychange", () => { if (!document.hidden) liveRefresh(); });
  window.addEventListener("online", liveRefresh);
  window.addEventListener("focus", liveRefresh);
}
boot();

document.addEventListener("keydown", (event) => {
 if (event.key === "Escape") { closeModal(); document.querySelector("#sidebar").classList.remove("open"); document.querySelector("#menuToggle").setAttribute("aria-expanded", "false"); }
});
document.addEventListener("click", (event) => {
 const sidebar = document.querySelector("#sidebar");
 if (event.target.closest(".nav-item") || (!sidebar.contains(event.target) && !event.target.closest("#menuToggle"))) {
  sidebar.classList.remove("open"); document.querySelector("#menuToggle").setAttribute("aria-expanded", "false");
 }
});
