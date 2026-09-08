# Decryptum 🔐

<p align="center">
  <a href="https://github.com/Doffi4/Decryptum/releases/tag/v1.0.0"><img src="https://img.shields.io/badge/Release-v1.0.0-brightgreen.svg" alt="Version"></a>
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPL v3"></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg" alt="Platform: Android"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Language-Kotlin%202.2-purple.svg" alt="Language: Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-4285F4.svg" alt="Jetpack Compose"></a>
  <a href="https://insert-koin.io/"><img src="https://img.shields.io/badge/DI-Koin-brightgreen.svg" alt="Koin"></a>
</p>

**Decryptum** is a modern, privacy-first, standalone open-source password manager and authenticator for Android built with Jetpack Compose, Material You design, Passkeys (WebAuthn), built-in 2FA/TOTP, and Clean Architecture.

---

### ✨ Core Features

#### 🛡️ Security & Cryptography
- **SQLCipher Full-Database Encryption**: Transparent 256-bit AES page-level database encryption (`sqlcipher-android`) protecting all SQLite tables, records, and metadata at rest.
- **Argon2id Key Derivation**: Master password hashing utilizing memory-hard **Argon2id** (RFC 9106) via `argon2kt`, offering robust protection against modern GPU brute-force attacks.
- **Hardware-Backed Envelope Encryption**: Strong **AES/GCM** encryption (`enc:2:...`) managed via **Android Keystore**. Master keys never leave secure hardware; decrypted keys reside in memory only during the active unlocked session.
- **Biometric Unlock & Master Password**: Secure login using Fingerprint or Face recognition (**BiometricPrompt**) alongside a hardened master password.
- **Compromised Password Audit (HaveIBeenPwned API)**: Zero-knowledge breach detection using the HaveIBeenPwned k-Anonymity API (SHA-1 prefix range query; full passwords or hashes are never sent over the network).
- **Configurable Auto-Lock**: Custom inactivity lock timeout (30 seconds, 1 minute, 5 minutes, 15 minutes, or never).
- **Anti-Snooping Protection**: Optional **`FLAG_SECURE`** enforcement prevents screenshots and recent apps window preview leakage.
- **Enhanced Sensitive Clipboard Auto-Wipe**: Sets `EXTRA_IS_SENSITIVE` on Android 13+ to prevent OS clipboard previews and keyboard snooping, backed by an Android AlarmManager broadcast receiver for guaranteed auto-clearing after 30 seconds.
- **Full CSV Vault Backup & Migration**: Bulk import and export compatible with **Google Chrome**, **Bitwarden**, **LastPass**, and **KeePass**.

#### 🔑 Passkeys (FIDO2 / WebAuthn)
- **Native Android Credential Manager Integration**: First-class support for passwordless passkey registration and login across supported websites and mobile applications.
- **Hardware-Backed Key Pairs**: Cryptographically secure ECDSA P-256 (secp256r1) key pairs generated directly inside Android Keystore.
- **Built-in CBOR & Assertion Engine**: Native authenticator data generation, signature verification, and client data JSON handling.
- **Intuitive Management**: Review and manage registered passkeys directly within account detail views.

#### ⏱️ Built-in 2FA / TOTP Authenticator (RFC 6238)
- **Time-Based One-Time Passwords**: Integrated 2FA authenticator supporting HMAC-SHA1, HMAC-SHA256, and HMAC-SHA512 algorithms, 6- or 8-digit codes, and 30-second / 60-second intervals.
- **Animated Countdown Indicator**: Smooth circular progress timer with real-time token regeneration and one-tap clipboard copy.
- **Live Camera QR Code Scanner**: Instant QR code scanning powered by CameraX and Google ML Kit Barcode Scanning.
- **Gallery Image QR Scanner**: Pick QR code screenshots or photos directly from device storage with ZXing fallback.
- **Manual Entry & Google Authenticator Import**: Manual secret key setup and one-tap batch migration from Google Authenticator (`otpauth-migration://offline?data=...`).

