# Changelog

## 3.0.0 - 2026-08-25

### Breaking Changes

- Relocate all Maven source modules under `modules/`; update module-specific build paths accordingly.
- Move playback implementation types into `camera`, `event`, `timeline`, and `track` packages.

### Features

- Add an extensible, frame-based playback session and track registration API.
- Add a display-entity camera backend for Java clients with configurable interpolation and anchoring.
- Add position and rotation interpolation modes backed by arc-length and Catmull-Rom splines.

### Refactor

- Separate source modules, documentation, integration tests, and local test-server artifacts.

### Tests

- Add Java unit coverage for playback lifecycle, camera transport, event scheduling, timelines, interpolation, and player restoration.
- Add Mineflayer playback-kernel and display-camera integration scenarios.
