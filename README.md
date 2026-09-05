# Decryptum 🔐

<p align="center">
  <a href="https://github.com/Doffi4/Decryptum/releases/tag/v0.9.0"><img src="https://img.shields.io/badge/Release-v0.9.0%20Beta-orange.svg" alt="Version"></a>
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

#### ⚡ Android Autofill & Credential Provider
- **System Autofill Integration**: Native Android Autofill Service (`AutofillService`) for instant credential suggestions in browsers (Chrome, Firefox, Brave) and native apps.
- **Inline Keyboard Suggestions**: First-class support for IME chips in keyboards such as Gboard, allowing one-tap logins directly above the keyboard.
- **Material 3 Bottom Sheet Dialog**: Modern bottom sheet presenting matching accounts, site favicons, and dedicated search picker.
- **Intelligent Field Detection**: Strict structure parsing (`AutofillStructureParser`) that accurately differentiates actual login/registration forms from search queries, chat inputs, and notes.
- > ℹ️ **Status Note**: *The autofill engine is fully functional, secure, and reliably fills credentials across browsers and native apps without issues. Further UX/ergonomic polishing and visual refinements are actively ongoing for an even smoother user experience.*

#### 🌐 Multi-Language Support
- **Per-App Language Selection**: Built-in support for **English**, **Ukrainian**, and **Russian**, with automatic system language fallback.
- **Android 13+ Compatibility**: Seamless integration with the system Per-App Language preferences via `AppLocaleManager` and `LocaleManager`, with backward compatibility down to Android 8.0 (API 26).

#### 🎲 Password Generator & Security Audit
- **Granular Customization**: Adjustable length slider (8 to 64 characters) with independent toggles for uppercase, lowercase, numbers, and symbols.
- **Avoid Ambiguous Characters**: Option to exclude easily confused characters (`0O1lI`).
- **One-Tap Presets**: Quick security presets (Weak, Medium, Strong, Very Strong).
- **Animated Strength Meter**: 4-segment real-time visual entropy feedback (`PasswordStrengthBadge`).
- **Security Audit**: Automatic detection and grouping of duplicate and vulnerable credentials.

#### 🚀 Performance & Design
- **Material You Dynamic Theming**: Adaptive color schemes based on your Android wallpaper, smooth animations, and pure dark mode launch (`#101418`) without white flash.
- **Custom Navigation Capsule**: Ergonomic floating bottom bar with dedicated Generator, Vault, and Settings tabs.
- **VaultWarmup Architecture**: Decryption pre-warming and favicon caching occur during the lock screen phase to ensure the vault renders instantly upon unlock.
- **Ultra-Lightweight (~2.6 MB APK)**: Stripped and optimized with **R8 Shrinker** and pre-compiled **Baseline Profiles** for fast DEX startup.
- **Embedded Developer Diagnostics**: Hidden dev tools (activated by 6 rapid taps on title) featuring real-time FPS/jank frame metrics and CPU load monitor.

---

