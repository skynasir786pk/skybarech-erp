(function () {
  const rawBase = String(window.SKYBARECH_CONFIG?.apiBaseUrl || "").trim().replace(/\/$/, "");
  const configured = /^https:\/\//i.test(rawBase) && !rawBase.includes("YOUR-DOMAIN");
  let accessToken = "";
  try { accessToken = sessionStorage.getItem("skybarech_platform_access") || ""; } catch {}
  const validToken = value => typeof value === "string" && /^[A-Za-z0-9_-]{32,256}$/.test(value);
  if (!validToken(accessToken)) accessToken = "";
  function storeToken(value) {
    accessToken = value;
    try { if (value) sessionStorage.setItem("skybarech_platform_access", value); else sessionStorage.removeItem("skybarech_platform_access"); } catch {}
  }
  function sessionLost() {
    storeToken("");
    if (typeof window.dispatchEvent === "function") window.dispatchEvent(new Event("skybarech:session-expired"));
  }

  async function request(path, options = {}) {
    if (!configured) throw new Error("Hostinger API URL config.js mein set karein.");
    const { publicRequest = false, ...fetchOptions } = options;
    const requestToken = publicRequest ? "" : accessToken;
    if (!publicRequest && !validToken(requestToken)) {
      sessionLost();
      const error = new Error("Please sign in to Super Admin again.");
      error.code = "platform_authentication_required";
      throw error;
    }
    let response;
    try {
      response = await fetch(`${rawBase}${path}`, {
        signal: AbortSignal.timeout(30000),
        ...fetchOptions,
        cache: "no-store",
        headers: {
          Accept: "application/json",
          ...(options.body ? { "Content-Type": "application/json" } : {}),
          ...(requestToken ? { Authorization: `Bearer ${requestToken}`, "X-Platform-Token": requestToken } : {}),
          ...(options.headers || {})
        }
      });
    } catch (networkError) {
      const error = new Error(`API connection failed. Check ${rawBase}/health and Hostinger CORS/deployment.`);
      error.code = "network_error";
      error.cause = networkError;
      throw error;
    }
    const payload = await response.json().catch(() => { throw new Error("API returned an invalid response. Check Hostinger routing and PHP logs."); });
    if (!response.ok || payload.success === false) {
      const error = new Error(payload.error?.message || `Server request failed (HTTP ${response.status}).`);
      error.code = payload.error?.code || `http_${response.status}`;
      error.status = response.status;
      error.requestId = payload.request_id || "";
      if (response.status === 401 && !publicRequest && accessToken === requestToken) {
        sessionLost();
        error.message = "Your administrator session has ended. Please sign in again.";
      }
      throw error;
    }
    if (!publicRequest && accessToken !== requestToken) {
      const error = new Error("Administrator session changed. Please refresh.");
      error.code = "session_changed";
      throw error;
    }
    return payload;
  }

  window.SkyBarechApi = Object.freeze({
    configured,
    baseUrl: rawBase,
    hasSession: () => Boolean(accessToken),
    login: async (email, password) => {
      const result = await request("/v1/platform/auth/login", {
        publicRequest: true,
        method: "POST",
        body: JSON.stringify({ email, password })
      });
      if (!validToken(result.tokens?.access_token) || !result.admin?.email) throw new Error("Invalid administrator login response. Update the Cloud API and sign in again.");
      storeToken(result.tokens.access_token);
      return result.admin;
    },
    logout: async () => {
      try { if (accessToken) await request("/v1/platform/auth/logout", { method: "POST", body: "{}" }); } finally {
        storeToken("");
      }
    },
    me: async () => (await request("/v1/platform/auth/me")).admin,
    shops: async () => (await request("/v1/platform/shops")).shops || [],
    createShop: async (shop) => (await request("/v1/platform/shops", { method: "POST", body: JSON.stringify(shop) })).created,
    updateShop: async (shop) => request("/v1/platform/shops/update", { method: "POST", body: JSON.stringify(shop) }),
    setShopStatus: async (id, status) => request("/v1/platform/shops/status", { method: "POST", body: JSON.stringify({ id, status }) }),
    deleteShop: async (id) => request("/v1/platform/shops/delete", { method: "POST", body: JSON.stringify({ id }) }),
    appearance: async (shopId) => (await request(`/v1/platform/appearance?shop_id=${encodeURIComponent(shopId)}`)).appearance,
    saveAppearance: async (settings) => (await request('/v1/platform/appearance', {method:'POST', body:JSON.stringify(settings)})).appearance,
    shopControl: async (shopId) => (await request(`/v1/platform/shop-control?shop_id=${encodeURIComponent(shopId)}`)).control,
    saveBranch: async (branch) => (await request("/v1/platform/branches", { method: "POST", body: JSON.stringify(branch) })).branch,
    saveUser: async (user) => (await request("/v1/platform/users", { method: "POST", body: JSON.stringify(user) })).user,
    setUserStatus: async (shopId, id, status) => request("/v1/platform/users/status", { method: "POST", body: JSON.stringify({ shopId, id, status }) }),
    setDeviceStatus: async (shopId, id, status) => request("/v1/platform/devices/status", { method: "POST", body: JSON.stringify({ shopId, id, status }) }),
    revokeSessions: async (shopId, userId = null) => request("/v1/platform/sessions/revoke", { method: "POST", body: JSON.stringify({ shopId, ...(userId ? { userId } : {}) }) }),
    resetActivation: async (shopId) => (await request("/v1/platform/activation/reset", { method: "POST", body: JSON.stringify({ shopId }) })).activation,
    permissions: async (shopId) => (await request(`/v1/platform/permissions?shop_id=${encodeURIComponent(shopId)}`)).permissions,
    savePermission: async (input) => (await request("/v1/platform/permissions", { method: "POST", body: JSON.stringify(input) })).permission,
    shopData: async (shopId, entityType = "", limit = 300) => (await request(`/v1/platform/shop-data?shop_id=${encodeURIComponent(shopId)}&entity_type=${encodeURIComponent(entityType)}&limit=${encodeURIComponent(limit)}`)).records || []
  });
})();
