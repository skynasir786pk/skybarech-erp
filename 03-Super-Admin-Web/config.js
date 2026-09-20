(function () {
  const isWeb = window.location.protocol === "https:" || window.location.protocol === "http:";
  const isLocalDev = ["localhost", "127.0.0.1"].includes(window.location.hostname);
  const sameOriginApi = isWeb && !isLocalDev ? `${window.location.origin}/api` : "";

  window.SKYBARECH_CONFIG = Object.freeze({
    // Production Super Admin and API are deployed on the same website.
    // This avoids stale temporary-domain/CORS failures after a domain change.
    apiBaseUrl: sameOriginApi || "https://lightgrey-cormorant-560478.hostingersite.com/api"
  });
})();
