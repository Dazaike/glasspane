# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [v1.1] - 2026-09-14

### Added

- Manual focus control: a "Manual focus" toggle and slider in the camera controls cluster, shown only on lenses that report Camera2 manual-focus support (`LENS_INFO_MINIMUM_FOCUS_DISTANCE > 0` and `CONTROL_AF_MODE_OFF` available). Toggling on locks focus at the midpoint of the lens's supported diopter range and applies immediately; the slider then racks focus across the full range. Toggling off restores continuous autofocus. (`FocusController.kt`, `CameraScreen.kt`)
- Switching lenses now resets manual focus to off and re-queries focus capability for the newly bound lens, so a manual distance never carries over to an unrelated lens. (`CameraScreen.kt`)

### Fixed

- Torch strength and torch-off now merge Camera2 capture request options instead of replacing the whole bundle, so toggling the torch no longer silently clears an active manual-focus lock (and vice versa). (`TorchController.kt`)

## [v1.0] - Initial public release

- Live camera preview with torch control.
- Photo and video capture.
- Grid overlay and adjustable overlay layers.
- Lens selection where the device supports multiple cameras.
- Portrait-only, edge-to-edge UI.