#### ⚡ Android Autofill & Credential Provider
- **System Autofill Integration**: Native Android Autofill Service (`AutofillService`) and Credential Provider for instant suggestions in browsers (Chrome, Firefox, Brave) and native apps.
- **Inline Keyboard Suggestions**: Support for IME chips in keyboards such as Gboard, allowing one-tap logins directly above the keyboard.
- **Material 3 Bottom Sheet Dialog**: Modern bottom sheet presenting matching accounts, site favicons, and dedicated search picker.
- **Intelligent Field Detection & Save Flow**: Form parser (`AutofillStructureParser`) that accurately detects authentication forms, paired with `AutofillSaveActivity` to save new credentials on the fly.

#### 🌐 Multi-Language Support
- **Per-App Language Selection**: Built-in support for **English (🇬🇧)**, **Ukrainian (🇺🇦)**, and **Russian (🇷🇺)**, with automatic system language fallback.
- **Android 13+ Compatibility**: Seamless integration with the system Per-App Language preferences via `AppLocaleManager` and `LocaleManager`, with backward compatibility down to Android 8.0 (API 26).

#### 🎲 Password Generator & Security Audit
- **Granular Customization**: Adjustable length slider (8 to 64 characters) with independent toggles for uppercase, lowercase, numbers, and symbols.
- **Avoid Ambiguous Characters**: Option to exclude easily confused characters (`0O1lI`).
- **One-Tap Presets**: Quick security presets (Weak, Medium, Strong, Very Strong).
- **Animated Strength Meter**: 4-segment real-time visual entropy feedback (`PasswordStrengthBadge`).
- **Security Audit**: Automatic detection and grouping of duplicate, vulnerable, and pwned credentials.

#### 🚀 Performance & Design
- **Material You Dynamic Theming**: Adaptive color schemes based on your Android wallpaper, smooth animations, and pure dark mode launch (`#101418`) without white flash.
- **Custom Navigation Capsule**: Ergonomic floating bottom bar with dedicated Generator, Vault, and Settings tabs.
- **Privacy-Preserving Favicon Fetcher**: Local ICO decoder (`IcoDecoder`) and deferred network loading that strictly avoids external network requests while the vault is locked.
- **Optimized Release**: Minified with **R8 Shrinker** and pre-compiled **Baseline Profiles** for rapid DEX startup.
- **Embedded Developer Diagnostics**: Hidden dev tools (activated by 6 rapid taps on title) featuring real-time FPS/jank frame metrics and CPU load monitor.

---

