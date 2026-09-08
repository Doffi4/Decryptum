# Decryptum

Офлайн-менеджер паролів та 2FA-аутентифікатор для Android з інтеграцією системного автозаповнення та підтримкою Passkeys.

[English](README.md) | [Українська] | [Русский](README.ru.md)

[![Release](https://img.shields.io/github/v/release/Doffi4/Decryptum?style=flat-square)](https://github.com/Doffi4/Decryptum/releases/latest)
[![License](https://img.shields.io/badge/license-GPLv3-blue.svg?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B%20(API%2026%2B)-green.svg?style=flat-square)](https://developer.android.com)

## Скріншоти

| Сховище | 2FA Аутентифікатор | Шторка автозаповнення |
|:---:|:---:|:---:|
| <img src="docs/screenshots/vault.png" width="260" alt="Плейсхолдер сховища"/> | <img src="docs/screenshots/totp.png" width="260" alt="Плейсхолдер 2FA"/> | <img src="docs/screenshots/autofill.png" width="260" alt="Плейсхолдер автозаповнення"/> |

## Можливості

### Сховище та шифрування
- Повне шифрування бази даних AES-256 через SQLCipher (`sqlcipher-android`) на рівні сторінок.
- Деривація майстер-пароля через Argon2id (RFC 9106) за допомогою бібліотеки `argon2kt`.
- Апаратне загортання DEK через Android Keystore AES-GCM (`enc:2:...`).
- Біометрична автентифікація (`BiometricPrompt`) з настроюваним тайм-аутом автоблокування.
- Автоматичне очищення буфера обміну через 30 секунд за допомогою `AlarmManager` та прапорця `EXTRA_IS_SENSITIVE` на Android 13+.
- Офлайн-експорт та імпорт CSV, сумісний з Bitwarden, KeePass, Chrome та LastPass.
- Інтеграція з HaveIBeenPwned k-Anonymity API для перевірки скомпрометованих паролів (5-символьний префікс SHA-1 без витоку даних).

### Passkeys / WebAuthn
- Реєстрація та автентифікація ключів доступу FIDO2 / WebAuthn через Android Credential Manager API.
- Апаратна генерація пар ключів ECDSA P-256 (secp256r1) в Android Keystore.
- Вбудовані кодувальник і декодувальник CBOR для обробки даних автентифікатора та JSON-клієнта.
- Перегляд та керування зареєстрованими Passkeys у деталях облікового запису.

### 2FA / TOTP Аутентифікатор
- Генератор одноразових паролів за часом (RFC 6238) з підтримкою HMAC-SHA1, HMAC-SHA256, HMAC-SHA512.
- Налаштування довжини коду (6 або 8 цифр) та інтервалу оновлення (30 або 60 секунд).
- Таймер зворотного відліку з індикатором прогресу та копіюванням у буфер в один дотик.
- Сканування QR-кодів камерою через CameraX та Google ML Kit Barcode Scanning.
- Розпізнавання QR-кодів із зображень галереї через ZXing.
- Пакетний імпорт акаунтів з Google Authenticator за схемою `otpauth-migration://`.

### Інтеграція з Android
- Реалізація системного сервісу `AutofillService` для браузерів (Chrome, Firefox, Brave) та нативних додатків.
- Інлайн-підказки в клавіатурі (чіпи IME) для Gboard та сумісних клавіатур.
- Інтеграція з Android 14+ Credential Provider (`DecryptumCredentialProviderService`).
- Парсер структури форм (`AutofillStructureParser`) для фільтрації полів пошуку та повідомлень у чатах.
- Збереження нових облікових записів (`AutofillSaveActivity`) під час реєстрації або входу.
- Підтримка вибору мови додатка (англійська, українська, російська) через Android 13+ `LocaleManager`.
- Опціональний захист вікна `FLAG_SECURE` від знімків екрана та попереднього перегляду в диспетчері задач.

## Стек технологій

- **Мова:** Kotlin 2.2.10 (Target SDK 37, Min SDK 26)
- **UI:** Jetpack Compose, Material 3, Navigation Compose
- **База даних:** Room 2.8.4 + SQLCipher 4.6.1
- **Криптографія:** Argon2kt 1.6.0, AndroidX Security Crypto, Android Keystore
- **Автентифікація та автозаповнення:** AndroidX Credentials 1.5.0, Android Autofill Framework
- **2FA та розпізнавання:** CameraX 1.4.1, Google ML Kit Barcode Scanning 17.3.0, ZXing Core 3.5.3
- **Впровадження залежностей:** Koin 3.5.0
- **Завантаження іконок:** Coil 2.7.0 (локальний декодер ICO + дисковий кеш)
- **Мінімізація коду:** ProGuard / R8 з базовими профілями (Baseline Profiles)

## Встановлення

Завантажте підписаний APK зі сторінки [GitHub Releases](https://github.com/Doffi4/Decryptum/releases):
- `Decryptum-v1.0.0-release.apk` - Оптимізована релізна збірка.
- `Decryptum-v1.0.0-debug.apk` - Дебаг-збірка з логами та діагностикою.

## Ліцензія

Проєкт поширюється за ліцензією GNU General Public License v3.0 (GPLv3). Подробиці у файлі [LICENSE](LICENSE).
