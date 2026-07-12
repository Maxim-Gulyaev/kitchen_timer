# KMP Source-Set Guidance

## commonMain

Use for timer state, reducers, ViewModels/state holders, formatting, shared Compose UI, theme tokens, resources, and interfaces for platform services.

## androidMain

Use for Android APIs: notifications, vibration, sound playback, foreground/background hooks, permissions, and Android-specific lifecycle glue.

## iosMain

Use for iOS APIs: `MainViewController`, haptics, audio, local notifications, and SwiftUI/UIKit bridge code required by Compose Multiplatform.

## App Roots

- `androidApp/MainActivity.kt` should host shared UI and wire platform dependencies.
- `iosApp` Swift files should host the Compose controller and platform dependency wiring.
- Do not duplicate domain state transitions in platform entry points.