### 🛠️ Verified Tech Stack
- **Language**: Kotlin 2.2.10 (minSdk 26, targetSdk 37 / Android 16 Ready)
- **UI Framework**: Jetpack Compose (BOM 2026.02), Material 3, Navigation Compose
- **Database**: Room Database 2.8.4 + **SQLCipher 4.6.1** (full 256-bit AES page encryption)
- **Key Derivation & Crypto**: **Argon2kt 1.6.0** (Argon2id RFC 9106), Android Keystore AES-GCM, AndroidX Security Crypto
- **Authentication**: AndroidX Credentials 1.5.0 (Passkeys / WebAuthn), AndroidX Biometric 1.2.0
- **Autofill**: Android Autofill Framework, AndroidX Autofill
- **2FA / QR Scanning**: CameraX 1.4.1, Google ML Kit Barcode Scanning 17.3.0, ZXing Core 3.5.3
- **Dependency Injection**: [Koin](https://insert-koin.io/) 3.5.0 (`koin-android`, `koin-androidx-compose`)
- **Image Loading**: Coil 2.7.0 (domain favicon cache & fallback letter avatars)
- **Localization**: Android 13+ LocaleManager & AndroidX AppCompat
- **Optimization**: AndroidX ProfileInstaller (Static Baseline Profiles) & ProGuard/R8

---

### 📥 Download & Releases
Pre-built, signed APKs and release notes are available on the [**GitHub Releases**](https://github.com/Doffi4/Decryptum/releases) page:
- **`Decryptum-v1.0.0-release.apk`**: Production release signed and optimized with R8.
- **`Decryptum-v1.0.0-debug.apk`**: Debug build with extended logging and diagnostics enabled.

> ℹ️ *Note: Decryptum has officially launched version **v1.0.0**. Cryptographic storage, Passkeys, 2FA/TOTP authenticator, and autofill core are fully functional, verified, and production-ready.*

---

### ☕ Support the Project

If you find Decryptum useful, you can support its independent development:

<p align="left">
  <a href="https://tronscan.org/#/address/TJRMN3nQvG3SQVjZJkV4JcrszNcVYdjQqb" target="_blank">
    <img src="https://img.shields.io/badge/USDT-TRC20-26A17B?style=for-the-badge&logo=tether&logoColor=white" alt="USDT TRC-20">
  </a>
  <a href="https://etherscan.io/address/0xBE7d70b17F26be6E7E34BC84b7c84871f27279F8" target="_blank">
    <img src="https://img.shields.io/badge/Ethereum%20%2F%20EVM-ETH%20%7C%20USDT-3C3C3D?style=for-the-badge&logo=ethereum&logoColor=white" alt="Ethereum EVM">
  </a>
</p>

<details open>
<summary>📋 <b>Click to copy Wallet Addresses</b></summary>

<br>

**USDT (TRC-20 / Tron Network)**
```text
TJRMN3nQvG3SQVjZJkV4JcrszNcVYdjQqb
```

**Ethereum / EVM (ETH, USDT, ERC-20)**
```text
0xBE7d70b17F26be6E7E34BC84b7c84871f27279F8
```

</details>

---

### 📄 License
Distributed under the **GNU General Public License v3.0 (GPLv3)**. See [LICENSE](LICENSE) for details.

---

<details>
<summary><b>🇺🇦 Натисніть тут, щоб прочитати опис українською</b></summary>

<br>

## Decryptum 🔐

**Decryptum** — це сучасний, повністю автономний менеджер паролів та 2FA-аутентифікатор з відкритим вихідним кодом для Android, побудований на базі Jetpack Compose, дизайну Material You, Passkeys (WebAuthn), вбудованого TOTP та повного шифрування бази даних SQLCipher.

### ✨ Основні можливості

#### 🛡️ Безпека та криптографія
- **Повне шифрування бази даних SQLCipher**: Прозоре 256-бітне AES шифрування на рівні сторінок SQLite (`sqlcipher-android`) для надійного захисту всіх записів і метаданих.
- **Деривація ключа через Argon2id**: Захист майстер-пароля сучасним стандартом **Argon2id** (RFC 9106) проти атак перебором на GPU.
- **Апаратне конвертне шифрування AES/GCM**: Ключі шифрування захищені за допомогою **Android Keystore** (`enc:2:...`). Майстер-ключ ніколи не залишає захищений апаратний чип пристрою.
- **Біометрія та майстер-пароль**: Безпечний вхід за відбитком пальця або розпізнаванням обличчя (**BiometricPrompt**).
- **Перевірка на витоки (HaveIBeenPwned API)**: Пошук скомпрометованих паролів через k-Anonymity API (повний пароль або повний хеш ніколи не передаються в мережу).
- **Гнучкий автолок**: Автоматичне блокування при бездіяльності (30 секунд, 1 хвилина, 5 хвилин, 15 хвилин або ніколи).
- **Захист від підглядання (`FLAG_SECURE`)**: Заборона знімків екрана та приховування вмісту у диспетчері програм.
- **Безпечний буфер обміну**: Позначка `EXTRA_IS_SENSITIVE` на Android 13+ та гарантоване очищення через AlarmManager через 30 секунд.
- **Повний бекап та імпорт CSV**: Резервне копіювання та міграція даних із **Google Chrome**, **Bitwarden**, **LastPass** і **KeePass**.

#### 🔑 Підтримка Passkeys (FIDO2 / WebAuthn)
- **Інтеграція з Android Credential Manager**: Безпарольний вхід на підтримуваних сайтах та додатках за допомогою ключів доступу (Passkeys).
- **Апаратні криптографічні ключі**: Генерація ключів ECDSA P-256 (secp256r1) безпосередньо в апаратному сховищі Android Keystore.
- **Вбудований CBOR & WebAuthn рушій**: Нативна генерація даних автентифікатора та підтвердження через біометрію.

#### ⏱️ Вбудований 2FA / TOTP аутентифікатор (RFC 6238)
- **Генератор одноразових паролів**: Підтримка алгоритмів HMAC-SHA1, SHA-256, SHA-512, 6 або 8 цифр, періодів 30 або 60 секунд.
- **Анімований круговий таймер**: Плавний зворотний відлік оновлення коду та копіювання в 1 дотик.
- **Сканер QR-кодів через камеру**: Швидке розпізнавання QR-кодів на базі CameraX та Google ML Kit.
- **Сканування QR із фото галереї**: Зчитування QR-кодів зі скріншотів із резервним парсером ZXing.
- **Імпорт із Google Authenticator**: Миттєвий імпорт усіх акаунтів через схему `otpauth-migration://`.

#### ⚡ Автозаповнення Android та Credential Provider
- **Системне автозаповнення**: Рідний сервіс автозаповнення Android (`AutofillService`) та Credential Provider.
- **Інлайн-підказки в клавіатурі**: Підтримка чіпів у Gboard та інших сучасних IME-клавіатурах.
- **Шторка Material 3**: Зручне спливаюче вікно для швидкого вибору облікового запису.
- **Збереження логінів**: Діалог автоматичного збереження нових паролів при реєстрації чи вході на сайти.

#### 🌐 Підтримка мов (Локалізація)
- Вбудована підтримка **української**, **англійської** та **російської** мов із системною інтеграцією Android 13+ Per-App Language Preferences.

#### 🎲 Генератор паролів та аудит
- Гнучкий слайдер довжини (8–64), фільтр неоднозначних знаків (`0O1lI`), готові пресети надійності та аудит сховища на дублікати і слабкі паролі.

#### 🚀 Продуктивність та оптимізація
- **Material You**: Адаптивні динамічні кольори, темна тема без білої вспышки (`#101418`).
- **Приватне завантаження іконок**: Локальний декодер `IcoDecoder` та відкладене завантаження фавіконів лише після розблокування.
- **Оптимізація R8**: Мінімізований реліз та скомпільовані Baseline Profiles.

---

### 📥 Завантаження та релізи
Готові підписані файли APK доступні на сторінці [**GitHub Releases**](https://github.com/Doffi4/Decryptum/releases):
- **`Decryptum-v1.0.0-release.apk`**: Оптимізована релізна збірка.
- **`Decryptum-v1.0.0-debug.apk`**: Дебаг-збірка для діагностики та тестування.

> ℹ️ *Примітка: Decryptum офіційно вийшов у реліз версії **v1.0.0**. Криптографічне сховище, Passkeys, 2FA/TOTP аутентифікатор та автозаповнення повністю стабільні і готові до щоденного використання.*

### 📄 Ліцензія
Поширюється за ліцензією **GNU General Public License v3.0 (GPLv3)**. Деталі у файлі [LICENSE](LICENSE).

</details>

---

<details>
<summary><b>🇷🇺 Нажмите здесь, чтобы прочитать описание на русском</b></summary>

<br>

## Decryptum 🔐

**Decryptum** — это современный, полностью автономный менеджер паролей и 2FA-аутентификатор с открытым исходным кодом для Android, разработанный на Jetpack Compose с дизайном Material You, поддержкой Passkeys (WebAuthn), встроенным TOTP генератором и полным шифрованием базы данных SQLCipher.

### ✨ Основные возможности

#### 🛡️ Безопасность и криптография
- **Полное шифрование базы данных SQLCipher**: Прозрачное 256-битное AES шифрование на уровне страниц SQLite (`sqlcipher-android`) для надежной защиты всех таблиц, записей и метаданных.
- **Деривация ключей через Argon2id**: Хеширование мастер-пароля современным стандартом **Argon2id** (RFC 9106) с защитой от перебора на GPU.
- **Аппаратное конвертное шифрование AES/GCM**: Ключи шифрования защищены через **Android Keystore** (`enc:2:...`). Мастер-ключ не покидает аппаратный защищенный чип.
- **Биометрия и мастер-пароль**: Разблокировка по отпечатку пальца или лицу (**BiometricPrompt**) и надежный мастер-пароль.
- **Проверка утечек (HaveIBeenPwned API)**: Проверка паролей по базе утечек через анонимный k-Anonymity API (пароль и его полный хеш никогда не передаются по сети).
- **Настраиваемый авто-лок**: Автоматическая блокировка приложения при бездействии (30 сек, 1 мин, 5 мин, 15 мин или никогда).
- **Защита от скриншотов (`FLAG_SECURE`)**: Запрет создания скриншотов и скрытие окна в диспетчере недавних задач.
- **Безопасный буфер обмена**: Флаг `EXTRA_IS_SENSITIVE` на Android 13+ и гарантированная очистка буфера через AlarmManager через 30 секунд.
- **Импорт и экспорт CSV**: Поддержка резервного копирования и миграции из **Google Chrome**, **Bitwarden**, **LastPass** и **KeePass**.

#### 🔑 Поддержка Passkeys (FIDO2 / WebAuthn)
- **Интеграция с Android Credential Manager**: Беспарольный вход на поддерживаемых сайтах и в приложениях с помощью ключей доступа (Passkeys).
- **Аппаратные криптографические ключи**: Генерация пар ключей ECDSA P-256 (secp256r1) в Android Keystore.
- **Встроенный CBOR & WebAuthn движок**: Нативная генерация данных аутентификатора и подтверждение по биометрии.

#### ⏱️ Встроенный 2FA / TOTP аутентификатор (RFC 6238)
- **Генератор одноразовых паролей**: Поддержка HMAC-SHA1, SHA-256, SHA-512, 6 или 8 знаков, периодов 30 или 60 секунд.
- **Анимированный круговой таймер**: Плавный отсчет времени до смены кода и копирование в буфер в 1 тап.
- **Сканер QR-кодов через камеру**: Мгновенное сканирование QR-кодов на базе CameraX и Google ML Kit.
- **Сканирование QR из галереи**: Распознавание QR-кодов со скриншотов с резервным парсером ZXing.
- **Импорт из Google Authenticator**: Миграция всех аккаунтов в 1 клик по схеме `otpauth-migration://`.

#### ⚡ Автозаполнение Android и Credential Provider
- **Системное автозаполнение**: Нативный сервис автозаполнения Android (`AutofillService`) и Credential Provider.
- **Инлайн-подсказки в клавиатуре**: Поддержка чипов в Gboard для входа в один тап прямо над клавиатурой.
- **Шторка Material 3**: Удобное всплывающее окно выбора аккаунта и поиск по хранилищу.
- **Сохранение новых логинов**: Автоматический диалог сохранения паролей при входе или регистрации на сайтах.

#### 🌐 Поддержка языков (Локализация)
- Встроенная поддержка **русского**, **украинского** и **английского** языков с системной интеграцией Android 13+ Per-App Language Preferences.

#### 🎲 Генератор паролей и аудит
- Слайдер длины (8–64), исключение похожих символов (`0O1lI`), готовые пресеты и аудит базы на скомпрометированные и повторяющиеся пароли.

#### 🚀 Производительность и оптимизация
- **Material You**: Динамические цвета обоев, темная тема без белой вспышки (`#101418`).
- **Приватная загрузка иконок**: Локальный декодер `IcoDecoder` и загрузка фавиконов строго после разблокировки хранилища.
- **Оптимизация R8**: Компактный размер и скомпилированные Baseline Profiles.

---

### 📥 Скачать приложение
Готовые установочные файлы (APK) доступны в разделе [**GitHub Releases**](https://github.com/Doffi4/Decryptum/releases):
- **`Decryptum-v1.0.0-release.apk`**: Релизный оптимизированный APK.
- **`Decryptum-v1.0.0-debug.apk`**: Дебаг-версия для разработчиков с логами.

> ℹ️ *Примечание: Decryptum официально вышел в релиз версии **v1.0.0**. Криптографическое хранилище, Passkeys, 2FA/TOTP аутентификатор и ядро автозаполнения полностью стабильны и готовы к повседневному использованию.*

### 📄 Лицензия
Распространяется под лицензией **GNU General Public License v3.0 (GPLv3)**. Подробности в файле [LICENSE](LICENSE).

</details>
