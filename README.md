# AI Dev Assistant

Professional AI + Developer assistant for Android (Kotlin + Jetpack Compose).

## Features
- Multi-model chat (Groq / Gemini / OpenRouter / Cloudflare)
- Firebase Realtime Database persistence
- File / Image / Video attachment ready
- Microphone (voice input) ready
- Background service + Notifications
- Dark professional AI/Dev UI

## Firebase
Database URL: `https://ai-api-project-1-default-rtdb.firebaseio.com/`

## Build with GitHub Actions
1. Push to `main`
2. Go to Actions → Build Release APK
3. Download artifact

## Local build
```bash
./gradlew assembleRelease
```

APK output: `app/build/outputs/apk/release/app-release.apk`

## Permissions
- Internet
- Read images / video / audio
- Camera
- Microphone
- Notifications
- Foreground service (background)
- Boot completed

## Package
`com.aidev.assistant`

## Encrypted API Keys
All keys are stored AES-256-GCM encrypted inside `SecretVault.kt`.
Decrypt at runtime via:
```kotlin
val groqKey = SecretVault.randomKey("groq")
val all = SecretVault.allServices()
```
Master key is derived with PBKDF2-HMAC-SHA256 (120k iterations). Fully reversible.
