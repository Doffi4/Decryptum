# Decryptum 🔐

<p align="center">
  <a href="https://github.com/Doffi4/Decryptum/releases/tag/v0.8.0"><img src="https://img.shields.io/badge/Release-v0.8.0%20Beta-orange.svg" alt="Version"></a>
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPL v3"></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg" alt="Platform: Android"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Language-Kotlin%202.2-purple.svg" alt="Language: Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-4285F4.svg" alt="Jetpack Compose"></a>
  <a href="https://insert-koin.io/"><img src="https://img.shields.io/badge/DI-Koin-brightgreen.svg" alt="Koin"></a>
</p>

**Decryptum** is a modern, privacy-first, standalone open-source password manager for Android built with Jetpack Compose, Material You design, and Clean Architecture.

---

### ✨ Core Features

#### 🛡️ Security & Cryptography
- **Hardware-Backed Envelope Encryption**: Strong **AES/GCM** encryption (`enc:2:...`) managed via **Android Keystore**. Master keys never leave secure hardware; decrypted keys reside in memory only during the active unlocked session.
- **Biometric Unlock & Master Password**: Secure login using Fingerprint or Face recognition (**BiometricPrompt**) alongside a hashed master password.
- **Configurable Auto-Lock**: Custom inactivity lock timeout (30 seconds, 1 minute, 5 minutes, 15 minutes, or never).
- **Anti-Snooping Protection**: Optional **`FLAG_SECURE`** enforcement prevents screenshots and recent apps window preview leakage.
- **Secure Clipboard Auto-Wipe**: Automatically clears copied passwords from the system clipboard after 30 seconds.
- **Full CSV Vault Backup & Migration**: Bulk import and export compatible with **Google Chrome**, **Bitwarden**, **LastPass**, and **KeePass**.

#### 🎲 Password Generator & Audit
- **Granular Customization**: Adjustable length slider (8 to 64 characters) with independent toggles for uppercase, lowercase, numbers, and symbols.
- **Avoid Ambiguous Characters**: Option to exclude easily confused characters (`0O1lI`).
- **One-Tap Presets**: Quick security presets (Weak, Medium, Strong, Very Strong).
- **Animated Strength Meter**: 4-segment real-time visual entropy feedback (`PasswordStrengthBadge`).
- **Security Audit**: Automatic detection and grouping of duplicate and vulnerable credentials.

#### ⚡ Performance & Design
- **Material You Dynamic Theming**: Adaptive color schemes based on your Android wallpaper, smooth animations, and pure dark mode launch (`#101418`) without white flash.
- **Custom Navigation Capsule**: Ergonomic floating bottom bar with dedicated Generator, Vault, and Settings tabs.
- **VaultWarmup Architecture**: Decryption pre-warming and favicon caching occur during the lock screen phase to ensure the vault renders instantly upon unlock.
- **Ultra-Lightweight (~2.6 MB APK)**: Stripped and optimized with **R8 Shrinker** and pre-compiled **Baseline Profiles** for fast DEX startup.
- **Embedded Developer Diagnostics**: Hidden dev tools (activated by 6 rapid taps on title) featuring real-time FPS/jank frame metrics and CPU load monitor.

---

### 🛠️ Verified Tech Stack
- **Language**: Kotlin 2.2.10 (minSdk 26, targetSdk 37)
- **UI Framework**: Jetpack Compose (BOM 2026.02), Material 3, Navigation Compose
- **Architecture**: Clean Architecture (Data, Domain, Presentation) + MVVM
- **Dependency Injection**: [Koin](https://insert-koin.io/) 3.5.0 (`koin-android`, `koin-androidx-compose`)
- **Local Storage**: Room Database 2.8.4 (KSP, Write-Ahead Logging WAL mode)
- **Security**: AndroidX Security Crypto & AndroidX Biometric 1.2.0
- **Image Loading**: Coil 2.7.0 (domain favicon cache & fallback letter avatars)
- **Optimization**: AndroidX ProfileInstaller (Static Baseline Profiles) & ProGuard/R8

---

### 📥 Download & Releases
Pre-built, signed APKs and release notes are available on the [**GitHub Releases**](https://github.com/Doffi4/Decryptum/releases) page.

> ⚠️ *Note: Decryptum is currently in **v0.8.0 Beta Preview**. Cryptographic storage and generator are fully functional; cold-start latency and large vault scrolling optimizations are actively being refined.*

---

### ☕ Support the Project

If you find Decryptum useful, you can support its independent development:

<p align="left">
  <img src="https://img.shields.io/badge/USDT-TRC20-26A17B?style=for-the-badge&logo=tether&logoColor=white" alt="USDT TRC-20">
  <img src="https://img.shields.io/badge/Ethereum%20%2F%20EVM-ETH%20%7C%20USDT-3C3C3D?style=for-the-badge&logo=ethereum&logoColor=white" alt="Ethereum EVM">
</p>

<details>
<summary>📋 <b>Click to reveal Crypto Wallet Addresses</b></summary>

<br>

**USDT (TRC-20 / Tron Network)**
```text
TJRMN3nQvG3SQVjZJkV4JcrszNcVYdjQqb
