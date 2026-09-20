# Architecture

- `01-Desktop-ERP`: Electron Windows app; production API is embedded in `src/config.js` and has a safe production fallback in `src/app.js`.
- `02-Android-Client`: Kotlin/Compose Android app; API base URL is in `gradle.properties` as `SKYBARECH_API_BASE_URL`.
- `03-Super-Admin-Web`: browser control panel for shops, permissions, staff and devices.
- `04-Hostinger-API`: PHP API and database schema; it remains the sole sync authority.

Client data is local-first. Every permitted write queues a cloud sync record; the API enforces role permissions.