### 🛠️ Verified Tech Stack
- **Language**: Kotlin 2.2.10 (minSdk 26, targetSdk 37 / Android 16 Ready)
- **UI Framework**: Jetpack Compose (BOM 2026.02), Material 3, Navigation Compose
- **Autofill**: Android Autofill Framework, AndroidX Autofill, Credential Provider
- **Localization**: Android 13+ LocaleManager & AndroidX AppCompat
- **Architecture**: Clean Architecture (Data, Domain, Presentation) + MVVM
- **Dependency Injection**: [Koin](https://insert-koin.io/) 3.5.0 (`koin-android`, `koin-androidx-compose`)
- **Local Storage**: Room Database 2.8.4 (KSP, Write-Ahead Logging WAL mode)
- **Security**: AndroidX Security Crypto & AndroidX Biometric 1.2.0 (Android Keystore, AES-GCM)
- **Image Loading**: Coil 2.7.0 (domain favicon cache & fallback letter avatars)
- **Optimization**: AndroidX ProfileInstaller (Static Baseline Profiles) & ProGuard/R8

---

### 📥 Download & Releases
Pre-built, signed APKs and release notes are available on the [**GitHub Releases**](https://github.com/Doffi4/Decryptum/releases) page.

> ⚠️ *Note: Decryptum is currently in **v0.9.0 Beta**. Cryptographic storage, generator, and autofill core are fully functional and reliable; UI refinements and advanced convenience options continue to be developed.*

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

**Decryptum** — це сучасний, повністю автономний менеджер паролів з відкритим вихідним кодом для Android, побудований на базі Jetpack Compose, дизайну Material You, чистої архітектури (Clean Architecture) та апаратного шифрування Android Keystore.

### ✨ Основні можливості

#### 🛡️ Безпека та криптографія
- **Апаратне конвертне шифрування AES/GCM**: Ваші ключі шифрування захищені за допомогою **Android Keystore** (`enc:2:...`). Майстер-ключ ніколи не залишає захищений апаратний чип пристрою, а розшифровані дані знаходяться в оперативній пам'яті виключно під час активної сесії.
- **Біометрія та майстер-пароль**: Безпечний вхід за відбитком пальця або розпізнаванням обличчя (**BiometricPrompt**) разом із хешованим майстер-паролем.
- **Гнучкий автолок**: Автоматичне блокування при бездіяльності (30 секунд, 1 хвилина, 5 хвилин, 15 хвилин або ніколи).
- **Захист від підглядання (`FLAG_SECURE`)**: Заборона знімків екрана та приховування вмісту у списку останніх запущених програм.
- **Безпечний буфер обміну**: Автоматичне очищення скопійованого пароля з буфера обміну через 30 секунд.
- **Повний бекап та імпорт CSV**: Резервне копіювання та міграція даних із **Google Chrome**, **Bitwarden**, **LastPass** і **KeePass**.

#### ⚡ Автозаповнення Android та Credential Provider
- **Системне автозаповнення**: Рідний сервіс автозаповнення Android (`AutofillService`) для швидкої підстановки логінів і паролів у браузерах (Chrome, Firefox, Brave тощо) та додатках.
- **Інлайн-підказки в клавіатурі**: Підтримка чіпів у Gboard та інших сучасних IME-клавіатурах для входу в один дотик безпосередньо над клавіатурою.
- **Шторка Material 3**: Зручне спливаюче вікно для вибору облікового запису з фавіконами сайтів та кнопкою швидкого пошуку у сховищі.
- **Розумне розпізнавання полів**: Суворий аналіз структури форми (`AutofillStructureParser`), що запобігає появі підказок у звичайних чатах месенджерів, нотатках чи рядках пошуку.
- > ℹ️ **Статус автозаповнення**: *Рушій автозаповнення повністю функціональний, безпечний і стабільно виконує свої завдання у браузерах і додатках без жодних збоїв. Наразі триває подальше вдосконалення інтерфейсу та ергономіки для ще зручнішого користування.*

#### 🌐 Підтримка мов (Локалізація)
- **Вибір мови додатку**: Повна підтримка **української**, **англійської** та **російської** мов із можливістю перемикання в налаштуваннях або використання системної мови.
- **Інтеграція з Android 13+**: Підтримка системного меню вибору мови для конкретного додатку (Per-App Language Preferences) через `AppLocaleManager`.

#### 🎲 Генератор паролів та аудит
- **Тонке налаштування**: Повзунок довжини (від 8 до 64 символів), перемикачі великих/малих літер, цифр і спецсимволів.
- **Виключення неоднозначних символів**: Можливість прибрати схожі символи (`0O1lI`).
- **Швидкі пресети**: Готові профілі надійності (Слабкий, Середній, Сильний, Максимальний).
- **Живий індикатор надійності**: 4-сегментна анімована оцінка ентропії пароля.
- **Аудит безпеки**: Автоматичне виявлення вразливих, однакових та слабких паролів у вашому сховищі.

#### 🚀 Продуктивність та оптимізація
- **Material You**: Адаптивні динамічні кольори під шпалери, плавні анімації та запуск без білого миготіння у темній темі (`#101418`).
- **Кастомна навігація**: Зручна плаваюча капсула навігації з вкладками Генератора, Сховища та Налаштувань.
- **VaultWarmup**: Прогрів дешифрування та кешування іконок прямо на екрані блокування для миттєвого відображення списку після розблокування.
- **Компактний розмір (~2.6 МБ APK)**: Оптимізація через **R8 Shrinker** та попередньо скомпільовані **Baseline Profiles** для надшвидкого холодного запуску.
- **Вбудована діагностика розробника**: Приховане меню для дебагу (активується 6 тапами по заголовку) з монітором FPS, втрачених кадрів та навантаження на процесор.

---

### 🛠️ Стек технологій
- **Мова**: Kotlin 2.2.10 (minSdk 26, targetSdk 37)
- **UI**: Jetpack Compose (BOM 2026.02), Material 3, Navigation Compose
- **Автозаповнення**: Android Autofill Framework, AndroidX Autofill, Credential Provider
- **Впровадження залежностей (DI)**: [Koin](https://insert-koin.io/) 3.5.0
- **База даних**: Room 2.8.4 (KSP, режим WAL)
- **Безпека**: AndroidX Security Crypto & Biometrics (Android Keystore, AES-GCM)
- **Завантаження іконок**: Coil 2.7.0
- **Оптимізація**: AndroidX ProfileInstaller (Baseline Profiles) & ProGuard/R8

---

### 📥 Завантаження та релізи
Готові підписані файли APK доступні на сторінці [**GitHub Releases**](https://github.com/Doffi4/Decryptum/releases).

> ⚠️ *Decryptum наразі знаходиться у версії **v0.9.0 Beta**. Криптографічне сховище, генератор та рушій автозаповнення повністю стабільні; робота над шліфуванням зручності інтерфейсу продовжується.*

### 📄 Ліцензія
Поширюється за ліцензією **GNU General Public License v3.0 (GPLv3)**. Деталі у файлі [LICENSE](LICENSE).

</details>

---

<details>
<summary><b>🇷🇺 Нажмите здесь, чтобы прочитать описание на русском</b></summary>

<br>

## Decryptum 🔐

**Decryptum** — это современный, полностью автономный менеджер паролей с открытым исходным кодом для Android, разработанный на Jetpack Compose с дизайном Material You, чистой архитектурой (Clean Architecture) и аппаратным шифрованием Android Keystore.

### ✨ Основные возможности

#### 🛡️ Безопасность и криптография
- **Аппаратное конвертное шифрование AES/GCM**: Ваши ключи шифрования защищены через **Android Keystore** (`enc:2:...`). Мастер-ключ не покидает защищенный чип устройства, а расшифрованные данные хранятся в оперативной памяти только во время разблокированной сессии.
- **Биометрия и мастер-пароль**: Разблокировка по отпечатку пальца или лицу (**BiometricPrompt**) и хешированный мастер-пароль.
- **Настраиваемый авто-лок**: Блокировка приложения при бездействии (30 сек, 1 мин, 5 мин, 15 мин или никогда).
- **Защита от скриншотов (`FLAG_SECURE`)**: Запрет создания снимков экрана и скрытие содержимого в диспетчере недавних задач.
- **Безопасный буфер обмена**: Автоматическое удаление скопированного пароля из буфера через 30 секунд.
- **Импорт и экспорт CSV**: Поддержка резервного копирования и миграции из **Google Chrome**, **Bitwarden**, **LastPass** и **KeePass**.

#### ⚡ Автозаполнение Android и Credential Provider
- **Системное автозаполнение**: Нативный сервис автозаполнения Android (`AutofillService`) для быстрой подстановки данных в браузерах (Chrome, Firefox, Brave) и приложениях.
- **Инлайн-подсказки в клавиатуре**: Поддержка чипов в Gboard для входа в один тап прямо над клавиатурой.
- **Шторка Material 3**: Удобное всплывающее окно выбора аккаунта с фавиконами сайтов и кнопкой поиска в хранилище.
- **Умное распознавание полей**: Строгий парсер структуры форм (`AutofillStructureParser`), исключающий ложные срабатывания в чатах, поисковиках и заметках.
- > ℹ️ **Статус автозаполнения**: *Движок автозаполнения полностью функционален, безопасен и стабильно выполняет свою работу в браузерах и приложениях без каких-либо сбоев. В настоящее время продолжается доработка удобства интерфейса (UX) и эргономики.*

#### 🌐 Поддержка языков (Локализация)
- **Выбор языка в приложении**: Встроенная поддержка **русского**, **украинского** и **английского** языков.
- **Интеграция с Android 13+**: Поддержка системного меню выбора языка приложения (Per-App Language) через `AppLocaleManager`.

#### 🎲 Генератор паролей и аудит
- **Гибкая настройка**: Слайдер длины (8–64 символа), переключатели регистра, цифр и спецсимволов.
- **Исключение похожих символов**: Отключение неоднозначных знаков (`0O1lI`).
- **Готовые пресеты**: Быстрый выбор сложности (Слабый, Средний, Сильный, Максимальный).
- **Живая оценка надежности**: Анимированный 4-сегментный индикатор сложности пароля.
- **Анализ уязвимостей**: Автоматический поиск дублирующихся и слабых паролей.

#### 🚀 Производительность и оптимизация
- **Material You**: Динамические цвета под обои рабочего стола, темный запуск без белой вспышки (`#101418`) и плавная кастомная капсула навигации.
- **VaultWarmup**: Фоновая расшифровка и кэширование иконок сайтов прямо во время показа экрана блокировки для мгновенного отклика списка.
- **Компактный APK (~2.6 МБ)**: Полная оптимизация R8 и встроенный **Baseline Profile** для быстрого холодного старта.
- **Встроенная диагностика**: Скрытый режим разработчика (6 быстрых тапов по заголовку) с оверлеями FPS, дропов кадров и нагрузки на процессор.

---

### 🛠️ Реальный стек технологий
- **Язык**: Kotlin 2.2.10 (minSdk 26, targetSdk 37)
- **UI**: Jetpack Compose (BOM 2026.02), Material 3, Navigation Compose
- **Автозаполнение**: Android Autofill Framework, AndroidX Autofill, Credential Provider
- **Внедрение зависимостей (DI)**: **Koin 3.5.0** (`koin-android`, `koin-androidx-compose`)
- **База данных**: Room 2.8.4 (KSP, режим WAL)
- **Безопасность**: AndroidX Security Crypto & Biometrics (Android Keystore, AES-GCM)
- **Загрузка фавиконов**: Coil 2.7.0
- **Оптимизация**: AndroidX ProfileInstaller (Baseline Profiles) & ProGuard/R8

---

### 📥 Скачать приложение
Готовые установочные файлы (APK) доступны в разделе [**GitHub Releases**](https://github.com/Doffi4/Decryptum/releases).

> ⚠️ *Decryptum находится на стадии **v0.9.0 Beta**. Защитное хранилище, генератор и ядро автозаполнения полностью работают; полировка удобства продолжается.*

### 📄 Лицензия
Распространяется под лицензией **GNU General Public License v3.0 (GPLv3)**. Подробности в файле [LICENSE](LICENSE).

</details>
