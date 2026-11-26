# Android – KontorKaffe Barista 1.2 test
The Android APK is a native, dedicated launcher for the private administrator PWA. It opens the administrator in the device browser to retain web push and printing support. It is not an offline or independently native order system. No admin secret is stored in the APK. A separate HTTPS site must be deployed before it can be used. GitHub Pages under a shared github.io origin is deliberately rejected. Only a dedicated KontorKaffe origin is allowed by project policy; verify the configured address yourself.

GitHub Actions builds a debug-signed test APK and uploads it as an artifact. Ephemeral debug keys may differ between builds: uninstall an older test APK before installing a newly signed one if Android reports a conflict. For stable production updates, establish a dedicated release signing key stored outside the repository. Do not share credentials or reuse signing keys from other projects.

Customers do not use the APK.
