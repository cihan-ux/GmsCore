# Huawei GMS Diagnostics — pre-release checklist

- [x] Read-only app adapted from existing GmsCore HW Assistant v1
- [x] Manifest includes explicit queried packages, no QUERY_ALL_PACKAGES
- [x] Report shows separate GSF / GMS / Play Store privileged permission requests and grants
- [x] Account types examined without account names
- [x] Copy, system share and Android SAF TXT export
- [ ] Verify unit tests and debug APK in GitHub Actions for this branch
- [ ] Device tests on Huawei P Smart S (EMUI 12 / API 29)
- [ ] Validate other Android 10 and Android 17 devices (package visibility)
- [ ] Review source tree licenses before standalone F-Droid publication
- [ ] Create dedicated GitHub repository and stable signing key for public release
- [ ] Capture clean screenshots, create original icon, publish changelog and tag
