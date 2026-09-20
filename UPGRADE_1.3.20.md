# SkyBarech ERP 1.3.20 — Premium 4-digit PIN source

Android: animated navy wave splash, light PIN login, dedicated fingerprint unlock screen (when enrolled), navy dashboard, eight coloured metrics, two-column module tiles and consistent S branding. Fingerprint enrollment still requires the real Android biometric prompt and an authenticated shop binding.

Desktop: light login with four masked PIN boxes, blue gradient actions, navy sidebar, eight dashboard metrics, green POS payment actions, focus/hover/entry effects and reduced-motion support.

Super admin: refreshed responsive login/dashboard style, matching logo, four-digit PIN sign-in. Server account rate limit applies across IP addresses; platform sign-ins serialize on the administrator row. PINs remain hashed, and leading zeroes are preserved.

Dashboard values use actual existing data. Sales Count is shown instead of an invented daily profit; the current Android sale model does not store historical cost of goods. Expenses remain labelled as recorded expenses, not incorrectly labelled today-only.

## Upgrade existing installations
1. Back up the existing database, desktop local data, and server configuration.
2. Deploy 04-Hostinger-API and 03-Super-Admin-Web together. Preserve the installed config/config.php and database. Do not rerun the fresh-install database setup.
3. BEFORE relying on the new admin login, reset the platform administrator to a chosen four-digit PIN on the server using the existing CLI tool: `php tools/create_platform_admin.php "Your Name" "your-admin-email" "YOUR_4_DIGIT_PIN"`. The placeholder must be replaced with four numeric digits. This resets that account and revokes its old platform sessions. No default/shared PIN is included.
4. Existing four-digit shop PINs continue working. Old passwords and six-digit PINs are not truncated or accepted by the new login. Use Super Admin > Shop > Reset Activation, then activate the owner device with the new activation code and temporary four-digit PIN, and choose a four-digit shop PIN. Existing one-time activation passwords must also be reissued. Activation reset revokes existing shop sessions. Back up any unsynced local work first.
5. Staff credentials must be reset to four digits through the existing authenticated user API/control before staff use the new login. Do not delete/recreate staff records merely to reset a PIN.
6. Build and install the updated desktop and Android clients using their included build scripts. Keep production signing keys and API URL settings. Android is versionCode 20 / versionName 1.3.20, desktop is 1.3.20.

The legacy API field names `password`, `password_hash` and temporary_password are retained for wire/database compatibility; their new credential values are four-digit PIN strings. Hashing/encryption has not been replaced with plaintext PIN storage.

## Verification and limitations
- Desktop and super-admin JavaScript syntax checks passed. Eight desktop PIN regression cases passed, including leading zeroes, passwords, six digits, Unicode digits and trailing newline. Android resource XML parses successfully.
- Desktop and super-admin login were visually checked in the browser; admin was also checked at a narrow mobile viewport.
- PHP and PIN policy regression checks are included under tests; run PHP tests on a server with PHP installed.
- Android compile attempt was blocked by Java `Unable to establish loopback connection`, including the retry outside the sandbox. APK and Windows installer are not built or signed in this delivery.
- Production login, database migration, live sync, Android rendering and device fingerprint behavior require validation in your test installation. No production deployment or live credential reset was performed.

The original 1.3.19 release notes are historical. This file describes the new source changes.
