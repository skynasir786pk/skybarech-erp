(() => {
  'use strict';

  /* --------------------------------------------------------------
     SkyBarech Desktop ERP - local offline source
     All data stays locally in localStorage until cloud sync is enabled.
  -------------------------------------------------------------- */

  const { t, html, htmlText } = window.SkyI18n || {t:x=>x, html:(s,...v)=>s.reduce((a,x,i)=>a+x+(v[i]??''),''), htmlText:x=>x};
  const workspaceThemes = [
    ['light', 'Classic Blue', 'Bright and familiar'], ['dark', 'Midnight', 'Comfortable after hours'],
    ['emerald', 'Emerald', 'Calm green accents'], ['violet', 'Amethyst', 'Soft violet accents'],
    ['slate', 'Slate', 'Quiet and focused'], ['sand', 'Warm Sand', 'Warm neutral tones']
  ];
  function savedTheme(fallback = 'dark') {
    let value = fallback;
    try { value = localStorage.getItem('skybarech-desktop-theme') || fallback; } catch {}
    return workspaceThemes.some(([key]) => key === value) ? value : 'dark';
  }
  function applyWorkspaceTheme(value) {
    if (state.shop?.appearance?.desktop?.enabled) return;
    if (!workspaceThemes.some(([key]) => key === value)) return;
    state.theme = value;
    try { localStorage.setItem('skybarech-desktop-theme', value); } catch {}
    document.documentElement.dataset.theme = value;
    document.querySelectorAll('[data-action="set-theme"]').forEach(button => {
      const selected = button.dataset.theme === value;
      button.classList.toggle('selected', selected);
      button.setAttribute('aria-pressed', String(selected));
    });
  }
  function themeChoices() {
    if (state.shop?.appearance?.desktop?.enabled) return html`<div class="security-tip">${icon('shield')}<span>Colors are managed by Super Admin.</span></div>`;
    return html`<div class="theme-grid" aria-label="${t('Workspace themes')}">${workspaceThemes.map(([key, name, description]) => html`<button type="button" class="theme-option ${state.theme === key ? 'selected' : ''}" data-action="set-theme" data-theme="${key}" aria-pressed="${state.theme === key}"><span class="theme-preview" aria-hidden="true"><span class="theme-preview-sidebar"></span><span class="theme-preview-page"><span></span><i></i><i></i><i></i></span><span class="theme-check">${icon('check')}</span></span><strong>${t(name)}</strong><small>${t(description)}</small></button>`).join('')}</div>`;
  }
  function applyManagedAppearance() {
    const root = document.documentElement;
    const theme = state.shop?.appearance?.desktop;
    const keys = ['--blue','--blue-2','--blue-3','--canvas','--surface','--surface-soft','--ink','--navy','--muted','--line','--on-accent','--sidebar-start','--sidebar-end','--sidebar-ink','--sidebar-muted'];
    if (!root.style?.setProperty) return;
    keys.forEach(key => root.style.removeProperty(key));
    if (!theme?.enabled || !['accent','background','surface','sidebar'].every(key => /^#[0-9a-f]{6}$/i.test(theme[key]))) {
      delete root.dataset.managedColors; root.dataset.theme = state.theme; return;
    }
    const luminance = hex => {
      const v = [1,3,5].map(i => parseInt(hex.slice(i,i+2),16)/255).map(v => v <= .04045 ? v/12.92 : ((v+.055)/1.055)**2.4);
      return .2126*v[0]+.7152*v[1]+.0722*v[2];
    };
    const ink = hex => luminance(hex) > .179 ? '#000000' : '#ffffff';
    const mix = (a,b,f) => '#' + [1,3,5].map(i => Math.round(parseInt(a.slice(i,i+2),16)*(1-f)+parseInt(b.slice(i,i+2),16)*f).toString(16).padStart(2,'0')).join('');
    const text = ink(theme.surface);
    const navText = ink(theme.sidebar);
    root.dataset.managedColors = 'true';
    root.dataset.theme = luminance(theme.surface) > .179 ? 'light' : 'dark';
    const colors = {'--blue':theme.accent,'--blue-2':theme.accent,'--blue-3':mix(theme.surface,theme.accent,.10),'--canvas':theme.background,'--surface':theme.surface,'--surface-soft':theme.surface,'--ink':text,'--navy':text,'--muted':text,'--line':mix(theme.surface,text,.20),'--on-accent':ink(theme.accent),'--sidebar-start':theme.sidebar,'--sidebar-end':theme.sidebar,'--sidebar-ink':navText,'--sidebar-muted':navText};
    Object.entries(colors).forEach(([key,value]) => root.style.setProperty(key,value));
  }
  function localPinBudget(failed = false, clear = false) {
    const key = 'skybarech-pin-attempts:' + (state.shop?.id || 'unlinked');
    try {
      let budget = JSON.parse(localStorage.getItem(key) || '{}');
      if (clear) { localStorage.removeItem(key); return false; }
      if (!budget.until || Date.now() >= budget.until) budget = {count:0, until:Date.now()+15*60*1000};
      if (failed) { budget.count++; localStorage.setItem(key, JSON.stringify(budget)); }
      return budget.count >= 5;
    } catch { return true; }
  }
  const app = document.getElementById('app');
  const modalRoot = document.getElementById('modal-root');
  const toastRoot = document.getElementById('toast-root');
  const STORAGE_KEY = 'skybarech-mobile-shop-erp-v1';
  // Production fallback keeps activation working even when an older packaged config.js is present.
  let apiBaseUrl = String(window.SKYBARECH_CONFIG?.apiBaseUrl || 'https://lightgrey-cormorant-560478.hostingersite.com/api').trim().replace(/\/+$/, '');
  let apiConfigured = /^https:\/\//i.test(apiBaseUrl) && !apiBaseUrl.includes('YOUR-DOMAIN');
  function setApiBaseUrl(value) {
    const candidate = String(value || '').trim().replace(/\/+$/, '');
    if (!/^https:\/\//i.test(candidate) || candidate.includes('YOUR-DOMAIN')) return false;
    apiBaseUrl = candidate;
    apiConfigured = true;
    try { localStorage.setItem('skybarech-api-base-url', candidate); } catch {}
    return true;
  }
  try { setApiBaseUrl(localStorage.getItem('skybarech-api-base-url') || apiBaseUrl); } catch {}
  let pendingRemoteActivation = null;
  let pendingShopSwitch = null;
  let pendingLoginShopSwitch = null;

  const makeId = (prefix = 'ID') => `${prefix}-${crypto.randomUUID()}`;
  const money = (value) => `${t('Rs.')} ${Number(value || 0).toLocaleString('en-PK')}`;
  const n = (value) => Number(value || 0).toLocaleString('en-PK');
  const localDateKey = (value = new Date()) => {
    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime())) return String(value || '').slice(0, 10);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };
  const recordDateKey = (record = {}) => String(record.date || record.time || record.timeLabel || '').slice(0, 10);
  const today = () => localDateKey();
  const prettyDate = (value) => {
    if (!value) return '—';
    const date = new Date(String(value).includes('T') ? value : `${value}T12:00:00`);
    if (Number.isNaN(date.getTime())) return esc(value);
    return date.toLocaleDateString(window.SkyI18n?.language === 'ur' ? 'ur-PK' : 'en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  };
  const initials = (name = '') => name.split(' ').map(x => x[0]).join('').slice(0, 2).toUpperCase() || 'SB';
  const esc = (value = '') => String(value).replace(/[&<>'"]/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#39;', '"':'&quot;' }[c]));

  function makeShopLogoDataUrl(name = '') {
    const text = initials(name || 'Mobile Shop');
    const safe = esc(text);
    const svg = html`<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color="#174be0"/><stop offset="100%" stop-color="#0f172a"/></linearGradient></defs><rect width="256" height="256" rx="58" fill="url(#g)"/><circle cx="198" cy="56" r="26" fill="#ffffff" opacity=".16"/><text x="50%" y="54%" text-anchor="middle" dominant-baseline="middle" font-family="Arial, Helvetica, sans-serif" font-size="88" font-weight="800" fill="#ffffff">${safe}</text></svg>`;
    return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
  }
  function ensureShopLogo(shop = {}) {
    return shop.logo || shop.logoUrl || shop.logo_url || '';
  }

  const iconPaths = {
    menu: htmlText('<path d="M4 7h16M4 12h16M4 17h16"/>'),
    dashboard: htmlText('<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>'),
    pos: htmlText('<path d="M4 5h16l-1 10H5L4 5Z"/><path d="M8 5a4 4 0 0 1 8 0M8 19h.01M16 19h.01"/>'),
    box: htmlText('<path d="m3 7 9-4 9 4-9 4-9-4Z"/><path d="M3 7v10l9 4 9-4V7M12 11v10"/>'),
    purchase: htmlText('<path d="M5 4h11l3 3v13H5V4Z"/><path d="M16 4v4h4M8 12h8M8 16h6"/><path d="m10 9 2 2 3-3"/>'),
    sale: htmlText('<path d="M6 3h12v18H6z"/><path d="M9 7h6M9 11h6M9 15h3"/>'),
    wrench: htmlText('<path d="M14.7 6.3a4 4 0 0 0-5.2 5.2L4 17l3 3 5.5-5.5a4 4 0 0 0 5.2-5.2l-2.5 2.5-2-2 1.5-3.7Z"/>'),
    users: htmlText('<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>'),
    supplier: htmlText('<path d="M3 21h18M5 21V7l7-4 7 4v14M9 21v-6h6v6M8 10h.01M16 10h.01"/>'),
    calendar: htmlText('<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01"/>'),
    chart: htmlText('<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>'),
    settings: htmlText('<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.1 2.1-.06-.06a1.7 1.7 0 0 0-1.88-.34 1.7 1.7 0 0 0-1.03 1.56v.1h-3v-.1a1.7 1.7 0 0 0-1.03-1.56 1.7 1.7 0 0 0-1.88.34l-.06.06-2.1-2.1.06-.06A1.7 1.7 0 0 0 7.06 15 1.7 1.7 0 0 0 5.5 14H5.4v-3h.1a1.7 1.7 0 0 0 1.56-1.03 1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.1-2.1.06.06a1.7 1.7 0 0 0 1.88.34A1.7 1.7 0 0 0 11.73 4.8v-.1h3v.1a1.7 1.7 0 0 0 1.03 1.53 1.7 1.7 0 0 0 1.88-.34l.06-.06 2.1 2.1-.06.06a1.7 1.7 0 0 0-.34 1.88A1.7 1.7 0 0 0 20.96 11h.1v3h-.1A1.7 1.7 0 0 0 19.4 15Z"/>'),
    help: htmlText('<path d="M4 14a8 8 0 1 1 16 0"/><path d="M18 19c0 1.1-.9 2-2 2h-4"/><path d="M4 14v4a2 2 0 0 0 2 2h1v-7H6a2 2 0 0 0-2 2ZM20 14v4a2 2 0 0 1-2 2h-1v-7h1a2 2 0 0 1 2 2Z"/>'),
    search: htmlText('<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>'),
    bell: htmlText('<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>'),
    plus: htmlText('<path d="M12 5v14M5 12h14"/>'),
    chevronDown: htmlText('<path d="m6 9 6 6 6-6"/>'),
    chevronRight: htmlText('<path d="m9 18 6-6-6-6"/>'),
    arrowLeft: htmlText('<path d="m15 18-6-6 6-6M9 12h11"/>'),
    arrowRight: htmlText('<path d="m9 18 6-6-6-6M4 12h11"/>'),
    filter: htmlText('<path d="M4 5h16l-6 7v5l-4 2v-7L4 5Z"/>'),
    scan: htmlText('<path d="M4 7V5a1 1 0 0 1 1-1h2M17 4h2a1 1 0 0 1 1 1v2M20 17v2a1 1 0 0 1-1 1h-2M7 20H5a1 1 0 0 1-1-1v-2M8 8v8M11 8v8M14 8v8M17 8v8"/>'),
    eye: htmlText('<path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z"/><circle cx="12" cy="12" r="2.5"/>'),
    lock: htmlText('<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>'),
    user: htmlText('<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>'),
    shield: htmlText('<path d="M12 3 4.5 6v5.6c0 4.6 3.2 7.8 7.5 9.4 4.3-1.6 7.5-4.8 7.5-9.4V6L12 3Z"/><path d="m9.2 12 1.8 1.8 3.8-4"/>'),
    phone: htmlText('<rect x="7" y="2" width="10" height="20" rx="2"/><path d="M11 18h2"/>'),
    laptop: htmlText('<path d="M5 5h14a1 1 0 0 1 1 1v9H4V6a1 1 0 0 1 1-1Z"/><path d="M2 19h20l-2-4H4l-2 4Z"/><path d="M9 17h6"/>'),
    wallet: htmlText('<path d="M3 7h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z"/><path d="M3 7V5a2 2 0 0 1 2-2h13v4M16 13h4"/>'),
    money: htmlText('<circle cx="12" cy="12" r="9"/><path d="M15.5 9.3c-.4-1-1.5-1.7-3.5-1.7-2.2 0-3.4.9-3.4 2.2 0 1.2.9 1.8 3.5 2.3 2.5.5 3.6 1.2 3.6 2.7 0 1.4-1.4 2.4-3.7 2.4-2.3 0-3.8-.9-4.2-2.2M12 5.8v12.4"/>'),
    printer: htmlText('<path d="M6 9V3h12v6M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2M6 14h12v7H6z"/>'),
    share: htmlText('<circle cx="18" cy="5" r="2"/><circle cx="6" cy="12" r="2"/><circle cx="18" cy="19" r="2"/><path d="m8 11 8-5M8 13l8 5"/>'),
    trash: htmlText('<path d="M4 7h16M10 11v6M14 11v6M9 7l1-3h4l1 3M6 7l1 14h10l1-14"/>'),
    edit: htmlText('<path d="m4 20 4.2-1 10.5-10.5a2.1 2.1 0 0 0-3-3L5.2 16 4 20Z"/><path d="m13.8 7.5 3 3"/>'),
    close: htmlText('<path d="M6 6l12 12M18 6 6 18"/>'),
    download: htmlText('<path d="M12 3v12M7 10l5 5 5-5M5 21h14"/>'),
    upload: htmlText('<path d="M12 16V4M7 9l5-5 5 5M5 20h14"/>'),
    check: htmlText('<path d="m5 12 4 4L19 6"/>'),
    info: htmlText('<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/>'),
    moon: htmlText('<path d="M20.6 15.2A8.5 8.5 0 0 1 8.8 3.4 8.5 8.5 0 1 0 20.6 15.2Z"/>'),
    sun: htmlText('<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>'),
    barcode: htmlText('<path d="M4 5v14M7 5v14M10 5v14M14 5v14M16 5v14M20 5v14"/>'),
    receipt: htmlText('<path d="M6 3h12v18l-3-2-3 2-3-2-3 2V3Z"/><path d="M9 8h6M9 12h6M9 16h3"/>'),
    cloud: htmlText('<path d="M17.5 19H7a5 5 0 1 1 .4-10A6 6 0 0 1 19 10.3 4.3 4.3 0 0 1 17.5 19Z"/><path d="M12 11v7M9.5 13.5 12 11l2.5 2.5"/>'),
    more: htmlText('<circle cx="5" cy="12" r="1" fill="currentColor"/><circle cx="12" cy="12" r="1" fill="currentColor"/><circle cx="19" cy="12" r="1" fill="currentColor"/>'),
    logout: htmlText('<path d="M10 17l5-5-5-5M15 12H3M14 4h5v16h-5"/>'),
    globe: htmlText('<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"/>'),
    star: htmlText('<path d="m12 3 2.7 5.5 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1-4.4-4.3 6.1-.9L12 3Z"/>')
  };

  function icon(name, extra = '') {
    return html`<svg class="icon ${extra}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${iconPaths[name] || iconPaths.info}</svg>`;
  }

  function productThumb(product = {}, size = '') {
    const image = product.image || '';
    const label = esc(product.name || product.model || 'Product');
    return html`<span class="product-thumb ${image ? 'has-image' : ''} ${size}">${image ? html`<img src="${esc(image)}" alt="${t(label)}">` : ''}</span>`;
  }

  function resizeProductImage(file, done) {
    if (!file || !file.type || !file.type.startsWith('image/')) {
      notify('Invalid image', 'Please choose a JPG, PNG or WebP product picture.', 'error');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      notify('Image too large', 'Please choose an image under 5 MB. It will be compressed for local storage.', 'error');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        const max = 720;
        const scale = Math.min(1, max / Math.max(img.width, img.height));
        const canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.round(img.width * scale));
        canvas.height = Math.max(1, Math.round(img.height * scale));
        const ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        done(canvas.toDataURL('image/jpeg', 0.82));
      };
      img.onerror = () => notify('Image error', 'This picture could not be loaded.', 'error');
      img.src = reader.result;
    };
    reader.onerror = () => notify('Image error', 'This picture could not be read.', 'error');
    reader.readAsDataURL(file);
  }

  function resizeDocumentFile(file, done) {
    if (!file) return;
    const allowed = file.type.startsWith('image/') || file.type === 'application/pdf';
    if (!allowed) {
      notify('Invalid file', 'Choose a JPG, PNG, WebP or PDF document.', 'error');
      return;
    }
    if (file.size > 4 * 1024 * 1024) {
      notify('File too large', 'Choose a document under 4 MB for local storage.', 'error');
      return;
    }
    if (file.type.startsWith('image/')) return resizeProductImage(file, done);
    const reader = new FileReader();
    reader.onload = () => done(reader.result);
    reader.onerror = () => notify('File error', 'This document could not be read.', 'error');
    reader.readAsDataURL(file);
  }

  function documentUploadSlot(name, label) {
    return html`<div class="field doc-upload-field"><label>${t(label)}</label><input type="hidden" name="${name}" data-doc-value="${name}"><label class="upload-slot doc-upload-slot">${icon('upload')} Upload ${t(label)}<input type="file" accept="image/*,application/pdf" data-doc-file data-doc-name="${name}" data-doc-label="${label}"></label><small class="muted" data-doc-preview="${name}">No file selected</small></div>`;
  }

  function allKnownImeis() {
    const values = [];
    (state.products || []).forEach(p => {
      if (Array.isArray(p.imeis)) values.push(...p.imeis);
      ['imei','imei1','imei2','sku','serial'].forEach(k => { if (p[k]) values.push(p[k]); });
      if (p.notes && String(p.notes).includes('IMEI')) values.push(...String(p.notes).match(/\d{10,18}/g) || []);
    });
    (state.stockMoves || []).forEach(m => { if (Array.isArray(m.imeis)) values.push(...m.imeis); });
    return new Set(values.map(x => String(x || '').replace(/\D/g, '')).filter(x => x.length >= 10 && x.length <= 18));
  }


  const ACCESSORY_CATEGORIES = ['Cover','Glass / Protector','Charger','Cable','Handsfree','Earbuds','Power Bank','Mobile Holder','Smart Watch','Speaker','OTG / Connector','Memory Card','Other','Accessory'];
  const SPARE_PART_CATEGORIES = ['Display / LCD','Touch Panel','Battery','Charging Board','Charging Strip','Speaker / Ringer','Mic','Camera','Back Camera','Front Camera','Fingerprint','Power Button Flex','Volume Button Flex','SIM Jacket','Back Cover','Frame / Body','Panel','IC / Board Part','Connector','Repair Part','Other Part'];
  const MODEL_BASED_ACCESSORY_CATEGORIES = ['Cover','Glass / Protector','Repair Part'];
  const ACCESSORY_BRANDS = ['iPhone','Samsung','Vivo','Oppo','Infinix','Tecno','Xiaomi','Realme','Itel','Nokia','Other'];
  const ACCESSORY_COLORS = ['Black','White','Blue','Red','Transparent','Mix','Other'];
  const ACCESSORY_QUALITIES = ['Original','Master Copy','A Quality','Local','China'];
  const ACCESSORY_WARRANTIES = ['No Warranty','7 Days','15 Days','1 Month','3 Months','6 Months'];
  const LAPTOP_BRANDS = ['HP','Dell','Lenovo','Apple','Acer','Asus','Microsoft','MSI','Samsung','Razer','Toshiba','Other'];
  const LAPTOP_MODELS = {
    HP: ['EliteBook','ProBook','Pavilion','Envy','Spectre','Victus','Omen','ZBook'],
    Dell: ['Latitude','Inspiron','Vostro','XPS','Precision','G Series','Alienware'],
    Lenovo: ['ThinkPad','IdeaPad','ThinkBook','Yoga','Legion','LOQ'],
    Apple: ['MacBook Air','MacBook Pro'], Acer: ['Aspire','Swift','TravelMate','Nitro','Predator'],
    Asus: ['VivoBook','ZenBook','ExpertBook','TUF','ROG'], Microsoft: ['Surface Laptop','Surface Book','Surface Pro'],
    MSI: ['Modern','Prestige','Katana','Stealth','Raider'], Samsung: ['Galaxy Book'], Razer: ['Blade'], Toshiba: ['Dynabook'], Other: []
  };

  const MOBILE_MODEL_LIBRARY = {
    iPhone: ['iPhone 6','iPhone 6 Plus','iPhone 7','iPhone 7 Plus','iPhone 8','iPhone 8 Plus','iPhone X','iPhone XR','iPhone XS','iPhone XS Max','iPhone 11','iPhone 11 Pro','iPhone 11 Pro Max','iPhone 12','iPhone 12 Mini','iPhone 12 Pro','iPhone 12 Pro Max','iPhone 13','iPhone 13 Mini','iPhone 13 Pro','iPhone 13 Pro Max','iPhone 14','iPhone 14 Plus','iPhone 14 Pro','iPhone 14 Pro Max','iPhone 15','iPhone 15 Plus','iPhone 15 Pro','iPhone 15 Pro Max'],
    Samsung: ['Galaxy A04','Galaxy A04s','Galaxy A05','Galaxy A05s','Galaxy A12','Galaxy A13','Galaxy A14','Galaxy A15','Galaxy A23','Galaxy A24','Galaxy A25','Galaxy A32','Galaxy A33','Galaxy A34','Galaxy A35','Galaxy A52','Galaxy A53','Galaxy A54','Galaxy A55','Galaxy S20','Galaxy S21','Galaxy S22','Galaxy S23','Galaxy S23 Ultra','Galaxy S24','Galaxy S24 Ultra','Galaxy Note 20'],
    Vivo: ['Vivo Y12','Vivo Y15','Vivo Y16','Vivo Y17','Vivo Y20','Vivo Y21','Vivo Y22','Vivo Y27','Vivo Y33s','Vivo Y35','Vivo V21','Vivo V23','Vivo V25','Vivo V27','Vivo V29'],
    Oppo: ['Oppo A16','Oppo A17','Oppo A18','Oppo A31','Oppo A54','Oppo A57','Oppo A58','Oppo A76','Oppo A77','Oppo A78','Oppo F17','Oppo F19','Oppo F21 Pro','Oppo Reno 6','Oppo Reno 8'],
    Infinix: ['Infinix Hot 10','Infinix Hot 11','Infinix Hot 12','Infinix Hot 20','Infinix Hot 30','Infinix Hot 40','Infinix Note 10','Infinix Note 11','Infinix Note 12','Infinix Note 30','Infinix Smart 6','Infinix Smart 7','Infinix Smart 8'],
    Tecno: ['Tecno Spark 6','Tecno Spark 7','Tecno Spark 8','Tecno Spark 9','Tecno Spark 10','Tecno Spark 20','Tecno Camon 17','Tecno Camon 18','Tecno Camon 19','Tecno Camon 20','Tecno Pova 3','Tecno Pova 5'],
    Xiaomi: ['Redmi 9','Redmi 10','Redmi 12','Redmi 13C','Redmi Note 10','Redmi Note 11','Redmi Note 12','Redmi Note 13','Redmi Note 13 Pro','Poco X3','Poco X4','Poco X5','Poco X6'],
    Realme: ['Realme C11','Realme C21','Realme C25','Realme C30','Realme C33','Realme C35','Realme C51','Realme C53','Realme C55','Realme 8','Realme 9','Realme 10','Realme 11'],
    Itel: ['Itel A23','Itel A26','Itel A48','Itel A60','Itel A70','Itel S23','Itel S23 Plus','Itel Vision 1','Itel Vision 2'],
    Nokia: ['Nokia 2.4','Nokia 3.4','Nokia 5.4','Nokia C10','Nokia C20','Nokia C21','Nokia C30','Nokia G10','Nokia G20','Nokia G21','Nokia G22'],
    Other: ['Universal','Other Model']
  };
  const modelListForBrand = (brand = 'Samsung') => MOBILE_MODEL_LIBRARY[brand] || MOBILE_MODEL_LIBRARY.Other;
  function mobileBrandSelect(selected = 'Samsung', label = 'Mobile Brand') {
    const clean = String(selected || 'Samsung');
    return html`<label>${esc(t(label))}</label><select class="select" name="brand" data-mobile-brand><option value="">Select Brand</option>${ACCESSORY_BRANDS.map(x=>html`<option ${clean===x?'selected':''}>${esc(x)}</option>`).join('')}</select><small class="field-hint">Select a brand from the dropdown. The model list will refresh automatically.</small>`;
  }
  function modelSyncBox(brand = 'Samsung', selected = '', fieldName = 'model') {
    const models = modelListForBrand(brand);
    const listId = `models-${Math.random().toString(36).slice(2, 8)}`;
    return html`<div class="model-sync-box" data-model-sync-box><div class="model-sync-head"><strong>Mobile Model</strong><span>Saved models for ${esc(brand)}</span></div><div class="input-wrap"><input class="input" name="${fieldName}" data-mobile-model-input list="${listId}" value="${esc(selected || '')}" placeholder="Search/select or type manually"><button class="btn btn-secondary btn-small" type="button" data-action="sync-mobile-models">Sync</button></div><datalist id="${listId}" data-mobile-model-list>${models.map(m=>html`<option value="${esc(m)}"></option>`).join('')}</datalist><small class="field-hint">Saved models are shown first. Use Sync to refresh; type manually if the model is missing.</small></div>`;
  }
  const currentShopId = () => state?.shop?.id || state?.session?.shop_id || 'SHOP-LOCAL-001';
  const SUPER_ADMIN_STORAGE_KEY = 'skybarech_super_admin_local_v2';
  const DESKTOP_SEED_KEY = 'skybarech_desktop_activation_seed';
  const DEFAULT_SHOP_PROFILE = {
    id: '',
    shopName: 'SkyBarech Mobile Shop',
    ownerName: '',
    ownerMobile: '',
    ownerMobile2: '',
    city: '',
    address: '',
    invoicePrefix: 'INV',
    plan: 'Not verified',
    status: 'unknown',
    shopCode: '',
    activationCode: '',
    androidPassword: '',
    logo: ''
  };
  function normalizeShopProfile(shop = {}) {
    const source = shop || {};
    return {
      ...DEFAULT_SHOP_PROFILE,
      ...source,
      id: source.id || source.shopId || source.shop_id || DEFAULT_SHOP_PROFILE.id,
      shopName: source.shopName || source.name || DEFAULT_SHOP_PROFILE.shopName,
      ownerName: source.ownerName || source.owner_name || source.owner || DEFAULT_SHOP_PROFILE.ownerName,
      ownerMobile: source.ownerMobile || source.owner_mobile || source.mobile || source.phone || DEFAULT_SHOP_PROFILE.ownerMobile,
      ownerMobile2: source.ownerMobile2 || source.mobile2 || source.phone2 || source.secondaryMobile || DEFAULT_SHOP_PROFILE.ownerMobile2,
      city: source.city || DEFAULT_SHOP_PROFILE.city,
      address: source.address || DEFAULT_SHOP_PROFILE.address,
      invoicePrefix: source.invoicePrefix || source.prefix || DEFAULT_SHOP_PROFILE.invoicePrefix,
      logo: ensureShopLogo({ ...source, shopName: source.shopName || source.name || DEFAULT_SHOP_PROFILE.shopName })
    };
  }
  function shopProfile() {
    state.shop = normalizeShopProfile(state.shop);
    return state.shop;
  }
  function shopDisplayName() { return shopProfile().shopName || 'Mobile Shop ERP'; }
  function shopOwnerName() { return shopProfile().ownerName || 'Shop Owner'; }
  function shopInitials(value = shopDisplayName()) { return initials(value); }
  function shopPhoneList(shop = shopProfile()) {
    const phones = [shop?.ownerMobile, shop?.ownerMobile2, shop?.mobile, shop?.phone].map(cleanText).filter(Boolean);
    return phones.filter((value, index, arr) => arr.indexOf(value) === index);
  }
  function shopPhoneText(shop = shopProfile()) {
    return shopPhoneList(shop).join(' / ');
  }
  function shopAddressText(shop = shopProfile()) {
    return [cleanText(shop?.address), cleanText(shop?.city)].filter(Boolean).join(', ');
  }
  function chunkReceiptLine(value = '', size = 34) {
    const words = cleanText(value).split(/\s+/).filter(Boolean);
    const lines = [];
    let line = '';
    for (const word of words) {
      const next = line ? `${line} ${word}` : word;
      if (next.length > size && line) { lines.push(line); line = word; }
      else line = next;
    }
    if (line) lines.push(line);
    return lines;
  }
  function formatReceiptAddress(value = '') {
    return chunkReceiptLine(value, 36).slice(0, 2).map(x => esc(x)).join(htmlText('<br>'));
  }
  function formatReceiptPhones(value = '') {
    return chunkReceiptLine(value, 30).slice(0, 2).map(x => esc(x)).join(htmlText('<br>'));
  }
  function receiptHeader(title = 'Sales Receipt', subtitle = '') {
    const shop = shopProfile();
    const phones = shopPhoneText(shop);
    const address = shopAddressText(shop);
    const logoUrl = shop.logo || makeShopLogoDataUrl(shop.shopName);
    const logo = html`<div class="receipt-logo"><img src="${esc(logoUrl)}" alt="${esc(shop.shopName)} logo"></div>`;
    return html`<div class="receipt-header designed-receipt-header">${logo}<div class="shop-name-frame"><span>${esc(shopDisplayName())}</span></div><div class="receipt-contact">${address ? html`<div class="receipt-address">${formatReceiptAddress(address)}</div>` : ''}${phones ? html`<div class="receipt-phone">${formatReceiptPhones(phones)}</div>` : ''}</div>${title ? html`<div class="receipt-title">${esc(t(title))}</div>` : ''}${subtitle ? html`<div class="receipt-subtitle">${esc(t(subtitle))}</div>` : ''}</div><div class="invoice-divider receipt-divider"></div>`;
  }
  function compactReceiptItemName(value = '', limit = 32) {
    const name = cleanText(value).replace(/\s+/g, ' ');
    return name.length > limit ? `${name.slice(0, Math.max(1, limit - 1)).trim()}…` : name;
  }
  function compactInvoiceItems(items = [], maxItems = 8) {
    const rows = Array.isArray(items) ? items : [];
    return { visible: rows.slice(0, maxItems), hidden: Math.max(0, rows.length - maxItems) };
  }
  function shopLogoMarkup(extra = '') {
    const shop = shopProfile();
    return shop.logo ? html`<span class="avatar shop-avatar ${extra}"><img src="${esc(shop.logo)}" alt="${esc(shop.shopName)} logo"></span>` : html`<span class="avatar shop-avatar ${extra}">${esc(shopInitials())}</span>`;
  }
  const cleanText = (v) => String(v || '').trim();
  const cleanLower = (v) => cleanText(v).toLowerCase();
  const cleanMobile = (v) => cleanText(v).replace(/\D/g, '');
  const sameMobile = (a, b) => cleanMobile(a) && cleanMobile(a) === cleanMobile(b);
  const shopPassword = (shop) => cleanText(shop?.androidPassword || shop?.tempPassword || shop?.password || '');
  async function passwordDigest(password, salt) {
    const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveBits']);
    const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: new TextEncoder().encode(salt), iterations: 150000 }, key, 256);
    return Array.from(new Uint8Array(bits), (byte) => byte.toString(16).padStart(2, '0')).join('');
  }
  async function withShopPassword(shop, password) {
    const passwordSalt = crypto.randomUUID();
    const passwordHash = await passwordDigest(password, passwordSalt);
    return normalizeShopProfile({ ...shop, passwordSalt, passwordHash, androidPassword: '', tempPassword: '', password: '' });
  }
  async function verifyShopPassword(shop, password) {
    if (shop?.passwordHash && shop?.passwordSalt) {
      return (await passwordDigest(password, shop.passwordSalt)) === shop.passwordHash;
    }
    return shopPassword(shop) === cleanText(password);
  }

  function readShopBucket(key) {
    try {
      const saved = JSON.parse(localStorage.getItem(key) || '{}');
      if (Array.isArray(saved)) return saved;
      if (Array.isArray(saved.shops)) return saved.shops;
      if (saved.shop) return [saved.shop];
      return [];
    } catch { return []; }
  }
  function writeDesktopSeedShop(shop) {
    try {
      const normalized = normalizeShopProfile(shop);
      const shops = readShopBucket(DESKTOP_SEED_KEY).filter((x) => (x.id || x.shopCode) !== (normalized.id || normalized.shopCode));
      shops.unshift(normalized);
      localStorage.setItem(DESKTOP_SEED_KEY, JSON.stringify({ shops, updatedAt: new Date().toISOString() }));
    } catch {}
  }
  function getSuperAdminShops() {
    const buckets = [readShopBucket(SUPER_ADMIN_STORAGE_KEY), readShopBucket(DESKTOP_SEED_KEY)];
    if (state?.shop?.id) buckets.push([state.shop]);
    return buckets.flat().map(normalizeShopProfile).filter((shop, index, arr) => {
      const key = shop.id || shop.shopCode || shop.ownerMobile;
      return arr.findIndex((x) => (x.id || x.shopCode || x.ownerMobile) === key) === index;
    });
  }
  function importActivationPayload(raw = '') {
    try {
      let text = String(raw || '').trim();
      if (!text) throw new Error('empty');
      if (!text.startsWith('{') && !text.startsWith('[')) {
        text = decodeURIComponent(escape(atob(text)));
      }
      const payload = JSON.parse(text);
      const shops = Array.isArray(payload) ? payload : (Array.isArray(payload.shops) ? payload.shops : (payload.shop ? [payload.shop] : []));
      if (!shops.length) throw new Error('no-shops');
      const normalized = shops.map(normalizeShopProfile);
      const existing = readShopBucket(DESKTOP_SEED_KEY);
      const merged = [...normalized, ...existing].filter((shop, index, arr) => {
        const key = shop.id || shop.shopCode || shop.ownerMobile;
        return arr.findIndex((x) => (x.id || x.shopCode || x.ownerMobile) === key) === index;
      });
      localStorage.setItem(DESKTOP_SEED_KEY, JSON.stringify({ updatedAt: new Date().toISOString(), shops: merged, activations: payload.activations || [] }));
      applyShopProfile(normalized[0], 'imported_super_admin_link');
      state.authMode = 'login';
      renderApp();
      notify('Shop link imported', `${normalized[0].shopName} loaded in Desktop. Log in with owner mobile and password.`, 'success');
      return true;
    } catch (err) {
      notify('Invalid shop link file', 'Import the SkyBarech activation JSON file or paste the copied SkyLink.', 'error');
      return false;
    }
  }
  function openActivationImport() {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'application/json,.json';
    input.onchange = () => {
      const file = input.files && input.files[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => importActivationPayload(String(reader.result || ''));
      reader.onerror = () => notify('File read error', 'Activation file could not be read.', 'error');
      reader.readAsText(file);
    };
    input.click();
  }
  function openPasteSkyLinkModal() {
    openModal('Paste SkyLink', 'Paste the copied SkyLink from Super Admin.', html`<form data-form="skylink-import"><div class="field"><label>SkyLink Code / JSON</label><textarea class="input" name="skylink" rows="7" required placeholder="Paste copied SkyLink here"></textarea></div><div class="form-actions"><button class="btn btn-secondary" type="button" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">Import Shop Link ${icon('arrowRight')}</button></div></form>`);
  }
  function findActivationShop(code = '', mobile = '', password = '') {
    const shops = getSuperAdminShops();
    return shops.find((shop) => {
      const codeOk = cleanLower(shop.activationCode) === cleanLower(code) || cleanLower(shop.shopCode) === cleanLower(code);
      const mobileOk = sameMobile(shop.ownerMobile, mobile);
      const passOk = shopPassword(shop) === cleanText(password);
      return codeOk && mobileOk && passOk && !['blocked','suspended','expired'].includes(cleanLower(shop.status));
    });
  }
  function createLocalActivationShop(data = {}) {
    const mobile = cleanText(data.mobile || data.username || DEFAULT_SHOP_PROFILE.ownerMobile);
    const code = cleanText(data.code || DEFAULT_SHOP_PROFILE.activationCode);
    return normalizeShopProfile({
      id: `SHOP-${cleanMobile(mobile).slice(-6) || Date.now().toString().slice(-6)}`,
      shopName: cleanText(data.shopName) || (state?.shop?.shopName && state.shop.shopName !== DEFAULT_SHOP_PROFILE.shopName ? state.shop.shopName : `Mobile Shop ${cleanMobile(mobile).slice(-4) || ''}`.trim()),
      ownerName: state?.shop?.ownerName || DEFAULT_SHOP_PROFILE.ownerName,
      ownerMobile: mobile,
      city: state?.shop?.city || '',
      address: state?.shop?.address || '',
      shopCode: code,
      activationCode: code,
      androidPassword: cleanText(data.password),
      logo: '',
      status: 'active',
      plan: 'Local Active'
    });
  }
  function applyShopProfile(shop, source = 'settings') {
    const normalized = normalizeShopProfile(shop);
    state.shop = normalized;
    state.session = { shop_id: normalized.id, source, ownerMobile: normalized.ownerMobile, activatedAt: new Date().toISOString() };
    
    writeDesktopSeedShop(normalized);
    persist();
    return normalized;
  }
  function startCleanRemoteShop(shop, source = 'remote_login') {
    const normalized = normalizeShopProfile(shop);
    const emptyCollections = ['products','customers','suppliers','repairs','installments','invoices','expenses','cashSessions','ewallets','supportRequests','staff','stockMovements','deletedRecords','notifications'];
    for (const key of emptyCollections) state[key] = [];
    state.posCart = [];
    state.shop = normalized;
    state.session = { shop_id: normalized.id, source, ownerMobile: normalized.ownerMobile, activatedAt: new Date().toISOString() };
    writeDesktopSeedShop(normalized);
    persist();
    return normalized;
  }
  const isAccessoryProduct = (p) => ACCESSORY_CATEGORIES.includes(p.category);
  const isSparePartProduct = (p) => SPARE_PART_CATEGORIES.includes(p.category);
  function variantOptionsForCategory(category) {
    const map = {
      'Cover': ['Silicon','Hard','Leather','Transparent','Fancy'],
      'Glass / Protector': ['9D','11D','Matte','Privacy','UV','Simple'],
      'Charger': ['10W','18W','25W','45W','Fast Charger'],
      'Cable': ['Type-C','Micro USB','Lightning / iPhone'],
      'Handsfree': ['Wired','Wireless','Bluetooth'],
      'Earbuds': ['Wired','Wireless','Bluetooth']
    };
    return map[category] || [];
  }
  function makeSku(name = '', category = '', brand = '') {
    const clean = (value, fallback) => String(value || fallback).replace(/[^a-z0-9]/gi,'').toUpperCase().slice(0,4) || fallback;
    return `${clean(category, 'SKU')}-${clean(brand || name, 'ITEM')}-${Date.now().toString().slice(-5)}`;
  }
  const defaults = {
    products: [],
    customers: [],
    suppliers: [],
    repairs: [],
    installments: [],
    invoices: [],
    supportRequests: [],
    expenses: [],
    cashSessions: [],
    ewallets: [],
    staff: [],
    permissions: [
      { key: 'can_create_sale', label: 'Create POS sale', cashier: true, manager: true, owner: true },
      { key: 'can_delete_sale', label: 'Delete old invoice', cashier: false, manager: false, owner: true },
      { key: 'can_manage_stock', label: 'Manage stock / price', cashier: false, manager: true, owner: true },
      { key: 'can_view_reports', label: 'View full reports', cashier: false, manager: true, owner: true },
      { key: 'can_refund', label: 'Refund / return sale', cashier: false, manager: true, owner: true },
      { key: 'can_manage_staff', label: 'Manage staff users', cashier: false, manager: false, owner: true }
    ],
    stockMovements: [],
    deletedRecords: [],
    notifications: [],
    shop: { ...DEFAULT_SHOP_PROFILE },
    session: { shop_id: '', source: 'unlinked' }
  };

  function loadState() {
    try {
      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}');
      return {
        ...defaults,
        ...saved,
        products: Array.isArray(saved.products) ? saved.products : [],
        customers: Array.isArray(saved.customers) ? saved.customers : [],
        suppliers: Array.isArray(saved.suppliers) ? saved.suppliers : [],
        repairs: Array.isArray(saved.repairs) ? saved.repairs : [],
        installments: Array.isArray(saved.installments) ? saved.installments : [],
        invoices: Array.isArray(saved.invoices) ? saved.invoices : [],
        supportRequests: Array.isArray(saved.supportRequests) ? saved.supportRequests : [],
        expenses: Array.isArray(saved.expenses) ? saved.expenses : [],
        cashSessions: Array.isArray(saved.cashSessions) ? saved.cashSessions : [],
        ewallets: Array.isArray(saved.ewallets) ? saved.ewallets : [],
        staff: Array.isArray(saved.staff) ? saved.staff : [],
        permissions: Array.isArray(saved.permissions) ? saved.permissions : defaults.permissions,
        stockMovements: Array.isArray(saved.stockMovements) ? saved.stockMovements : [],
        deletedRecords: Array.isArray(saved.deletedRecords) ? saved.deletedRecords : [],
        notifications: Array.isArray(saved.notifications) ? saved.notifications : [],
        shop: normalizeShopProfile(saved.shop || defaults.shop),
        session: saved.session || defaults.session,
        activePage: saved.activePage || 'dashboard',
        theme: savedTheme(saved.theme),
        isLoggedIn: false,
        authMode: 'login',
        posCart: [],
        posPayment: 'Cash',
        inventorySearch: '',
        inventorySort: 'name',
        inventoryPage: 1,
        inventoryLow: false,
        laptopSearch: '',
        accessorySearch: '',
        repairStatus: 'All',
        reportRange: 'This Month',
        settingsTab: 'profile',
        sidebarCollapsed: false,
        mobileMenuOpen: false
      };
    } catch {
      return { ...defaults, products: [], customers: [], suppliers: [], repairs: [], installments: [], invoices: [], supportRequests: [], expenses: [], cashSessions: [], ewallets: [], staff: [], stockMovements: [], deletedRecords: [], notifications: [], shop: normalizeShopProfile(defaults.shop), session: defaults.session, activePage: 'dashboard', theme: savedTheme('dark'), isLoggedIn: false, authMode: 'login', posCart: [], posPayment: 'Cash', inventorySearch: '', inventorySort: 'name', inventoryPage: 1, inventoryLow: false, laptopSearch: '', accessorySearch: '', sparePartSearch: '', repairStatus: 'All', reportRange: 'This Month', settingsTab: 'profile', sidebarCollapsed: false, mobileMenuOpen: false };
    }
  }

  let state = loadState();
  let desktopSyncStatus = {
    pending: 0, failed: 0, synced: 0, apiBaseUrl: '', accessToken: '', refreshToken: '',
    lastSyncAt: null, lastSyncError: '', licenseStatus: 'unknown', lastEntitlementAt: null, revokedReason: ''
  };
  let syncBusy = false;
  let splashVisible = true;

  function lockRevokedShop(reason = '') {
    const message = reason || 'This shop was deleted, blocked, or expired in Super Admin.';
    desktopSyncStatus = { ...desktopSyncStatus, licenseStatus: 'revoked', revokedReason: message, lastSyncError: message };
    state.isLoggedIn = false;
    state.authMode = 'login';
    state.session = { ...(state.session || {}), revoked: true, revokedReason: message, revokedAt: new Date().toISOString() };
    splashVisible = false;
    renderApp();
    notify('Shop access stopped', `${t(message)} Internet milne ke baad revoked shop offline login nahi kar sakti.`, 'error');
  }

  window.skybarechDesktop?.onLicenseRevoked?.((payload) => lockRevokedShop(payload?.reason));
  window.skybarechDesktop?.onSyncCompleted?.(() => { if (state.isLoggedIn) hydrateFromDesktopStore(); });

  function persist(change = null) {
    const save = {
      products: state.products,
      customers: state.customers,
      suppliers: state.suppliers,
      repairs: state.repairs,
      installments: state.installments,
      invoices: state.invoices,
      supportRequests: state.supportRequests,
      expenses: state.expenses,
      cashSessions: state.cashSessions,
      ewallets: state.ewallets,
      staff: state.staff,
      permissions: state.permissions,
      stockMovements: state.stockMovements,
      deletedRecords: state.deletedRecords,
      notifications: state.notifications,
      shop: normalizeShopProfile(state.shop),
      session: state.session,
      activePage: state.activePage,
      theme: state.theme
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(save));
    const syncChange = change && typeof change === 'object' ? change : {
      entityType: 'device_snapshot',
      entityId: `${save.shop?.id || 'local-shop'}:desktop`,
      action: 'upsert',
      payload: save
    };
    window.skybarechDesktop?.saveSnapshot(save, syncChange)
      .then(() => refreshDesktopSyncStatus())
      .catch((error) => console.error('SQLite save failed:', error));
  }

  async function refreshDesktopSyncStatus() {
    try {
      desktopSyncStatus = await window.skybarechDesktop?.syncStatus() || desktopSyncStatus;
      if (desktopSyncStatus.licenseStatus === 'revoked') lockRevokedShop(desktopSyncStatus.revokedReason);
    } catch (error) {
      desktopSyncStatus = { ...desktopSyncStatus, lastSyncError: error.message || 'Status unavailable' };
    }
  }

  async function hydrateFromDesktopStore(preserveRuntime = state.isLoggedIn) {
    const draftInputs = Array.from(document.querySelectorAll('.content input, .content textarea, .content select')).map(node => ({
      value: node.value, checked: node.checked, focused: node === document.activeElement,
      start: node.selectionStart, end: node.selectionEnd
    }));
    const runtime = preserveRuntime ? {
      isLoggedIn: state.isLoggedIn,
      authMode: state.authMode,
      posCart: state.posCart,
      posPayment: state.posPayment,
      activePage: state.activePage,
      inventorySearch: state.inventorySearch,
      inventorySort: state.inventorySort,
      inventoryPage: state.inventoryPage,
      inventoryLow: state.inventoryLow,
      laptopSearch: state.laptopSearch,
      accessorySearch: state.accessorySearch,
      sparePartSearch: state.sparePartSearch,
      repairStatus: state.repairStatus,
      reportRange: state.reportRange,
      settingsTab: state.settingsTab,
      sidebarCollapsed: state.sidebarCollapsed,
      mobileMenuOpen: state.mobileMenuOpen,
      theme: state.theme
    } : null;

    try {
      const saved = await window.skybarechDesktop?.loadSnapshot();
      if (saved?.snapshot) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(saved.snapshot));
        state = loadState();
        if (runtime) state = { ...state, ...runtime };
      } else {
        persist();
      }
      await refreshDesktopSyncStatus();
      renderApp();
      if (preserveRuntime) Array.from(document.querySelectorAll('.content input, .content textarea, .content select')).forEach((node, index) => {
        const draft = draftInputs[index];
        if (!draft || node.readOnly || node.type === 'file') return;
        node.value = draft.value;
        if (node.type === 'checkbox' || node.type === 'radio') node.checked = draft.checked;
        if (draft.focused) { node.focus(); if (draft.start != null && node.setSelectionRange) node.setSelectionRange(draft.start, draft.end); }
      });
    } catch (error) {
      console.error('SQLite hydration failed:', error);
    }
  }

  function metricIcon(type) {
    return html`<div class="metric-icon">${icon(type)}</div>`;
  }

  function badge(status = '') {
    const low = status.toLowerCase();
    let cls = 'info';
    if (['active', 'paid', 'ready', 'done', 'delivered', 'credit', 'resolved'].some(k => low.includes(k))) cls = 'success';
    if (['pending', 'due', 'progress', 'open'].some(k => low.includes(k))) cls = 'warning';
    if (['low', 'payable', 'overdue', 'high'].some(k => low.includes(k))) cls = 'danger';
    return html`<span class="badge ${cls}">${esc(t(status))}</span>`;
  }

  let brandSequence = 0;
  function brandMarkup(hero = false) {
    return html`<div class="brand-mark sky-logo ${hero ? 'hero' : ''}" aria-label="SkyBarech ERP logo"><img src="skybarech-mark.svg" alt="" width="72" height="72"></div>`;
  }

  function renderSplash() {
    document.documentElement.dataset.theme = 'light';
    app.innerHTML = html`<main class="splash">
      <div class="splash-content">
        ${brandMarkup(true)}
        <h1>SkyBarech <span>Desktop ERP</span></h1>
        <p>Powering Your Shop. Growing Your Business.</p>
        <div class="splash-ring" aria-label="Loading"></div>
      </div>
    </main>`;
  }

  function renderAuth() {
    document.documentElement.dataset.theme = 'light';
    const mode = state.authMode;
    const authContent = mode === 'activate' ? activationView() : mode === 'password' ? passwordView() : loginView();
    app.innerHTML = html`<main class="auth-layout">
      <section class="auth-side">
        <div class="auth-visual-grid" aria-hidden="true"></div>
        <div class="auth-orb auth-orb-one" aria-hidden="true"></div>
        <div class="auth-orb auth-orb-two" aria-hidden="true"></div>
        <div class="auth-side-content">
          <div class="auth-brand">
            ${brandMarkup()}
            <div class="brand-copy"><div class="brand-name">SkyBarech</div><div class="brand-sub">Desktop ERP · Mobile Shop</div></div>
          </div>
          <div class="auth-side-hero">
            <div class="auth-eyebrow"><span></span> BUILT FOR MODERN RETAIL</div>
            <h1>One secure login.<br><span style="color:#bbd2ff">Your whole shop.</span></h1>
            <p>Sales, inventory, repairs and accounts stay connected across desktop and Android.</p>
            <div class="auth-device-scene" aria-hidden="true">
              <div class="auth-cloud">${icon('cloud')}<span>${icon('lock')}</span></div>
              <div class="auth-shop"><div class="auth-shop-awning"></div><strong>YOUR SHOP</strong><div class="auth-shop-window"></div></div>
              <div class="auth-laptop">${icon('laptop')}</div><div class="auth-phone">${icon('phone')}</div>
              <div class="auth-device-caption">One shop. Every device.</div>
            </div>
          </div>
          <div class="auth-benefits">
            <div class="auth-benefit"><span class="icon-shell">${icon('shield')}</span><span><strong>Shop isolated</strong><br>Every account stays separate</span></div>
            <div class="auth-benefit"><span class="icon-shell">${icon('cloud')}</span><span><strong>Always connected</strong><br>Desktop and Android sync</span></div>
            <div class="auth-benefit"><span class="icon-shell">${icon('chart')}</span><span><strong>Business ready</strong><br>Clear daily performance</span></div>
          </div>
          <div class="auth-side-foot">Cloud Ready • Secure Login</div>
        </div>
      </section>
      <section class="auth-panel"><button class="language-switch auth-language" data-action="toggle-language" type="button" aria-label="Language">English / اردو</button>${authContent}</section>
    </main>`;
  }

  function pinCodeField(name, label, length = 4, autocomplete = 'current-password') {
    return html`<div class="field pin-code-field"><label for="pin-${name}">${t(label)}</label><div class="pin-code-shell" data-pin-shell data-length="${length}"><input id="pin-${name}" class="pin-code-input" type="password" name="${name}" inputmode="numeric" autocomplete="${autocomplete}" maxlength="${length}" pattern="[0-9]{${length}}" aria-label="${t(label)}" enterkeyhint="done" required><div class="pin-code-boxes" aria-hidden="true">${Array.from({length:4},(_,index)=>html`<span class="pin-code-box ${index >= length ? 'pin-box-hidden' : ''}" data-pin-index="${index}"></span>`).join('')}</div></div><small class="pin-code-hint">${t(`Enter exactly ${length} digits`)}</small></div>`;
  }

  function refreshPinCodeInput(input) {
    if (!input?.matches?.('.pin-code-input')) return;
    const shell = input.closest('[data-pin-shell]');
    const length = Number(shell?.dataset.length || input.maxLength || 6);
    input.value = input.value.replace(/\D/g, '').slice(0, length);
    shell?.querySelectorAll('[data-pin-index]').forEach((box, index) => {
      box.classList.toggle('pin-box-hidden', index >= length);
      box.classList.toggle('filled', index < input.value.length);
      box.classList.toggle('active', document.activeElement === input && index === Math.min(input.value.length, length - 1));
      box.textContent = index < input.value.length ? '•' : '';
    });
    shell?.classList.toggle('complete', input.value.length === length);
  }

  function loginView() {
    
    return html`<form class="auth-card" data-form="login" novalidate>
      <div class="auth-card-brand">${brandMarkup()}<div><strong>SkyBarech ERP</strong><span>Connected retail workspace</span></div><em><i></i> SECURE LOGIN</em></div>
      <div class="auth-card-head">
        <div class="micro">${icon('shield')} VERIFIED SHOP ACCESS</div>
        <h2>Welcome back<span>.</span></h2>
        <p>${esc(t('Enter your owner account and shop PIN.'))}</p>
      </div>
      <div class="auth-fields">
        <div class="field"><label for="login-username">Owner Mobile / Username</label><div class="input-wrap auth-input">${icon('user')}<input id="login-username" class="input" name="username" autocomplete="username" value="${esc(state.loginDraftUsername || '')}" placeholder="03XX XXXXXXX"></div></div>
        ${pinCodeField('password','4-digit Shop PIN',4)}
        <div class="auth-actions"><span class="pin-only-note">4-digit PIN access</span><button class="text-link" type="button" data-action="support-auth">Forgot PIN?</button></div>
        <button class="btn btn-primary full auth-login-button" type="button" data-action="desktop-login"><span>Login to Dashboard</span>${icon('arrowRight')}</button>
        <div class="auth-security-line">${icon('shield')} Encrypted session · One shop per local workspace</div>
      </div>
      <div class="auth-bottom"><button type="button" class="btn btn-secondary" style="flex:1" data-action="open-auth" data-mode="activate">${icon('shield')} Activate Shop</button><button type="button" class="btn btn-secondary" style="flex:1" data-action="support-auth">${icon('help')} Support</button></div>
      
    </form>`;
  }

  function activationView() {
    return html`<form class="auth-card" data-form="activate" novalidate>
      <div class="auth-card-head">
        <div class="micro">${icon('shield')} FIRST TIME SETUP</div>
        <h2>Activate your shop</h2>
        <p>Enter your account details to activate this desktop device.</p>
      </div>
      <div class="auth-fields">
        <div class="field"><label>Shop Name <span style="color:var(--muted);font-weight:500">(optional)</span></label><div class="input-wrap">${icon('phone')}<input class="input" name="shopName" placeholder="e.g. Ali Mobile Accessories"></div></div>
        <div class="field"><label>Activation Code <span class="required">*</span></label><div class="input-wrap">${icon('shield')}<input class="input" name="code" placeholder="e.g. SB-2026-ACTIVE" required></div></div>
        <div class="field"><label>Owner Mobile <span class="required">*</span></label><div class="input-wrap">${icon('user')}<input class="input" name="mobile" placeholder="03XX-XXXXXXX" required></div></div>
        <div class="field"><label>Temporary PIN <span class="required">*</span></label><div class="input-wrap">${icon('lock')}<input class="input" type="password" name="password" placeholder="Enter temporary PIN" inputmode="numeric" minlength="4" maxlength="4" pattern="[0-9]{4}" required><button type="button" class="right-action" data-action="toggle-password">${icon('eye')}</button></div></div>
        <div class="field"><label>Shop Logo <span style="color:var(--muted);font-weight:500">(optional)</span></label><button class="upload-slot" type="button" data-action="upload-local">${icon('upload')} Upload logo PNG / JPG (Max 2MB)</button></div>
        <button class="btn btn-primary full" type="button" data-action="activate-shop-now">Activate Account ${icon('arrowRight')}</button><div class="activation-error-box" data-activation-error hidden></div>
        <div class="auth-link-note"><strong>Direct activation code</strong><span>JSON import is optional. Enter the shop name to create a local profile.</span></div>
        <div class="auth-bottom"><button type="button" class="btn btn-secondary" style="flex:1" data-action="import-activation-file">${icon('upload')} Import Link File</button><button type="button" class="btn btn-secondary" style="flex:1" data-action="paste-skylink">Paste SkyLink</button></div>
      </div>
      <div class="auth-bottom"><button type="button" class="btn btn-secondary" style="flex:1" data-action="open-auth" data-mode="login">${icon('arrowLeft')} Back to Login</button><button type="button" class="btn btn-secondary" style="flex:1" data-action="support-auth">${icon('help')} Support</button></div>
    </form>`;
  }

  function passwordView() {
    const firstTime = !!state.firstActivationPassword;
    return html`<form class="auth-card" data-form="password" novalidate>
      <div class="auth-card-head">
        <div class="micro">${icon('lock')} ${firstTime ? 'FIRST ACTIVATION' : 'ACCOUNT SECURITY'}</div>
        <h2>${firstTime ? 'Create PIN' : 'Change PIN'}</h2>
        <p>${firstTime ? 'Choose a PIN for your shop.' : 'Use the same PIN on desktop and Android.'}</p>
      </div>
      <div class="auth-fields">
        ${!firstTime ? htmlText('<div class="field"><label>Current PIN</label><input class="input" type="password" name="currentPassword" autocomplete="current-password" required></div>') : ''}
        <input type="hidden" name="pinLength" value="4">${pinCodeField('newPassword','New PIN',4,'new-password')}
        ${pinCodeField('confirmPassword','Confirm PIN',4,'new-password')}
        <div class="security-tip">${icon('shield')}<span>Use the same PIN on desktop and Android.</span></div>
        <button class="btn btn-primary full" type="submit">Save PIN & Open Dashboard ${icon('arrowRight')}</button>
      </div>
      ${state.isLoggedIn ? '' : html`<div class="auth-bottom"><button type="button" class="btn btn-secondary full" data-action="open-auth" data-mode="login">${icon('arrowLeft')} Back to Login</button></div>`}
    </form>`;
  }

  const navGroups = [
    { label: 'Workspace', items: [
      ['dashboard', 'Dashboard', 'dashboard'], ['pos', 'POS Billing', 'pos'], ['inventory', 'Inventory', 'box'], ['accessories', 'Mobile Accessories', 'phone'], ['spareParts', 'Spare Parts', 'wrench'], ['laptops', 'Laptop', 'laptop']
    ] },
    { label: 'Operations', items: [
      ['purchase', 'Mobile Purchase', 'purchase'], ['sale', 'Mobile Sale', 'sale'], ['repairs', 'Repair Jobs', 'wrench'], ['customers', 'Customers', 'users'], ['suppliers', 'Suppliers', 'supplier']
    ] },
    { label: 'Management', items: [
      ['installments', 'Installments', 'calendar'], ['expenses', 'Expenses', 'money'], ['ewallets', 'Easypaisa/Jazz', 'wallet'], ['cashClosing', 'Cash Closing', 'wallet'], ['ledgers', 'Ledgers', 'receipt'], ['staff', 'Staff & Permissions', 'users'], ['audit', 'Audit / Deleted', 'shield'], ['reports', 'Reports', 'chart'], ['settings', 'Settings', 'settings'], ['support', 'Help Center', 'help']
    ] }
  ];

  const pageMeta = {
    dashboard: ['Dashboard', 'Overview of today’s shop performance'],
    pos: ['POS Billing', 'Create a fast walk-in sale and collect payment'],
    inventory: ['Inventory', 'Manage products, stock, prices and barcodes'],
    accessories: ['Mobile Accessories', 'Separate accessory stock, prices, barcodes and quick actions'],
    spareParts: ['Mobile Spare Parts', 'Repair parts stock, rack, supplier cost, sale price and low stock alerts'],
    laptops: ['Laptop', 'Separate laptop stock, specs, prices and quick actions'],
    purchase: ['Mobile Purchase', 'Record stock purchases with complete device details'],
    sale: ['Mobile Sale', 'Create an invoice for direct sales or installments'],
    repairs: ['Repair Jobs', 'Track repair workflow, technician and delivery status'],
    customers: ['Customers', 'Manage customer credit, purchase history and ledger'],
    suppliers: ['Suppliers', 'Manage supplier payables and purchase records'],
    installments: ['Installments', 'Track due amounts and receive monthly payments'],
    expenses: ['Expenses', 'Record rent, utilities, salary and daily shop spending'],
    ewallets: ['Easypaisa/Jazz', 'Simple Easypaisa and JazzCash in-out entries'],
    cashClosing: ['Cash Closing', 'Open and close daily cash sessions with expected balance'],
    ledgers: ['Ledgers', 'Customer receivable, supplier payable and payment history'],
    staff: ['Staff & Permissions', 'Cashier, manager and owner access control'],
    audit: ['Audit / Deleted Records', 'Soft-delete history, activity log and restore safety'],
    reports: ['Reports', 'Review sales, profit, stock, expenses and business trends'],
    settings: ['Settings', 'Manage your shop, backup, users and subscription'],
    support: ['Help Center', 'Create and review support requests']
  };

  function sidebarMarkup() {
    return html`<aside class="sidebar">
      <div class="sidebar-brand">
        ${shopLogoMarkup('brand-logo')}
        <div class="brand-copy"><div class="brand-name">${esc(shopDisplayName())}</div><div class="brand-sub">${esc(shopProfile().city || t('Mobile Shop ERP'))} · ${esc(t(shopProfile().status || 'Active'))}</div></div>
      </div>
      <nav class="sidebar-nav" aria-label="Main navigation">
        ${navGroups.map(group => html`<div class="nav-label">${t(group.label)}</div>${group.items.map(([key, label, i, count]) => html`<button class="nav-item ${state.activePage === key ? 'active' : ''}" data-action="navigate" data-page="${key}" title="${t(label)}">${icon(i)}<span>${t(label)}</span>${['repairs','installments'].includes(key) && state[key]?.length ? html`<b class="nav-badge">${state[key].length}</b>` : ''}</button>`).join('')}`).join('')}
      </nav>
      <div class="sidebar-bottom"><button class="sidebar-user" data-action="open-user-menu">${shopLogoMarkup()}<span class="user-text"><strong>${esc(shopOwnerName())}</strong><span>Shop Owner</span></span>${icon('more')}</button></div>
    </aside>`;
  }

  function connectionButton() {
    const problem = desktopSyncStatus.lastSyncError || desktopSyncStatus.failed;
    const pending = Number(desktopSyncStatus.pending || 0) + Number(desktopSyncStatus.syncing || 0);
    const label = syncBusy ? 'Syncing…' : problem ? 'Check connection' : pending ? `${pending} waiting` : desktopSyncStatus.lastSyncAt ? 'Up to date' : 'Connect shop';
    return html`<button class="connection-button ${problem ? 'has-error' : ''}" data-action="sync-details" title="Open shop connection details" aria-label="${esc(t(label))}. Open connection details"><span class="connection-dot"></span>${icon('cloud')}<span>${esc(t(label))}</span>${icon('chevronDown')}</button>`;
  }

  function topbarMarkup() {
    const [title, subtitle] = pageMeta[state.activePage] || pageMeta.dashboard;
    return html`<header class="topbar">
      <div class="topbar-left">
        <button class="btn btn-ghost btn-icon" data-action="toggle-sidebar" aria-label="Toggle sidebar" aria-expanded="${window.innerWidth <= 760 ? state.mobileMenuOpen : !state.sidebarCollapsed}">${icon('menu')}</button>
        <div class="workspace-heading"><h1 title="${esc(shopDisplayName())}">${esc(shopDisplayName())}</h1><div class="breadcrumb"><span>${t('Workspace')}</span>${icon('chevronRight')}<span>${t(title)}</span></div></div>
      </div>
      <div class="topbar-right">
        <button class="language-switch" data-action="toggle-language" type="button" aria-label="Language">${window.SkyI18n?.language === "ur" ? "English" : "اردو"}</button>
        ${connectionButton()}
        <div class="input-wrap topbar-search">${icon('search')}<input class="input" data-global-search placeholder="Search products, customers…"></div>
        <button class="btn btn-secondary btn-icon theme-trigger" data-action="open-themes" title="${t('Workspace themes')}" aria-label="${t('Workspace themes')}">${icon('sun')}</button>
        <button class="btn btn-ghost btn-icon ${state.products.some(p => p.stock <= (p.minStock || 0)) ? 'notification-dot' : ''}" data-action="open-notifications" aria-label="Notifications">${icon('bell')}</button>
        <button class="sidebar-user" style="padding:0 0 0 2px;width:auto" data-action="open-user-menu">${shopLogoMarkup()}</button>
      </div>
    </header>`;
  }

  function renderShell() {
    document.documentElement.dataset.theme = state.theme;
    app.innerHTML = html`<main class="app-shell ${state.sidebarCollapsed ? 'sidebar-collapsed' : ''} ${state.mobileMenuOpen ? 'mobile-menu-open' : ''}">
      ${sidebarMarkup()}
      <section class="main-panel">${topbarMarkup()}<div class="content">${renderPage()}</div></section>
    </main>`;
  }

  function renderPageHead(actionHtml = '') {
    const [title, subtitle] = pageMeta[state.activePage] || pageMeta.dashboard;
    return html`<div class="page-head"><div><h2>${t(title)}</h2><p>${t(subtitle)}</p></div><div class="page-head-actions">${actionHtml}</div></div>`;
  }

  function renderPage() {
    switch (state.activePage) {
      case 'pos': return renderPos();
      case 'inventory': return renderInventory();
      case 'accessories': return renderAccessories();
      case 'spareParts': return renderSpareParts();
      case 'laptops': return renderLaptops();
      case 'purchase': return renderPurchase();
      case 'sale': return renderSale();
      case 'repairs': return renderRepairs();
      case 'customers': return renderCustomers();
      case 'suppliers': return renderSuppliers();
      case 'installments': return renderInstallments();
      case 'expenses': return renderExpenses();
      case 'ewallets': return renderEwallets();
      case 'cashClosing': return renderCashClosing();
      case 'ledgers': return renderLedgers();
      case 'staff': return renderStaffPermissions();
      case 'audit': return renderAudit();
      case 'reports': return renderReports();
      case 'settings': return renderSettings();
      case 'support': return renderSupport();
      default: return renderDashboard();
    }
  }

  function renderDashboard() {
    const sales = state.invoices.reduce((sum, row) => sum + row.total, 0);
    const stock = state.products.reduce((sum, row) => sum + row.stock, 0);
    const due = state.installments.reduce((sum, row) => sum + row.due, 0);
    const lowStock = state.products.filter((p) => p.stock <= (p.minStock || 0)).length;
    const repairOpen = state.repairs.filter((r) => !['Delivered'].includes(r.status)).length;
    const profit = state.products.reduce((sum, row) => sum + Math.max(0, row.price - row.cost) * Math.max(0, row.stock), 0);
    return html`${renderPageHead(html`<button class="btn btn-secondary" data-action="download-report">${icon('download')} Export Summary</button><button class="btn btn-primary" data-action="navigate" data-page="pos">${icon('plus')} New Sale</button>`)}
      <section class="client-hero">
        <div class="client-hero-copy">
          <h2>Your shop, at a glance.</h2>
          <p>Sales, stock and accounts.</p>
          <div class="client-hero-actions"><button class="btn btn-primary" data-action="navigate" data-page="pos">${icon('pos')} Start Billing</button><button class="btn btn-secondary" data-action="navigate" data-page="inventory">${icon('box')} Manage Stock</button></div>
        </div>
        <div class="client-live-card">
          <div class="live-ring"></div>
          <strong>${money(sales)}</strong>
          <span>Total Sales Record</span>
          <small>${n(stock)} stock units · ${repairOpen} repairs open</small>
        </div>
      </section>
      <div class="dashboard-grid client-dashboard">
        <div class="metric-grid premium-metrics">
          ${metricCard('Today Sales', money(state.invoices.filter(i=>recordDateKey(i)===today()).reduce((t,i)=>t+Number(i.total||0),0)), 'wallet', 'Android and desktop sales today', 'up')}
          ${metricCard('Recorded Invoices', n(state.invoices.length), 'chart', 'On this device', 'up')}
          ${metricCard('Total Stock', n(stock), 'box', `${lowStock} items low in stock`, lowStock ? 'down' : 'up')}
          ${metricCard('Low Stock', n(lowStock), 'box', 'Reorder alerts', 'down')}
          ${metricCard('Customer Balance', money(state.customers.reduce((sum,c)=>sum+Number(c.balance||0),0)), 'users', 'Receivables', 'up')}
          ${metricCard('Supplier Balance', money(state.suppliers.reduce((sum,c)=>sum+Number(c.payable||0),0)), 'supplier', 'Payables', 'down')}
          ${metricCard('Pending Repairs', n(repairOpen), 'wrench', 'Open repair jobs', 'up')}
          ${metricCard('Recorded Expenses', money((state.expenses||[]).reduce((t,e)=>t+Number(e.amount||0),0)), 'money', 'On this device', 'up')}
        </div>
        <div class="command-strip">
          ${quick('POS Billing', 'pos', 'pos')}${quick('Add Product', 'inventory', 'box', 'add-product-modal')}${quick('Spare Parts', 'spareParts', 'wrench')}${quick('Laptop', 'laptops', 'laptop')}${quick('Repair Job', 'repairs', 'wrench', 'add-repair-modal')}${quick('Customer', 'customers', 'users', 'add-customer-modal')}${quick('Installment', 'installments', 'calendar')}${quick('Reports', 'reports', 'chart')}
        </div>
        <div class="dashboard-mid">
          <section class="card card-pad chart-card premium-chart">
            <div class="chart-header"><div><h3>Sales Intelligence</h3><p>Recorded sales over the last seven days</p></div><span class="badge info">Last 7 days</span></div>
            ${lineChart()}
          </section>
          <section class="card card-pad client-insight"><div class="section-title"><div><h3>Business Health</h3><p>Client-friendly overview</p></div></div>
            <div class="insight-list">
              <div><b>${money(profit)}</b><span>Projected stock margin</span></div>
              <div><b>${money(due)}</b><span>Installment due amount</span></div>
              <div><b>${lowStock}</b><span>Low stock alerts</span></div>
              <div><b>${repairOpen}</b><span>Repair jobs open</span></div>
            </div>
          </section>
        </div>
        <div class="dashboard-bottom">
          <section class="card table-card premium-table"><div class="section-title card-pad" style="padding-bottom:10px"><div><h3>Recent Sales</h3><p>Latest completed invoices</p></div><button class="text-link" data-action="view-all-sales">View All</button></div>${recentSalesTable()}</section>
          <section class="card card-pad"><div class="section-title"><div><h3>Progress Snapshot</h3><p>Current shop position</p></div></div>
            <div class="progress-list">
              ${progressRow('Products above minimum stock', `${state.products.length - lowStock} / ${state.products.length}`, state.products.length ? 100 * (state.products.length - lowStock) / state.products.length : 0)}
              ${progressRow('Completed repairs', `${state.repairs.length - repairOpen} / ${state.repairs.length}`, state.repairs.length ? 100 * (state.repairs.length - repairOpen) / state.repairs.length : 0)}
            </div>
            <div class="premium-sub-card"><div>${icon('shield','icon-lg')}<div><strong>Cloud sync</strong><span>${t(desktopSyncStatus.lastSyncError ? "Needs attention" : desktopSyncStatus.lastSyncAt ? "Connected" : "Waiting for connection")}</span></div></div></div>
          </section>
        </div>
      </div>`;
  }

  function metricCard(label, value, iconName, note, direction) {
    return html`<section class="card metric-card"><div class="metric-top"><span class="metric-label">${t(label)}</span>${metricIcon(iconName)}</div><div class="metric-value">${t(value)}</div><div class="metric-note ${direction === 'down' ? 'down' : ''}">${icon(direction === 'down' ? 'info' : 'chart')}<span>${t(note)}</span></div></section>`;
  }

  function quick(label, page, iconName, action = '') {
    return html`<button class="quick-action" data-action="${action || 'navigate'}" ${action ? '' : `data-page="${page}"`}><span class="quick-icon">${icon(iconName)}</span><span>${t(label)}</span></button>`;
  }

  function lineChart() {
    const points = Array.from({length:7}, (_, i) => { const d = new Date(); d.setDate(d.getDate() - 6 + i); const key = localDateKey(d); return {label:d.toLocaleDateString('en-GB',{weekday:'short'}),total:state.invoices.filter(row=>recordDateKey(row)===key).reduce((t,row)=>t+Number(row.total||0),0)}; });
    const max = Math.max(1,...points.map(p=>p.total));
    const coordinates = points.map((p,i)=>`${30+i*120},${220-p.total/max*180}`).join(' ');
    return html`<svg class="line-chart" viewBox="0 0 800 260" role="img" aria-label="Recorded sales in last seven days: ${esc(points.map(p=>`${t(p.label)} ${money(p.total)}`).join(', '))}"><path d="M30 40H750M30 130H750M30 220H750" stroke="var(--line)" fill="none"/><polyline points="${coordinates}" stroke="var(--blue)" stroke-width="4" fill="none"/>${points.map((p,i)=>html`<text x="${30+i*120}" y="252" text-anchor="middle" fill="var(--muted)" font-size="14">${t(p.label)}</text>`).join('')}</svg>`;
  }

  function recentSalesTable() {
    return html`<div class="table-wrap"><table><thead><tr><th>Invoice</th><th>Customer</th><th>Payment</th><th>Amount</th><th>Date</th><th></th></tr></thead><tbody>${state.invoices.slice(0, 5).map(inv => html`<tr><td><strong>${esc(inv.id)}</strong></td><td>${esc(inv.customer)}</td><td>${badge(inv.payment)}</td><td class="amount">${money(inv.total)}</td><td>${prettyDate(inv.date)}</td><td><button class="icon-action" data-action="invoice-preview" data-id="${inv.id}" title="View invoice">${icon('eye')}</button></td></tr>`).join('')}</tbody></table></div>`;
  }

  function progressRow(label, value, progress) {
    return html`<div class="progress-row"><div class="progress-head"><b>${t(label)}</b><span>${t(value)}</span></div><div class="progress"><i style="width:${progress}%"></i></div></div>`;
  }

  function renderPos() {
    const cart = state.posCart;
    const subtotal = cart.reduce((sum, item) => sum + (item.price * item.qty), 0);
    const discount = 0;
    const total = Math.max(0, subtotal - discount);
    const filtered = state.products.filter(p => p.stock > 0).slice(0, 8);
    return html`${renderPageHead(html`<button class="btn btn-secondary" data-action="barcode-scan">${icon('scan')} Barcode Scan</button><button class="btn btn-primary" data-action="pos-pay" ${cart.length ? '' : 'disabled'}>${icon('wallet')} Collect Payment</button>`)}
      <div class="pos-layout">
        <section class="card card-pad pos-products"><div class="section-title"><div><h3>Product Search</h3><p>Search by name, brand, model or SKU</p></div></div><div class="input-wrap" style="margin-bottom:12px">${icon('search')}<input class="input" data-pos-search placeholder="Search available products"></div><div class="pill-tabs" style="margin-bottom:12px"><button class="pill-tab active" data-action="barcode-scan">${icon('scan')} Barcode Scan</button><button class="pill-tab" data-action="imei-search">${icon('search')} IMEI Search</button></div><div class="pos-product-list" id="pos-product-list">${filtered.map(posProduct).join('')}</div><button class="btn btn-secondary full" style="margin-top:14px" data-action="quick-add-product">${icon('plus')} Quick Add Product</button></section>
        <section class="card card-pad"><div class="section-title"><div><h3>Cart & Payment</h3><p>${cart.length ? `${cart.length} product${cart.length > 1 ? 's' : ''} in current cart` : t('Add a product to begin a sale')}</p></div><button class="btn btn-ghost btn-icon" data-action="pos-clear" title="Clear cart">${icon('trash')}</button></div>
          <div class="cart-items">${cart.length ? cart.map(cartRow).join('') : html`<div class="empty">${icon('pos','icon-xl')}<strong>Cart is empty</strong><span>Choose products from the search panel.</span></div>`}</div>
          <div class="cart-summary"><div class="summary-row"><span>Subtotal</span><strong>${money(subtotal)}</strong></div><div class="summary-row"><span>Discount</span><strong style="color:var(--success)">- ${money(discount)}</strong></div><div class="summary-row"><span>Tax (optional)</span><strong>${money(0)}</strong></div><div class="summary-row total"><span>Total Payable</span><strong>${money(total)}</strong></div></div>
        </section>
        <section class="card card-pad"><div class="section-title"><div><h3>Payment Details</h3><p>Select payment method</p></div></div><div class="payment-methods">${['Cash','EasyPaisa In','EasyPaisa Out','JazzCash In','JazzCash Out','Bank Transfer','Credit Card','Installment'].map(method => html`<button class="method-btn ${state.posPayment === method ? 'active' : ''}" data-action="set-pos-payment" data-method="${method}">${icon(method === 'Cash' ? 'money' : method === 'Installment' ? 'calendar' : method === 'EasyPaisa' ? 'wallet' : 'receipt')}<span>${t(method)}</span></button>`).join('')}</div><div class="field" style="margin-top:16px"><label>Customer <span style="color:var(--muted);font-weight:500">(optional)</span></label><select class="select" id="pos-customer"><option>Walk-in Customer</option>${state.customers.map(c => html`<option>${esc(c.name)}</option>`).join('')}</select></div><div class="field" style="margin-top:12px"><label>Paid Amount</label><input class="input" id="paid-amount" type="number" value="${total}" min="0"></div><button class="btn btn-primary full" style="margin-top:16px" data-action="pos-pay" ${cart.length ? '' : 'disabled'}>Collect Payment ${icon('arrowRight')}</button></section>
      </div>`;
  }

  function posProduct(p) {
    return html`<div class="pos-product">${productThumb(p)}<div class="pos-product-info"><strong>${esc(p.name)}</strong><span>${esc(p.ram)}/${esc(p.storage)} · Stock: ${p.stock}</span><div class="pos-product-price">${money(p.price)}</div></div><button class="btn btn-primary btn-small" data-action="add-cart" data-id="${p.id}">${icon('plus')} Add</button></div>`;
  }

  function cartRow(item) {
    return html`<div class="cart-row"><div><strong>${esc(item.name)}</strong><span>${money(item.price)} each</span></div><div class="qty-control"><button data-action="cart-qty" data-id="${item.id}" data-delta="-1">−</button><span>${item.qty}</span><button data-action="cart-qty" data-id="${item.id}" data-delta="1">+</button></div><strong class="amount">${money(item.price * item.qty)}</strong><button class="icon-action" data-action="cart-remove" data-id="${item.id}" title="Remove">${icon('trash')}</button></div>`;
  }

  function renderInventory() {
    const all = filterProducts();
    const perPage = 6;
    const pages = Math.max(1, Math.ceil(all.length / perPage));
    const page = Math.min(state.inventoryPage, pages);
    const rows = all.slice((page - 1) * perPage, page * perPage);
    return html`${renderPageHead(html`<button class="btn btn-secondary" data-action="barcode-scan">${icon('scan')} Scan Barcode</button><button class="btn btn-secondary" data-action="navigate" data-page="accessories">${icon('phone')} Accessories</button><button class="btn btn-secondary" data-action="navigate" data-page="spareParts">${icon('wrench')} Spare Parts</button><button class="btn btn-primary" data-action="add-product-modal">${icon('plus')} Add Product</button>`)}
      <section class="card table-card">
        <div class="table-tools"><div class="input-wrap">${icon('search')}<input class="input" data-inventory-search value="${esc(state.inventorySearch)}" placeholder="Search product, brand, model or SKU"></div><div style="display:flex;gap:8px;flex-wrap:wrap"><select class="select" data-inventory-category style="min-height:38px;width:145px"><option value="All" ${(state.inventoryCategory || 'All') === 'All' ? 'selected' : ''}>All Categories</option><option value="Smartphone" ${state.inventoryCategory === 'Smartphone' ? 'selected' : ''}>Smartphones</option><option value="Tablet" ${state.inventoryCategory === 'Tablet' ? 'selected' : ''}>Tablets</option></select><button class="btn btn-secondary btn-small ${state.inventoryLow ? 'active-filter' : ''}" data-action="toggle-low-stock">${icon('filter')} Low Stock</button></div></div>
        <div class="table-wrap"><table><thead><tr><th><button class="sort-btn" data-action="sort-inventory" data-key="name">Product ${icon('chevronDown')}</button></th><th>Category</th><th>Brand / Model</th><th><button class="sort-btn" data-action="sort-inventory" data-key="stock">Stock ${icon('chevronDown')}</button></th><th><button class="sort-btn" data-action="sort-inventory" data-key="price">Sale Price ${icon('chevronDown')}</button></th><th>Barcode / SKU</th><th>Action</th></tr></thead><tbody>${rows.length ? rows.map(productTableRow).join('') : noRows(7, 'No products match your filters.')}</tbody></table></div>
        <div class="table-foot"><span>Showing ${rows.length ? ((page - 1) * perPage) + 1 : 0}–${Math.min(page * perPage, all.length)} of ${all.length} products</span><div class="pagination">${Array.from({length: pages}, (_, i) => html`<button class="page-btn ${page === i + 1 ? 'active' : ''}" data-action="inventory-page" data-page="${i + 1}">${i + 1}</button>`).join('')}</div></div>
      </section>`;
  }

  function filterProducts() {
    const search = state.inventorySearch.trim().toLowerCase();
    const category = state.inventoryCategory || 'All';
    return [...state.products]
      .filter(p => !isAccessoryProduct(p) && !isSparePartProduct(p) && p.category !== 'Laptop')
      .filter(p => !state.inventoryLow || p.stock <= 7)
      .filter(p => category === 'All' || p.category === category)
      .filter(p => !search || `${p.name} ${p.category} ${p.brand} ${p.model} ${p.sku} ${p.rack || ''}`.toLowerCase().includes(search))
      .sort((a, b) => state.inventorySort === 'price' ? a.price - b.price : state.inventorySort === 'stock' ? a.stock - b.stock : a.name.localeCompare(b.name));
  }

  function productTableRow(p) {
    return html`<tr><td><div class="product-cell">${productThumb(p)}<div><strong>${esc(p.name)}</strong><div class="cell-muted">${esc(p.ram)}/${esc(p.storage)}</div></div></div></td><td>${badge(p.category)}</td><td><strong>${esc(p.brand)}</strong><div class="cell-muted">${esc(p.model)}</div></td><td><strong>${p.stock}</strong>${p.stock <= 7 ? html`<div class="cell-muted" style="color:var(--danger)">Low Stock</div>` : ''}</td><td class="amount">${money(p.price)}</td><td><code style="font-size:10px;color:var(--muted)">${esc(p.sku)}</code></td><td><div class="table-actions"><button class="icon-action" data-action="edit-product" data-id="${p.id}" title="Edit">${icon('edit')}</button><button class="icon-action" data-action="delete-product" data-id="${p.id}" title="Delete">${icon('trash')}</button></div></td></tr>`;
  }


  function renderAccessories() {
    const search = (state.accessorySearch || '').trim().toLowerCase();
    const accessories = state.products
      .filter(p => isAccessoryProduct(p))
      .filter(p => !search || `${p.name} ${p.category} ${p.brand} ${p.model} ${p.compatibleModels || ''} ${p.sku} ${p.rack || ''}`.toLowerCase().includes(search))
      .sort((a, b) => a.name.localeCompare(b.name));
    const units = accessories.reduce((sum, p) => sum + Number(p.stock || 0), 0);
    const value = accessories.reduce((sum, p) => sum + Number(p.stock || 0) * Number(p.price || 0), 0);
    const low = accessories.filter(p => Number(p.stock || 0) <= 7).length;
    return html`${renderPageHead(html`<button class="btn btn-secondary" data-action="navigate" data-page="inventory">${icon('box')} Mobile Inventory</button><button class="btn btn-primary" data-action="add-accessory-modal">${icon('plus')} Add Accessory</button>`)}
      <section class="laptop-hero">
        <div><span class="client-pill">ACCESSORY STOCK DESK</span><h2>Separate mobile accessories tab.</h2><p>Manage chargers, handsfree, tempered glass, covers, cables and AirPods cases separately from mobile inventory.</p></div>
        <div class="laptop-hero-icon">${icon('phone','icon-xl')}</div>
      </section>
      <div class="metric-grid laptop-metrics">
        ${metricCard('Accessory Items', n(accessories.length), 'phone', 'Total accessory models', 'up')}
        ${metricCard('Accessory Units', n(units), 'box', `${low} low stock`, low ? 'down' : 'up')}
        ${metricCard('Accessory Value', money(value), 'wallet', 'Sale value in stock', 'up')}
        ${metricCard('POS Ready', 'Barcode', 'scan', 'Accessories show in POS', 'up')}
      </div>
      <section class="card table-card premium-table">
        <div class="table-tools"><div class="input-wrap">${icon('search')}<input class="input" data-accessory-search value="${esc(state.accessorySearch || '')}" placeholder="Search name, category, brand, model, SKU or rack"></div><button class="btn btn-secondary btn-small" data-action="add-accessory-modal">${icon('plus')} Add Accessory</button></div>
        <div class="table-wrap"><table><thead><tr><th>Accessory</th><th>Category</th><th>Brand / Model</th><th>Variant</th><th>Stock</th><th>Rack</th><th>SKU</th><th>Action</th></tr></thead><tbody>${accessories.length ? accessories.map(accessoryTableRow).join('') : noRows(8, 'No mobile accessories added yet. Click Add Accessory to create accessory stock.')}</tbody></table></div>
      </section>`;
  }

  function accessoryTableRow(p) {
    return html`<tr><td><div class="product-cell">${productThumb(p)}<div><strong>${esc(p.name)}</strong><div class="cell-muted">${money(p.cost)} → ${money(p.price)}</div></div></div></td><td>${badge(p.category)}</td><td><strong>${esc(p.brand || 'Other')}</strong><div class="cell-muted">${esc(p.model || p.compatibleModels || 'Universal')}</div></td><td><strong>${esc(p.variant || p.ram || 'Standard')}</strong><div class="cell-muted">${esc(p.color || '')} ${p.quality ? '· ' + esc(p.quality) : ''}</div></td><td><strong>${n(p.stock)}</strong>${p.stock <= Number(p.minStock || 7) ? html`<div class="cell-muted" style="color:var(--danger)">Low Stock</div>` : ''}</td><td>${esc(p.rack || '—')}</td><td><code style="font-size:10px;color:var(--muted)">${esc(p.sku)}</code></td><td><div class="table-actions"><button class="icon-action" data-action="edit-product" data-id="${p.id}" title="Edit Accessory">${icon('edit')}</button><button class="icon-action" data-action="delete-product" data-id="${p.id}" title="Delete">${icon('trash')}</button></div></td></tr>`;
  }


  function renderSpareParts() {
    const search = (state.sparePartSearch || '').trim().toLowerCase();
    const parts = state.products
      .filter(p => isSparePartProduct(p))
      .filter(p => !search || `${p.name} ${p.category} ${p.brand} ${p.model} ${p.quality || ''} ${p.sku} ${p.rack || ''}`.toLowerCase().includes(search))
      .sort((a, b) => a.name.localeCompare(b.name));
    const units = parts.reduce((sum, p) => sum + Number(p.stock || 0), 0);
    const value = parts.reduce((sum, p) => sum + Number(p.stock || 0) * Number(p.price || 0), 0);
    const low = parts.filter(p => Number(p.stock || 0) <= Number(p.minStock || 3)).length;
    return html`${renderPageHead(html`<button class="btn btn-secondary" data-action="navigate" data-page="repairs">${icon('wrench')} Repair Jobs</button><button class="btn btn-primary" data-action="add-spare-part-modal">${icon('plus')} Add Spare Part</button>`)}
      <section class="laptop-hero spare-parts-hero">
        <div><span class="client-pill">SPARE PARTS STOCK</span><h2>Manage repair parts separately.</h2><p>Track LCD, battery, charging board, camera, flex, speaker and repair parts with rack, SKU, brand/model and low-stock alerts.</p></div>
        <div class="laptop-hero-icon">${icon('wrench','icon-xl')}</div>
      </section>
      <div class="metric-grid laptop-metrics">
        ${metricCard('Part Items', n(parts.length), 'wrench', 'Total spare part models', 'up')}
        ${metricCard('Part Units', n(units), 'box', `${low} low stock`, low ? 'down' : 'up')}
        ${metricCard('Parts Value', money(value), 'wallet', 'Sale value in stock', 'up')}
        ${metricCard('Repair Ready', 'Linked', 'shield', 'Use parts with repair jobs', 'up')}
      </div>
      <section class="card table-card premium-table">
        <div class="table-tools"><div class="input-wrap">${icon('search')}<input class="input" data-spare-part-search value="${esc(state.sparePartSearch || '')}" placeholder="Search part, category, brand, model, SKU or rack"></div><button class="btn btn-secondary btn-small" data-action="add-spare-part-modal">${icon('plus')} Add Spare Part</button></div>
        <div class="table-wrap"><table><thead><tr><th>Part</th><th>Category</th><th>Brand / Model</th><th>Quality</th><th>Stock</th><th>Rack</th><th>SKU</th><th>Action</th></tr></thead><tbody>${parts.length ? parts.map(sparePartTableRow).join('') : noRows(8, 'No spare parts added yet. Click Add Spare Part to create repair stock.')}</tbody></table></div>
      </section>`;
  }

  function sparePartTableRow(p) {
    return html`<tr><td><div class="product-cell">${productThumb(p)}<div><strong>${esc(p.name)}</strong><div class="cell-muted">${esc(p.notes || 'Repair part')}</div></div></div></td><td>${badge(p.category)}</td><td><strong>${esc(p.brand || '—')}</strong><div class="cell-muted">${esc(p.model || 'Any model')}</div></td><td>${esc(p.quality || '—')}</td><td><strong>${p.stock}</strong>${p.stock <= (p.minStock || 3) ? html`<div class="cell-muted" style="color:var(--danger)">Low Stock</div>` : ''}</td><td>${esc(p.rack || '—')}</td><td><code style="font-size:10px;color:var(--muted)">${esc(p.sku || '—')}</code></td><td><div class="table-actions"><button class="icon-action" data-action="edit-product" data-id="${p.id}" title="Edit Spare Part">${icon('edit')}</button><button class="icon-action" data-action="delete-product" data-id="${p.id}" title="Delete">${icon('trash')}</button></div></td></tr>`;
  }


  function renderLaptops() {
    const search = (state.laptopSearch || '').trim().toLowerCase();
    const laptops = state.products
      .filter(p => p.category === 'Laptop')
      .filter(p => !search || `${p.name} ${p.brand} ${p.model} ${p.processor} ${p.generation} ${p.ram} ${p.storage} ${p.storageType} ${p.graphics} ${p.sku}`.toLowerCase().includes(search))
      .sort((a, b) => a.name.localeCompare(b.name));
    const units = laptops.reduce((sum, p) => sum + Number(p.stock || 0), 0);
    const value = laptops.reduce((sum, p) => sum + Number(p.stock || 0) * Number(p.price || 0), 0);
    const low = laptops.filter(p => Number(p.stock || 0) <= 7).length;
    return html`${renderPageHead(html`<button class="btn btn-secondary" data-action="navigate" data-page="inventory">${icon('box')} All Inventory</button><button class="btn btn-primary" data-action="add-laptop-modal">${icon('plus')} Add Laptop</button>`)}
      <section class="laptop-hero">
        <div><span class="client-pill">LAPTOP STOCK DESK</span><h2>Laptop stock for premium clients.</h2><p>Track HP, Dell, Lenovo, Apple, Asus and used/import laptops separately with specs, stock, cost, sale price and SKU.</p></div>
        <div class="laptop-hero-icon">${icon('laptop','icon-xl')}</div>
      </section>
      <div class="metric-grid laptop-metrics">
        ${metricCard('Laptop Models', n(laptops.length), 'laptop', 'Total laptop items', 'up')}
        ${metricCard('Laptop Units', n(units), 'box', `${low} low stock`, low ? 'down' : 'up')}
        ${metricCard('Laptop Stock Value', money(value), 'wallet', 'Sale value in stock', 'up')}
        ${metricCard('Quick Billing', 'POS Ready', 'pos', 'Laptops also show in POS', 'up')}
      </div>
      <section class="card table-card premium-table">
        <div class="table-tools"><div class="input-wrap">${icon('search')}<input class="input" data-laptop-search value="${esc(state.laptopSearch || '')}" placeholder="Search laptop brand, model, specs or SKU"></div><button class="btn btn-secondary btn-small" data-action="add-laptop-modal">${icon('plus')} Add Laptop</button></div>
        <div class="table-wrap"><table><thead><tr><th>Laptop</th><th>Specs</th><th>Stock</th><th>Purchase</th><th>Sale Price</th><th>SKU</th><th>Action</th></tr></thead><tbody>${laptops.length ? laptops.map(laptopTableRow).join('') : noRows(7, 'No laptops added yet. Click Add Laptop to create laptop stock.')}</tbody></table></div>
      </section>`;
  }

  function laptopTableRow(p) {
    return html`<tr><td><div class="product-cell laptop-product">${productThumb(p)}<div><strong>${esc(p.name)}</strong><div class="cell-muted">${esc(p.brand)} · ${esc(p.model)}</div></div></div></td><td><strong>${esc([p.processor, p.generation].filter(Boolean).join(' · ') || p.variant || 'Specs not set')}</strong><div class="cell-muted">${esc([p.ram, p.storage, p.storageType, p.graphics, p.screenSize].filter(Boolean).join(' · '))}</div><div class="cell-muted">${esc(p.quality || '')}${p.batteryHealth ? ` · Battery ${esc(p.batteryHealth)}` : ''}</div></td><td><strong>${n(p.stock)}</strong>${p.stock <= (p.minStock || 1) ? html`<div class="cell-muted" style="color:var(--danger)">Low Stock</div>` : ''}</td><td class="amount">${money(p.cost)}</td><td class="amount">${money(p.price)}</td><td><code style="font-size:10px;color:var(--muted)">${esc(p.sku)}</code></td><td><div class="table-actions"><button class="icon-action" data-action="edit-product" data-id="${p.id}" title="Edit Laptop">${icon('edit')}</button><button class="icon-action" data-action="delete-product" data-id="${p.id}" title="Delete">${icon('trash')}</button></div></td></tr>`;
  }

  function noRows(colspan, text) { return html`<tr><td colspan="${colspan}"><div class="empty">${icon('search','icon-xl')}<strong>${t(text)}</strong></div></td></tr>`; }

  function renderPurchase() {
    const purchaseBrands = ['Samsung','Apple','Oppo','Vivo','Xiaomi','Realme','Infinix','Tecno','Itel','Nokia','Huawei','OnePlus','Google Pixel','Honor','Motorola','Sony','LG','HP','Dell','Lenovo','Asus','Acer'];
    return html`${renderPageHead(html`<button class="btn btn-secondary" data-action="navigate" data-page="suppliers">${icon('supplier')} Supplier Ledger</button>`)}
      <form class="card form-panel" data-form="purchase" novalidate>
        <div class="form-section"><h3>Device Details</h3><div class="form-grid three"><div class="field"><label>Brand <span class="required">*</span></label><select class="select" name="brand" data-mobile-brand required><option value="">Select brand</option>${purchaseBrands.map(b => html`<option>${b}</option>`).join('')}</select></div><div class="field"><label>Model <span class="required">*</span></label><div class="model-sync-box" data-model-sync-box><div class="model-sync-head"><span>Saved/online-safe models</span><button class="mini-btn" type="button" data-action="sync-mobile-models">${icon('cloud')} Sync</button></div><input class="input" name="model" list="purchase-model-list" data-mobile-model-input placeholder="Select/type model" required><datalist id="purchase-model-list" data-mobile-model-list></datalist><small class="muted">Type the model manually if it is not listed.</small></div></div><div class="field"><label>Category</label><select class="select" name="category"><option>Smartphone</option><option>Laptop</option><option>Tablet</option></select></div><div class="field"><label>RAM <span class="required">*</span></label><select class="select" name="ram" required><option>4GB</option><option selected>8GB</option><option>12GB</option><option>16GB</option></select></div><div class="field"><label>Storage <span class="required">*</span></label><select class="select" name="storage" required><option>64GB</option><option>128GB</option><option selected>256GB</option><option>512GB</option></select></div><div class="field"><label>Stock Quantity <span class="required">*</span></label><input class="input" name="stock" type="number" min="1" value="1" required><small class="muted">Stock quantity will match the IMEI count.</small></div></div></div>
        <div class="form-section"><h3>IMEI / Barcode Scan</h3><div class="imei-scan-panel" data-purchase-imei-panel><div class="field"><label>Scan or Manual IMEI</label><div class="input-wrap">${icon('scan')}<input class="input" data-purchase-imei-input placeholder="Scan with a barcode scanner or type the IMEI manually" inputmode="numeric"></div><small class="muted">Press Enter from the scanner to add IMEI automatically.</small></div><div class="imei-actions"><button class="btn btn-secondary" type="button" data-action="purchase-scan-imei">${icon('barcode')} Scan Barcode</button><button class="btn btn-primary" type="button" data-action="purchase-add-imei">${icon('plus')} Add IMEI</button></div><input type="hidden" name="imeiList" data-purchase-imei-list-value><div class="imei-chip-list" data-purchase-imei-list><span class="empty-mini">No IMEI added yet.</span></div></div></div>
        <div class="form-section"><h3>Purchase Details</h3><div class="form-grid three"><div class="field"><label>Supplier <span class="required">*</span></label><select class="select" name="supplier" required>${state.suppliers.map(s => html`<option>${esc(s.name)}</option>`).join('')}</select></div><div class="field"><label>Purchase Price <span class="required">*</span></label><input class="input" name="cost" type="number" min="0" placeholder="78000" required></div><div class="field"><label>Expected Sale Price <span class="required">*</span></label><input class="input" name="price" type="number" min="0" placeholder="92000" required></div><div class="field"><label>Barcode / SKU</label><input class="input" name="sku" placeholder="Auto generated if blank"></div><div class="field"><label>IMEI 1 / Serial</label><input class="input" name="imei1" placeholder="Auto filled from first IMEI"></div><div class="field"><label>IMEI 2</label><input class="input" name="imei2" placeholder="Auto filled from second IMEI"></div></div></div>
        <div class="form-section"><h3>Documents</h3><div class="form-grid three"><div class="field"><label>CNIC (Optional)</label><input class="input" name="cnic" placeholder="35202-1234567-1"></div>${documentUploadSlot('cnicFront', 'CNIC Front')}${documentUploadSlot('cnicBack', 'CNIC Back')}${documentUploadSlot('purchaseInvoice', 'Purchase Invoice')}</div></div>
        <div class="form-actions"><button class="btn btn-secondary" type="reset">Clear Form</button><button class="btn btn-primary" type="submit">Save Purchase ${icon('arrowRight')}</button></div>
      </form>`;
  }

  function renderSale() {
    return html`${renderPageHead(html`<button class="btn btn-secondary" data-action="navigate" data-page="pos">${icon('pos')} Open POS Billing</button>`)}
      <form class="card form-panel" data-form="sale" novalidate>
        <div class="form-section"><h3>Customer Details</h3><div class="form-grid three"><div class="field"><label>Customer <span class="required">*</span></label><select class="select" name="customer" required><option>Walk-in Customer</option>${state.customers.filter(c => c.name !== 'Walk-in Customer').map(c => html`<option>${esc(c.name)}</option>`).join('')}</select></div><div class="field"><label>Mobile Number</label><input class="input" name="phone" placeholder="0300-1234567"></div><div class="field"><label>Sale Type</label><select class="select" name="saleType"><option>Cash Sale</option><option>Installment</option><option>Credit Sale</option></select></div></div></div>
        <div class="form-section"><h3>Device Details</h3><div class="form-grid three"><div class="field"><label>Brand <span class="required">*</span></label><select class="select" name="brand">${[...new Set(state.products.map(p => p.brand))].map(x => html`<option>${esc(x)}</option>`).join('')}</select></div><div class="field"><label>Product / Model <span class="required">*</span></label><select class="select" name="product" required>${state.products.filter(p => p.stock > 0).map(p => html`<option value="${p.id}">${esc(p.name)} — ${money(p.price)}</option>`).join('')}</select></div><div class="field"><label>IMEI / Serial</label><input class="input" name="imei" placeholder="Enter IMEI or laptop serial"></div></div></div>
        <div class="form-section"><h3>Sale Details</h3><div class="form-grid three"><div class="field"><label>Sale Price <span class="required">*</span></label><input class="input" name="salePrice" type="number" min="1" placeholder="Enter sale price" required></div><div class="field"><label>Discount</label><input class="input" name="discount" type="number" min="0" value="0"></div><div class="field"><label>Payment Method</label><select class="select" name="payment"><option>Cash</option><option>EasyPaisa In</option><option>EasyPaisa Out</option><option>JazzCash In</option><option>JazzCash Out</option><option>Card</option><option>Installment</option></select></div><div class="field"><label>Down Payment</label><input class="input" name="downPayment" type="number" value="0"></div><div class="field"><label>Installments</label><select class="select" name="months"><option>6 Months</option><option>9 Months</option><option>12 Months</option></select></div></div></div>
        <div class="form-actions"><button class="btn btn-secondary" type="reset">Clear Form</button><button class="btn btn-primary" type="submit">Generate Invoice ${icon('arrowRight')}</button></div>
      </form>`;
  }

  function renderRepairs() {
    const list = state.repairs.filter(r => state.repairStatus === 'All' || r.status === state.repairStatus);
    return html`${renderPageHead(html`<button class="btn btn-primary" data-action="add-repair-modal">${icon('plus')} Add Repair Job</button>`)}
      <div class="list-screen-grid"><section class="card card-pad"><div class="table-tools" style="padding:0 0 15px"><div class="input-wrap">${icon('search')}<input class="input" data-repair-search placeholder="Search customer, phone, device or repair ID"></div><button class="btn btn-secondary btn-small" data-action="repair-filter">${icon('filter')} Filter</button></div><div class="pill-tabs status-tabs">${['All','Pending','In Progress','Ready','Job Done','Delivered'].map(status => html`<button class="pill-tab ${state.repairStatus === status ? 'active' : ''}" data-action="repair-status" data-status="${status}">${t(status)}</button>`).join('')}</div><div class="repair-list">${list.map(repairRow).join('') || html`<div class="empty">${icon('wrench','icon-xl')}<strong>No repair jobs found</strong></div>`}</div></section><aside class="stats-side"><section class="card side-stat"><span>Total Repairs</span><strong>${state.repairs.length}</strong></section><section class="card side-stat"><span>Pending Repairs</span><strong>${state.repairs.filter(r=>r.status==='Pending').length}</strong><small style="color:var(--warning)">Need attention today</small></section><section class="card side-stat"><span>Ready for Delivery</span><strong>${state.repairs.filter(r=>['Ready','Job Done'].includes(r.status)).length}</strong><small>Notify customers</small></section><section class="card card-pad"><div class="section-title"><div><h3>Repair Workflow</h3><p>Update each job when status changes.</p></div></div><div class="progress-list">${progressRow('Pending', `${state.repairs.filter(r=>r.status==='Pending').length} jobs`, state.repairs.length ? 100*state.repairs.filter(r=>r.status==='Pending').length/state.repairs.length : 0)}${progressRow('In Progress', `${state.repairs.filter(r=>r.status==='In Progress').length} jobs`, state.repairs.length ? 100*state.repairs.filter(r=>r.status==='In Progress').length/state.repairs.length : 0)}${progressRow('Ready', `${state.repairs.filter(r=>['Ready','Job Done'].includes(r.status)).length} jobs`, state.repairs.length ? 100*state.repairs.filter(r=>['Ready','Job Done'].includes(r.status)).length/state.repairs.length : 0)}</div></section></aside></div>`;
  }

  function repairRow(r) {
    const canPrint = ['Ready', 'Job Done', 'Delivered'].includes(r.status);
    return html`<article class="repair-row"><span class="repair-id">${esc(r.id)}</span><div><strong>${esc(r.customer)} · ${esc(r.device)}</strong><p>${esc(r.issue)} · ${prettyDate(r.date)} · ${esc(r.technician)}</p></div>${badge(r.status)}<div class="repair-actions"><span class="amount">${money(r.cost)}</span>${canPrint ? html`<button class="icon-action" data-action="repair-receipt" data-id="${r.id}" title="Print job receipt">${icon('printer')}</button>` : ''}<button class="icon-action" data-action="repair-details" data-id="${r.id}" title="Open repair">${icon('chevronRight')}</button></div></article>`;
  }

  function renderCustomers() {
    return html`${renderPageHead(html`<button class="btn btn-primary" data-action="add-customer-modal">${icon('plus')} Add Customer</button>`)}
      <div class="list-screen-grid"><section class="card table-card"><div class="table-tools"><div class="input-wrap">${icon('search')}<input class="input" data-customer-search placeholder="Search customer name or mobile"></div><button class="btn btn-secondary btn-small" data-action="export-customers">${icon('download')} Export</button></div><div class="table-wrap"><table><thead><tr><th>Customer</th><th>Mobile Number</th><th>Purchases</th><th>Balance</th><th>Status</th><th>Ledger</th></tr></thead><tbody>${state.customers.map(c => html`<tr><td><div class="person-cell"><span class="avatar">${initials(c.name)}</span><strong>${esc(c.name)}</strong></div></td><td>${esc(c.phone)}</td><td>${c.purchases}</td><td class="amount">${money(c.balance)}</td><td>${badge(c.status)}</td><td><button class="btn btn-secondary btn-small" data-action="customer-ledger" data-id="${c.id}">${icon('receipt')} Ledger</button></td></tr>`).join('')}</tbody></table></div></section><aside class="stats-side"><section class="card side-stat"><span>Customer Credit</span><strong>${money(state.customers.reduce((s,c)=>s+c.balance,0))}</strong><small>Receivable balance</small></section><section class="card side-stat"><span>Total Customers</span><strong>${state.customers.length}</strong></section><section class="card card-pad"><div class="section-title"><div><h3>Credit Reminder</h3><p>Follow up on overdue payments.</p></div></div>${state.customers.filter(c=>c.status==='Overdue').map(c=>html`<div class="due-item"><span class="avatar">${initials(c.name)}</span><div class="due-main"><strong>${esc(c.name)}</strong><span>${esc(c.phone)}</span></div><div class="due-amount"><b>${money(c.balance)}</b><small>Overdue</small></div></div>`).join('') || htmlText('<p style="color:var(--muted);font-size:12px">No overdue customers.</p>')}</section></aside></div>`;
  }

  function renderSuppliers() {
    return html`${renderPageHead(html`<button class="btn btn-primary" data-action="add-supplier-modal">${icon('plus')} Add Supplier</button>`)}
      <div class="list-screen-grid"><section class="card table-card"><div class="table-tools"><div class="input-wrap">${icon('search')}<input class="input" data-supplier-search placeholder="Search supplier, phone or city"></div><button class="btn btn-secondary btn-small" data-action="export-suppliers">${icon('download')} Export</button></div><div class="table-wrap"><table><thead><tr><th>Supplier</th><th>Mobile Number</th><th>City</th><th>Payable</th><th>Status</th><th>Ledger</th></tr></thead><tbody>${state.suppliers.map(s => html`<tr><td><div class="person-cell"><span class="avatar">${initials(s.name)}</span><strong>${esc(s.name)}</strong></div></td><td>${esc(s.phone)}</td><td>${esc(s.city)}</td><td class="amount">${money(s.payable)}</td><td>${badge(s.status)}</td><td><button class="btn btn-secondary btn-small" data-action="supplier-ledger" data-id="${s.id}">${icon('receipt')} Ledger</button></td></tr>`).join('')}</tbody></table></div></section><aside class="stats-side"><section class="card side-stat"><span>Supplier Payables</span><strong>${money(state.suppliers.reduce((s,c)=>s+c.payable,0))}</strong><small style="color:var(--danger)">Amount to settle</small></section><section class="card side-stat"><span>Total Suppliers</span><strong>${state.suppliers.length}</strong><small>All active vendors</small></section><section class="card card-pad"><div class="section-title"><div><h3>Top Payables</h3><p>Highest amount due first.</p></div></div>${[...state.suppliers].sort((a,b)=>b.payable-a.payable).slice(0,3).map(s=>html`<div class="due-item"><span class="avatar">${initials(s.name)}</span><div class="due-main"><strong>${esc(s.name)}</strong><span>${esc(s.phone)}</span></div><div class="due-amount"><b>${money(s.payable)}</b><small>Payable</small></div></div>`).join('')}</section></aside></div>`;
  }

  function renderInstallments() {
    const dueToday = state.installments.filter(x => x.status === 'Due Today');
    const dueSum = dueToday.reduce((sum, x) => sum + x.due, 0);
    return html`${renderPageHead(html`<button class="btn btn-secondary" data-action="receive-payment-modal">${icon('wallet')} Receive Payment</button><button class="btn btn-primary" data-action="add-installment-modal">${icon('plus')} Add Installment</button>`)}
      <div class="installment-grid"><section class="card card-pad"><div class="metric-grid" style="grid-template-columns:repeat(2,minmax(0,1fr));margin-bottom:17px">${metricCard('Due Installments', dueToday.length, 'calendar', 'Payments due today', 'down')}${metricCard('Due Amount', money(dueSum), 'money', 'Collect from customers', 'up')}</div><div class="section-title"><div><h3>Due Today</h3><p>Customers scheduled for collection</p></div><button class="text-link" data-action="view-all-installments">View all</button></div><div class="due-list">${dueToday.map(installmentItem).join('')}</div></section>
        <section class="card card-pad"><div class="section-title"><div><h3>Upcoming Dues</h3><p>Next scheduled payments</p></div><button class="btn btn-secondary btn-small" data-action="download-report">${icon('download')} Report</button></div><div class="due-list">${state.installments.filter(x => x.status !== 'Due Today').map(installmentItem).join('')}</div><div class="card" style="margin-top:17px;padding:16px;background:var(--blue-3);border-color:#c9d8ff"><div style="display:flex;gap:11px"><span style="color:var(--blue)">${icon('chart','icon-lg')}</span><div><strong style="font-size:13px">Installment Collection Insight</strong><p style="font-size:11px;color:var(--muted);line-height:1.5;margin:5px 0 0">Review scheduled payments and outstanding balances.</p></div></div></div></section>
      </div>`;
  }

  function installmentItem(i) {
    return html`<article class="due-item"><span class="avatar">${initials(i.customer)}</span><div class="due-main"><strong>${esc(i.customer)}</strong><span>${esc(i.product)} · ${esc(i.phone)}</span></div><div class="due-amount"><b>${money(i.due)}</b><small>${esc(t(i.status))}</small></div><button class="icon-action" data-action="receive-for-installment" data-id="${i.id}" title="Receive payment">${icon('arrowRight')}</button></article>`;
  }


  function renderExpenses() {
    const expenses = state.expenses || [];
    const total = expenses.reduce((sum, e) => sum + Number(e.amount || 0), 0);
    const todayTotal = expenses.filter(e => e.date === today()).reduce((sum, e) => sum + Number(e.amount || 0), 0);
    return html`${renderPageHead(html`<button class="btn btn-primary" data-action="add-expense-modal">${icon('plus')} Add Expense</button>`)}
      <div class="report-grid">${reportCard('Total Expenses', money(total), 'receipt')}${reportCard('Today Expense', money(todayTotal), 'money')}${reportCard('Expense Records', n(expenses.length), 'chart')}${reportCard('Audit Safe', 'Soft Delete', 'shield')}</div>
      <section class="card table-card" style="margin-top:17px"><div class="section-title card-pad" style="padding-bottom:10px"><div><h3>Expense List</h3><p>Daily rent, utility, salary and shop spending records</p></div></div><div class="table-wrap"><table><thead><tr><th>Date</th><th>Category</th><th>Note</th><th>Amount</th><th>Action</th></tr></thead><tbody>${expenses.map(e => html`<tr><td>${prettyDate(e.date)}</td><td>${badge(e.category || 'General')}</td><td>${esc(e.note || '—')}</td><td class="amount">${money(e.amount)}</td><td><button class="btn btn-danger btn-small" data-action="soft-delete-record" data-type="expense" data-id="${esc(e.id)}">${icon('trash')} Delete</button></td></tr>`).join('') || html`<tr><td colspan="5"><div class="empty">${icon('receipt','icon-xl')}<strong>No expense record found</strong></div></td></tr>`}</tbody></table></div></section>`;
  }


  function renderEwallets() {
    const wallets = state.ewallets || [];
    const defaultWallet = state.ewalletQuickWallet || 'EasyPaisa';
    const defaultDirection = state.ewalletQuickDirection || 'In';
    const total = (name, direction) => wallets.filter(w => w.wallet === name && w.direction === direction).reduce((sum, w) => sum + Number(w.amount || 0), 0);
    const easyIn = total('EasyPaisa', 'In');
    const easyOut = total('EasyPaisa', 'Out');
    const jazzIn = total('JazzCash', 'In');
    const jazzOut = total('JazzCash', 'Out');
    return html`${renderPageHead(html`<button class="btn btn-primary" data-action="quick-ewallet" data-wallet="EasyPaisa" data-direction="In">${icon('wallet')} EasyPaisa In</button><button class="btn btn-secondary" data-action="quick-ewallet" data-wallet="EasyPaisa" data-direction="Out">${icon('wallet')} EasyPaisa Out</button><button class="btn btn-primary" data-action="quick-ewallet" data-wallet="JazzCash" data-direction="In">${icon('wallet')} JazzCash In</button><button class="btn btn-secondary" data-action="quick-ewallet" data-wallet="JazzCash" data-direction="Out">${icon('wallet')} JazzCash Out</button>`)}
      <div class="report-grid">${reportCard('EasyPaisa Balance', money(easyIn - easyOut), 'wallet')}${reportCard('JazzCash Balance', money(jazzIn - jazzOut), 'wallet')}${reportCard('Today In', money(easyIn + jazzIn), 'money')}${reportCard('Today Out', money(easyOut + jazzOut), 'receipt')}</div>
      <section class="card card-pad" style="margin-top:17px">
        <div class="section-title"><div><h3>Quick Entry</h3><p>Select wallet and type, then save.</p></div></div>
        <form data-form="ewallet-inline" class="form-grid three">
          <div class="field"><label>Wallet</label><select class="select" name="wallet"><option ${defaultWallet === 'EasyPaisa' ? 'selected' : ''}>EasyPaisa</option><option ${defaultWallet === 'JazzCash' ? 'selected' : ''}>JazzCash</option></select></div>
          <div class="field"><label>Type</label><select class="select" name="direction"><option ${defaultDirection === 'In' ? 'selected' : ''}>In</option><option ${defaultDirection === 'Out' ? 'selected' : ''}>Out</option></select></div>
          <div class="field"><label>Amount</label><input class="input" name="amount" type="number" min="1" required></div>
          <div class="field"><label>Mobile Number</label><input class="input" name="mobile" placeholder="03XX-XXXXXXX"></div>
          <div class="field"><label>Customer / Party</label><input class="input" name="customer" value="Walk-in Customer"></div>
          <div class="field"><label>Fee</label><input class="input" name="fee" type="number" min="0" value="0"></div>
          <div class="field" style="grid-column:span 3"><label>Reference</label><input class="input" name="note" placeholder="Transaction ID or note"></div>
          <div class="form-actions" style="grid-column:span 3"><button class="btn btn-primary" type="submit">Save & Print ${icon('printer')}</button></div>
        </form>
      </section>
      <section class="card table-card" style="margin-top:17px"><div class="section-title card-pad" style="padding-bottom:10px"><div><h3>Transactions</h3><p>All Easypaisa and JazzCash entries.</p></div></div><div class="table-wrap"><table><thead><tr><th>Date</th><th>Wallet</th><th>Type</th><th>Party</th><th>Mobile</th><th>Amount</th><th>Fee</th><th>Print</th></tr></thead><tbody>${wallets.map(w => html`<tr><td>${prettyDate(w.date)}</td><td>${badge(w.wallet)}</td><td>${badge(w.direction)}</td><td><strong>${esc(w.customer || 'Walk-in Customer')}</strong><div class="cell-muted">${esc(w.note || '')}</div></td><td>${esc(w.mobile || '—')}</td><td class="amount">${money(w.amount)}</td><td class="amount">${money(w.fee || 0)}</td><td><button class="btn btn-secondary btn-small" data-action="ewallet-receipt" data-id="${esc(w.id)}">${icon('printer')} Thermal</button></td></tr>`).join('') || html`<tr><td colspan="8"><div class="empty">${icon('wallet','icon-xl')}<strong>No wallet transaction found</strong></div></td></tr>`}</tbody></table></div></section>`;
  }

  function renderCashClosing() {
    const sessions = state.cashSessions || [];
    const wallets = state.ewallets || [];
    const last = sessions[0] || { opening: 0, sales: 0, expenses: 0, closing: 0, status: 'Open', date: today() };
    const walletTotal = (name, direction) => wallets.filter(w => w.wallet === name && w.direction === direction).reduce((sum, w) => sum + Number(w.amount || 0), 0);
    const easyBalance = walletTotal('EasyPaisa', 'In') - walletTotal('EasyPaisa', 'Out');
    const jazzBalance = walletTotal('JazzCash', 'In') - walletTotal('JazzCash', 'Out');
    return html`${renderPageHead(html`<button class="btn btn-secondary" data-action="add-ewallet-modal" data-wallet="EasyPaisa">${icon('wallet')} EasyPaisa In / Out</button><button class="btn btn-secondary" data-action="add-ewallet-modal" data-wallet="JazzCash">${icon('wallet')} JazzCash In / Out</button><button class="btn btn-primary" data-action="add-cash-session-modal">${icon('plus')} New Cash Closing</button>`)}
      <div class="report-grid">${reportCard('Opening Cash', money(last.opening), 'wallet')}${reportCard('Sales Cash', money(last.sales), 'money')}${reportCard('EasyPaisa Balance', money(easyBalance), 'wallet')}${reportCard('JazzCash Balance', money(jazzBalance), 'wallet')}</div>
      <section class="card table-card" style="margin-top:17px"><div class="section-title card-pad" style="padding-bottom:10px"><div><h3>EasyPaisa / JazzCash In-Out</h3><p>Wallet payments, withdrawals and transfers with thermal receipt.</p></div></div><div class="table-wrap"><table><thead><tr><th>Date</th><th>Wallet</th><th>Type</th><th>Customer / Party</th><th>Mobile</th><th>Amount</th><th>Fee</th><th>Print</th></tr></thead><tbody>${wallets.map(w => html`<tr><td>${prettyDate(w.date)}</td><td>${badge(w.wallet)}</td><td>${badge(w.direction)}</td><td><strong>${esc(w.customer || 'Walk-in Customer')}</strong><div class="cell-muted">${esc(w.note || '')}</div></td><td>${esc(w.mobile || '—')}</td><td class="amount">${money(w.amount)}</td><td class="amount">${money(w.fee || 0)}</td><td><button class="btn btn-secondary btn-small" data-action="ewallet-receipt" data-id="${esc(w.id)}">${icon('printer')} Thermal</button></td></tr>`).join('') || html`<tr><td colspan="8"><div class="empty">${icon('wallet','icon-xl')}<strong>No wallet transaction found</strong></div></td></tr>`}</tbody></table></div></section>
      <section class="card table-card" style="margin-top:17px"><div class="section-title card-pad" style="padding-bottom:10px"><div><h3>Cash Sessions</h3><p>Daily cash counter records</p></div></div><div class="table-wrap"><table><thead><tr><th>Date</th><th>Opened By</th><th>Opening</th><th>Sales</th><th>Expenses</th><th>Closing</th><th>Status</th></tr></thead><tbody>${sessions.map(c => html`<tr><td>${prettyDate(c.date)}</td><td>${esc(c.openedBy || '—')}</td><td class="amount">${money(c.opening)}</td><td class="amount">${money(c.sales)}</td><td class="amount">${money(c.expenses)}</td><td class="amount">${money(c.closing)}</td><td>${badge(c.status || 'Closed')}</td></tr>`).join('') || html`<tr><td colspan="7"><div class="empty">${icon('wallet','icon-xl')}<strong>No cash session found</strong></div></td></tr>`}</tbody></table></div></section>`;
  }

  function renderLedgers() {
    const customerTotal = state.customers.reduce((sum, c) => sum + Number(c.balance || 0), 0);
    const supplierTotal = state.suppliers.reduce((sum, s) => sum + Number(s.payable || 0), 0);
    const wallets = state.ewallets || [];
    const walletIn = wallets.filter(w => w.direction === 'In').reduce((sum, w) => sum + Number(w.amount || 0), 0);
    const walletOut = wallets.filter(w => w.direction === 'Out').reduce((sum, w) => sum + Number(w.amount || 0), 0);
    return html`${renderPageHead(html`<button class="btn btn-secondary" data-action="add-ewallet-modal">${icon('wallet')} Wallet Entry</button><button class="btn btn-secondary" data-action="download-report">${icon('download')} Export Ledger</button>`)}
      <div class="report-grid">${reportCard('Customer Receivable', money(customerTotal), 'users')}${reportCard('Supplier Payable', money(supplierTotal), 'supplier')}${reportCard('Wallet In', money(walletIn), 'wallet')}${reportCard('Wallet Out', money(walletOut), 'receipt')}</div>
      <div class="reports-lower" style="margin-top:17px"><section class="card table-card"><div class="section-title card-pad" style="padding-bottom:10px"><div><h3>Customer Ledger</h3><p>Receivable balances</p></div></div><div class="table-wrap"><table><thead><tr><th>Customer</th><th>Phone</th><th>Status</th><th>Balance</th><th>Action</th></tr></thead><tbody>${state.customers.map(c => html`<tr><td><strong>${esc(c.name)}</strong></td><td>${esc(c.phone)}</td><td>${badge(c.status)}</td><td class="amount">${money(c.balance)}</td><td><button class="btn btn-secondary btn-small" data-action="customer-ledger" data-id="${esc(c.id)}">Open</button></td></tr>`).join('')}</tbody></table></div></section><section class="card table-card"><div class="section-title card-pad" style="padding-bottom:10px"><div><h3>Supplier Ledger</h3><p>Payable balances</p></div></div><div class="table-wrap"><table><thead><tr><th>Supplier</th><th>Phone</th><th>City</th><th>Payable</th><th>Action</th></tr></thead><tbody>${state.suppliers.map(s => html`<tr><td><strong>${esc(s.name)}</strong></td><td>${esc(s.phone)}</td><td>${esc(s.city)}</td><td class="amount">${money(s.payable)}</td><td><button class="btn btn-secondary btn-small" data-action="supplier-ledger" data-id="${esc(s.id)}">Open</button></td></tr>`).join('')}</tbody></table></div></section></div>
      <section class="card table-card" style="margin-top:17px"><div class="section-title card-pad" style="padding-bottom:10px"><div><h3>EasyPaisa / JazzCash Ledger</h3><p>In and out wallet transaction history.</p></div></div><div class="table-wrap"><table><thead><tr><th>Date</th><th>Wallet</th><th>Type</th><th>Party</th><th>Amount</th><th>Fee</th><th>Receipt</th></tr></thead><tbody>${wallets.map(w => html`<tr><td>${prettyDate(w.date)}</td><td>${badge(w.wallet)}</td><td>${badge(w.direction)}</td><td>${esc(w.customer || 'Walk-in Customer')}</td><td class="amount">${money(w.amount)}</td><td class="amount">${money(w.fee || 0)}</td><td><button class="btn btn-secondary btn-small" data-action="ewallet-receipt" data-id="${esc(w.id)}">${icon('printer')} Print</button></td></tr>`).join('') || html`<tr><td colspan="7"><div class="empty">${icon('wallet','icon-xl')}<strong>No wallet ledger found</strong></div></td></tr>`}</tbody></table></div></section>`;
  }

  function renderStaffPermissions() {
    const staff = state.staff || [];
    const permissions = state.permissions || [];
    return html`${renderPageHead(html`<button class="btn btn-primary" data-action="add-user-modal">${icon('plus')} Add Staff</button>`)}
      <div class="list-screen-grid"><section class="card table-card"><div class="section-title card-pad" style="padding-bottom:10px"><div><h3>Staff Users</h3><p>Server staff accounts. Offline desktop and Android sign-in currently require the shop owner.</p></div></div><div class="table-wrap"><table><thead><tr><th>Name</th><th>Role</th><th>Phone</th><th>Sales</th><th>Status</th></tr></thead><tbody>${staff.map(u => html`<tr><td><div class="person-cell"><span class="avatar">${initials(u.name)}</span><strong>${esc(u.name)}</strong></div></td><td>${esc(u.role)}</td><td>${esc(u.phone || '—')}</td><td class="amount">${money(u.sales || 0)}</td><td>${badge(u.status || 'Active')}</td></tr>`).join('') || html`<tr><td colspan="5"><div class="empty">${icon('users','icon-xl')}<strong>No staff added</strong></div></td></tr>`}</tbody></table></div></section><aside class="stats-side"><section class="card side-stat"><span>Total Staff</span><strong>${staff.length}</strong><small>Active local users</small></section><section class="card card-pad"><div class="section-title"><div><h3>Permission Summary</h3><p>Owner/manager/cashier controls</p></div></div><div class="progress-list">${permissions.map(p => progressRow(p.label, p.owner ? 'Owner allowed' : 'Limited', p.owner ? 100 : 50)).join('')}</div></section></aside></div>`;
  }

  function renderAudit() {
    const deleted = state.deletedRecords || [];
    const stockMoves = state.stockMovements || [];
    return html`${renderPageHead(html`<button class="btn btn-secondary" data-action="download-backup">${icon('download')} Backup</button>`)}
      <div class="report-grid">${reportCard('Deleted Records', n(deleted.length), 'trash')}${reportCard('Stock Movements', n(stockMoves.length), 'box')}${reportCard('Audit Mode', 'Active', 'shield')}${reportCard('Soft Delete', 'Safe', 'check')}</div>
      <div class="reports-lower" style="margin-top:17px"><section class="card table-card"><div class="section-title card-pad" style="padding-bottom:10px"><div><h3>Deleted Records</h3><p>Soft-delete history with safe restore</p></div></div><div class="table-wrap"><table><thead><tr><th>Date</th><th>Type</th><th>Record</th><th>Deleted By</th><th>Action</th></tr></thead><tbody>${deleted.map(d => html`<tr><td>${prettyDate(d.date)}</td><td>${badge(d.type)}</td><td>${esc(d.label || d.id || '—')}</td><td>${esc(d.by || '—')}</td><td>${d.payload ? html`<button class="btn btn-secondary btn-small" data-action="restore-record" data-id="${esc(d.deleteId || '')}">${icon('refresh')} Restore</button>` : '—'}</td></tr>`).join('') || html`<tr><td colspan="5"><div class="empty">${icon('shield','icon-xl')}<strong>No deleted record</strong></div></td></tr>`}</tbody></table></div></section><section class="card table-card"><div class="section-title card-pad" style="padding-bottom:10px"><div><h3>Stock Movement</h3><p>Sale, purchase and adjustment history</p></div></div><div class="table-wrap"><table><thead><tr><th>Date</th><th>Product</th><th>Type</th><th>Qty</th><th>By</th></tr></thead><tbody>${stockMoves.map(m => html`<tr><td>${prettyDate(m.date)}</td><td><strong>${esc(m.product)}</strong></td><td>${badge(m.type)}</td><td class="amount">${n(m.qty)}</td><td>${esc(m.by || '—')}</td></tr>`).join('') || html`<tr><td colspan="5"><div class="empty">${icon('box','icon-xl')}<strong>No stock movement</strong></div></td></tr>`}</tbody></table></div></section></div>`;
  }

  function reportSales() {
    const now = new Date(); const year = now.getFullYear(); const month = now.getMonth();
    const start = state.reportRange === 'This Year' ? new Date(year, 0, 1) : state.reportRange === 'Last Month' ? new Date(year, month - 1, 1) : new Date(year, month, 1);
    const end = state.reportRange === 'Last Month' ? new Date(year, month, 1) : new Date(year, month + 1, 1);
    return state.invoices.filter(sale => { const date = new Date(sale.date || sale.time || ''); return date >= start && date < end; });
  }
  function renderReports() {
    const sales = reportSales();
    return html`${renderPageHead(html`<select class="select" aria-label="Report period" data-report-range>${['This Month','Last Month','This Year'].map(range => html`<option value="${range}" ${state.reportRange === range ? 'selected' : ''}>${t(range)}</option>`).join('')}</select><button class="btn btn-secondary" data-action="download-report">${icon('download')} Export CSV</button>`)}<p class="audit-scope">Sales use the selected period. Other totals cover records currently on this device; sync before comparing devices. Legacy undated sales are excluded from period totals.</p><div class="report-grid">${reportCard('Period Sales', money(sales.reduce((t,s)=>t+Number(s.total||0),0)), 'wallet')}${reportCard('Period Invoices', n(sales.length), 'receipt')}${reportCard('Stock Units', n(state.products.reduce((t,p)=>t+Number(p.stock||0),0)), 'box')}${reportCard('Recorded Expenses', money((state.expenses||[]).reduce((t,e)=>t+Number(e.amount||0),0)), 'receipt')}</div><section class="card table-card"><div class="card-pad"><h3>Sales in selected period</h3></div><div class="table-wrap"><table><thead><tr><th>Invoice</th><th>Date</th><th>Customer</th><th>Total</th><th>Payment</th></tr></thead><tbody>${sales.map(sale => html`<tr><td>${esc(sale.id)}</td><td>${prettyDate(sale.date || sale.time)}</td><td>${esc(sale.customer)}</td><td>${money(sale.total)}</td><td>${esc(t(sale.payment))}</td></tr>`).join('') || htmlText('<tr><td colspan="5">No sales in this period.</td></tr>')}</tbody></table></div></section>`;
  }

  function reportCard(label, value, iconName) {
    return html`<section class="card report-card"><span class="report-icon">${icon(iconName)}</span><strong>${t(value)}</strong><span>${t(label)}</span></section>`;
  }

  const settingTabs = [
    ['profile','Shop Profile','Manage shop information','supplier'],
    ['theme','Theme','Choose your workspace colors','sun'],
    ['security','Shop PIN','Change your shop PIN','lock'],
    ['backup','Backup & Restore','Secure your business data','cloud'],
    ['users','User Management','Manage app users and roles','users'],
    ['sync','Cloud','Connection and saved changes','cloud'],
    ['subscription','Subscription','Manage your plan','shield']
  ];



  function renderSettings() {
    const detail = renderSettingsDetail();
    return html`${renderPageHead()}
      <div class="settings-layout"><aside class="card settings-menu">${settingTabs.map(([key,title,sub,ic])=>html`<button class="${state.settingsTab===key?'active':''}" data-action="settings-tab" data-tab="${key}">${icon(ic)}<span><strong>${t(title)}</strong><small>${t(sub)}</small></span>${icon('chevronRight')}</button>`).join('')}</aside><section class="card settings-details">${detail}</section></div>`;
  }

  function renderSettingsDetail() {
    if (state.settingsTab === 'security') return passwordView();
    if (state.settingsTab === 'theme') return html`<div class="section-title"><div><h2>Workspace themes</h2><p>Choose your workspace colors</p></div></div>${themeChoices()}<p class="theme-note">Saved on this device. Your shop data stays the same.</p>`;
    if (state.settingsTab === 'backup') return html`<div class="section-title"><div><h2>Backup & Restore</h2><p>Create a safe local copy of your ERP data.</p></div><button class="btn btn-primary" data-action="download-backup">${icon('download')} Create Backup</button></div><div class="setting-row"><div><strong>Last Backup</strong><span>Not recorded yet — create a backup before major changes.</span></div><button class="btn btn-secondary btn-small" data-action="download-backup">Backup Now</button></div><div class="setting-row"><div><strong>Restore Data</strong><span>Restore a previously exported local backup.</span></div><label class="btn btn-secondary btn-small file-btn">${icon('upload')} Restore<input type="file" accept="application/json,.json" data-restore-file></label></div><div class="security-tip" style="margin-top:16px">${icon('shield')}<span>Backup downloads a JSON snapshot of the current local data. Keep it in a safe location.</span></div>`;
    if (state.settingsTab === 'users') {
      const rows = [{ name: shopOwnerName(), role: 'Owner / Super Admin', status: 'Active' }, ...(state.staff || []).map((u) => ({ name: u.name, role: u.role, status: u.status }))];
      return html`<div class="section-title"><div><h2>User Management</h2><p>Real Cloud-linked owner and staff access.</p></div><button class="btn btn-primary" data-action="add-user-modal">${icon('plus')} Add User</button></div>${rows.map(({name, role, status}) => html`<div class="setting-row"><div style="display:flex;align-items:center;gap:10px"><span class="avatar">${initials(name)}</span><div><strong>${esc(name)}</strong><span>${esc(role)}</span></div></div>${badge(status)}</div>`).join('')}`;
    }
    if (state.settingsTab === 'sync') return html`<div class="section-title"><div><h2>Cloud</h2><p>Changes are saved on this device and queued for upload.</p></div><button class="btn btn-primary" data-action="sync-now">${icon('cloud')} Cloud sync</button></div><div class="report-grid">${reportCard('Pending', n(desktopSyncStatus.pending), 'cloud')}${reportCard('Failed', n(desktopSyncStatus.failed), 'info')}${reportCard('Synced', n(desktopSyncStatus.synced), 'check')}</div><div class="setting-row"><div><strong>Connection</strong><span>Secure cloud connection</span></div></div><div class="setting-row"><div><strong>Last successful sync</strong><span>${esc(desktopSyncStatus.lastSyncAt || 'No successful sync yet')}</span></div></div><div class="security-tip">${icon('shield')}<span>${esc(desktopSyncStatus.lastSyncError || 'Sign in online to refresh your connection. Credentials are managed automatically.')}</span></div>`;
    if (state.settingsTab === 'subscription') {
      const shop = shopProfile();
      const expiry = shop.subscriptionExpiresAt || shop.subscription_expires_at || shop.expiresAt || shop.expires_at || shop.endDate || shop.expiryDate || '';
      return html`<div class="section-title"><div><h2>Subscription</h2><p>Last verified shop information. Sync to check for updates.</p></div><button class="btn btn-secondary" data-action="sync-now">Refresh</button></div><section class="card card-pad"><h3>${esc(shop.plan || 'Not verified')}</h3><div class="setting-row"><strong>Shop status</strong>${badge(shop.status || 'Unknown')}</div><div class="setting-row"><strong>Validity</strong><span>${expiry ? prettyDate(expiry) : 'Not supplied by server'}</span></div><div class="setting-row"><strong>Device access</strong>${badge(desktopSyncStatus.licenseStatus || 'Unknown')}</div><div class="setting-row"><strong>Shop ID</strong><span>${esc(shop.id || 'Not linked')}</span></div></section><button class="btn btn-secondary" data-action="support-auth">Contact Support</button>`;
    }
    const shop = shopProfile();
    return html`<div class="section-title"><div><h2>Shop Profile</h2><p>Update shop name, logo and invoice details.</p></div><button class="btn btn-primary" data-action="save-profile">${icon('check')} Save Changes</button></div><form class="form-grid" data-form="profile"><input type="hidden" name="id" value="${esc(shop.id)}"><input type="hidden" name="logo" value="${esc(shop.logo || '')}" data-shop-logo-value><div class="field" style="grid-column:1/-1"><label>Shop Logo</label><div class="product-image-panel"><div class="product-image-preview ${shop.logo ? 'has-image' : ''}" data-shop-logo-preview>${shop.logo ? html`<img src="${esc(shop.logo)}" alt="${esc(shop.shopName)} logo">` : html`<span>${icon('box','icon-xl')}</span><strong>${esc(shopInitials())}</strong>`}</div><div><label class="btn btn-secondary file-btn">${icon('upload')} Choose Logo<input type="file" accept="image/*" data-shop-logo-file></label><p class="field-hint">After saving the logo, shop branding will appear on the sidebar and invoices.</p><button class="btn btn-ghost btn-small" type="button" data-action="remove-shop-logo" ${shop.logo ? '' : 'disabled'}>${icon('trash')} Remove Logo</button></div></div></div><div class="field"><label>Shop Name</label><input class="input" name="shopName" value="${esc(shop.shopName)}"></div><div class="field"><label>Owner Name</label><input class="input" name="ownerName" value="${esc(shop.ownerName)}"></div><div class="field"><label>Mobile Number</label><input class="input" name="ownerMobile" value="${esc(shop.ownerMobile)}"></div><div class="field"><label>Alternate Mobile</label><input class="input" name="ownerMobile2" value="${esc(shop.ownerMobile2 || '')}" placeholder="Optional second number"></div><div class="field"><label>City</label><input class="input" name="city" value="${esc(shop.city)}"></div><div class="field" style="grid-column:1/-1"><label>Shop Address</label><input class="input" name="address" value="${esc(shop.address)}" placeholder="Complete market / road / city address"></div><div class="field"><label>Invoice Prefix</label><input class="input" name="invoicePrefix" value="${esc(shop.invoicePrefix)}"></div><div class="field"><label>Shop Code</label><input class="input" name="shopCode" readonly value="${esc(shop.shopCode)}"></div></form>`;
  }

  function renderSupport() {
    return html`${renderPageHead()}
      <div class="support-grid"><form class="card form-panel" data-form="support" novalidate><div class="section-title"><div><h3>Send us a Request</h3><p>Describe the issue and our support team can review it.</p></div></div><div class="form-grid"><div class="field"><label>Issue Type <span class="required">*</span></label><select class="select" name="type" required><option value="">Select issue type</option><option>Sync Issue</option><option>Receipt Printing</option><option>Stock</option><option>Account</option><option>Other</option></select></div><div class="field"><label>Priority <span class="required">*</span></label><select class="select" name="priority" required><option value="">Select priority</option><option>Low</option><option>Medium</option><option>High</option></select></div></div><div class="field" style="margin-top:15px"><label>Complaint / Message <span class="required">*</span></label><textarea class="textarea" name="message" maxlength="500" placeholder="Describe your issue in detail…" required></textarea></div><div class="form-actions"><button class="btn btn-primary" type="submit">Send Request ${icon('share')}</button></div></form><section class="card card-pad"><div class="section-title"><div><h3>Previous Requests</h3><p>Track current support progress.</p></div><button class="text-link" data-action="view-all-requests">View All</button></div><div class="request-list">${state.supportRequests.map(requestItem).join('')}</div></section></div>`;
  }

  function requestItem(r) {
    return html`<article class="request-item"><div class="request-top"><strong>${esc(r.type)}</strong>${badge(r.status)}</div><p>${esc(r.message)}</p><time>${esc(r.id)} · ${prettyDate(r.date)} · ${esc(r.priority)} priority</time></article>`;
  }

  function renderApp() {
    const active = document.activeElement;
    const focusKey = active && app.contains(active) ? (active.id ? { id: active.id } : active.name ? { name: active.name } : [...active.attributes].find(a => a.name.startsWith('data-')) ? { attr: [...active.attributes].find(a => a.name.startsWith('data-')).name } : null) : null;
    const selection = active && typeof active.selectionStart === 'number' ? [active.selectionStart, active.selectionEnd] : null;
    try {
      if (splashVisible) renderSplash();
      else if (!state.isLoggedIn) renderAuth();
      else renderShell();
      applyManagedAppearance();
      labelControls(app);
      if (focusKey) {
        const next = focusKey.id ? document.getElementById(focusKey.id) : focusKey.name ? app.querySelector(`[name="${CSS.escape(focusKey.name)}"]`) : app.querySelector(`[${focusKey.attr}]`);
        if (next) { next.focus({preventScroll:true}); if (selection && next.setSelectionRange) next.setSelectionRange(...selection); }
      }
    } catch (error) {
      console.error('SkyBarech render error:', error);
      splashVisible = false;
      state.mobileMenuOpen = false;
      const brokenPage = state.activePage;
      state.activePage = 'dashboard';
      try {
        if (state.isLoggedIn) {
          renderShell();
          notify('Dashboard recovered', `${brokenPage || 'Page'} had a render issue. Dashboard has been opened safely.`, 'error');
          return;
        }
      } catch (secondError) {
        console.error('SkyBarech dashboard recovery failed:', secondError);
      }
      state.isLoggedIn = false;
      state.authMode = 'login';
      app.innerHTML = html`<main class="auth-layout"><section class="auth-panel"><div class="auth-card"><div class="auth-card-head"><div class="micro">SECURE SHOP LOGIN</div><h2>Login screen recovered</h2><p>A saved page had an issue. Login again or reset local data.</p></div><button class="btn btn-primary full" type="button" data-action="desktop-login">Login to Dashboard</button><button class="btn btn-secondary full" style="margin-top:10px" type="button" data-action="reset-local-data">Reset Local Data</button></div></section></main>`;
    }
  }

  /* --------------------------------------------------------------
     Event actions
  -------------------------------------------------------------- */
  function notify(title, message, type = 'success') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = html`${icon(type === 'error' ? 'info' : type === 'success' ? 'check' : 'info')}<div><strong>${esc(t(title))}</strong><span>${esc(t(message))}</span></div>`;
    toastRoot.appendChild(toast);
    setTimeout(() => toast.remove(), 3900);
  }

  let modalOpener = null;
  function labelControls(root) {
    root.querySelectorAll('.field').forEach((field, index) => {
      const label = field.querySelector('label'); const input = field.querySelector('input, select, textarea');
      if (label && input) { input.id ||= `field-${root.id}-${index}`; label.htmlFor = input.id; }
    });
    root.querySelectorAll('button[title]').forEach(button => { if (!button.hasAttribute('aria-label')) button.setAttribute('aria-label', button.title); });
  }
  function openModal(title, sub, content, wide = false) {
    if (!modalRoot.firstElementChild) modalOpener = document.activeElement;
    modalRoot.innerHTML = html`<div class="modal-backdrop" data-action="close-modal-backdrop"><section class="modal ${wide ? 'wide' : ''}" role="dialog" aria-modal="true" aria-labelledby="dialog-title" tabindex="-1"><div class="modal-head"><div><h3 id="dialog-title">${t(title)}</h3>${sub ? html`<p>${t(sub)}</p>` : ''}</div><button class="close-modal" data-action="close-modal" aria-label="Close">${icon('close')}</button></div><div class="modal-body">${content}</div></section></div>`;
    app.inert = true;
    labelControls(modalRoot);
    requestAnimationFrame(() => (modalRoot.querySelector('[autofocus], input:not([type="hidden"]), select, textarea, button') || modalRoot.querySelector('[role="dialog"]'))?.focus());
  }

  function closeModal() { modalRoot.innerHTML = ''; app.inert = false; if (modalOpener?.isConnected) modalOpener.focus(); modalOpener = null; }

  function confirmModal(title, message, confirmLabel, onConfirmAction, data = '') {
    openModal(title, '', html`<div class="confirm-box"><div class="confirm-icon">${icon('trash','icon-xl')}</div><strong>${esc(t(title))}</strong><p>${esc(t(message))}</p><div class="confirm-actions"><button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-danger" data-action="${onConfirmAction}" data-id="${esc(data)}">${confirmLabel}</button></div></div>`);
  }

  function productModal(product = null, defaultCategory = 'Smartphone') {
    const isEditingAccessory = product && isAccessoryProduct(product);
    const isEditingSpare = product && isSparePartProduct(product);
    const isAccessory = defaultCategory === 'Accessory' || isEditingAccessory;
    const isSpare = defaultCategory === 'SparePart' || isEditingSpare;
    const isLaptop = defaultCategory === 'Laptop' || product?.category === 'Laptop';
    const p = product || { name:'', brand: isAccessory ? 'Samsung' : (isSpare ? 'Samsung' : (isLaptop ? 'HP' : 'Samsung')), model:'', compatibleModels:'', category:isAccessory ? 'Cover' : (isSpare ? 'Display / LCD' : defaultCategory), variant:'', ram: isAccessory ? '—' : (isLaptop ? '16GB' : '8GB'), storage: isAccessory ? '—' : (isLaptop ? '512GB' : '128GB'), color:'Black', quality:'A Quality', warranty:'No Warranty', wholesalePrice:'', minStock:2, rack:'', notes:'', cost:'', price:'', stock:1, sku:'', image:'' };
    const title = product ? 'Edit Product' : (isLaptop ? 'Add Laptop' : isSpare ? 'Add Spare Part' : isAccessory ? 'Add Mobile Accessory' : 'Add Product');
    const subtitle = isAccessory ? 'Fast entry form: name, category, purchase price, sale price and quantity are required.' : (isSpare ? 'Save repair parts in the separate spare parts tab.' : (isLaptop ? 'Laptop stock is saved only in the laptop tab.' : 'Create a normal mobile/tablet inventory item.'));
    if (isSpare) {
      const cat = SPARE_PART_CATEGORIES.includes(p.category) ? p.category : 'Display / LCD';
      openModal(title, 'Repair shop fast entry: part name, category, purchase price, sale price and quantity are required.', html`<form data-form="product" novalidate><input type="hidden" name="id" value="${esc(product?.id || '')}"><input type="hidden" name="shop_id" value="${esc(product?.shop_id || currentShopId())}"><input type="hidden" name="image" value="${esc(p.image || '')}" data-product-image-value>
        <div class="fast-entry-note">${icon('wrench')}<div><strong>Spare Parts Fast Add</strong><span>Display, battery, charging board, camera, flex, speaker and repair parts are saved in separate stock.</span></div></div>
        <div class="form-grid accessory-fast-grid">
          <div class="field form-span-2"><label>Part Name <span class="required">*</span></label><input class="input input-lg" name="name" value="${esc(p.name)}" required placeholder="e.g. Samsung A14 LCD / iPhone 11 Battery"></div>
          <div class="field"><label>Part Category <span class="required">*</span></label><select class="select input-lg" name="category">${SPARE_PART_CATEGORIES.map(x => html`<option ${cat===x?'selected':''}>${x}</option>`).join('')}</select></div>
          <div class="field"><label>Quantity <span class="required">*</span></label><input class="input input-lg" name="stock" type="number" min="0" value="${esc(p.stock)}" required></div>
          <div class="field"><label>Purchase Price <span class="required">*</span></label><input class="input input-lg" name="cost" type="number" min="0" value="${esc(p.cost)}" required></div>
          <div class="field"><label>Sale Price <span class="required">*</span></label><input class="input input-lg" name="price" type="number" min="0" value="${esc(p.price)}" required></div>
          <div class="field"><label>Mobile Brand</label><select class="select" name="brand" data-mobile-brand>${ACCESSORY_BRANDS.map(x=>html`<option ${String(p.brand || 'Samsung')===x?'selected':''}>${x}</option>`).join('')}</select></div>
          <div class="field">${modelSyncBox(String(p.brand || 'Samsung'), p.model)}</div>
          <div class="field"><label>Rack / Shelf</label><input class="input" name="rack" value="${esc(p.rack || '')}" placeholder="P-1"></div>
          <div class="field"><label>Barcode / SKU</label><input class="input" name="sku" value="${esc(p.sku)}" placeholder="Auto if blank"></div>
          <div class="field"><label>Quality</label><select class="select" name="quality">${ACCESSORY_QUALITIES.map(x=>html`<option ${String(p.quality || 'A Quality')===x?'selected':''}>${x}</option>`).join('')}</select></div>
          <div class="field"><label>Min Stock Alert</label><input class="input" name="minStock" type="number" min="0" value="${esc(p.minStock || 2)}"></div>
          <div class="field form-span-2"><label>Notes</label><textarea class="textarea" name="notes" placeholder="Supplier / compatibility / warranty notes">${esc(p.notes || '')}</textarea></div>
        </div>
        <div class="form-actions"><button class="btn btn-secondary" type="button" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">${t(product ? 'Save Changes' : 'Save Part')} ${icon('arrowRight')}</button></div></form>`, true);
      return;
    }
    if (isAccessory) {
      const cat = ACCESSORY_CATEGORIES.includes(p.category) && p.category !== 'Accessory' ? p.category : 'Cover';
      const variantOptions = variantOptionsForCategory(cat);
      openModal(title, 'Fast entry: only key fields are required; extra details are optional.', html`<form data-form="product" novalidate><input type="hidden" name="id" value="${esc(product?.id || '')}"><input type="hidden" name="shop_id" value="${esc(product?.shop_id || currentShopId())}"><input type="hidden" name="image" value="${esc(p.image || '')}" data-product-image-value>
        <div class="fast-entry-note">${icon('zap')}<div><strong>Fast Add</strong><span>Enter product name, category, purchase price, sale price and quantity. Other fields are optional.</span></div></div>
        <div class="form-grid accessory-fast-grid">
          <div class="field form-span-2"><label>Product Name <span class="required">*</span></label><input class="input input-lg" name="name" value="${esc(p.name)}" required placeholder="e.g. A14 Glass / 25W Charger / Type-C Cable"></div>
          <div class="field"><label>Category <span class="required">*</span></label><select class="select input-lg" name="category" data-accessory-category>${ACCESSORY_CATEGORIES.filter(x=>x !== 'Accessory').map(x => html`<option ${cat===x?'selected':''}>${x}</option>`).join('')}</select></div>
          <div class="field"><label>Quantity <span class="required">*</span></label><input class="input input-lg" name="stock" type="number" min="0" value="${esc(p.stock)}" required placeholder="0"></div>
          <div class="field"><label>Purchase Price <span class="required">*</span></label><input class="input input-lg" name="cost" type="number" min="0" value="${esc(p.cost)}" required placeholder="Rs. 0"></div>
          <div class="field"><label>Sale Price <span class="required">*</span></label><input class="input input-lg" name="price" type="number" min="0" value="${esc(p.price)}" required placeholder="Rs. 0"></div>
          <div class="field"><label>Barcode / SKU</label><div class="input-wrap"><input class="input" name="sku" value="${esc(p.sku)}" placeholder="Auto if blank"><button class="btn btn-secondary btn-small" type="button" data-action="generate-sku">Auto</button></div></div>
          <div class="field"><label>Rack / Shelf</label><input class="input" name="rack" value="${esc(p.rack || '')}" placeholder="A-2"></div>
        </div>
        <details class="more-details-box">
          <summary>${icon('settings')} More Details Optional</summary>
          <div class="product-image-panel compact"><div class="product-image-preview ${p.image ? 'has-image' : ''}" data-product-image-preview>${p.image ? html`<img src="${esc(p.image)}" alt="${esc(p.name || 'Accessory')}">` : html`<span>${icon('phone','icon-xl')}</span><strong>Photo optional</strong>`}</div><div><label class="btn btn-secondary file-btn">${icon('upload')} Photo<input type="file" accept="image/*" data-product-image-file></label><button class="btn btn-ghost btn-small" type="button" data-action="remove-product-image" ${p.image ? '' : 'disabled'}>${icon('trash')} Remove</button></div></div>
          <div class="form-grid accessory-more-grid">
            <div class="field"><label>Mobile Brand</label><select class="select" name="brand" data-mobile-brand>${ACCESSORY_BRANDS.map(x=>html`<option ${String(p.brand || 'Samsung')===x?'selected':''}>${x}</option>`).join('')}</select><small class="field-hint">For covers, glass and repair parts.</small></div>
            <div class="field">${modelSyncBox(String(p.brand || 'Samsung'), p.model)}</div>
            <div class="field"><label>Compatible Models</label><input class="input" name="compatibleModels" value="${esc(p.compatibleModels || '')}" placeholder="A14 / A15"></div>
            <div class="field"><label>Type / Variant</label><select class="select" name="variant" data-accessory-variant>${variantOptions.length ? variantOptions.map(x=>html`<option ${String(p.variant || p.ram || '')===x?'selected':''}>${x}</option>`).join('') : html`<option>${esc(p.variant || 'Standard')}</option>`}</select><input class="input" name="variantManual" data-accessory-variant-manual value="${esc(variantOptions.length ? '' : (p.variant || ''))}" placeholder="Manual type / variant" style="${variantOptions.length ? 'display:none' : 'margin-top:8px'}"></div>
            <div class="field"><label>Color</label><select class="select" name="color">${ACCESSORY_COLORS.map(x=>html`<option ${String(p.color || 'Black')===x?'selected':''}>${x}</option>`).join('')}</select></div>
            <div class="field"><label>Quality</label><select class="select" name="quality">${ACCESSORY_QUALITIES.map(x=>html`<option ${String(p.quality || 'A Quality')===x?'selected':''}>${x}</option>`).join('')}</select></div>
            <div class="field"><label>Wholesale Price</label><input class="input" name="wholesalePrice" type="number" min="0" value="${esc(p.wholesalePrice || '')}" placeholder="Optional"></div>
            <div class="field"><label>Minimum Stock Alert</label><input class="input" name="minStock" type="number" min="0" value="${esc(p.minStock || 2)}"></div>
            <div class="field"><label>Warranty</label><select class="select" name="warranty">${ACCESSORY_WARRANTIES.map(x=>html`<option ${String(p.warranty || 'No Warranty')===x?'selected':''}>${x}</option>`).join('')}</select></div>
            <div class="field form-span-2"><label>Notes</label><textarea class="textarea" name="notes" placeholder="Optional notes">${esc(p.notes || '')}</textarea></div>
          </div>
        </details>
        <div class="security-tip" style="margin-top:14px">${icon('shield')}<span>Shop ID is attached automatically from the current login.</span></div>
        <div class="form-actions"><button class="btn btn-secondary" type="button" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">${product ? 'Save Changes' : 'Save'} ${icon('arrowRight')}</button></div></form>`, true);
      return;
    }
    const categoryOptions = isLaptop ? ['Laptop'] : ['Smartphone','Tablet'];
    openModal(title, subtitle, html`<form data-form="product" novalidate><input type="hidden" name="id" value="${esc(product?.id || '')}"><input type="hidden" name="shop_id" value="${esc(product?.shop_id || currentShopId())}"><input type="hidden" name="image" value="${esc(p.image || '')}" data-product-image-value><div class="product-image-panel"><div class="product-image-preview ${p.image ? 'has-image' : ''}" data-product-image-preview>${p.image ? html`<img src="${esc(p.image)}" alt="${esc(p.name || 'Product')}">` : html`<span>${icon(isLaptop ? 'laptop' : 'box','icon-xl')}</span><strong>No picture</strong>`}</div><div><label class="btn btn-secondary file-btn">${icon('upload')} Choose Product Picture<input type="file" accept="image/*" data-product-image-file></label><p class="field-hint">Image will be auto-compressed for local storage.</p><button class="btn btn-ghost btn-small" type="button" data-action="remove-product-image" ${p.image ? '' : 'disabled'}>${icon('trash')} Remove Picture</button></div></div><div class="form-grid"><div class="field"><label>Product Name <span class="required">*</span></label><input class="input" name="name" value="${esc(p.name)}" required></div><div class="field"><label>Category <span class="required">*</span></label><select class="select" name="category">${categoryOptions.map(x => html`<option ${p.category===x?'selected':''}>${x}</option>`).join('')}</select></div>${isLaptop ? laptopFields(p) : html`<div class="field">${mobileBrandSelect(p.brand || 'Samsung')}</div><div class="field">${modelSyncBox(String(p.brand || 'Samsung'), p.model)}</div><div class="field"><label>RAM</label><select class="select" name="ram">${['—','4GB','6GB','8GB','12GB','16GB','32GB'].map(x=>html`<option ${x===p.ram?'selected':''}>${x}</option>`).join('')}</select></div><div class="field"><label>Storage</label><select class="select" name="storage">${['—','64GB','128GB','256GB','512GB','1TB','2TB'].map(x=>html`<option ${x===p.storage?'selected':''}>${x}</option>`).join('')}</select></div>`}<div class="field"><label>Purchase Price <span class="required">*</span></label><input class="input" name="cost" type="number" min="0" value="${esc(p.cost)}" required></div><div class="field"><label>Sale Price <span class="required">*</span></label><input class="input" name="price" type="number" min="0" value="${esc(p.price)}" required></div><div class="field"><label>Stock Quantity <span class="required">*</span></label><input class="input" name="stock" type="number" min="0" value="${esc(p.stock)}" required></div><div class="field"><label>Barcode / SKU</label><input class="input" name="sku" value="${esc(p.sku)}" placeholder="Auto generate if blank"></div><div class="field"><label>Rack / Shelf</label><input class="input" name="rack" value="${esc(p.rack || '')}" placeholder="L-1"></div><div class="field"><label>Warranty</label><select class="select" name="warranty">${['No Warranty','7 Days','15 Days','1 Month','3 Months','6 Months','1 Year'].map(x=>html`<option ${String(p.warranty || 'No Warranty')===x?'selected':''}>${x}</option>`).join('')}</select></div><div class="field form-span-2"><label>Notes</label><textarea class="textarea" name="notes" placeholder="Supplier, charger, battery or warranty notes">${esc(p.notes || '')}</textarea></div></div><div class="form-actions"><button class="btn btn-secondary" type="button" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">${t(product ? 'Save Changes' : 'Save Product')} ${icon('arrowRight')}</button></div></form>`, true);
  }

  function customerModal() {
    openModal('Add Customer', 'Create a customer record for sales, credit and ledger tracking.', html`<form data-form="customer" novalidate><div class="form-grid"><div class="field"><label>Customer Name <span class="required">*</span></label><input class="input" name="name" required placeholder="Full name"></div><div class="field"><label>Mobile Number <span class="required">*</span></label><input class="input" name="phone" required placeholder="03XX-XXXXXXX"></div><div class="field"><label>Opening Balance</label><input class="input" name="balance" type="number" min="0" value="0"></div><div class="field"><label>Status</label><select class="select" name="status"><option>Paid</option><option>Credit</option></select></div></div><div class="form-actions"><button class="btn btn-secondary" type="button" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">Save Customer ${icon('arrowRight')}</button></div></form>`);
  }

  function supplierModal() {
    openModal('Add Supplier', 'Create a supplier record for purchase bills and payable ledger.', html`<form data-form="supplier" novalidate><div class="form-grid"><div class="field"><label>Supplier Name <span class="required">*</span></label><input class="input" name="name" required placeholder="Supplier or company name"></div><div class="field"><label>Mobile Number <span class="required">*</span></label><input class="input" name="phone" required placeholder="03XX-XXXXXXX"></div><div class="field"><label>City</label><input class="input" name="city" placeholder="City"></div><div class="field"><label>Opening Payable</label><input class="input" name="payable" type="number" min="0" value="0"></div></div><div class="form-actions"><button class="btn btn-secondary" type="button" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">Save Supplier ${icon('arrowRight')}</button></div></form>`);
  }

  function repairModal(repair = null) {
    const r = repair || { customer:'', phone:'', device:'', issue:'', cost:'', technician:'', date:today(), status:'Pending' };
    openModal(repair ? 'Repair Job Details' : 'Add Repair Job', repair ? 'Update the repair status, cost or technician assignment.' : 'Record a device repair with estimate and expected delivery.', html`<form data-form="repair" novalidate><input type="hidden" name="id" value="${esc(repair?.id || '')}"><div class="form-grid"><div class="field"><label>Customer Name <span class="required">*</span></label><input class="input" name="customer" value="${esc(r.customer)}" required></div><div class="field"><label>Mobile Number <span class="required">*</span></label><input class="input" name="phone" value="${esc(r.phone)}" required></div><div class="field"><label>Device / Model <span class="required">*</span></label><input class="input" name="device" value="${esc(r.device)}" required></div><div class="field"><label>Problem Type <span class="required">*</span></label><input class="input" name="issue" value="${esc(r.issue)}" required placeholder="e.g. Display Issue"></div><div class="field"><label>Estimated Cost <span class="required">*</span></label><input class="input" name="cost" type="number" min="0" value="${esc(r.cost)}" required></div><div class="field"><label>Technician</label><input class="input" name="technician" value="${esc(r.technician || '')}" placeholder="Technician name (optional)"></div><div class="field"><label>Expected Delivery</label><input class="input" name="date" type="date" value="${esc(r.date)}"></div><div class="field"><label>Status</label><select class="select" name="status">${['Pending','In Progress','Ready','Job Done','Delivered'].map(x=>html`<option ${x===r.status?'selected':''}>${x}</option>`).join('')}</select><small class="field-hint">Thermal receipt is available when the job is marked Done or Delivered.</small></div></div><div class="form-actions">${repair && ['Ready','Job Done','Delivered'].includes(r.status) ? html`<button class="btn btn-secondary" type="button" data-action="repair-receipt" data-id="${esc(r.id)}">${icon('printer')} Print Job Receipt</button>` : ''}<button class="btn btn-secondary" type="button" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">${t(repair ? 'Update Repair Job' : 'Save Repair Job')} ${icon('arrowRight')}</button></div></form>`);
  }

  function installmentModal() {
    openModal('Add Installment', 'Create a structured monthly payment plan for a customer.', html`<form data-form="installment" novalidate><div class="form-grid"><div class="field"><label>Customer Name <span class="required">*</span></label><select class="select" name="customer" required>${state.customers.map(c=>html`<option>${esc(c.name)}</option>`).join('')}</select></div><div class="field"><label>Mobile / Product <span class="required">*</span></label><select class="select" name="product" required>${state.products.filter(p=>p.stock>0).map(p=>html`<option>${esc(p.name)}</option>`).join('')}</select></div><div class="field"><label>Total Price <span class="required">*</span></label><input class="input" name="total" type="number" min="1" placeholder="Enter agreed price" required></div><div class="field"><label>Advance Payment</label><input class="input" name="advance" type="number" min="0" value="0"></div><div class="field"><label>Months <span class="required">*</span></label><select class="select" name="months"><option>3</option><option selected>6</option><option>9</option><option>12</option></select></div><div class="field"><label>First Due Date <span class="required">*</span></label><input class="input" name="date" type="date" value="${today()}" required></div></div><div class="form-actions"><button class="btn btn-secondary" type="button" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">Create Installment ${icon('arrowRight')}</button></div></form>`);
  }

  function receivePaymentModal(installment = null) {
    const i = installment || state.installments.find(x=>x.status==='Due Today') || state.installments[0];
    if (!i) return notify('No installment found', 'Create an installment record first.', 'error');
    openModal('Receive Monthly Payment', `Collect the pending amount from ${i.customer}.`, html`<form data-form="receive-payment" novalidate><input type="hidden" name="id" value="${i.id}"><div class="card" style="padding:14px;background:var(--surface-soft);margin-bottom:16px"><div class="summary-row"><span>Customer</span><strong>${esc(i.customer)}</strong></div><div class="summary-row"><span>Product</span><strong>${esc(i.product)}</strong></div><div class="summary-row total"><span>Due Amount</span><strong>${money(i.due)}</strong></div></div><div class="form-grid"><div class="field"><label>Amount <span class="required">*</span></label><input class="input" name="amount" type="number" min="1" max="${i.due}" value="${i.due}" required></div><div class="field"><label>Payment Method</label><select class="select" name="method"><option>Cash</option><option>EasyPaisa In</option><option>EasyPaisa Out</option><option>JazzCash In</option><option>JazzCash Out</option><option>Bank Transfer</option></select></div><div class="field"><label>Payment Date</label><input class="input" name="date" type="date" value="${today()}"></div><div class="field"><label>Notes</label><input class="input" name="notes" placeholder="Optional note"></div></div><div class="form-actions"><button class="btn btn-secondary" type="button" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">Receive Payment ${icon('arrowRight')}</button></div></form>`);
  }

  function ledgerModal(type, record) {
    const isSupplier = type === 'supplier';
    const amount = isSupplier ? record.payable : record.balance;
    openModal(`${isSupplier ? 'Supplier' : 'Customer'} Balance`, `${esc(record.name)} · ${esc(record.phone)}`, html`<div class="summary-row"><span>Recorded ${isSupplier ? 'payable' : 'balance'}</span><strong>${money(amount)}</strong></div><p>This is the current recorded balance. Transaction history is not available in this view.</p><div class="form-actions"><button class="btn btn-secondary" data-action="close-modal">Close</button></div>`);

  }

  function ewalletModal(wallet = 'EasyPaisa') {
    const selected = wallet || 'EasyPaisa';
    openModal('EasyPaisa / JazzCash In-Out', 'Record wallet cash-in, cash-out, supplier payment or customer receipt.', html`<form data-form="ewallet" novalidate><div class="form-grid three"><div class="field"><label>Wallet</label><select class="select" name="wallet"><option ${selected==='EasyPaisa'?'selected':''}>EasyPaisa</option><option ${selected==='JazzCash'?'selected':''}>JazzCash</option></select></div><div class="field"><label>Type</label><select class="select" name="direction"><option>In</option><option>Out</option></select></div><div class="field"><label>Amount <span class="required">*</span></label><input class="input" name="amount" type="number" min="1" required></div><div class="field"><label>Fee / Charges</label><input class="input" name="fee" type="number" min="0" value="0"></div><div class="field"><label>Customer / Party</label><input class="input" name="customer" value="Walk-in Customer"></div><div class="field"><label>Mobile Number</label><input class="input" name="mobile" placeholder="03XX-XXXXXXX"></div><div class="field"><label>Date</label><input class="input" name="date" type="date" value="${today()}"></div><div class="field" style="grid-column:span 2"><label>Note</label><input class="input" name="note" placeholder="Purpose / reference"></div></div><div class="form-actions"><button class="btn btn-secondary" type="button" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">Save & Print ${icon('printer')}</button></div></form>`);
  }

  function ewalletReceiptModal(w) {
    if (!w) return;
    openModal('Wallet Thermal Receipt (80mm)', 'Print customer or office copy.', html`<div class="invoice-paper thermal-design">${receiptHeader(`${w.wallet.toUpperCase()} ${w.direction.toUpperCase()}`, 'Wallet Receipt')}<div class="receipt-meta"><div><span>Receipt</span><b>${esc(w.id)}</b></div><div><span>Date</span><b>${prettyDate(w.date)}</b></div><div><span>Type</span><b>${esc(w.direction)}</b></div></div><div class="invoice-divider"></div><div class="invoice-line"><span>Party</span><b>${esc(w.customer || 'Walk-in Customer')}</b></div><div class="invoice-line"><span>Mobile</span><b>${esc(w.mobile || '—')}</b></div><div class="invoice-line"><span>Amount</span><b>${money(w.amount)}</b></div><div class="invoice-line"><span>Fee</span><b>${money(w.fee || 0)}</b></div><div class="invoice-line invoice-total"><span>Total</span><b>${money(Number(w.amount || 0) + Number(w.fee || 0))}</b></div><div class="invoice-divider"></div><div class="receipt-footer"><strong>Thank you</strong><span>${esc(w.note || 'Wallet transaction recorded.')}</span></div></div><div class="form-actions"><button class="btn btn-secondary" data-action="print-ewallet-receipt">${icon('printer')} Print (80mm)</button><button class="btn btn-primary" data-action="close-modal">Done</button></div>`);
  }

  function invoiceModal(invoice) {
    if (!invoice) return;
    const items = compactInvoiceItems(invoice.items);
    openModal('Thermal Invoice (80mm)', 'Compact premium customer receipt.', html`<div class="invoice-paper thermal-design">${receiptHeader('SALE INVOICE', 'Customer Copy')}<div class="receipt-meta receipt-meta-compact"><div><span>Invoice</span><b>${esc(invoice.id)}</b></div><div><span>Date</span><b>${prettyDate(invoice.date)}</b></div><div class="receipt-customer"><span>Customer</span><b>${esc(compactReceiptItemName(invoice.customer || 'Walk-in Customer', 25))}</b></div></div><div class="invoice-divider"></div><div class="invoice-items-head"><span>Item / Qty</span><span>Amount</span></div>${items.visible.map(i=>html`<div class="invoice-line"><span>${esc(compactReceiptItemName(i.name))} ×${i.qty}</span><b>${money(i.price * i.qty)}</b></div>`).join('')}${items.hidden ? html`<div class="receipt-more-items">+ ${items.hidden} more item${items.hidden === 1 ? '' : 's'} — see app invoice</div>` : ''}<div class="invoice-divider"></div><div class="invoice-line"><span>Payment</span><b>${esc(invoice.payment)}</b></div><div class="invoice-line invoice-total"><span>Total</span><b>${money(invoice.total)}</b></div><div class="invoice-divider"></div><div class="receipt-footer"><strong>Thank you for shopping with us</strong><span>Exchange / warranty according to shop policy.</span></div></div><div class="form-actions"><button class="btn btn-secondary" data-action="print-invoice">${icon('printer')} Print (80mm)</button><button class="btn btn-secondary" data-action="share-invoice">${icon('share')} Share</button><button class="btn btn-primary" data-action="close-modal">Done</button></div>`);
  }


  function repairReceiptModal(repair) {
    if (!repair) return notify('Repair not found', 'This repair job is not available.', 'error');
    openModal('Repair Job Thermal Receipt (80mm)', 'Print customer copy when the job is done or delivered.', html`<div class="invoice-paper thermal-design repair-receipt">${receiptHeader('REPAIR RECEIPT', 'Customer Copy')}<div class="receipt-meta"><div><span>Job #</span><b>${esc(repair.id)}</b></div><div><span>Date</span><b>${prettyDate(repair.date)}</b></div><div><span>Status</span><b>${esc(repair.status)}</b></div></div><div class="invoice-divider"></div><div class="invoice-line"><span>Customer</span><b>${esc(repair.customer)}</b></div><div class="invoice-line"><span>Mobile</span><b>${esc(repair.phone)}</b></div><div class="invoice-line"><span>Device</span><b>${esc(repair.device)}</b></div><div class="invoice-line"><span>Problem</span><b>${esc(repair.issue)}</b></div><div class="invoice-line"><span>Technician</span><b>${esc(repair.technician)}</b></div><div class="invoice-divider"></div><div class="invoice-line invoice-total"><span>Repair Charges</span><b>${money(repair.cost)}</b></div><div class="invoice-divider"></div><div class="receipt-footer"><strong>Device received by customer</strong><span>Warranty only on mentioned repair work.</span></div><div class="signature-line"><span>Customer Sign</span><span>Shop Sign</span></div></div><div class="form-actions"><button class="btn btn-secondary" data-action="print-repair-receipt">${icon('printer')} Print (80mm)</button><button class="btn btn-secondary" data-action="repair-details" data-id="${esc(repair.id)}">Edit Job</button><button class="btn btn-primary" data-action="close-modal">Done</button></div>`);
  }

  async function refreshShopAdminControl() {
    if (!apiConfigured || !window.skybarechDesktop?.shopAdminControl) return null;
    try {
      const control = await window.skybarechDesktop.shopAdminControl();
      const users = Array.isArray(control?.users) ? control.users : [];
      const branches = Array.isArray(control?.branches) ? control.branches : [];
      state.session = { ...(state.session || {}), branches };
      state.staff = users
        .filter((user) => String(user.role || '').toLowerCase() !== 'super_admin')
        .map((user) => ({
          id: user.id,
          name: user.fullName || user.full_name || 'Staff User',
          role: ({ branch_manager: 'Branch Manager', cashier: 'Cashier', technician: 'Technician', accountant: 'Accountant' })[String(user.role || '').toLowerCase()] || user.role,
          roleKey: String(user.role || '').toLowerCase(),
          phone: user.mobile || '',
          email: user.email || '',
          branchId: user.branchId ?? user.branch_id ?? null,
          status: String(user.status || 'active').replace(/^./, (c) => c.toUpperCase()),
          sales: 0
        }));
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        ...JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}'),
        staff: state.staff,
        session: state.session
      }));
      renderApp();
      return control;
    } catch (error) {
      console.warn('Shop admin control refresh failed:', error);
      return null;
    }
  }

  function completeDesktopLogin(shop = null, message = '') {
    const validPages = Object.keys(pageMeta);
    if (!validPages.includes(state.activePage)) state.activePage = 'dashboard';
    if (shop) applyShopProfile(shop, 'super_admin_activation');
    else shopProfile();
    state.session = { ...(state.session || {}), revoked: false, revokedReason: '', revokedAt: null };
    localPinBudget(false, true);
    state.isLoggedIn = true;
    state.authMode = 'login';
    splashVisible = false;
    persist();
    renderApp();
    notify(`Welcome back, ${shopOwnerName()}`, message || `${shopDisplayName()} dashboard is ready.`);
    refreshShopAdminControl();
  }
  function laptopFields(p) {
    const models = LAPTOP_MODELS[p.brand] || [];
    return html`<div class="field"><label>Laptop Brand <span class="required">*</span></label><select class="select" name="brand" required>${LAPTOP_BRANDS.map(x=>html`<option ${String(p.brand || 'HP')===x?'selected':''}>${x}</option>`).join('')}</select></div>
      <div class="field"><label>Series / Model <span class="required">*</span></label><input class="input" name="model" list="laptop-models" value="${esc(p.model || '')}" placeholder="EliteBook 840 G8" required><datalist id="laptop-models">${models.map(x=>html`<option value="${esc(x)}"></option>`).join('')}</datalist></div>
      <div class="field"><label>Processor</label><input class="input" name="processor" value="${esc(p.processor || '')}" placeholder="Intel Core i5-1135G7"></div>
      <div class="field"><label>Generation</label><select class="select" name="generation">${['—','6th Gen','7th Gen','8th Gen','9th Gen','10th Gen','11th Gen','12th Gen','13th Gen','14th Gen','Ryzen 3000','Ryzen 4000','Ryzen 5000','Ryzen 6000','Ryzen 7000','Apple M1','Apple M2','Apple M3','Apple M4'].map(x=>html`<option ${String(p.generation || '—')===x?'selected':''}>${x}</option>`).join('')}</select></div>
      <div class="field"><label>RAM</label><select class="select" name="ram">${['4GB','8GB','16GB','32GB','64GB'].map(x=>html`<option ${String(p.ram || '16GB')===x?'selected':''}>${x}</option>`).join('')}</select></div>
      <div class="field"><label>Storage</label><div class="input-wrap"><select class="select" name="storageType">${['SSD','NVMe SSD','HDD','eMMC'].map(x=>html`<option ${String(p.storageType || 'SSD')===x?'selected':''}>${x}</option>`).join('')}</select><select class="select" name="storage">${['128GB','256GB','512GB','1TB','2TB'].map(x=>html`<option ${String(p.storage || '512GB')===x?'selected':''}>${x}</option>`).join('')}</select></div></div>
      <div class="field"><label>Graphics</label><input class="input" name="graphics" value="${esc(p.graphics || '')}" placeholder="Intel Iris Xe / NVIDIA GTX"></div>
      <div class="field"><label>Screen Size</label><select class="select" name="screenSize">${['12.5 inch','13.3 inch','14 inch','15.6 inch','16 inch','17.3 inch'].map(x=>html`<option ${String(p.screenSize || '14 inch')===x?'selected':''}>${x}</option>`).join('')}</select></div>
      <div class="field"><label>Operating System</label><select class="select" name="operatingSystem">${['Windows 11','Windows 10','macOS','Linux','No OS'].map(x=>html`<option ${String(p.operatingSystem || 'Windows 11')===x?'selected':''}>${x}</option>`).join('')}</select></div>
      <div class="field"><label>Condition</label><select class="select" name="quality">${['New','Used','Open Box','Refurbished'].map(x=>html`<option ${String(p.quality || 'Used')===x?'selected':''}>${x}</option>`).join('')}</select></div>
      <div class="field"><label>Battery Health</label><input class="input" name="batteryHealth" value="${esc(p.batteryHealth || '')}" placeholder="85% / Good"></div>
      <div class="field"><label>Serial Number</label><input class="input" name="serialNumber" value="${esc(p.serialNumber || '')}" placeholder="Laptop serial number"></div>`;
  }

  async function finishOnlineLogin(online, pin, backupNote = '') {
    const synced = await window.skybarechDesktop.syncNow();
    if (!synced?.success) {
      notify('Shop data unavailable', synced?.error || 'Sign in again to reconnect sync.', 'error');
      return false;
    }
    await hydrateFromDesktopStore(false);
    const profile = normalizeShopProfile({...shopProfile(), ...online.shop,
      shopName: online.shop?.name, ownerMobile: online.user?.mobile,
      ownerName: online.shop?.owner_name || online.user?.full_name});
    completeDesktopLogin(await withShopPassword(profile, pin), `Your Android shop data is loaded on Desktop.${backupNote}`);
    return true;
  }

  function showLoginShopSwitchDialog(online, username, password) {
    const conflict = online.deviceConflict;
    pendingLoginShopSwitch = { online, username, password };
    openModal('Connect this Desktop to your shop?', 'Your PIN is correct. An older local cache must be backed up before the Android shop can be loaded.', html`
      <div class="confirm-box shop-switch-confirm">
        <div class="confirm-icon">${icon('shield','icon-xl')}</div>
        <strong>${esc(conflict.currentShopName)} → ${esc(online.shop?.name || 'Connected Android shop')}</strong>
        <p>${esc(`${conflict.unsyncedChanges} waiting or failed local change(s) will be preserved in a recovery backup.`)}</p>
        <div class="connection-summary">
          <span>Old local shop</span><strong>${esc(conflict.currentShopId)}</strong>
          <span>Cloud shop</span><strong>${esc(conflict.targetShopId)}</strong>
          <span>Saved records</span><strong>${n(conflict.localRecords)}</strong>
        </div>
        <p class="field-hint">Your old Cloud shop is not deleted. After backup, this Desktop will load the same shop data used on Android.</p>
        <div class="confirm-actions"><button class="btn btn-secondary" data-action="cancel-login-shop-switch">Cancel</button><button class="btn btn-primary" data-action="confirm-login-shop-switch">Back up & connect ${icon('arrowRight')}</button></div>
      </div>`);
  }

  async function submitLogin(form) {
    const data = Object.fromEntries(new FormData(form));
    if (!data.username || !data.password) return notify('Login required', 'Enter your owner mobile and PIN.', 'error');
    if (data.password.length !== 4 || !/^[0-9]{4}$/.test(data.password)) return notify('Invalid PIN', 'Enter exactly 4 digits.', 'error');
    if (!apiConfigured) return notify('Desktop setup incomplete', 'Administrator must set the production API once in src/config.js before creating the Windows installer.', 'error');
    if (localPinBudget()) return notify('Try again later', 'Too many failed PIN attempts. Try again in 15 minutes.', 'error');
    const button = form.querySelector('[data-action="desktop-login"], button[type="submit"]');
    if (button?.disabled) return;
    const buttonMarkup = button?.innerHTML;
    if (button) { button.disabled = true; button.textContent = t('Loading your shop…'); }
    try {
      const online = apiConfigured ? await window.skybarechDesktop.onlineLogin(apiBaseUrl, data.username, data.password) : null;
      if (online?.deviceConflict) {
        showLoginShopSwitchDialog(online, data.username, data.password);
        return;
      }
      if (online?.success) {
        return finishOnlineLogin(online, data.password);
      }
      if (online?.status || online?.authoritative) { if ([401,429].includes(online.status)) localPinBudget(true); return notify('Sign-in failed', online.message, 'error'); }
      // Offline fallback is only for the current, previously verified shop.
      const profile = shopProfile();
      if (sameMobile(profile.ownerMobile, data.username) && await verifyShopPassword(profile, data.password)
          && !state.session?.revoked && desktopSyncStatus.licenseStatus !== 'revoked') {
        return completeDesktopLogin(profile, 'Offline — showing data saved on this device. Updates resume when connected.');
      }
      localPinBudget(true);
      notify('Connection required', online?.message || 'Connect to the internet for the first sign-in on this device.', 'error');
    } catch (error) { notify('Sign-in failed', error.message || 'Could not load your shop.', 'error'); }
    finally { if (button && button.isConnected) { button.disabled = false; button.innerHTML = buttonMarkup; } }
  }

  function showActivationError(title, message, type = 'error') {
    const box = document.querySelector('[data-activation-error]');
    const text = `${t(title)}: ${t(message)}`;
    if (box) {
      box.hidden = false;
      box.className = `activation-error-box ${type}`;
      box.innerHTML = html`<strong>${esc(t(title))}</strong><span>${esc(t(message))}</span>`;
    }
    notify(title, message, type);
  }

  function clearActivationError() {
    const box = document.querySelector('[data-activation-error]');
    if (box) {
      box.hidden = true;
      box.innerHTML = '';
    }
  }

  function continueVerifiedActivation(verified, code, mobile, password, switchResult = null) {
    const activation = verified.activation;
    pendingRemoteActivation = {
      activationId: activation.activation_id,
      shopId: activation.shop_id,
      code,
      mobile,
      temporaryPassword: password,
    };
    const matchedOnline = normalizeShopProfile({
      id: activation.shop_id,
      shopName: activation.shop_name,
      ownerMobile: mobile,
      activationCode: code,
      androidPassword: '',
      plan: 'Online',
      status: 'active',
    });
    state.shop = matchedOnline;
    state.session = {shop_id: matchedOnline.id, source: 'activation_pending'};
    state.isLoggedIn = false;
    state.authMode = 'password';
    state.firstActivationPassword = true;
    state.activePage = 'dashboard';
    splashVisible = false;
    renderApp();
    const backupNote = switchResult?.backupPath ? ' Previous shop data was backed up safely.' : '';
    notify('Activation verified', `${matchedOnline.shopName} verified. Choose a 4 digit PIN.${backupNote}`, 'success');
    return true;
  }

  function showShopSwitchDialog(verified, code, mobile, password) {
    const conflict = verified.deviceConflict;
    pendingShopSwitch = { verified, code, mobile, password };
    const warning = conflict.unsyncedChanges
      ? `${conflict.unsyncedChanges} unsynced change(s) are present. A protected local backup will be created before switching.`
      : 'A protected local backup will be created before switching.';
    openModal('Switch shop on this computer?', 'A different shop is already saved on this installation.', html`
      <div class="confirm-box shop-switch-confirm">
        <div class="confirm-icon">${icon('shield','icon-xl')}</div>
        <strong>${esc(conflict.currentShopName)} → ${esc(verified.activation.shop_name || 'New shop')}</strong>
        <p>${esc(warning)}</p>
        <div class="connection-summary">
          <span>Current shop</span><strong>${esc(conflict.currentShopId)}</strong>
          <span>Saved records</span><strong>${n(conflict.localRecords)}</strong>
          <span>Waiting / failed</span><strong>${n(conflict.unsyncedChanges)}</strong>
        </div>
        <p class="field-hint">Only this computer's old cache is replaced. The old shop's Cloud data is not deleted.</p>
        <div class="confirm-actions"><button class="btn btn-secondary" data-action="cancel-shop-switch">Keep current shop</button><button class="btn btn-primary" data-action="confirm-shop-switch">Back up & switch shop ${icon('arrowRight')}</button></div>
      </div>`);
  }

  async function submitActivation(form) {
    try {
      if (!form) return showActivationError('Activation form not found', 'Open the Activate Shop screen again and retry.');
      clearActivationError();
      const data = Object.fromEntries(new FormData(form));
      const code = cleanText(data.code);
      const mobile = cleanText(data.mobile);
      const password = cleanText(data.password);
      const shopName = cleanText(data.shopName);
      if (!code) return showActivationError('Activation Code required', 'Activation Code is required.');
      if (!mobile) return showActivationError('Owner Mobile required', 'Owner mobile number is required.');
      if (cleanMobile(mobile).length < 10) return showActivationError('Mobile number invalid', 'Owner mobile must be at least 10 digits. Example: 03001234567');
      if (!password) return showActivationError('Temporary PIN required', 'Temporary PIN is required.');

      if (apiConfigured) {
        const verified = await window.skybarechDesktop.verifyActivation(apiBaseUrl, code, mobile, password);
        if (verified.deviceConflict) { showShopSwitchDialog(verified, code, mobile, password); return true; }
        return continueVerifiedActivation(verified, code, mobile, password);
      }

      return showActivationError('Server configuration required', 'Configure the HTTPS API before activating a production shop.');
      let matched = findActivationShop(code, mobile, password);
      if (!matched) {
        matched = createLocalActivationShop({ ...data, code, mobile, password, shopName });
      }
      const activated = applyShopProfile(matched, matched ? 'activation_code_direct' : 'activation_code_local');
      state.isLoggedIn = false;
      state.authMode = 'password';
      state.firstActivationPassword = true;
      state.activePage = 'dashboard';
      splashVisible = false;
      persist();
      renderApp();
      notify('Shop Activated', `${activated.shopName} activated. Create your new password.`, 'success');
      return true;
    } catch (err) {
      console.error('Activation failed:', err);
      showActivationError('Activation system error', err?.message || 'Unknown error. Reset local data and try again.');
      return false;
    }
  }

  async function submitPassword(form) {
    const data = Object.fromEntries(new FormData(form));
    const newPassword = cleanText(data.newPassword);
    if (!/^[0-9]{4}$/.test(newPassword) || newPassword.length !== 4) return notify('Invalid PIN', 'Enter exactly the selected number of digits.', 'error');
    if (newPassword !== cleanText(data.confirmPassword)) return notify('PINs do not match', 'Enter the same PIN again.', 'error');
    if (state.firstActivationPassword && apiConfigured && !pendingRemoteActivation) return notify('Activation required', 'Verify online activation again before setting a password.', 'error');
    if (state.firstActivationPassword && apiConfigured && pendingRemoteActivation) {
      try {
        await window.skybarechDesktop.completeActivation(apiBaseUrl, pendingRemoteActivation, newPassword);
        pendingRemoteActivation = null;
        await hydrateFromDesktopStore(false);
        state.firstActivationPassword = true;
        await refreshDesktopSyncStatus();
      } catch (error) {
        return notify('Activation failed', error.message || 'Cloud activation could not be completed.', 'error');
      }
    }
    if (!state.firstActivationPassword) {
      if (!state.isLoggedIn) return notify('Sign in required', 'Ask the administrator to reset your activation if you forgot your password.', 'error');
      if (apiConfigured) {
        try { await window.skybarechDesktop.changePassword(data.currentPassword, newPassword); }
        catch (error) { return notify('Password update failed', error.message, 'error'); }
      } else if (!(await verifyShopPassword(shopProfile(), data.currentPassword))) {
        return notify('Incorrect password', 'Enter the current password.', 'error');
      }
    } else if (apiConfigured && pendingRemoteActivation) {
      return notify('Activation incomplete', 'Complete online activation first.', 'error');
    }
    const updatedShop = await withShopPassword(shopProfile(), newPassword);
    applyShopProfile(updatedShop, 'password_set');
    state.firstActivationPassword = false;
    state.isLoggedIn = true;
    state.authMode = 'login';
    state.activePage = 'dashboard';
    persist();
    renderApp();
    notify('PIN saved', 'Use the same PIN on desktop and Android.', 'success');
  }

  function submitShopProfile(form) {
    const data = Object.fromEntries(new FormData(form));
    const current = shopProfile();
    const updated = normalizeShopProfile({
      ...current,
      ...data,
      shopName: cleanText(data.shopName) || current.shopName,
      ownerName: cleanText(data.ownerName) || current.ownerName,
      ownerMobile: cleanText(data.ownerMobile) || current.ownerMobile,
      ownerMobile2: cleanText(data.ownerMobile2),
      city: cleanText(data.city),
      address: cleanText(data.address),
      invoicePrefix: cleanText(data.invoicePrefix) || current.invoicePrefix,
      shopCode: cleanText(data.shopCode) || current.shopCode,
      activationCode: cleanText(data.activationCode) || current.activationCode,
      logo: cleanText(data.logo)
    });
    applyShopProfile(updated, 'settings_profile');
    renderApp();
    notify('Shop profile saved', 'Updated shop details will appear on thermal receipts, sidebar and invoices.', 'success');
    return true;
  }

  function submitProduct(form) {
    const d = Object.fromEntries(new FormData(form));
    const category = d.category || 'Smartphone';
    const isAcc = ACCESSORY_CATEGORIES.includes(category);
    const isSpare = SPARE_PART_CATEGORIES.includes(category);
    if (!d.name || !category || !d.price || !d.cost || d.stock === '') return notify('Required fields missing', 'Product name, category, purchase price, sale price and quantity are required.', 'error');
    const variant = (d.variantManual || d.variant || d.ram || 'Standard').trim();
    const sku = d.sku || makeSku(d.name, category, d.brand);
    const item = {
      id: d.id || makeId('P'),
      shop_id: d.shop_id || currentShopId(),
      name: d.name,
      brand: d.brand || (isAcc ? 'Other' : ''),
      model: d.model || '',
      processor: d.processor || '',
      generation: d.generation || '',
      storageType: d.storageType || '',
      graphics: d.graphics || '',
      screenSize: d.screenSize || '',
      operatingSystem: d.operatingSystem || '',
      batteryHealth: d.batteryHealth || '',
      serialNumber: d.serialNumber || '',
      compatibleModels: d.compatibleModels || '',
      category,
      variant,
      ram: (isAcc || isSpare) ? variant : (d.ram || '—'),
      storage: (isAcc || isSpare) ? '—' : (d.storage || '—'),
      color: d.color || '',
      quality: d.quality || '',
      warranty: d.warranty || 'No Warranty',
      wholesalePrice: Number(d.wholesalePrice || 0),
      minStock: Number(d.minStock || 0),
      rack: d.rack || '',
      notes: d.notes || '',
      cost:Number(d.cost),
      price:Number(d.price),
      stock:Number(d.stock || 0),
      sku,
      image:d.image || '',
      low:Number(d.stock || 0) <= Number(d.minStock || 7)
    };
    if (d.id) state.products = state.products.map(p => p.id === d.id ? { ...p, ...item } : p);
    else state.products.unshift(item);
    persist(); closeModal(); renderApp(); notify(d.id ? 'Product updated' : 'Product added', `${item.name} ${isAcc ? 'accessories tab' : isSpare ? 'spare parts tab' : 'inventory'} saved successfully.`);
  }

  function submitPurchase(form) {
    const d = Object.fromEntries(new FormData(form));
    if (!d.brand || !d.model || !d.cost || !d.price) return notify('Required fields missing', 'Complete brand, model, purchase price and sale price.', 'error');
    let imeis = [];
    try { imeis = JSON.parse(d.imeiList || '[]').filter(Boolean); } catch (e) { imeis = []; }
    [d.imei1, d.imei2].forEach(code => {
      const clean = String(code || '').replace(/\D/g, '');
      if (clean && !imeis.includes(clean)) imeis.push(clean);
    });
    imeis = [...new Set(imeis)].filter(code => code.length >= 10 && code.length <= 18);
    const existingImeis = allKnownImeis();
    const duplicateImeis = imeis.filter(code => existingImeis.has(code));
    if (duplicateImeis.length) return notify('Duplicate IMEI found', `${duplicateImeis[0]} already exists in stock.`, 'error');
    const stockQty = Math.max(Number(d.stock || 1), imeis.length || 1);
    const product = {
      id:makeId('P'),
      name:`${d.brand} ${d.model}`,
      brand:d.brand,
      model:d.model,
      category:d.category,
      ram:d.ram,
      storage:d.storage,
      cost:Number(d.cost),
      price:Number(d.price),
      stock:stockQty,
      sku:d.sku || `${d.brand.slice(0,3).toUpperCase()}-${Date.now().toString().slice(-5)}`,
      imeis,
      imei1: imeis[0] || d.imei1 || '',
      imei2: imeis[1] || d.imei2 || '',
      cnic: d.cnic || '',
      cnicFront: d.cnicFront || '',
      cnicBack: d.cnicBack || '',
      purchaseInvoice: d.purchaseInvoice || '',
      documentCount: [d.cnicFront, d.cnicBack, d.purchaseInvoice].filter(Boolean).length,
      low:stockQty <= 7
    };
    state.products.unshift(product);
    state.stockMoves = [{ id: makeId('MOV'), product: product.name, type: 'Purchase', qty: stockQty, date: today(), by: shopOwnerName(), imeis }, ...(state.stockMoves || [])];
    const supplier = state.suppliers.find(s => s.name === d.supplier);
    if (supplier) supplier.payable += Number(d.cost) * product.stock;
    persist(); form.reset(); renderApp(); notify('Purchase saved', `${product.name} added to stock${imeis.length ? ` · ${imeis.length} IMEI saved` : ''}.`);
  }

  function submitSale(form) {
    const d = Object.fromEntries(new FormData(form));
    const product = state.products.find(p => p.id === d.product);
    if (!product) return notify('Choose a product', 'Select an available product before generating invoice.', 'error');
    const price = Number(d.salePrice || product.price), discount = Number(d.discount || 0);
    if (product.stock < 1) return notify('Out of stock', 'This product has no available stock.', 'error');
    product.stock -= 1;
    const customerName = d.customer || 'Walk-in Customer';
    const inv = { id:makeId('INV'), customer:customerName, total:Math.max(0,price-discount), payment:d.payment, date:today(), items:[{name:product.name,qty:1,price}] };
    const customerRecord = state.customers.find(c => c.name === customerName);
    if (customerRecord && customerName !== 'Walk-in Customer') customerRecord.purchases = Number(customerRecord.purchases || 0) + 1;
    state.invoices.unshift(inv);
    persist(); invoiceModal(inv); renderApp(); notify('Invoice created', `${inv.id} was created successfully.`);
  }

  function submitRepair(form) {
    const d = Object.fromEntries(new FormData(form));
    if (!d.customer || !d.phone || !d.device || !d.issue || !d.cost) return notify('Required details missing', 'Complete the customer, device, issue and estimated cost.', 'error');
    const job = { id:d.id || `RJ-${Date.now().toString().slice(-8)}`, customer:d.customer, phone:d.phone, device:d.device, issue:d.issue, cost:Number(d.cost), technician:d.technician, date:d.date || today(), status:d.status };
    if (d.id) state.repairs = state.repairs.map(r=>r.id===d.id?job:r);
    else state.repairs.unshift(job);
    persist(); closeModal(); renderApp(); notify(d.id ? 'Repair updated' : 'Repair job created', `${job.id} is now ${job.status}.`);
    if (['Job Done', 'Delivered'].includes(job.status)) repairReceiptModal(job);
  }

  function submitCustomer(form) {
    const d = Object.fromEntries(new FormData(form));
    if (!d.name || !d.phone) return notify('Customer details missing', 'Enter customer name and mobile number.', 'error');
    state.customers.unshift({ id:makeId('C'), name:d.name, phone:d.phone, balance:Number(d.balance||0), status:d.status || 'Paid', purchases:0 });
    persist(); closeModal(); renderApp(); notify('Customer added', `${d.name} is now in your customer list.`);
  }

  function submitSupplier(form) {
    const d = Object.fromEntries(new FormData(form));
    if (!d.name || !d.phone) return notify('Supplier details missing', 'Enter supplier name and mobile number.', 'error');
    state.suppliers.unshift({ id:makeId('S'), name:d.name, phone:d.phone, city:d.city || '—', payable:Number(d.payable||0), status:Number(d.payable||0) ? 'Payable' : 'Paid' });
    persist(); closeModal(); renderApp(); notify('Supplier added', `${d.name} is now in your supplier list.`);
  }

  function submitInstallment(form) {
    const d = Object.fromEntries(new FormData(form));
    const customer = state.customers.find(c => c.name === d.customer);
    const total = Number(d.total), advance = Number(d.advance || 0), months = Number(d.months || 6);
    const due = Math.ceil((total - advance) / months);
    const i = { id:makeId('I'), customer:d.customer, phone:customer?.phone || '', product:d.product, total, advance, months, due, date:d.date, status:d.date === today() ? 'Due Today' : 'Due in 3 Days' };
    state.installments.unshift(i); persist(); closeModal(); renderApp(); notify('Installment created', `${d.customer} has a monthly due of ${money(due)}.`);
  }

  function submitReceivePayment(form) {
    const d = Object.fromEntries(new FormData(form));
    const i = state.installments.find(x => x.id === d.id);
    if (!i) return notify('Installment not found', 'This record is no longer available.', 'error');
    const amount = Number(d.amount || 0);
    if (!amount || amount > i.due) return notify('Invalid amount', `Enter an amount up to ${money(i.due)}.`, 'error');
    i.due -= amount;
    i.status = i.due === 0 ? 'Paid' : 'Due Today';
    persist(); closeModal(); renderApp(); notify('Payment received', `${money(amount)} received from ${i.customer}.`);
  }


  function openExpenseModal() {
    openModal('Add Expense', 'Record daily shop expense.', html`<form data-form="expense" novalidate><div class="form-grid"><div class="field"><label>Category <span class="required">*</span></label><select class="select" name="category" required><option>Rent</option><option>Utility</option><option>Salary</option><option>Repair</option><option>Transport</option><option>Other</option></select></div><div class="field"><label>Amount <span class="required">*</span></label><input class="input" name="amount" type="number" min="1" required placeholder="Rs. 0"></div><div class="field"><label>Date</label><input class="input" name="date" type="date" value="${today()}"></div><div class="field"><label>Note</label><input class="input" name="note" placeholder="Optional note"></div></div><div class="form-actions"><button class="btn btn-secondary" type="button" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">Save Expense ${icon('arrowRight')}</button></div></form>`);
  }

  function submitEwallet(form) {
    const d = Object.fromEntries(new FormData(form));
    const amount = Number(d.amount || 0);
    if (!amount) return notify('Amount required', 'Enter a valid wallet transaction amount.', 'error');
    const tx = { id: makeId('EW'), wallet: d.wallet || 'EasyPaisa', direction: d.direction || 'In', amount, fee: Number(d.fee || 0), customer: d.customer || 'Walk-in Customer', mobile: d.mobile || '', note: d.note || '', date: d.date || today() };
    state.ewallets = [tx, ...(state.ewallets || [])];
    persist(); closeModal(); renderApp(); ewalletReceiptModal(tx); notify('Wallet transaction saved', `${tx.wallet} ${tx.direction} ${money(tx.amount)} recorded.`);
  }

  function submitExpense(form) {
    const d = Object.fromEntries(new FormData(form));
    if (!d.category || !d.amount) return notify('Expense missing', 'Category and amount are required.', 'error');
    state.expenses = [{ id: makeId('EXP'), category: d.category, amount: Number(d.amount || 0), date: d.date || today(), note: d.note || '' }, ...(state.expenses || [])];
    persist(); closeModal(); renderApp(); notify('Expense saved', `${money(d.amount)} expense recorded.`);
  }

  function openCashSessionModal() {
    const sales = state.invoices.filter(i => recordDateKey(i) === today()).reduce((sum, i) => sum + Number(i.total || 0), 0);
    const expenses = (state.expenses || []).filter(e => e.date === today()).reduce((sum, e) => sum + Number(e.amount || 0), 0);
    openModal('Cash Closing', 'Close daily counter cash.', html`<form data-form="cash-session" novalidate><div class="form-grid"><div class="field"><label>Opened By</label><input class="input" name="openedBy" value="${esc(shopOwnerName())}"></div><div class="field"><label>Opening Cash <span class="required">*</span></label><input class="input" name="opening" type="number" min="0" value="0" required></div><div class="field"><label>Sales Cash</label><input class="input" name="sales" type="number" min="0" value="${sales}"></div><div class="field"><label>Expenses</label><input class="input" name="expenses" type="number" min="0" value="${expenses}"></div><div class="field"><label>Closing Cash</label><input class="input" name="closing" type="number" min="0" value="${sales - expenses}"></div><div class="field"><label>Date</label><input class="input" name="date" type="date" value="${today()}"></div></div><div class="form-actions"><button class="btn btn-secondary" type="button" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">Save Closing ${icon('arrowRight')}</button></div></form>`);
  }

  function submitCashSession(form) {
    const d = Object.fromEntries(new FormData(form));
    if (d.opening === '') return notify('Opening cash missing', 'Enter opening cash.', 'error');
    const opening = Number(d.opening || 0), sales = Number(d.sales || 0), expenses = Number(d.expenses || 0);
    const closing = d.closing === '' ? opening + sales - expenses : Number(d.closing || 0);
    state.cashSessions = [{ id: makeId('CASH'), openedBy: d.openedBy || shopOwnerName(), opening, sales, expenses, closing, status: 'Closed', date: d.date || today() }, ...(state.cashSessions || [])];
    persist(); closeModal(); renderApp(); notify('Cash closing saved', `${money(closing)} closing balance recorded.`);
  }

  function submitSupport(form) {
    const d = Object.fromEntries(new FormData(form));
    if (!d.type || !d.priority || !d.message.trim()) return notify('Request incomplete', 'Choose issue type, priority and write your message.', 'error');
    state.supportRequests.unshift({ id:`SR-${1000 + state.supportRequests.length + 1}`, type:d.type, priority:d.priority, message:d.message.trim(), status:'Open', date:today() });
    persist(); form.reset(); renderApp(); notify('Support request sent', 'Your request has been added to the help center.');
  }

  function downloadFile(filename, content, type = 'application/json') {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([content], { type }));
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 800);
  }


  function purchaseImeiPanel(el) {
    return el?.closest('[data-purchase-imei-panel]') || document.querySelector('[data-purchase-imei-panel]');
  }

  function readPurchaseImeis(panel) {
    const hidden = panel?.querySelector('[data-purchase-imei-list-value]');
    if (!hidden || !hidden.value) return [];
    try { return JSON.parse(hidden.value).filter(Boolean); } catch (e) { return []; }
  }

  function writePurchaseImeis(panel, imeis) {
    if (!panel) return;
    const clean = [...new Set((imeis || []).map(x => String(x || '').replace(/\D/g, '')).filter(x => x.length >= 10 && x.length <= 18))];
    const hidden = panel.querySelector('[data-purchase-imei-list-value]');
    const list = panel.querySelector('[data-purchase-imei-list]');
    const form = panel.closest('form');
    if (hidden) hidden.value = JSON.stringify(clean);
    if (list) {
      list.innerHTML = clean.length
        ? clean.map(code => html`<span class="imei-chip">${icon('barcode')} ${esc(code)} <button type="button" data-action="purchase-remove-imei" data-imei="${esc(code)}">×</button></span>`).join('')
        : html`<span class="empty-mini">No IMEI added yet.</span>`;
    }
    const stockInput = form?.querySelector('[name="stock"]');
    const imei1 = form?.querySelector('[name="imei1"]');
    const imei2 = form?.querySelector('[name="imei2"]');
    if (stockInput && clean.length && Number(stockInput.value || 0) < clean.length) stockInput.value = String(clean.length);
    if (imei1 && !imei1.value && clean[0]) imei1.value = clean[0];
    if (imei2 && !imei2.value && clean[1]) imei2.value = clean[1];
  }

  function addPurchaseImei(source) {
    const panel = purchaseImeiPanel(source);
    const input = panel?.querySelector('[data-purchase-imei-input]');
    const raw = String(input?.value || '').replace(/\D/g, '');
    if (!raw) return notify('IMEI missing', 'Scan the barcode or type IMEI manually.', 'error');
    if (raw.length < 10 || raw.length > 18) return notify('Invalid IMEI', 'IMEI / serial must be 10 to 18 digits.', 'error');
    const imeis = readPurchaseImeis(panel);
    if (imeis.includes(raw)) {
      if (input) input.value = '';
      return notify('Duplicate IMEI', 'This IMEI is already in the list.', 'error');
    }
    imeis.push(raw);
    writePurchaseImeis(panel, imeis);
    if (input) { input.value = ''; input.focus(); }
    notify('IMEI added', `${raw} added to the purchase list.`);
  }

  document.addEventListener('click', async (event) => {
    const target = event.target.closest('[data-action]');
    if (!target) return;
    const action = target.dataset.action;
    if (action === 'toggle-language') {
      const fields = [...app.querySelectorAll('input,textarea,select')].map(el=>({name:el.name,search:el.hasAttribute('data-global-search'),value:el.value,checked:el.checked,type:el.type}));
      window.SkyI18n.setLanguage(window.SkyI18n.language === 'ur' ? 'en' : 'ur');
      renderApp();
      fields.forEach(field=>{const el=field.name ? [...app.querySelectorAll('input,textarea,select')].find(x=>x.name===field.name) : field.search ? app.querySelector('[data-global-search]') : null;if(el&&field.type!=='file'){el.value=field.value;el.checked=field.checked;if(el.matches('.pin-code-input'))refreshPinCodeInput(el)}});
      return;
    }
    const id = target.dataset.id;
    const page = target.dataset.page;

    if (action === 'close-modal-backdrop' && event.target === target) return closeModal();
    if (action === 'close-modal') return closeModal();
    if (action === 'toggle-password') {
      const input = target.parentElement.querySelector('input');
      input.type = input.type === 'password' ? 'text' : 'password';
      target.innerHTML = icon('eye');
      return;
    }
    if (action === 'open-auth') { state.authMode = target.dataset.mode; renderApp(); return; }
    if (action === 'set-login-pin-length') {
      const form = target.closest('form');
      state.loginDraftUsername = form?.elements?.username?.value || '';
      state.loginPinLength = 4;
      renderApp();
      requestAnimationFrame(() => app.querySelector('.pin-code-input')?.focus());
      return;
    }
    if (action === 'support-auth') { openModal('Contact Support', 'SkyBarech Technology support', html`<div class="confirm-box"><div class="confirm-icon" style="background:var(--blue-3);color:var(--blue)">${icon('help','icon-xl')}</div><strong>Need help with activation?</strong><p>Call or WhatsApp the support team at 0333-7776614 and share your shop activation code.</p><div class="confirm-actions"><button class="btn btn-primary" data-action="close-modal">Done</button></div></div>`); return; }
    if (action === 'activate-shop-now') { const form = target.closest('form'); submitActivation(form); return; }
    if (action === 'cancel-shop-switch') { pendingShopSwitch = null; closeModal(); showActivationError('Shop switch cancelled', 'Your existing shop data is unchanged.', 'error'); return; }
    if (action === 'cancel-login-shop-switch') { pendingLoginShopSwitch = null; closeModal(); notify('Connection cancelled', 'Your existing local data is unchanged.', 'error'); return; }
    if (action === 'confirm-login-shop-switch') {
      if (!pendingLoginShopSwitch) { closeModal(); return notify('Connection expired', 'Enter your mobile and PIN again.', 'error'); }
      target.disabled = true;
      target.textContent = t('Creating backup…');
      try {
        const pending = pendingLoginShopSwitch;
        const conflict = pending.online.deviceConflict;
        const result = await window.skybarechDesktop.switchActivationShop(conflict.targetShopId, conflict.switchToken);
        pendingLoginShopSwitch = null;
        closeModal();
        localStorage.removeItem(STORAGE_KEY);
        state = loadState();
        const online = await window.skybarechDesktop.onlineLogin(apiBaseUrl, pending.username, pending.password);
        if (!online?.success) throw new Error(online?.message || 'Could not connect this Desktop to your shop.');
        return await finishOnlineLogin(online, pending.password, result?.backupPath ? ' The previous local cache was backed up safely.' : '');
      } catch (error) {
        target.disabled = false;
        target.innerHTML = `${t('Back up & connect')} ${icon('arrowRight')}`;
        notify('Shop connection failed', error.message || 'The old local data was not changed.', 'error');
      }
      return;
    }
    if (action === 'confirm-shop-switch') {
      if (!pendingShopSwitch) { closeModal(); return showActivationError('Activation expired', 'Verify the activation again.'); }
      target.disabled = true;
      target.textContent = t('Creating backup…');
      try {
        const pending = pendingShopSwitch;
        const result = await window.skybarechDesktop.switchActivationShop(pending.verified.activation.shop_id, pending.verified.deviceConflict.switchToken);
        pendingShopSwitch = null;
        closeModal();
        localStorage.removeItem(STORAGE_KEY);
        return continueVerifiedActivation(pending.verified, pending.code, pending.mobile, pending.password, result);
      } catch (error) {
        target.disabled = false;
        target.innerHTML = `${t('Back up & switch shop')} ${icon('arrowRight')}`;
        notify('Shop switch failed', error.message || 'The old shop data was not changed.', 'error');
      }
      return;
    }
    if (action === 'import-activation-file') { openActivationImport(); return; }
    if (action === 'paste-skylink') { openPasteSkyLinkModal(); return; }
    if (action === 'desktop-login') {
      const form = target.closest('form');
      if (form) return submitLogin(form);
      return completeDesktopLogin();
    }
    if (action === 'reset-local-data') {
      await window.skybarechDesktop?.clearLocalData?.();
      localStorage.removeItem(STORAGE_KEY);
      state = loadState();
      splashVisible = false;
      renderApp();
      notify('Local data reset', 'Local saved data was cleared. Try login again.');
      return;
    }
    if (action === 'navigate') { state.activePage = page; state.mobileMenuOpen = false; renderApp(); return; }
    if (action === 'toggle-sidebar') {
      if (window.innerWidth <= 760) state.mobileMenuOpen = !state.mobileMenuOpen;
      else { state.sidebarCollapsed = !state.sidebarCollapsed; state.mobileMenuOpen = false; }
      renderApp(); return;
    }
    if (action === 'open-themes') { openModal(t('Workspace themes'), t('Choose your workspace colors'), themeChoices()); return; }
    if (action === 'set-theme') { applyWorkspaceTheme(target.dataset.theme); return; }
    if (action === 'toggle-theme') { applyWorkspaceTheme(state.theme === 'dark' ? 'light' : 'dark'); return; }
    if (action === 'open-notifications') { openModal('Notifications', 'Recent ERP activity', html`<div class="request-list"><article class="request-item"><div class="request-top"><strong>Low Stock Alert</strong>${badge('Low Stock')}</div><p>${state.products.filter(p => p.stock <= (p.minStock || 0)).length} product(s) at or below their reorder level.</p></article><article class="request-item"><div class="request-top"><strong>Installment Due</strong>${badge('Due Today')}</div><p>${state.installments.filter(x=>x.status==='Due Today').length} payment(s) are due for collection today.</p></article><article class="request-item"><div class="request-top"><strong>Backup Reminder</strong>${badge('Open')}</div><p>Create a local backup before closing the day.</p></article></div>`); return; }
    if (action === 'open-user-menu') { openModal('Account', `${esc(shopOwnerName())} · ${esc(shopDisplayName())}`, html`<div class="request-list"><button class="btn btn-secondary full" data-action="navigate-settings">${icon('settings')} Account Settings</button><button class="btn btn-danger full" data-action="logout">${icon('logout')} Logout</button></div>`); return; }
    if (action === 'navigate-settings') { closeModal(); state.activePage = 'settings'; renderApp(); return; }
    if (action === 'logout') { closeModal(); state.isLoggedIn = false; state.authMode = 'login'; renderApp(); notify('Logged out', 'You have safely signed out.'); return; }
    if (action === 'sync-details') {
        openModal('Cloud', 'Saved changes upload when connected.',
        html`<div class="connection-summary"><span>Shop</span><strong>${esc(shopDisplayName())} · ${esc(currentShopId())}</strong><span>Connection</span><strong>Secure cloud connection</strong><span>Last contact</span><strong>${esc(desktopSyncStatus.lastSyncAt || 'Not yet connected')}</strong><span>Waiting / failed</span><strong>${n(desktopSyncStatus.pending)} / ${n(desktopSyncStatus.failed)}</strong></div><p role="status">${esc(desktopSyncStatus.lastSyncError || 'No connection error reported.')}</p><div class="form-actions"><button class="btn btn-secondary" data-action="close-modal">Close</button><button class="btn btn-primary" data-action="sync-now">${icon('cloud')} Cloud sync</button></div>`);
      return;
    }
    if (action === 'sync-now') {
      if (syncBusy) return;
      syncBusy = true;
      if (target) { target.disabled = true; target.textContent = t('Syncing…'); }
      try {
        const result = await window.skybarechDesktop?.syncNow();
        await hydrateFromDesktopStore();
        if (result?.success) notify('Server checked', `${result.uploaded || 0} uploaded · ${result.downloaded || 0} downloaded.`);
        else notify('Sync incomplete', result?.error || 'Sign in online to connect your shop.', 'error');
      } catch (error) { notify('Connection unavailable', error.message, 'error'); }
      finally { syncBusy = false; if (target) { target.disabled = false; target.textContent = t('Cloud sync'); } }
      return;
    }

    if (action === 'add-product-modal' || action === 'quick-add-product') return productModal();
    if (action === 'add-accessory-modal') return productModal(null, 'Accessory');
    if (action === 'add-spare-part-modal') return productModal(null, 'SparePart');
    if (action === 'add-laptop-modal') return productModal(null, 'Laptop');
    if (action === 'edit-product') return productModal(state.products.find(p => p.id === id));
    if (action === 'generate-sku') { const form = target.closest('form'); const name = form?.querySelector('[name="name"]')?.value || ''; const category = form?.querySelector('[name="category"]')?.value || ''; const brand = form?.querySelector('[name="brand"]')?.value || ''; const skuInput = form?.querySelector('[name="sku"]'); if (skuInput) skuInput.value = makeSku(name, category, brand); notify('SKU generated', 'Barcode / SKU field auto-filled.'); return; }
    if (action === 'sync-mobile-models') {
      const form = target.closest('form');
      const brand = form?.querySelector('[data-mobile-brand]')?.value || form?.querySelector('[name="brand"]')?.value || 'Samsung';
      const box = target.closest('[data-model-sync-box]') || form?.querySelector('[data-model-sync-box]');
      const input = box?.querySelector('[data-mobile-model-input]');
      const list = box?.querySelector('[data-mobile-model-list]');
      const models = modelListForBrand(brand);
      if (list) list.innerHTML = models.map(m => html`<option value="${esc(m)}"></option>`).join('');
      if (input && !input.value && models.length) input.value = models[0];
      notify('Models synced', `${models.length} ${brand} models are ready in the dropdown. Missing models can be typed manually.`);
      return;
    }
    if (action === 'purchase-scan-imei') {
      const panel = purchaseImeiPanel(target);
      const input = panel?.querySelector('[data-purchase-imei-input]');
      if (input) { input.focus(); input.select(); }
      notify('Scanner ready', 'Use a barcode/IMEI scanner or type the number manually.');
      return;
    }
    if (action === 'purchase-add-imei') { addPurchaseImei(target); return; }
    if (action === 'purchase-remove-imei') {
      const panel = purchaseImeiPanel(target);
      const imei = target.dataset.imei;
      writePurchaseImeis(panel, readPurchaseImeis(panel).filter(x => x !== imei));
      notify('IMEI removed', `${imei} removed from purchase list.`);
      return;
    }
    if (action === 'remove-product-image') {
      const form = target.closest('form');
      const hidden = form?.querySelector('[data-product-image-value]');
      const preview = form?.querySelector('[data-product-image-preview]');
      if (hidden) hidden.value = '';
      if (preview) { preview.classList.remove('has-image'); preview.innerHTML = html`<span>${icon('box','icon-xl')}</span><strong>No picture</strong>`; }
      target.disabled = true;
      notify('Picture removed', 'Save product to keep this change.');
      return;
    }
    if (action === 'delete-product') return confirmModal('Delete product?', 'This will remove the product from inventory. This action cannot be undone.', 'Delete Product', 'confirm-delete-product', id);
    if (action === 'confirm-delete-product') {
      const product = state.products.find(p=>p.id===id);
      if (!product) return notify('Delete failed', 'Product record was not found.', 'error');
      state.deletedRecords = [{ deleteId: `DEL-${Date.now()}`, type: 'product', label: product.name, date: today(), by: 'Owner', payload: product }, ...(state.deletedRecords || [])];
      state.products = state.products.filter(p=>p.id!==id);
      persist({ entityType: 'product', entityId: id, action: 'delete', payload: {} }); closeModal(); renderApp(); notify('Product moved to Deleted Records', 'It can be restored from Audit / Deleted Records.', 'success'); return;
    }
    if (action === 'restore-record') {
      const index = (state.deletedRecords || []).findIndex(d => d.deleteId === id);
      const deleted = index >= 0 ? state.deletedRecords[index] : null;
      if (!deleted?.payload || deleted.type !== 'product') return notify('Restore failed', 'Restorable record was not found.', 'error');
      if (state.products.some(p => p.id === deleted.payload.id || (p.sku && p.sku === deleted.payload.sku))) return notify('Restore blocked', 'A product with the same ID or SKU already exists.', 'error');
      state.products.unshift(deleted.payload);
      state.deletedRecords.splice(index, 1);
      persist({ entityType: 'product', entityId: deleted.payload.id, action: 'upsert', payload: { ...deleted.payload, salePrice: deleted.payload.price, purchasePrice: deleted.payload.cost } }); renderApp(); notify('Product restored', `${deleted.label} is back in inventory.`, 'success'); return;
    }
    if (action === 'sort-inventory') { const key = target.dataset.key; state.inventorySort = state.inventorySort === key ? (key === 'name' ? 'stock' : 'name') : key; renderApp(); return; }
    if (action === 'toggle-low-stock') { state.inventoryLow = !state.inventoryLow; state.inventoryPage = 1; renderApp(); return; }
    if (action === 'inventory-page') { state.inventoryPage = Number(target.dataset.page); renderApp(); return; }

    if (action === 'add-cart') { const p = state.products.find(x=>x.id===id); if (!p) return; const existing = state.posCart.find(x=>x.id===id); if (existing) { if (existing.qty >= p.stock) return notify('Stock limit reached', `Only ${p.stock} unit(s) are available.`, 'error'); existing.qty += 1; } else state.posCart.push({ ...p, qty:1 }); renderApp(); notify('Added to cart', `${p.name} was added to current sale.`); return; }
    if (action === 'cart-qty') { const item = state.posCart.find(x=>x.id===id); const product = state.products.find(x=>x.id===id); if (!item) return; item.qty += Number(target.dataset.delta); if (item.qty <= 0) state.posCart = state.posCart.filter(x=>x.id!==id); else if (product && item.qty > product.stock) { item.qty = product.stock; notify('Stock limit reached', `Only ${product.stock} unit(s) are available.`, 'error'); } renderApp(); return; }
    if (action === 'cart-remove') { state.posCart = state.posCart.filter(x=>x.id!==id); renderApp(); return; }
    if (action === 'pos-clear') { if (!state.posCart.length) return; state.posCart=[]; renderApp(); notify('Cart cleared', 'The current sale cart is empty.'); return; }
    if (action === 'set-pos-payment') { state.posPayment = target.dataset.method; renderApp(); return; }
    if (action === 'pos-pay') return processPosPayment();
    if (action === 'barcode-scan') { openModal('Barcode Scan', 'Use an external scanner or enter a barcode manually.', html`<div class="field"><label>Barcode / SKU</label><div class="input-wrap">${icon('barcode')}<input class="input" id="barcode-input" autofocus placeholder="Scan or type barcode / SKU"></div></div><div class="form-actions"><button class="btn btn-secondary" data-action="close-modal">Cancel</button><button class="btn btn-primary" data-action="find-barcode">Find Product ${icon('search')}</button></div>`); setTimeout(()=>document.getElementById('barcode-input')?.focus(),0); return; }
    if (action === 'find-barcode') { const q = document.getElementById('barcode-input')?.value.trim().toLowerCase(); const p = state.products.find(x => x.sku.toLowerCase() === q || x.name.toLowerCase().includes(q)); if (p) { const existing = state.posCart.find(x=>x.id===p.id); if (p.stock <= (existing?.qty || 0)) return notify('Stock limit reached', 'No additional units are available.', 'error'); closeModal(); existing ? existing.qty += 1 : state.posCart.push({ ...p, qty:1 }); renderApp(); notify('Product found', `${p.name} was added to the cart.`); } else notify('No matching product', 'No product was found for that barcode or keyword.', 'error'); return; }
    if (action === 'imei-search') {
      const code = String(prompt('Enter IMEI / serial number') || '').replace(/\D/g, '');
      if (!code) return;
      const found = (state.products || []).find(p => {
        const values = [p.sku, p.imei, p.imei1, p.imei2, ...(Array.isArray(p.imeis) ? p.imeis : []), p.notes].join(' ');
        return values.includes(code);
      });
      if (!found) return notify('IMEI not found', `${code} is not available in local stock.`, 'error');
      return openModal('IMEI Found', 'Local stock record', html`<div class="summary-row"><span>Product</span><strong>${esc(found.name)}</strong></div><div class="summary-row"><span>Brand / Model</span><strong>${esc(found.brand)} ${esc(found.model)}</strong></div><div class="summary-row"><span>Stock</span><strong>${n(found.stock)}</strong></div><div class="summary-row total"><span>Sale Price</span><strong>${money(found.price)}</strong></div><div class="form-actions"><button class="btn btn-primary" data-action="close-modal">Done</button></div>`);
    }
    if (action === 'invoice-preview') return invoiceModal(state.invoices.find(i=>i.id===id));
    if (action === 'print-invoice') { window.print(); notify('Print window opened', '80mm thermal invoice ready.'); return; }
    if (action === 'share-invoice') { const receipt = modalRoot.querySelector('.invoice-paper, .receipt-paper, .invoice-receipt'); if (!receipt) return notify('Receipt unavailable', 'Open an invoice first.', 'error'); const blob = new Blob([receipt.innerText], {type:'text/plain;charset=utf-8'}); const link = document.createElement('a'); link.href = URL.createObjectURL(blob); link.download = 'SkyBarech-receipt.txt'; link.click(); setTimeout(()=>URL.revokeObjectURL(link.href),1000); notify('Receipt exported', 'Attach the downloaded receipt to your message.'); return; }

    if (action === 'add-repair-modal') return repairModal();
    if (action === 'repair-details') return repairModal(state.repairs.find(r=>r.id===id));
    if (action === 'repair-receipt') return repairReceiptModal(state.repairs.find(r=>r.id===id));
    if (action === 'print-repair-receipt') { window.print(); notify('Print window opened', 'Repair job receipt is ready for 80mm thermal printer.'); return; }
    if (action === 'add-ewallet-modal') return ewalletModal(target.dataset.wallet || 'EasyPaisa', target.dataset.direction || 'In');
    if (action === 'quick-ewallet') { state.ewalletQuickWallet = target.dataset.wallet || 'EasyPaisa'; state.ewalletQuickDirection = target.dataset.direction || 'In'; return ewalletModal(state.ewalletQuickWallet, state.ewalletQuickDirection); }
    if (action === 'ewallet-receipt') return ewalletReceiptModal((state.ewallets || []).find(w => w.id === id));
    if (action === 'print-ewallet-receipt') { window.print(); notify('Print window opened', 'Wallet receipt is ready for 80mm thermal printer.'); return; }
    if (action === 'repair-status') { state.repairStatus = target.dataset.status; renderApp(); return; }
    if (action === 'repair-filter') return notify('Repair filters', 'Status tabs and the search field can filter repair jobs.');

    if (action === 'add-customer-modal') return customerModal();
    if (action === 'customer-ledger') return ledgerModal('customer', state.customers.find(c=>c.id===id));
    if (action === 'export-customers') return downloadFile('customers.csv', csv(['Customer','Mobile','Balance','Status'], state.customers.map(x=>[x.name,x.phone,x.balance,x.status])), 'text/csv');
    if (action === 'add-supplier-modal') return supplierModal();
    if (action === 'supplier-ledger') return ledgerModal('supplier', state.suppliers.find(s=>s.id===id));
    if (action === 'export-suppliers') return downloadFile('suppliers.csv', csv(['Supplier','Mobile','City','Payable','Status'], state.suppliers.map(x=>[x.name,x.phone,x.city,x.payable,x.status])), 'text/csv');

    if (action === 'add-installment-modal') return installmentModal();
    if (action === 'receive-payment-modal') return receivePaymentModal();
    if (action === 'receive-for-installment') return receivePaymentModal(state.installments.find(i=>i.id===id));
    if (action === 'view-all-installments') return notify('Installments overview', 'All scheduled installment records are listed in this module.');

    if (action === 'settings-tab') { state.settingsTab = target.dataset.tab; renderApp(); return; }
    if (action === 'save-profile') { const form = document.querySelector('[data-form="profile"]'); if (form) return submitShopProfile(form); }
    if (action === 'remove-shop-logo') { const form = target.closest('form'); const hidden = form?.querySelector('[data-shop-logo-value]'); const preview = form?.querySelector('[data-shop-logo-preview]'); if (hidden) hidden.value = ''; if (preview) { preview.classList.remove('has-image'); preview.innerHTML = html`<span>${icon('box','icon-xl')}</span><strong>${esc(shopInitials())}</strong>`; } target.disabled = true; notify('Logo removed', 'Save changes to apply.'); return; }
    if (action === 'download-backup') {
      persist();
      const backup = { format: 'skybarech-desktop-backup', version: 2, exportedAt: new Date().toISOString(), data: JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') };
      downloadFile(`skybarech-backup-${today()}.json`, JSON.stringify(backup, null, 2));
      notify('Backup created', 'Validated versioned backup downloaded as JSON.', 'success'); return;
    }
    if (action === 'restore-note') return notify('Restore data', 'Choose a valid backup JSON file.');
    if (action === 'add-expense-modal') return openExpenseModal();
    if (action === 'add-cash-session-modal') return openCashSessionModal();
    if (action === 'soft-delete-record') { const type = target.dataset.type || 'record'; const id = target.dataset.id || ''; state.deletedRecords = [{ type, label: id, date: today(), by: shopOwnerName() }, ...(state.deletedRecords || [])]; if (type === 'expense') state.expenses = (state.expenses || []).filter(x => x.id !== id); persist(); renderApp(); notify('Record soft deleted', `${id} saved in audit/deleted records.`); return; }
    if (action === 'add-user-modal') {
      const branches = Array.isArray(state.session?.branches) ? state.session.branches : [];
      if (apiConfigured && !branches.length) {
        refreshShopAdminControl().then(() => notify('Branch required', 'Cloud se branches reload ho gayi hain. Add User dobara open karein.', 'error'));
        return;
      }
      return openModal('Add User', 'Create a real Cloud staff login linked to one branch.', html`<form data-form="user"><div class="form-grid"><div class="field"><label>Full Name</label><input class="input" name="name" required></div><div class="field"><label>Role</label><select class="select" name="role"><option value="cashier">Cashier</option><option value="branch_manager">Branch Manager</option><option value="technician">Technician</option><option value="accountant">Accountant</option></select></div><div class="field"><label>Branch</label><select class="select" name="branchId" required><option value="">Select branch</option>${branches.map((branch) => html`<option value="${esc(branch.id)}">${esc(branch.name)}</option>`).join('')}</select></div><div class="field"><label>Mobile Number</label><input class="input" name="phone" required></div><div class="field"><label>Email (optional)</label><input class="input" name="email" type="email"></div><div class="field"><label>4-digit PIN</label><input class="input" name="password" type="password" inputmode="numeric" pattern="[0-9]{4}" minlength="4" maxlength="4" required></div></div><div class="form-actions"><button class="btn btn-secondary" type="button" data-action="close-modal">Cancel</button><button class="btn btn-primary" type="submit">Add User</button></div></form>`);
    }
    if (action === 'download-report') return downloadReport();
    if (action === 'open-report') return openReport(target.dataset.report);
    if (action === 'view-all-sales') { state.activePage = 'pos'; renderApp(); return; }
    if (action === 'view-all-requests') return notify('Support history', 'All saved requests are shown on this page.');
    if (action === 'upload-local') return notify('Document slot selected', 'Use the upload field to save this file locally.');
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && state.mobileMenuOpen && !modalRoot.firstElementChild) { state.mobileMenuOpen = false; renderApp(); return; }
    if (modalRoot.firstElementChild) {
      if (event.key === 'Escape') { event.preventDefault(); closeModal(); return; }
      if (event.key === 'Tab') {
        const targets = [...modalRoot.querySelectorAll('button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), a[href], [tabindex="0"]')].filter(el => el.getClientRects().length);
        const first = targets[0], last = targets[targets.length - 1];
        if (!first) { event.preventDefault(); return; }
        if (event.shiftKey && (document.activeElement === first || !targets.includes(document.activeElement))) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
      }
    }
    if (event.target.matches('[data-purchase-imei-input]') && event.key === 'Enter') {
      event.preventDefault();
      addPurchaseImei(event.target);
    }
  });

  document.addEventListener('input', (event) => {
    if (event.target.matches('.pin-code-input')) refreshPinCodeInput(event.target);
    if (event.target.matches('[data-inventory-search]')) {
      state.inventorySearch = event.target.value;
      state.inventoryPage = 1;
      renderApp();
    }
    if (event.target.matches('[data-laptop-search]')) {
      state.laptopSearch = event.target.value;
      renderApp();
    }
    if (event.target.matches('[data-accessory-search]')) {
      state.accessorySearch = event.target.value;
      renderApp();
    }
    if (event.target.matches('[data-spare-part-search]')) {
      state.sparePartSearch = event.target.value;
      renderApp();
    }
    if (event.target.matches('[data-pos-search]')) {
      const term = event.target.value.toLowerCase();
      const list = document.getElementById('pos-product-list');
      if (list) list.innerHTML = state.products.filter(p => p.stock > 0 && `${p.name} ${p.brand} ${p.model} ${p.sku}`.toLowerCase().includes(term)).map(posProduct).join('') || html`<div class="empty">${icon('search','icon-xl')}<strong>No matching product found</strong></div>`;
    }
    if (event.target.matches('[data-global-search]')) {
      const q = event.target.value.trim();
      if (q.length >= 2) {
        const inSpareParts = state.products.some(p => isSparePartProduct(p) && `${p.name} ${p.category} ${p.brand} ${p.model} ${p.sku} ${p.rack || ''}`.toLowerCase().includes(q.toLowerCase()));
        const inAccessories = state.products.some(p => isAccessoryProduct(p) && `${p.name} ${p.category} ${p.brand} ${p.model} ${p.compatibleModels || ''} ${p.sku} ${p.rack || ''}`.toLowerCase().includes(q.toLowerCase()));
        state.activePage = inSpareParts ? 'spareParts' : (inAccessories ? 'accessories' : 'inventory');
        if (inSpareParts) state.sparePartSearch = q;
        if (inAccessories) state.accessorySearch = q;
        state.inventorySearch = q;
        state.inventoryPage = 1;
        renderApp();
      }
    }
    if (event.target.matches('[data-customer-search]')) filterTableRows(event.target, 0, 1);
    if (event.target.matches('[data-supplier-search]')) filterTableRows(event.target, 0, 1, 2);
    if (event.target.matches('[data-repair-search]')) filterRepairRows(event.target);
  });

  document.addEventListener('change', (event) => {
    if (event.target.name === 'pinLength') {
      const form = event.target.closest('form');
      form.querySelectorAll('[name=newPassword], [name=confirmPassword]').forEach(input => {
        input.value = ''; input.maxLength = Number(event.target.value); input.pattern = '[0-9]{' + event.target.value + '}';
        const shell = input.closest('[data-pin-shell]');
        if (shell) shell.dataset.length = event.target.value;
        const hint = input.closest('.pin-code-field')?.querySelector('.pin-code-hint');
        if (hint) hint.textContent = t(`Enter exactly ${event.target.value} digits`);
        refreshPinCodeInput(input);
      });
      return;
    }

    if (event.target.matches('[data-shop-logo-file]')) {
      const file = event.target.files?.[0];
      const form = event.target.closest('form');
      const hidden = form?.querySelector('[data-shop-logo-value]');
      const preview = form?.querySelector('[data-shop-logo-preview]');
      const removeBtn = form?.querySelector('[data-action="remove-shop-logo"]');
      resizeProductImage(file, (dataUrl) => {
        if (hidden) hidden.value = dataUrl;
        if (preview) { preview.classList.add('has-image'); preview.innerHTML = html`<img src="${dataUrl}" alt="Shop logo">`; }
        if (removeBtn) removeBtn.disabled = false;
        notify('Logo ready', 'Press Save Changes to update branding and invoices.', 'success');
      });
      return;
    }
    if (event.target.matches('[data-product-image-file]')) {
      const file = event.target.files?.[0];
      const form = event.target.closest('form');
      const hidden = form?.querySelector('[data-product-image-value]');
      const preview = form?.querySelector('[data-product-image-preview]');
      const removeBtn = form?.querySelector('[data-action="remove-product-image"]');
      resizeProductImage(file, (dataUrl) => {
        if (hidden) hidden.value = dataUrl;
        if (preview) { preview.classList.add('has-image'); preview.innerHTML = html`<img src="${dataUrl}" alt="Product picture">`; }
        if (removeBtn) removeBtn.disabled = false;
        notify('Picture added', 'Product photo is ready. Press Save Product to store it locally.', 'success');
      });
      return;
    }
    if (event.target.matches('[data-doc-file]')) {
      const file = event.target.files?.[0];
      const form = event.target.closest('form');
      const name = event.target.dataset.docName;
      const label = event.target.dataset.docLabel || 'Document';
      const hidden = form?.querySelector(`[data-doc-value="${name}"]`);
      const preview = form?.querySelector(`[data-doc-preview="${name}"]`);
      resizeDocumentFile(file, (dataUrl) => {
        if (hidden) hidden.value = dataUrl;
        if (preview) preview.textContent = `${t(label)} selected: ${file.name}`;
        notify(`${t(label)} saved`, 'Document is ready. Press Save Purchase to store it locally.', 'success');
      });
      return;
    }
    if (event.target.matches('[data-restore-file]')) {
      const file = event.target.files?.[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => {
        try {
          const parsed = JSON.parse(String(reader.result || '{}'));
          if (!parsed || parsed.format !== 'skybarech-desktop-backup' || parsed.version !== 2 || !parsed.data || typeof parsed.data !== 'object') throw new Error('Invalid backup');
          const requiredArrays = ['products','customers','suppliers','sales','expenses'];
          if (!requiredArrays.every(key => !parsed.data[key] || Array.isArray(parsed.data[key]))) throw new Error('Invalid data structure');
          localStorage.setItem(STORAGE_KEY, JSON.stringify(parsed.data));
          state = loadState();
          renderApp();
          notify('Backup restored', 'Local data has been restored successfully.', 'success');
        } catch (err) {
          notify('Restore failed', 'Choose a valid SkyBarech backup JSON file.', 'error');
        }
      };
      reader.readAsText(file);
      return;
    }
    if (event.target.matches('[data-mobile-brand]')) {
      const form = event.target.closest('form');
      const box = form?.querySelector('[data-model-sync-box]');
      const list = box?.querySelector('[data-mobile-model-list]');
      const input = box?.querySelector('[data-mobile-model-input]');
      const models = modelListForBrand(event.target.value || 'Other');
      if (list) list.innerHTML = models.map(m => html`<option value="${esc(m)}"></option>`).join('');
      if (input) {
        input.value = models[0] || '';
        input.placeholder = `${event.target.value || 'Selected'} model search/manual add`;
      }
    }
    if (event.target.matches('[data-inventory-category]')) { state.inventoryCategory = event.target.value; state.inventoryPage = 1; renderApp(); }
    if (event.target.matches('[data-report-range]')) { state.reportRange = event.target.value; notify('Report range updated', `Showing data for ${state.reportRange}.`); }
  });

  document.addEventListener('focusin', (event) => { if (event.target.matches('.pin-code-input')) refreshPinCodeInput(event.target); });
  document.addEventListener('focusout', (event) => { if (event.target.matches('.pin-code-input')) setTimeout(() => refreshPinCodeInput(event.target), 0); });

  document.addEventListener('change', (event) => {
    const categorySelect = event.target.closest?.('[data-accessory-category]');
    if (!categorySelect) return;
    const form = categorySelect.closest('form');
    const variantSelect = form?.querySelector('[data-accessory-variant]');
    const manual = form?.querySelector('[data-accessory-variant-manual]');
    const opts = variantOptionsForCategory(categorySelect.value);
    if (variantSelect && manual) {
      if (opts.length) {
        variantSelect.innerHTML = opts.map(x => html`<option>${esc(x)}</option>`).join('');
        variantSelect.style.display = '';
        manual.style.display = 'none';
        manual.value = '';
      } else {
        variantSelect.innerHTML = htmlText('<option>Manual</option>');
        variantSelect.style.display = 'none';
        manual.style.display = '';
        manual.focus();
      }
    }
  });

  document.addEventListener('submit', (event) => {
    const form = event.target;
    if (!form.dataset.form) return;
    event.preventDefault();
    const handler = {
      login: submitLogin,
      activate: submitActivation,
      password: submitPassword,
      product: submitProduct,
      purchase: submitPurchase,
      sale: submitSale,
      repair: submitRepair,
      customer: submitCustomer,
      supplier: submitSupplier,
      installment: submitInstallment,
      'receive-payment': submitReceivePayment,
      ewallet: submitEwallet,
      'ewallet-inline': submitEwallet,
      expense: submitExpense,
      'cash-session': submitCashSession,
      support: submitSupport,
      profile: submitShopProfile,
      'sync-settings': async (f) => {
        const d = Object.fromEntries(new FormData(f));
        try {
          desktopSyncStatus = await window.skybarechDesktop.configureSync(d);
          renderApp();
          notify('Sync settings saved', 'SQLite remains primary; online upload will run automatically.');
        } catch (error) {
          notify('Sync settings failed', error.message || 'Check the HTTPS API URL.', 'error');
        }
      },
      user: async (f) => {
        const d = Object.fromEntries(new FormData(f));
        if (apiConfigured && window.skybarechDesktop?.saveShopUser) {
          try {
            const user = await window.skybarechDesktop.saveShopUser({ fullName: d.name, mobile: d.phone, email: d.email || '', password: d.password, role: d.role, branchId: d.branchId, status: 'active' });
            await refreshShopAdminControl();
            closeModal(); renderApp();
            notify('User added', `${user?.fullName || d.name} ka Cloud staff login create ho gaya.`);
          } catch (error) {
            notify('User save failed', error.message || 'Cloud staff user create nahi ho saka.', 'error');
          }
          return;
        }
        notify('Online connection required', 'Sign in online to create a server staff account.', 'error');
      },
      'skylink-import': (f) => { const d = Object.fromEntries(new FormData(f)); if (importActivationPayload(d.skylink)) closeModal(); }
    }[form.dataset.form];
    handler?.(form);
  });

  function filterTableRows(input, ...columns) {
    const q = input.value.toLowerCase();
    input.closest('.table-card')?.querySelectorAll('tbody tr').forEach(row => {
      const text = columns.map(i=>row.cells[i]?.innerText || '').join(' ').toLowerCase();
      row.style.display = text.includes(q) ? '' : 'none';
    });
  }

  function filterRepairRows(input) {
    const q = input.value.toLowerCase();
    document.querySelectorAll('.repair-row').forEach(row => row.style.display = row.innerText.toLowerCase().includes(q) ? '' : 'none');
  }

  function processPosPayment() {
    const cart = state.posCart;
    if (!cart.length) return notify('Cart is empty', 'Add one or more products before collecting payment.', 'error');
    if (cart.some(item => { const p = state.products.find(p=>p.id===item.id); return !p || item.qty <= 0 || item.qty > p.stock; })) return notify('Stock changed', 'Review cart quantities before collecting payment.', 'error');
    const subtotal = cart.reduce((sum, x) => sum + (x.price * x.qty), 0);
    const discount = 0;
    const total = Math.max(0, subtotal - discount);
    const paidEl = document.getElementById('paid-amount');
    const paid = Number(paidEl?.value || total);
    if (!Number.isFinite(paid) || paid < total) return notify('Payment incomplete', 'Enter the complete payable amount. Create scheduled dues from Installments.', 'error');
    const customer = document.getElementById('pos-customer')?.value || 'Walk-in Customer';
    for (const item of cart) {
      const product = state.products.find(p=>p.id===item.id);
      if (product) product.stock = Math.max(0, product.stock - item.qty);
    }
    const inv = { id:makeId('INV'), customer, total, payment:state.posPayment, date:today(), items:cart.map(x=>({name:x.name,qty:x.qty,price:x.price})) };
    state.invoices.unshift(inv); state.posCart=[]; persist(); renderApp(); invoiceModal(inv); notify('Payment collected', `${money(total)} sale has been completed.`);
  }

  function csv(header, rows) {
    return [header, ...rows].map(row => row.map(value => `"${String(value).replace(/"/g,'""')}"`).join(',')).join('\n');
  }

  function downloadReport() {
    const rows = reportSales().map(i=>[i.id,i.customer,i.total,i.payment,i.date]);
    downloadFile(`sales-report-${today()}.csv`, csv(['Invoice','Customer','Amount','Payment','Date'], rows), 'text/csv');
    notify('Report exported', 'The sales report was downloaded as a CSV file.');
  }

  function openReport(name) {
    state.activePage = 'reports'; renderApp();
  }

  renderApp();
  hydrateFromDesktopStore();
  setTimeout(() => { splashVisible = false; renderApp(); }, 950);
})();
