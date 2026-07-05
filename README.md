# Glasspane

Minimal Android camera app built with CameraX and Jetpack Compose.

## Features

- Live camera preview with torch control
- Photo and video capture
- Grid overlay and adjustable overlay layers
- Lens selection where the device supports multiple cameras
- Portrait-only, edge-to-edge UI

## Requirements

- Android device with a rear camera
- Android Studio Hedgehog or newer
- Camera and storage permissions (requested on first launch)

## Build

Open the project root in Android Studio and run the `app` configuration, or:

```bash
./gradlew :app:assembleDebug
```

## Project layout

```
app/src/main/java/com/glasspane/app/
├── camera/          CameraX + torch controllers
├── permissions/     Runtime permission gate
└── ui/              Compose screens, overlays, settings
```

## License

MIT
