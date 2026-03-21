# Architecture overview

## Project structure

AetherSuite is a multi-module Android project. Each application is its own Gradle module. They all depend on `aether-core`, which is a shared library module.

```
AetherSuite/
  aether-core/          Shared library : theme, encryption, inter-app intents
  aether-sms/           SMS and MMS messaging
  aether-contacts/      Contact management
  aether-phone/         Phone dialer
  aether-notes/         Encrypted notes
  aether-files/         File manager
  aether-gallery/       Photo and video gallery
  aether-music/         Local music player
  aether-calendar/      Calendar and reminders
```

## Technology stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM (ViewModel + StateFlow) |
| Navigation | Compose Navigation |
| Encryption | AES-256-GCM via Android Keystore |
| Biometrics | BiometricPrompt API |
| SMS/MMS | ContentResolver, TelephonyManager |
| MMS sending | Custom MmsSender (vendored encoder) |
| Voice input | Android SpeechRecognizer API |
| Video | ExoPlayer |
| Background audio | ForegroundService |
| Reminders | AlarmManager |
| Minimum Android | API 26 (Android 8.0) |
| Target Android | API 34 (Android 14) |

## aether-core

`aether-core` is not an app. It is a library included in every other module. It provides :

- AetherTheme : the shared colour palette and typography used across all 9 apps
- AetherIntents : the Intent definitions that let apps communicate with each other
- AES-256-GCM encryption utilities used by AetherNotes and AetherCalendar
- Biometric authentication helpers

## MVVM pattern

Each screen follows the same structure :

```
Screen (Composable)
  observes
ViewModel (StateFlow)
  reads/writes
Repository
  accesses
ContentResolver or local storage
```

ViewModels never hold references to Context beyond what is needed for ContentResolver access. State is exposed as StateFlow and collected in Composables.

## Inter-app communication

Apps communicate through explicit Android Intents defined in `aether-core/AetherIntents.kt`. Each intent includes the package name of the target app, so there is no ambiguity.

Example : when AetherGallery sends a photo to AetherSMS, it fires an AetherIntents.SEND_MEDIA intent with the photo URI as an extra. AetherSMS receives it and opens the conversation picker.

## Photo compression strategy in AetherSMS

The MmsSender applies the following rules before sending a photo :

| Original file size | Action |
|---|---|
| Under 300 KB | No compression, sent as-is at 100% quality |
| 300 KB to 800 KB | Compressed at quality 90, visually lossless |
| 800 KB to 1.4 MB | Compressed at quality 80, image remains sharp |
| Over 1.4 MB | Resized to maximum 2048px and compressed at quality 70 |

This keeps photos within MMS operator limits while avoiding visible blur.

## Privacy model

- No network calls are made to third-party servers for SMS, MMS, notes, contacts, calendar, files, music, or gallery.
- MMS traffic goes through the mobile operator's APN, not through any third-party server.
- Notes and calendar events are encrypted on the device using AES-256-GCM. The key is stored in Android Keystore and is only accessible when the device is unlocked.
- There is no analytics library, no crash reporting library, and no advertising SDK anywhere in the project.
