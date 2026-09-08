# Decryptum

Офлайн-менеджер паролей и 2FA-аутентификатор для Android с интеграцией системного автозаполнения и поддержкой Passkeys.

[English](README.md) | [Українська](README.uk.md) | [Русский]

[![Release](https://img.shields.io/github/v/release/Doffi4/Decryptum?style=flat-square)](https://github.com/Doffi4/Decryptum/releases/latest)
[![License](https://img.shields.io/badge/license-GPLv3-blue.svg?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B%20(API%2026%2B)-green.svg?style=flat-square)](https://developer.android.com)

## Скриншоты

| Хранилище | 2FA Аутентификатор | Шторка автозаполнения |
|:---:|:---:|:---:|
| <img src="docs/screenshots/vault.png" width="260" alt="Плейсхолдер хранилища"/> | <img src="docs/screenshots/totp.png" width="260" alt="Плейсхолдер 2FA"/> | <img src="docs/screenshots/autofill.png" width="260" alt="Плейсхолдер автозаполнения"/> |

## Возможности

### Хранилище и шифрование
- Полное шифрование базы данных AES-256 через SQLCipher (`sqlcipher-android`) на уровне страниц.
- Деривация мастер-пароля через Argon2id (RFC 9106) с использованием библиотеки `argon2kt`.
- Аппаратное оборачивание DEK через Android Keystore AES-GCM (`enc:2:...`).
- Биометрическая аутентификация (`BiometricPrompt`) с настраиваемым таймаутом автоблокировки.
- Автоматическая очистка буфера обмена через 30 секунд через `AlarmManager` и флаг `EXTRA_IS_SENSITIVE` на Android 13+.
- Офлайн-экспорт и импорт CSV, совместимый с Bitwarden, KeePass, Chrome и LastPass.
- Интеграция с HaveIBeenPwned k-Anonymity API для проверки скомпрометированных паролей (5-символьный префикс SHA-1 без утечки данных).

### Passkeys / WebAuthn
- Регистрация и аутентификация ключей доступа FIDO2 / WebAuthn через Android Credential Manager API.
- Аппаратная генерация пар ключей ECDSA P-256 (secp256r1) в Android Keystore.
- Встроенные кодировщик и декодировщик CBOR для обработки данных аутентификатора и JSON-клиента.
- Просмотр и управление зарегистрированными Passkeys в деталях учетной записи.

### 2FA / TOTP Аутентификатор
- Генератор одноразовых паролей по времени (RFC 6238) с поддержкой HMAC-SHA1, HMAC-SHA256, HMAC-SHA512.
- Настройка длины кода (6 или 8 цифр) и интервала смены (30 или 60 секунд).
- Таймер обратного отсчета с индикатором прогресса и копированием в буфер в одно касание.
- Сканирование QR-кодов камерой через CameraX и Google ML Kit Barcode Scanning.
- Распознавание QR-кодов с изображений из галереи через ZXing.
- Пакетный импорт аккаунтов из Google Authenticator по схеме `otpauth-migration://`.

### Интеграция с Android
- Реализация системного сервиса `AutofillService` для браузеров (Chrome, Firefox, Brave) и нативных приложений.
- Инлайн-подсказки в клавиатуре (чипы IME) для Gboard и совместимых клавиатур.
- Интеграция с Android 14+ Credential Provider (`DecryptumCredentialProviderService`).
- Парсер структуры форм (`AutofillStructureParser`) для исключения полей поиска и чатов.
- Сохранение новых учетных записей (`AutofillSaveActivity`) при регистрации или входе.
- Поддержка выбора языка приложения (английский, украинский, русский) через Android 13+ `LocaleManager`.
- Опциональная защита окна `FLAG_SECURE` от создания скриншотов и превью в списке задач.

## Стек технологий

- **Язык:** Kotlin 2.2.10 (Target SDK 37, Min SDK 26)
- **UI:** Jetpack Compose, Material 3, Navigation Compose
- **База данных:** Room 2.8.4 + SQLCipher 4.6.1
- **Криптография:** Argon2kt 1.6.0, AndroidX Security Crypto, Android Keystore
- **Аутентификация и автозаполнение:** AndroidX Credentials 1.5.0, Android Autofill Framework
- **2FA и распознавание:** CameraX 1.4.1, Google ML Kit Barcode Scanning 17.3.0, ZXing Core 3.5.3
- **Внедрение зависимостей:** Koin 3.5.0
- **Загрузка иконок:** Coil 2.7.0 (локальный декодер ICO + дисковый кэш)
- **Минимизация кода:** ProGuard / R8 с базовыми профилями (Baseline Profiles)

## Установка

Скачайте подписанный APK со страницы [GitHub Releases](https://github.com/Doffi4/Decryptum/releases):
- `Decryptum-v1.0.0-release.apk` - Оптимизированная релизная сборка.
- `Decryptum-v1.0.0-debug.apk` - Дебаг-сборка с логами и диагностикой.

## Лицензия

Проект распространяется под лицензией GNU General Public License v3.0 (GPLv3). Подробности в файле [LICENSE](LICENSE).
