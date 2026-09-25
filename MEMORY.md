# Project memory

Production API: `https://lightgrey-cormorant-560478.hostingersite.com/api`

Desktop activation and Android activation must use this same API. Android local builds currently fail on this PC because Java NIO selector/loopback fails before Gradle compiles. The repository includes GitHub Actions to produce a debug APK in the cloud.

v1.3.26 UI work: compact light login, light Android header/navigation, animated onboarding, colored POS selection and persistent checkout. See UI_UX_AUDIT.md for exact scope and outstanding device checks. Do not restore the old 190dp login header or desktop minimum grid widths of 480+540px. GitHub CLI works with elevated execution on this host; sandbox credential failure does not mean reauthentication is required.
