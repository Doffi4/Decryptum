# Decryptum (DoffiSecure) 🔐

<p align="center">
  <a href="#-english">English</a> •
  <a href="#-русский">Русский</a> •
  <a href="#-українська">Українська</a>
</p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPL v3"></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform: Android"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Language-Kotlin-purple.svg" alt="Language: Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="Jetpack Compose"></a>
</p>

---

## 🇬🇧 English

**Decryptum** (also known as **DoffiSecure**) is a modern, privacy-first, open-source password manager for Android built with Jetpack Compose, Material You design, and Clean Architecture.

### ✨ Features
- 🛡️ **Zero-Knowledge Local Storage**: All passwords are encrypted using strong cryptographic primitives and stored exclusively on your device.
- 🎲 **Customizable Password Generator**: Generate strong passwords with configurable length, uppercase/lowercase characters, numbers, and special symbols.
- 🔍 **Security Audit & Duplicate Detection**: Automatically highlights duplicate, weak, or compromised passwords.
- 🎨 **Material You (Material 3) UI**: Fluid animations, dynamic color support based on Android wallpaper, and seamless dark/light modes.
- ⚡ **Lightning Fast Search**: Instantly filter credentials by service name, URL, or login.
- 🧱 **Clean Architecture**: Designed with maintainability, scalability, and testability in mind.

### 🛠️ Tech Stack
- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) & Material 3
- **Architecture**: Clean Architecture (Data, Domain, Presentation) + MVVM
- **Database**: [Room Database](https://developer.android.com/training/data-storage/room)
- **Dependency Injection**: [Dagger Hilt](https://dagger.dev/hilt/)
- **Async & Reactive**: Kotlin Coroutines & StateFlow

### 🚀 Getting Started & Build Instructions
1. **Clone the repository:**
   ```bash
   git clone https://github.com/Doffi4/Decryptum.git
   cd Decryptum
   ```
2. **Open in Android Studio:**
   - Launch Android Studio.
   - Select **Open** and choose the `Decryptum` folder.
   - Wait for Gradle Sync to complete.
3. **Run the Application:**
   - Connect an Android device with USB debugging enabled or launch an Android Virtual Device (AVD).
   - Press **Run** (`Shift + F10`) or click the green Play button.

### 📄 License
Distributed under the **GNU General Public License v3.0 (GPLv3)**. See [LICENSE](LICENSE) for more information.

---

## 🇷🇺 Русский

**Decryptum** (ранее известный как **DoffiSecure**) — это современный, полностью автономный менеджер паролей с открытым исходным кодом для Android, разработанный на Jetpack Compose с дизайном Material You и чистой архитектурой (Clean Architecture).

### ✨ Основные возможности
- 🛡️ **Локальная безопасность и шифрование**: Ваши пароли надежно шифруются и хранятся исключительно локально на вашем устройстве без сторонних облаков.
- 🎲 **Генератор надежных паролей**: Гибкая настройка длины, регистра букв, цифр и специальных символов.
- 🔍 **Анализ безопасности и поиск дубликатов**: Автоматическое обнаружение повторяющихся и слабых паролей для предотвращения утечек.
- 🎨 **Интерфейс Material You (Material 3)**: Плавные анимации, поддержка динамических цветов системы и темная тема.
- ⚡ **Быстрый поиск**: Мгновенный поиск нужных учетных записей по названию сервиса или логину.

### 🛠️ Стек технологий
- **Язык**: Kotlin
- **Интерфейс**: Jetpack Compose, Material 3
- **Архитектура**: Clean Architecture + MVVM + Use Cases
- **База данных**: Room
- **Внедрение зависимостей**: Dagger Hilt
- **Асинхронность**: Coroutines & Flow

### 🚀 Инструкция по сборке и запуску
1. **Клонируйте репозиторий:**
   ```bash
   git clone https://github.com/Doffi4/Decryptum.git
   cd Decryptum
   ```
2. **Откройте проект в Android Studio:**
   - Запустите Android Studio, выберите **Open** и укажите папку проекта.
   - Дождитесь завершения синхронизации Gradle.
3. **Запустите приложение:**
   - Подключите реальное устройство через USB или запустите эмулятор.
   - Нажмите кнопку **Run** (`Shift + F10`).

### 📄 Лицензия
Проект распространяется под условиями открытой лицензии **GNU General Public License v3.0 (GPLv3)**. Подробности смотрите в файле [LICENSE](LICENSE).

---

## 🇺🇦 Українська

**Decryptum** (також відомий як **DoffiSecure**) — це сучасний, приватний та швидкий менеджер паролів з відкритим вихідним кодом для Android, створений на Jetpack Compose із дизайном Material You та Clean Architecture.

### ✨ Особливості
- 🛡️ **Повна конфіденційність**: Шифрування та локальне збереження паролів без використання сторонніх серверів.
- 🎲 **Генератор надійних паролів**: Створення паролів будь-якої складності за кілька кліків.
- 🔍 **Аналіз безпеки**: Пошук дублікатів та оцінка надійності паролів.
- 🎨 **Сучасний дизайн Material 3**: Плавні анімації та темна тема.

### 📄 Ліцензія
Поширюється за відкритою ліцензією **GNU General Public License v3.0 (GPLv3)**.
