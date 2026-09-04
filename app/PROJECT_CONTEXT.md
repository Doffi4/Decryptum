# DECRYPTUM — Контекст проєкту для Antigravity IDE

## 📌 Загальна інформація про проєкт
- **Назва додатку (Бренд)**: Decryptum (раніше DoffiSecure). Усі видимі тексти, заголовки, діалоги та Settings містять `Decryptum`.
- **Поточна версія**: `v0.8.0` (Beta Preview). `versionCode = 1`, `versionName = "0.8.0"`.
- **Шлях до проєкту**: `E:\Android studio Projects\DoffiSecure` (корінь Gradle-проєкту, `rootProject.name = "Decryptum"`). Модуль додатку: `E:\Android studio Projects\DoffiSecure\app`.
- **Package / Application ID**: `com.doffi4.doffisecure` (КРИТИЧНО: НЕ перейменовувати! Зміна `applicationId` призведе до втрати даних користувачів. Префікси `doffisecure_*` у Keystore/SharedPreferences також НЕ змінювати).
- **GitHub репозиторій**: `https://github.com/Doffi4/Decryptum`
- **Ліцензія**: GNU General Public License v3.0 (GPLv3).

---

## 🛠️ Стек технологій та конфігурація
- **SDK**: `minSdk = 26` (Android 8.0+), `targetSdk = 37`, `compileSdk = 37`.
- **Мова**: Kotlin 2.2.10.
- **UI Framework**: Jetpack Compose (BOM 2026.02.01), Material 3, Navigation Compose 2.9.8.
- **Архітектура**: Clean Architecture (Data, Domain, Presentation) + MVVM + Use Cases.
- **State Management**: ViewModel + `StateFlow` / `SharedFlow`.
- **База даних**: Room 2.8.4 (KSP 2.3.11) у режимі WAL (`setJournalMode(WRITE_AHEAD_LOGGING)`).
- **Dependency Injection**: **Koin 3.5.0** (`koin-android`, `koin-androidx-compose`). ЖОДНОГО Hilt чи Dagger!
- **Image Loading**: Coil 2.7.0 (`respectCacheHeaders(false)` для надійного дискового кешу фавіконів).
- **Криптографія**: AndroidX Security Crypto (`security-crypto:1.1.0-alpha06`) + конвертне шифрування AES/GCM через Android Keystore (`enc:2:...`).
- **Біометрія**: AndroidX Biometric (`biometric:1.2.0-alpha05` — `BiometricPrompt`).
- **Оптимізація та реліз**: R8 Shrinking (`optimization.enable = true` + `proguard-rules.pro`) + статичний Baseline Profile (`app/src/main/baselineProfiles/baseline-prof.txt`). Розмір Release APK: **~2.61 МБ**.

---

## 💻 Інструкції для виконання команд у терміналі Antigravity
Перед запуском будь-яких Gradle-команд у PowerShell ОБОВ'ЯЗКОВО вказувати шлях до JBR Android Studio:
```powershell
$env:JAVA_HOME = 'E:\Android studio\jbr'

# Збірка Debug
.\gradlew.bat -p 'E:\Android studio Projects\DoffiSecure' :app:assembleDebug

# Збірка підписаного Release APK (~2.61 МБ)
.\gradlew.bat -p 'E:\Android studio Projects\DoffiSecure' :app:assembleRelease

# Запуск юніт-тестів (7/7)
.\gradlew.bat -p 'E:\Android studio Projects\DoffiSecure' :app:testDebugUnitTest

# Перевірка лінтера (lint 0 errors)
.\gradlew.bat -p 'E:\Android studio Projects\DoffiSecure' :app:lintDebug
```

---

## ⚠️ ЖОРСТКІ ПРАВИЛА ТА ОБМЕЖЕННЯ (DO NOT TOUCH)
1. **Капсула навігації (`CustomBottomNavigation.kt`)**: 
   - Користувач власноруч детально доводив дизайн та анімацію капсули.
   - **ПРАВКИ ВНОСИТИ ТІЛЬКИ ЗА ЯВНИМ ПРОХАННЯМ КОРИСТУВАЧА!**
   - Кастомну іконку Генератора (`GeneratorIcon`: тонка риска + зірочки `*` та плюс `+`) НЕ замінювати на стандартний ключ/шестерню.
2. **Ключі Keystore та SharedPreferences**:
   - Жодних перейменувань: `doffisecure_lock`, `doffisecure_dev`, `doffisecure_settings`, `doffisecure_crypto`, `doffisecure_master_key`.
   - Назва файлу бекапу: `doffisecure_passwords.csv`.
3. **Криптографія Keystore**:
   - Операції шифрування/розшифрування НЕ можна розпаралелювати (Android Keystore вимагає суворо послідовного виконання, інакше виникає `KeyStoreException`).
4. **Режим розробника (Dev Mode)**:
   - Активація: 6 швидких тапів по слову `Decryptum` у заголовку (вікно 1.5 с) -> введення пароля `IrkaSec08`. Пароль `IrkaSec08` НЕ змінювати.
   - Оверлеї CPU та FPS/Jank показуються **ТІЛЬКИ** на вкладці «Паролі». На вкладках «Генератор» та «Налаштування» вони сховані (`overlayHidden = route == Settings || route == Generator`). Зберігати цю поведінку!
5. **Частота оновлення екрану (`RefreshRateController.kt`)**:
   - Режими: `ACTIVE (120Hz)` та `IDLE_60 (60Hz)` через 10 с бездіяльності.
   - **НЕ повертати 30 FPS ступінь** (викликає відчутний мікрофриз при першому дотику після простою).
6. **Оптимізація R8**:
   - R8 увімкнено для релізу (`optimization.enable = true`). Всі keep-правила у `app/proguard-rules.pro` для Koin, Room, ViewModels та DevMode мають зберігатися.
7. **Git Workflow**:
   - Репозиторій: `https://github.com/Doffi4/Decryptum.git`. Гілка: `main`.
   - Усі локальні логи (`logcat.txt`), звіти помилок, `.idea` та `.apk` строго в `.gitignore`.

---

## 🎯 Поточні цілі та план розвитку (Роадмап v0.8.0 -> v1.0.0)
1. **Зменшення затримки холодного старту**:
   - Поточний час розігріву: ~2 с (ініціалізація Koin + `VaultWarmup` розшифрування + перший композит).
   - Оптимізувати `reportFullyDrawn()` та послідовність прогріву сховища.
2. **Плавність скролу списку (500+ елементів)**:
   - Протестувати та підібрати ідеальний `prefetchStrategy` (0/4/8/12) для `LazyColumn` на пристроях із високою частотою (наприклад, OnePlus CPH2653 120Hz).
3. **On-device Baseline Profile**:
   - За бажанням реалізувати динамічну генерацію Art Profile на реальному девайсі.
