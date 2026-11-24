# G01 Foundation — release and migration validation

## Signing and versioning
Development package com.pocketnexus.console.debug is separate from release com.pocketnexus.console. Android's same-package in-place upgrade requires the same signer. Fresh CI debug runners can generate different keys; never uninstall a user's existing app to resolve a signature mismatch. Back up settings first, and use a privately held stable signer for personal preview or stable release.

Release packaging now requires NEXUS_KEYSTORE, NEXUS_STORE_PASSWORD, NEXUS_KEY_ALIAS, NEXUS_KEY_PASSWORD and NEXUS_VERSION_CODE (integer 20000..2100000000). Use a new private key, never inherited repository keys. Owner generates and stores it offline, with an encrypted backup; do not paste passwords, upload the keystore to Git, or use untrusted CI. Record public certificate SHA-256 separately and compare with apksigner verify --print-certs before distributing. Choose explicit strictly increasing version codes; test on an expendable install first. Never publish ephemeral CI signed artifacts as stable releases. A signing configuration is not proof of actual key ownership or tested release upgrades.

## Evidence
- Existing ARM64 development CI builds and runs pocket unit tests. Toolchain versions pinned in Gradle/CI; bit-for-bit build reproducibility not established.
- New Android 13 x86_64 emulator upgrade workflow tests two synthetic version codes, same signer, same package and retention of three app-private preference files and a private file; no real user data. This is package-manager upgrade evidence, not proof of every app schema migration or physical RP6 release upgrade. Test APKs are not distributed.
- Backup restore validates all stores before commits and attempts rollback of touched keys on synchronous commit errors. The rollback is best-effort: process death, storage failure or concurrent preference edits can still interrupt it. Import trusted backups while idle; keep original export. Three new tests for successful restore, type mismatch and rollback.
- G01 remains open until combined CI succeeds, toolchain/rebuild evidence is assessed, actual signer is established privately, and data-preserving upgrade and legacy migration are validated on target device. Keep original installation unchanged. G14 separately requires final full release verification.
