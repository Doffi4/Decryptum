# Decryptum — аудит готовности к публичному выпуску

Дата: **7 октября 2026**. Проверяемый commit: **`a74b6a1ab6819593f25ab40cdc5f6f918833fed6`**, локальная ветка и GitHub `main` совпали на момент проверки. Версия приложения: **1.0.1 / versionCode 5**.

Это аудит исходников, продукта и публичного представления с планом работ. Это **не независимая сертификация безопасности** и не подтверждение безопасности на реальном устройстве. Фазы 2–6 не выполнены; исходники приложения не изменялись.

Уровни уверенности:

- **Код:** поведение непосредственно следует из просмотренных исходников; его выполнение на устройстве может требовать отдельной проверки.
- **Проверка:** получен результат команды, тестов, просмотра существующего изображения или GitHub API.
- **Риск:** последствия следуют из обнаруженного пути, но авария/эксплуатация не воспроизводилась на устройстве.
- **Не проверено:** необходимого окружения или доказательств нет. Отсутствие обнаруженной проблемы не означает подтверждённую безопасность.

Приоритеты: **Critical** — потеря данных, выдача секретов через нарушенную границу блокировки; **High** — безопасность интеграций, сохранность восстановления, блокеры выпуска и ложные обещания; **Medium** — доступность, состояния интерфейса и документация; косметика не входит в обязательный объём.

## Current product state

**Вывод: у Decryptum есть содержательная база продукта, но текущую ветку нельзя рекомендовать как готовую к более сильному публичному выпуску.** Сначала нужны исправления границы блокировки, миграций и восстановления; далее — честные состояния проверки безопасности, проверенный autofill и решение по passkeys. Улучшение внешнего вида само по себе эти проблемы не решит.

Подтверждено кодом:

- Локальный Android vault с созданием мастер-пароля, разблокировкой, паролями, поиском, группировкой по названию сервиса, деталями, редактированием и удалением.
- Генератор на `SecureRandom`, настройки длины/наборов символов и пресеты. Оценка силы — собственная эвристика, не доказанная оценка энтропии.
- TOTP: генерация, ручной ввод, QR с камеры/изображения, разбор Google Authenticator migration payload. TOTP-only записи представлены обычными записями с пустым паролем.
- Android Autofill Framework и Credential Provider: код выбора/сохранения password credentials и создания/использования public-key credentials. Наличие реализации не доказывает совместимость с браузерами и серверами.
- CSV export и CSV/JSON import, включая поля TOTP и собственные поля passkey. Это не проверенная переносимая защищённая резервная копия.
- HIBP Pwned Passwords и сетевые favicons; поэтому «локальное хранение» не означает «приложение не обращается в сеть».
- Material 3, динамические цвета, светлая/тёмная тема; реально объявленные языки — **EN/RU**. Украинский README есть, украинской локализации приложения нет.

Не обнаружены в исходниках: облачная синхронизация, собственный аккаунт/сервер, Claude integration, полноценный локальный Security Center, папки/теги/категории, сайт и автоматизированный release pipeline. Это описание текущего репозитория, не утверждение об отсутствии внешних проектов.

### Охват и выполненные проверки

- Инвентаризированы **190 tracked-файлов**: Gradle/configuration, весь production source tree, manifest/resources, тесты, README EN/RU/UK, CHANGELOG, лицензия, screenshots, R8/keep rules и baseline profile. Проверены все функциональные подсистемы; наиболее подробно — потоки ключей, хранения, блокировки, импорта/экспорта и credential integrations. Это не построчная независимая экспертиза каждого ресурса или generated profile.
- Просмотрены четыре существующих screenshot: vault, generator, TOTP, autofill. Это статические материалы, не live UX-тест.
- GitHub API: metadata, remote commit, releases, issues search и Actions runs. Найдены четыре публичных релиза: v0.8.0, v0.9.0, v1.0.0, v1.0.1; последний опубликован 2026-09-08. По выполненному issue-запросу результатов нет; Actions runs — `total_count=0`.
- `adb devices -l`: подключённых устройств нет. Instrumentation, TalkBack, biometrics, фактический SQLCipher upgrade, WebAuthn/browser interoperability и восстановление реального vault **не выполнялись**.
- Первоначальный Gradle запуск остановился из-за отсутствия Java в PATH/JAVA_HOME. Повтор использовал только временный `JAVA_HOME=E:\Android Studio\jbr`.

| Команда/проверка | Фактический результат |
|---|---|
| `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug --console=plain` с указанным JAVA_HOME | Общая команда exit 1 на `:app:packageRelease`; результаты ниже относятся к реально выполненным задачам |
| `:app:testDebugUnitTest` и XML `app/build/test-results/testDebugUnitTest/TEST-*.xml` | **139 тестов, 19 suites; 0 failures/errors/skipped**, задача выполнялась |
| `:app:assembleDebug` | Завершилась успешно **UP-TO-DATE**; это не clean build и не новая компиляция всего дерева |
| `:app:assembleRelease` | Packaging заблокирован: `Unable to delete directory ...\app\build\intermediates\incremental\packageRelease\tmp`; дополнительные ошибки доступа при cleanup Gradle. Новый release APK не подтверждён |
| Отдельно `gradlew.bat :app:lintDebug --console=plain` | **exit 1: 3 errors, 147 warnings**; отчёты `app/build/reports/lint-results-debug.{xml,html,sarif}` |
| Lint errors | `LocalContextGetResourceValueCall`: `QrCodeScannerDialog.kt:122,336`, `TotpScreen.kt:105` |
| Анализ warning-групп | Включая 61 UnusedResources, 20 PluralsCandidate, 19 GradleDependency, 17 UseKtx, 7 NewerVersionAvailable, 2 MissingPermission; число warnings не равно числу security vulnerabilities |

Файлы build/cache не удалялись, процессы не завершались принудительно. Никакие пользовательские vault, production secrets, экспорты или данные телефона не использовались. Сеть использовалась для публичных GitHub metadata, документации и обычного Gradle resolution, не для проверки реальных паролей.

Автоматический Codex Security Deep Scan **не стартовал**: инструмент вернул `Deep Scan cannot safely start a read-only worker: the parent must provide a managed filesystem permission profile.` Также advisory сообщил отсутствие Daybreak access. Повтор/обход ограничения не выполнялся. Здесь приведены результаты ручного review и локальных проверок, а не завершённого независимого Deep Scan.

Во время проверки появился untracked `exports/stitch/` с временем создания 10:50; данный аудит его не создавал и не менял. Он исключён из проверяемого tracked snapshot; Stitch MCP не вызывался.

## Architecture summary

### Модули и поток выполнения

Один Gradle-модуль **`:app`**, namespace/applicationId **`com.doffi4.doffisecure`**. Переименование не требуется. Kotlin/Compose, Koin, Room, SQLCipher; `minSdk=26`, `compileSdk=targetSdk=37`, Java language target 11.

`DecryptumApplication` → native SQLCipher loading, Koin, Coil → `MainActivity` / состояние блокировки → Compose `NavGraph` → ViewModel → существующие domain use cases → repository → Room DAO. Autofill/Credential Provider входят через отдельные services/activities и используют те же singleton managers/repositories.

| Область | Фактическая структура и точка интеграции |
|---|---|
| DI | `di/AppModule.kt`: managers, repositories, DB и warmup — долгоживущие зависимости; use cases и ViewModels подключены существующим способом |
| UI/navigation | `ui/navigation/{MainScreen,NavGraph,CustomBottomNavigation}.kt`; четыре вкладки: vault, TOTP, generator, settings; route деталей по ID |
| Блокировка/onboarding | `security/AppLockManager.kt`, `ui/lock/{AppLockViewModel,LockScreen}.kt`; setup встроен в lock flow |
| Данные | `data/local/{entities,dao,database}`, `data/mapper`, `data/repository`; domain models/interfaces/use cases уже разделены |
| Password vault | `PasswordRepositoryImpl`, `PasswordViewModel`, `PasswordScreen`, `PasswordDetailScreen` |
| Интеграции | `autofill/`: parser/matcher/presentation, Autofill auth/picker/save; Credential Provider и password/passkey activities |
| Crypto/backup/network | `security/{PasswordCrypto,DatabaseKeyManager,DatabaseMigrator,CsvManager,SecureClipboard,FaviconFetcher}.kt`, `data/repository/PwnedPasswordsRepository.kt` |
| TOTP/WebAuthn | `security/totp/` и `security/webauthn/`; есть CBOR encoder, отдельного CBOR decoder не найдено |
| Дизайн/настройки | `ui/theme/{Color,Theme,Type}.kt`, `security/UserSettingsManager.kt`, `SettingsScreen` |
| Developer tools | `dev/`, `DeveloperSection`, `DevToolsViewModel`, `DevModeManager`; часть действий меняет реальные данные vault |

Room schema **v4**, `exportSchema=false`. `password_table`: service, username, password, URL, timestamp, TOTP. `passkey_table`: credential ID, RP/user metadata, encrypted private key, COSE public key, algorithm, sign counter, optional password link. Foreign key при удалении password использует `SET NULL`. Зарегистрированы миграции **2→3, 3→4**; пути 1→2 нет. Какая опубликованная версия реально создавала schema 1, отдельно не воспроизводилось.

Поиск — SQLite LIKE по service/username/url. Группировка — текст service, не проверенная доменная идентичность. Категорий нет. Экран добавления/редактирования не позволяет полноценно задать URL, хотя matcher и модель URL используют.

**Архитектурный вывод:** существующего разделения достаточно для следующих фаз. Нужны локальные изменения lifecycle/auth/results и их проверки; переписывание MVVM/DI, новые параллельные repositories и переименование схемы не обоснованы.

## Security architecture: verified facts

### Модель защиты и ключи

| Механизм | Подтверждено исходниками | Ограничение |
|---|---|---|
| SQLCipher | `sqlcipher-android 4.6.1`, Room factory и migration bootstrap в DI | Работа native DB/всех ABI на устройстве не проверена; используемые defaults SQLCipher не равны независимо проверенной конфигурации |
| DB key | `DatabaseKeyManager`: случайные 32 bytes, AES-256-GCM wrapping ключом AndroidKeyStore, encrypted blob/IV в private preferences | Это **отдельный от мастер-пароля** ключ; Keystore wrapping не требует пользовательской аутентификации, ключ DB кешируется в процессе |
| Field encryption | `PasswordCrypto`: AES/GCM/NoPadding; DEK 32 bytes, IV 12 bytes, tag 128 bits; password и TOTP зашифрованы дополнительно | Некоторые fallback-пути принимают повреждённый ciphertext как результат; см. S05 |
| Master KDF | Argon2id, memory **32768 KiB**, iterations **2**, parallelism **2**, salt **16 bytes**; KEK wrapping DEK | Минимум мастер-пароля в UI — 4 символа. Производительность и достаточность параметров для целевых устройств не измерялись |
| Biometrics | Keystore biometric wrapping slot и `CryptoObject`-based unwrap существуют; strong biometric authorization для современного API | Picker использует другой, некриптографический путь. Гарантии hardware backing/StrongBox по устройству не проверяются; слово «hardware» нельзя распространять на все ключи |
| Secure preferences | AndroidX Security Crypto есть в dependencies, но вызовов EncryptedSharedPreferences/MasterKey в production source не найдено. Wrapped DEK/DB key и настройки хранятся в обычных private SharedPreferences | Защита wrapped key blobs обеспечивается их собственным шифрованием, а не encrypted preferences. Нельзя выводить использование secure preferences из наличия библиотеки |
| Passkeys | JCA EC secp256r1, ECDSA/SHA-256, COSE ES256, encrypted PKCS8 private bytes в DB, attestation `none` | **Ключи software/exportable, не AndroidKeyStore ECDSA**. Создание/assertion и caller trust имеют блокеры S04 |
| TOTP | Локальные HMAC-based генерация/параметры и parsing/import; тесты алгоритма присутствуют | End-to-end QR, clock changes, восстановление TOTP-only и доступность UI на устройстве не подтверждены |
| Генератор | SecureRandom, обязательный символ из включённых классов и перемешивание | Сила в интерфейсе эвристическая; не оценивать шаблонные пароли как гарантированно надёжные |
| HIBP | HTTPS range API, SHA-1 prefix **5 hex**, `Add-Padding: true`, сравнение suffix локально | Это k-anonymity-проверка; prefix/IP/время запроса уходят наружу. Не «zero-knowledge» и не доказательство отсутствия утечек |
| Clipboard | Sensitive flag на API 33+, очистка примерно через 30 s, coroutine/alarm receiver | Background clipboard/OEM ограничения и fallback могут помешать очистке. Гарантия таймера на всех устройствах не подтверждена |
| Screenshots | `FLAG_SECURE` в MainActivity при запрещённых screenshots, настройка по умолчанию запрещает | Другие sensitive activities не имеют такой же политики; обновление MainActivity flags привязано к lifecycle |
| Android backup | Manifest `allowBackup=false`; extraction rules исключают DB/preferences/files для cloud и device transfer | Это **не recovery**. Потеря устройства/Keystore без рабочей независимой копии может означать потерю vault; pre-31 backup XML шаблонный |
| Сеть | INTERNET, HIBP, favicons: Google → DuckDuckGo → сайт; локальный disk cache | Нет доказанного режима полного запрета сети; favicon toggle применяется не везде, HIBP стартует автоматически |

Практическая граница угроз: шифрование файлов на диске и master/biometric unwrap полезны, но не защищают от произвольного исполнения внутри уже разблокированного процесса или скомпрометированной ОС. SQLCipher key доступен приложению независимо от мастер-пароля; безопасность закрытого vault зависит также от правильной защиты DEK, plaintext caches и всех integration entry points. Гарантированного zeroization JVM strings нет.

### Наиболее существенные находки

Для каждого пункта «исправить» ниже — предложение следующего этапа, а не изменение в этом аудите. Ссылки на исходники относительны корню репозитория; номера относятся к указанному commit.

**S01 — Critical: блокировка не закрывает все пути выдачи credentials.**

- Доказательства: `PasswordRepositoryImpl.kt:32,77–96` кеширует расшифрованный список; `AppLockManager.setLocked(true)` очищает DEK через `PasswordCrypto.lock()`, но не этот кеш. `AutofillPickerActivity.kt:115–116` получает список до аутентификации; `606–653` запускает BiometricPrompt без CryptoObject, success меняет lock flag, а при недоступной biometrics вызывает `fillAndFinish`.
- Подтверждён путь в коде: unlock/warm cache → lock в том же процессе → picker → biometrics unavailable → выдача cached plaintext credential. Эксплуатация на телефоне не выполнялась. При холодном процессе fallback может вместо пароля передать ciphertext.
- Дополнительно `PasswordCrypto.kt:250` возвращает success для любого supplied master input, если DEK уже cached. Поэтому вызов `verifyPassword` не обеспечивает fresh re-auth. Rate limit реализован в основном AppLockViewModel, а не во всех fallback entry points. Timeout оценивается преимущественно при возвращении/входе, не является непрерывным таймером бездействия.
- Минимальное исправление: единая существующая auth policy для всех entry points, подтверждённый unwrap либо master verification; недоступная biometrics должна вести к master fallback/отказу. Очистить plaintext/cache/jobs при lock, запретить read/fill закрытого vault, отдельно реализовать fresh verification без early-success.
- Gate: warmed и cold process, unavailable/enrollment-changed biometrics, wrong master, timeout, process recreation, race lock↔warmup; ни один закрытый/ошибочный путь не возвращает credential или ciphertext.

**S02 — Critical: миграция plaintext DB не обеспечивает сохранность исходной копии.**

- `DatabaseMigrator.kt:93–119`: после `sqlcipher_export` удаляет исходную DB/WAL/SHM **до** успешного rename; при ошибке удаляет temp. Отказ rename/crash способен лишить обеих рабочих копий. Нет проверенного reopen/integrity/schema/count gate и rollback.
- `user_version` не переносится. Согласно [SQLCipher API](https://www.zetetic.net/sqlcipher/sqlcipher-api/), `sqlcipher_export` не меняет target user_version. Для старых schemas это создаёт риск неверного Room upgrade/schema validation. Реальная авария на устройстве не воспроизводилась.
- `DatabaseMigratorTest` проверяет только SQLite header, не migration. `AppDatabase` не экспортирует schemas и не имеет 1→2.
- Минимальное исправление: сохранять source до валидированного target и успешного переключения, переносить version, обеспечить recovery после прерывания. Не добавлять destructive fallback.
- Gate: fixtures всех реально выпущенных schemas, WAL, low disk, failure rename/export, interruption каждого шага, повторный запуск; после любой ошибки доступна валидная исходная или новая копия.

**S03 — High: CSV содержит секреты без защиты и не обеспечивает восстановление.**

- `CsvManager.kt:27,41–140`: экспортирует plaintext passwords/TOTP и расшифрованные private PKCS8 passkey bytes в Base64. Base64 не шифрование. `SettingsScreen` открывает SAF export без явного предупреждения и fresh re-auth; пользователь может выбрать cloud document provider.
- `CsvManager.kt:326,329`: обрезает whitespace пароля и пропускает TOTP-only строки; JSON import также пропускает blank password. Passkey export не сохраняет userId, import задаёт `ByteArray(0)` (`:395`). Linked passkeys могут создавать несколько password rows. Ошибка private-key decrypt переключается на encrypted blob без ясного различения формата.
- Import имеет частичные успехи, не даёт полного skipped/error отчёта, password/passkey восстановление не является одной проверенной транзакцией. Unlimited file read создаёт риск OOM.
- Минимальное исправление: разделить **опасный interoperability export** и **защищённую versioned backup**; перед CSV предупреждение/reauth. Сохранять точные secret bytes/strings и необходимые identity fields; отчёт о каждом отказе без содержимого секретов. Формат backup проектировать отдельно, используя проверенную AEAD/KDF, без самодельной криптографии.
- Gate: restore на новом устройстве/ключах, wrong backup password, corruption, TOTP-only, whitespace/Unicode/newlines, standalone/linked passkeys, множественные credentials и partial failure. До gate не обещать переносимость passkeys.

**S04 — High: passkeys не готовы к заявленной совместимости и имеют незавершённую trust boundary.**

- `WebAuthnCryptoEngine` создаёт software EC keypair и выставляет UV в authenticator data без самостоятельной проверки результата конкретной авторизации; algorithm parameter не равен реально поддерживаемому произвольному алгоритму — реализация ES256.
- `PasskeySaveActivity.kt:162–165`: response clientDataJSON содержит **пустой challenge**, синтезирует `https://rpId`; request challenge/clientDataHash не используются корректно. RP ID редактируемый. Credential сохраняется до подтверждения сервером.
- `DecryptumCredentialProviderService.kt:75–98`: reflection/fallback origin, RP ID из JSON; не найдена полноценная certificate/package allowlist через `CallingAppInfo.getOrigin(...)`. Для assertion отсутствует обработка allowCredentials; `PasskeyAuthActivity.kt:254–278` синтезирует clientDataJSON, который может не соответствовать предоставленному clientDataHash.
- Это подтверждённые дефекты протокольного пути и риск выдачи credential неправильно связанному caller, **не доказанный захват аккаунта**. Первичные требования: [Android credential providers](https://developer.android.com/identity/sign-in/credential-provider), [CallingAppInfo](https://developer.android.com/reference/androidx/credentials/provider/CallingAppInfo), [WebAuthn](https://www.w3.org/TR/webauthn/).
- Минимальное решение для v1.1: корректная проверка caller/RP/challenge/allowlist, flags и response с тестами; **либо выключить новую выдачу/создание passkeys и публичное обещание поддержки**, сохранив существующие записи и продумав доступ к ним. Не менять примитивы или schema по эстетическим причинам.
- Gate: настоящая registration/assertion со строгим test RP; negative caller/certificate/RP/challenge/allowCredentials cases, native Android и поддерживаемые браузеры, fresh user verification, signCount race/retry, recovery.

**S05 — High: ошибки расшифровки маскируются под успешные данные.**

- `PasswordCrypto.decrypt` и `PasswordRepositoryImpl.decryptPassword` при части ошибок возвращают исходный encoded payload. `encryptForStorage` доверяет входу с `enc:` prefix. Следствие: ciphertext способен попасть в отображение, autofill, export или повторное шифрование как обычный пароль; это не утверждение об отсутствии SQLCipher.
- `checkEncryptionIntegrity` не обнаруживает все такие ошибки, поскольку ожидает exception, который fallback поглощает. Успешный badge/count не доказывает целостность vault.
- Минимальное исправление: явный locked/corrupt/legacy результат и fail-closed операции с секретами; legacy migration — отдельный проверяемый путь. Не уничтожать повреждённые данные автоматически.
- Gate: tampered GCM tag/IV, truncated/versioned payload, prefix-like пользовательский пароль, mixed legacy rows; никаких fill/export «успешных» повреждённых значений.

**S06 — High: upgrade/edit/maintenance могут потерять доступ или связи.**

- Legacy unlock в `PasswordCrypto.unlockWithPassword` требует validator callback, но `AppLockManager.verifyPassword` его не передаёт. Достижимость для конкретной старой опубликованной установки требует fixtures; текущий legacy путь по коду не завершён.
- `PasswordDao.insertPassword` использует REPLACE и для update; foreign key passkeys — ON DELETE SET NULL. По семантике SQLite replace может разорвать связь при обычном редактировании; проверить Room/DB test, использовать update в существующем DAO.
- Developer duplicate cleanup оставляет MIN(id) по service+username, игнорируя разные password/URL/TOTP. Это разные credentials, а не доказанные дубликаты. Developer mode доступен в release и защищён скрытым UI/константой, а не release policy.
- Минимальное исправление: безопасные upgrade fixtures и update, убрать destructive dev actions из production через build policy. Никакой автоматической «чистки» по двум metadata-полям.
- Gate: legacy→current unlock; edit password сохраняет passkey link; одинаковые service/login с разными секретами сохраняются.

**S07 — High: сетевое поведение расходится с privacy обещаниями.**

- `UserSettingsManager`: favicons включены по умолчанию. `FaviconFetcher.kt:103–125`: Google, DuckDuckGo и прямой сайт получают домен/IP; локальный cache содержит доменную metadata.
- Toggle не применяется ко всем `SiteAvatar`/integration presentation путям. Autofill presentation может запрашивать favicon до auth. Настройка «напрямую с сайтов» не описывает реальный waterfall.
- `PasswordViewModel.kt:185`: загрузка списка автоматически запускает HIBP audit; детали также проверяют пароль. Явного сетевого opt-in нет.
- Минимальное исправление: явная единая настройка/согласие на сетевые операции, default offline для privacy-first onboarding, один policy gate в существующей network точке и прозрачное описание получателей. Не отправлять реальную vault metadata в AI/design services.
- Gate: network deny/toggle/locked vault во всех screens/services; fake-client tests и device traffic inspection на синтетических credentials.

**S08 — High: ошибка проверки может выглядеть как отсутствие компрометации.**

- `PwnedPasswordsRepository.kt:59` считает missing response body `Clean`; aggregate audit игнорирует failed checks при выводе результата. Пустой список compromised может означать отсутствие завершённых проверок.
- Минимальное исправление: `Not checked / Checking / Clean at time / Found / Error`, coverage и timestamp; malformed/empty/error response не Clean. «Не найден в HIBP» не значит «безопасный пароль».
- Gate: offline, timeout, HTTP error, malformed body, partial completion/cancellation; ни одно из них не показывает зелёное «всё безопасно».

**S09 — High: защита экрана/логов/clipboard непоследовательна.**

- `FLAG_SECURE` найден в MainActivity, не в sensitive save/auth/passkey/picker activities. На них отсутствует единая screenshot policy.
- Release Log calls содержат service/domain/package/username/import URI. Прямого намеренного логирования plaintext passwords/TOTP seeds/private keys не обнаружено, однако передача parser exception messages в logs/UI может включать input fragments и требует sanitation review.
- Clipboard timeout best effort; отдельный fallback receiver не равен основному protected copy. Toast при копировании TOTP раскрывает текущий код на экране.
- Минимальное исправление: общая window policy в текущих activities, только безопасные error IDs в production, metadata minimization, точные ограничения clipboard в docs; никакого вывода секретов в regression reports.
- Gate: Recents/screenshot/prompt во всех sensitive activities, release log capture на синтетических данных, foreground/background clipboard/OEM cases.

**S10 — High: release pipeline и signing не готовы.**

- `app/build.gradle.kts`: release явно подписывается **debug signingConfig**. Это факт текущей конфигурации, **не проверенный сертификат опубликованного APK**.
- CI/workflows отсутствуют, Actions runs 0. Lint не проходит; новая release packaging не завершилась. Debug success из cache не доказывает чистую воспроизводимую сборку.
- Минимальное исправление: закрыть lint errors по существующей Compose модели без blanket baseline; отдельно устранить file-lock ограничение окружения, затем fresh build в изолированном output. Ввести проверяемый CI, managed release signing и документировать fingerprints.
- Перед сменой ключа установить actual signing cert существующих artifacts и continuity plan. **Не советовать uninstall/reinstall пользователю как способ обновления:** это может уничтожить vault/Keystore. Новый debug keystore на другом runner также способен нарушить обновление.
- Gate: fresh checkout build/test/lint/release, cert/hash verification, upgrade-install без потери данных, secrets не в repo/APK/logs.

**S11 — High: эвристическое совпадение credential не подтверждает идентичность получателя.**

- `AutofillMatcher.scoreMatch`: при отсутствии URL сравнивает service с основной текстовой меткой домена; для native app — keywords package name, включая contains. Одинаковая метка в другом домене или похожее имя произвольного package не подтверждают тот же сервис. Explicit URL отсекает часть fuzzy matches, что полезно, но двусторонний subdomain match и metadata-based native match требуют отдельной policy.
- User auth подтверждает пользователя vault, а не право сайта/приложения получить конкретный credential. Наличие adversarial matcher tests не доказывает caller identity.
- Минимальное исправление: явные сохранённые domain/app bindings, безопасные правила domain matching и проверка доверенной app identity там, где доступна; fuzzy results только как явно непроверенный ручной выбор с показом получателя. Добавить URL в существующую форму, не создавать второй credential store.
- Gate: одинаковые service labels в разных доменах, attacker-controlled package keywords, subdomains/public suffix cases; неподтверждённое совпадение не предлагается как доверенное автозаполнение. Реальная выдача стороннему приложению в этом аудите не воспроизводилась.

## UX/design findings

Существующую Material You структуру следует сохранить. Высокое влияние дают ясные auth/recovery states и надёжность операций, а не смена визуального языка.

| ID / приоритет | Наблюдение и доказательство | Минимальное улучшение / критерий |
|---|---|---|
| U01 High | Lock setup допускает 4 символа; KDF setup/verify вызывается синхронно из UI пути; нет связного объяснения recovery, сети и integrations | Поощрять длинную passphrase; выполнять тяжёлое derive вне main thread, progress и запрет повторной отправки; объяснить последствия потери master/device, предложить biometric/autofill осознанно |
| U02 High | Add dialog проверяет service/password, VM требует также username; dialog закрывается до результата. Edit тоже закрывается до подтверждения и rejects blank password у TOTP/passkey-only | Единая валидация по типу записи; сохранить введённое до success, inline error, retry; дать задать URL. Не создавать второй механизм форм |
| U03 High | `PasswordDetailScreen` удаляет credential и уходит назад без confirmation/undo; другие delete flows подтверждаются | Подтверждение с ясными последствиями linked TOTP/passkeys либо безопасное undo; результат операции должен быть подтверждён |
| U04 Medium | Search collector и full-list collector могут одновременно менять uiState; DB change сбрасывает filtered state; empty vault/no results сходны | Один активный query-driven flow; отдельные состояния «vault пуст», «ничего не найдено», «не удалось загрузить» с retry |
| U05 High/Medium | TOTP экран сводит Loading/Error к пустому списку, не обрабатывает uiEvent так же, как vault; вручную вводимый seed видим | Состояния загрузки/ошибки/успеха; masked seed с явным reveal; не показывать текущий код в copy toast; accessibility без озвучивания секрета по умолчанию |
| U06 Medium | Detail содержит fillMaxSize Column без общего verticalScroll, несколько cards; fixed bottom padding/offset 90–120dp | Проверить малый экран, IME, landscape, fontScale 1.3/2.0; scroll/insets по фактической панели, без перестройки всей навигации |
| U07 Medium | Custom nav unselected icons имеют null descriptions, нет ясной selected/role semantics; встречаются маленькие actions/reveal icons | Имена всех destinations, selected state/role, эффективная touch target ≥48dp, TalkBack/focus order и локализованные подписи |
| U08 Medium | Generator имеет «Weak» preset и heuristic strength; некоторые status цвета hardcoded, а не theme tokens | Убрать слабый пресет из рекомендуемых вариантов, пояснить оценку; проверить contrast в light/dark/dynamic/high contrast, не обещать математическую энтропию |
| U09 Medium | Некоторые auth/save/passkey Compose activities не обёрнуты общим DecryptumTheme; статические screenshots только тёмные | Применить существующую theme и общие компоненты точечно; runtime visual parity проверить отдельно |
| U10 Medium | Settings смешивает privacy, auth, integrations, data и dev tools; privacy labels не соответствуют сети | Сохранить экран, выделить группы «Защита», «Сеть и приватность», «Интеграции», «Данные и восстановление»; dev не в production |

Группы по service уже полезны. Категории/теги — будущая product feature со schema impact, не обязательный косметический пункт v1.1. В деталях и autofill показывать проверенный домен/приложение и последствия выбора; fuzzy service match не представлять как подтверждённую связь с сайтом.

Четыре screenshots показывают узнаваемый M3 интерфейс, но не подтверждают accessibility или реальные integration states. В autofill screenshot есть account-like metadata; происхождение синтетических данных не подтверждено. Новые публичные/design материалы должны использовать явно вымышленные безопасные fixtures, без действительных паролей, адресов и OTP.

## Public presentation findings

Проверенные источники: [репозиторий](https://github.com/Doffi4/Decryptum), [v1.0.1](https://github.com/Doffi4/Decryptum/releases/tag/v1.0.1), [Actions API](https://api.github.com/repos/Doffi4/Decryptum/actions/runs?per_page=5). Metadata/публикации могут измениться после даты проверки.

| Область | Текущее состояние | Что нужно до усиленного публичного запуска |
|---|---|---|
| README EN/RU/UK | Есть описание, screenshots, установка и стек, но claims опережают код | Исправить software passkeys vs hardware, HIBP «zero knowledge»/«без утечки», UA app locale, favicon recipients, CBOR decode и гарантии backup/clipboard; таблица supported/experimental/disabled |
| Repository metadata | Description акцентирует aesthetics/standalone/generator/2FA; homepage пустой, has_pages=false; часть topics нерелевантна (`cli`, `decryption`, developer tools) | Краткое точное позиционирование, Android/offline vault/TOTP/security; homepage только после существующего проверенного сайта |
| SECURITY.md | Нет | Threat model, security limitations, supported versions, реальный private disclosure contact/process, реакция на сообщения без придуманных SLA/сертификаций |
| PRIVACY.md | Нет | Что локально, что передаётся HIBP/favicon/SAF provider, permissions/cache/logs, network consent, backup/recovery и будущий AI отдельно |
| CONTRIBUTING/templates | Нет CONTRIBUTING, issue/PR templates | Build/test prerequisites, synthetic fixtures only, запрет secret attachments, security reports не в public issue, review checklist миграций/auth |
| Changelog/releases | CHANGELOG и 4 releases есть; v1.0.1 предлагает debug и release APK, GitHub содержит asset SHA-256 metadata | Один ясный supported artifact, стабильная подпись/upgrade policy, проверенные hashes/fingerprint, известные ограничения и migration notes; debug обозначить как development |
| Release provenance | Нет собственных checksum manifest/SBOM/подтверждённого CI artifact provenance | Связь commit→tests→artifact→cert, лицензии native/transitive dependencies, reproducible inputs насколько возможно |
| Screenshots | 4 тёмных изображения, нет onboarding/error/recovery/light mode coverage | Небольшой набор актуальных синтетических screenshots; реальные auth/privacy states и поддерживаемые темы |
| Website/domain | В tracked repo сайта/CNAME/deployment config нет; homepage отсутствует | Исходники простой страницы и draft содержания. Владение доменом/DNS/внешний сайт **не проверены**; ничего не покупать и не публиковать без отдельной команды |
| Startup materials | Нет подтверждённых traction/business figures | Честный founder/product brief, threat model, roadmap, demo evidence и гипотезы рынка; не придумывать пользователей, выручку, юридическое лицо или партнёрство |

Признаки hobby release здесь — debug signing, отсутствие disclosure/recovery policy и CI, обещания неподтверждённых возможностей. Наличие серьёзных криптобиблиотек и большого README само по себе не заменяет доказательства безопасного обновления/восстановления.

## Technical debt relevant to launch

1. **State lifetime:** singleton plaintext caches и background jobs не связаны с vault lock; несколько входов реализуют разные auth правила. Исправлять в текущих manager/repository/activities, без новой архитектуры.
2. **Migration evidence:** schema export выключен, нет восстановительных/upgrade fixtures. Не добавлять миграции наугад — сначала извлечь schemas выпущенных версий на synthetic DB.
3. **Test fidelity:** 139 passing JVM tests полезны для parsers/algorithms, но `unitTests.isReturnDefaultValues=true` и test-injected crypto/KDF не доказывают AndroidKeyStore/Argon2 native/SQLCipher/bio behavior. Единственный instrumentation source тестирует RSA OAEP, не production journeys. `SecurityAuditSuiteTest` не является внешним аудитом.
4. **Persistence correctness:** REPLACE для edits, partial import и overloaded password/TOTP/passkey representation требуют узких поправок и проверки связей; новая универсальная schema сейчас не нужна.
5. **Error model:** encoded-as-plaintext fallbacks, dynamic exception strings, unknown-as-clean; важнее косметического refactor.
6. **Build/dependencies:** alpha AndroidX security/biometric, AGP legacy DSL (`android.newDsl=false`, deprecated BaseAppModuleExtension), много lint debt. Есть version catalog и wrapper SHA-256 pin, но нет dependency verification/lock/SBOM. OkHttp используется через transitive dependency, её контракт следует зафиксировать. Не обновлять весь стек сразу; каждой чувствительной версии нужен rationale/test.
7. **Native/release compatibility:** SQLCipher/Argon2, R8 и target SDK 37 требуют реальных ABI/API/OEM и 16 KB page-size проверок. Их несовместимость сейчас не доказана; проверка обязательна перед объявлением поддержки.
8. **Performance:** master derive на main thread, decrypt-all/automatic breach checks, read-all import, повторные collectors — конкретные точки измерения. Не переписывать rendering/navigation без измерений.

## Risks

| Приоритет | Риск | Решение для запуска | ID |
|---|---|---|---|
| Critical | Cached secrets доступны через неправильную auth boundary | Обязательный gate до новых features | S01 |
| Critical | Миграция удаляет source до гарантированного перехода | Crash-safe migration и verified rollback/fixtures | S02 |
| High | Незащищённый export/невосстановимый TOTP/passkey backup | Явный unsafe CSV и рабочий защищённый recovery; не обещать непроверенное | S03 |
| High | Некорректный WebAuthn/caller trust | Доказать строгую совместимость либо выключить capability безопасно | S04 |
| High | Ошибка crypto воспринимается как пароль/успешная целостность | Fail-closed и видимый recoverable error | S05 |
| High | Потеря legacy доступа/passkey связей/данных cleanup | Upgrade/edit tests; исключить destructive dev tools | S06 |
| High | Скрытые сетевые запросы/ложный Clean | Consent/policy gate и честное Unknown/Error | S07–S08 |
| High | Screen/log leakage и непоследовательные protections | Общая policy и release проверка | S09 |
| High | Непроверенный artifact/подпись/обновление, failing lint | CI + clean checks + key continuity | S10 |
| High | Credential сопоставлен с чужим доменом/похожим package | Проверенные bindings и явное предупреждение для ручного fuzzy выбора | S11 |
| High | Потеря вводимых/удаляемых пользователем данных | Подтверждение операций и результат перед dismiss | U02–U03 |
| Medium | Неподтверждённая accessibility/состояния | Точечные исправления + device matrix | U04–U10 |
| High | Security claims сильнее доказательств | Исправить docs до релиза и startup pitch | Public presentation |

Неизвестное не заполнять предположениями: actual published APK cert; аппаратный уровень Keystore; работа на OEM; все исторические upgrade cohorts; WebAuthn interoperability; фактическая background clipboard cleanup; ownership/domain; transitive library runtime traffic. Потребуются отдельные проверки на синтетических данных.

## Recommended scope for v1.1

**Предложение: v1.1 = надёжный локальный vault/TOTP + безопасный autofill + честная диагностика + проверяемый выпуск.** Не делать AI или новый визуальный стиль условиями релиза.

Обязательный scope:

- Закрыть S01/S02 и release-blocking S03/S05/S06/S09/S10; предусмотреть восстановление данных, а не только happy path.
- Привести passkeys к проверенному supported статусу **либо явно отключить неподтверждённую capability**, сохранив данные и разъяснив ограничения. Не заявлять hardware-backed passkeys при текущем software хранении.
- Единый offline/network consent, HIBP unknown/error/coverage; безопасная свежая авторизация для export и sensitive integrations, проверенные credential recipient bindings S11.
- Исправить сохранение/удаление, TOTP-only import/edit, точность паролей, passkey links; добавить versioned encrypted backup с реальным restore gate либо не называть CSV backup и явно обозначить отсутствие полноценного recovery. Для сильного public launch рабочее recovery предпочтительно считать обязательным.
- Точечная M3/accessibility/state коррекция без перестройки app; объяснение master/recovery/autofill в onboarding.
- Локальный Security Center с объективными правилами и ограничениями (Phase 3); не выдавать общий «vault безопасен» score.
- SECURITY/PRIVACY/README/release evidence/CI, безопасные screenshots и минимальные website/startup drafts.

Если объём нельзя закончить и проверить, уменьшать feature scope, а не снимать Critical gates. Claude advisor — отдельный optional milestone после безопасного локального продукта.

## Out-of-scope items

- В этом аудите: любые исправления production code, phases 2–6 implementation, Stitch calls, Claude integration, cloud sync, releases/PR publication, deployment/DNS, покупка сервисов, использование production secrets.
- Для базового v1.1: архитектурная перепись, module/package rename, новая category/folder schema ради UI, массовое обновление библиотек, замена crypto primitives без доказанной необходимости, собственная криптография, enterprise/shared vault и accounts backend.
- Не обещать сертификацию, абсолютную защиту, hardware backing на любом устройстве, compliance, startup eligibility или traction без доказательств.

## Five-phase execution plan

Пять **следующих** фаз имеют номера 2–6. Это предложение scope следующих prompts, не разрешение автоматически выполнять их сейчас. Оценки — рабочие дни для одного разработчика с помощью агента; зависят от доступности устройств, исторических fixtures и решения по passkeys. Общий реалистичный порядок — **6–10 недель**, optional AI может быть перенесён. Критические исправления включены в начало Phase 2, чтобы дизайн не шёл поверх небезопасного ядра.

### Phase 2 — Stitch MCP + design system

**Цель:** закрыть базовые safety blockers и оформить существующий Material You язык в небольшой проверяемый design system.

1. **Checkpoint 2A, прежде дизайна (5–10 дней):** воспроизвести S01/S02/S05 на synthetic fixtures; исправить lock/cache/fresh auth, fail-closed, crash-safe migration и минимальные export warnings. Закрыть lint errors и получить чистый build в доступном окружении. Проверить S06 upgrade/edit; принять passkey gate-решение S04 и безопасное ограничение production dev tools. Если backend/protocol fix большой — сохранить отдельный passkey work item и блокировку capability до доказательств.
2. **Checkpoint 2B (3–5 дней):** инвентаризация `ui/theme`, существующих cards/forms/dialogs/nav/settings, карта состояний и token contrast light/dark/dynamic. Проверить доступность/auth Stitch MCP **тогда**, без предположений о готовой связи.
3. Передать Stitch только synthetic screen references, layout/component map и требования. Не отправлять vault, реальные аккаунты, OTP/private material или необработанный secret-containing code/export. Stitch результат — reference/spec; не заменять существующий Compose код автоматически.
4. Подготовить `docs/DESIGN_SYSTEM.md`, component/state matrix, privacy/auth flows, предложения небольших изменений U01–U10. Сохранить четыре вкладки и текущие routes; место Security Center предложить в существующем vault/settings, без автоматической пятой вкладки.
5. При недоступном Stitch — записать точное ограничение, завершить локальную token/state spec; не имитировать MCP результат.

**Точки интеграции:** AppLockManager/PasswordCrypto/repositories/DatabaseMigrator и integration activities для 2A; `Theme/Color/Type`, существующие screens/components для 2B.

**Exit gate:** Critical пути закрыты измеренными тестами; schema/data не меняются без fixtures; compile/unit/lint подтверждены; design spec перечисляет конкретные компоненты/состояния и сохраняет структуру приложения. Device gates, которые пока невозможно пройти, остаются явными блокерами выпуска.

**Следующий prompt:** «Выполни Phase 2 из LAUNCH_AUDIT: сначала checkpoint 2A с regression fixtures для S01/S02/S05 и build/lint; затем design-system inventory и безопасные Stitch references. Не переписывай архитектуру, не публикуй ничего; остановись на reviewable checkpoints и явно запиши неподтверждённые device checks».

### Phase 3 — local Security Center

**Цель:** полезные локальные сигналы без сетевой зависимости и без обещания абсолютной безопасности. Оценка **5–8 дней**, после 2A.

- Использовать существующие domain/repositories/strength logic, улучшив ошибки, а не новый vault engine. Определить rule IDs и состояния: weak/reused password, доступная TOTP coverage, настройки timeout/screenshots/clipboard, backup readiness. Отсутствие TOTP у passkey-only/неподдерживаемого сервиса не считать автоматически проблемой.
- Reuse comparison выполнять локально только в unlocked session; не сохранять plaintext/постоянные hash fingerprints в reports. Очистить derived state и jobs при lock.
- Каждой находке дать причину, affected count и переход в существующие детали; не раскрывать секреты в aggregate UI. Явно показывать incomplete/error/unsupported состояния.
- HIBP оставить отдельной ручной опцией с consent, progress, coverage/timestamp и S08 behavior. Отсутствие HIBP ответа не снижает неизвестность.
- Добавить unit/regression cases для TOTP-only/passkey-only, reuse, corrupted/locked records и cancellation; UI тесты empty/loading/partial/error и accessibility.

**Deliverables:** rule specification, локальный use case/ViewModel/UI в текущей архитектуре, tests и краткие пользовательские пояснения.

**Exit gate:** offline local analysis работает на synthetic vault; locked state не держит secrets/derived per-entry data; error не превращается в green Clean; нет новых внешних отправок и самопроизвольных изменений vault.

**Prompt:** «Реализуй Phase 3: локальный Security Center по утверждённым правилам и текущему design system; HIBP только отдельно по согласию, unknown/error явно. Сохрани существующие routes и repositories, проверь очистку при lock и смешанные типы записей».

### Phase 4 — optional Claude Security Advisor

**Цель:** объяснять обезличенные локальные выводы по запросу пользователя; не передавать vault модели. Оценка **4–7 дней после выбора transport/key model**; допустимо отложить за v1.1.

1. Начать с privacy feasibility checkpoint: DTO только rule IDs, severity и aggregate counts, без passwords, TOTP, private keys, master/DB keys, hash prefixes, usernames, URLs/domains, service names и raw exceptions. Даже aggregates объяснить пользователю как передачу наружу.
2. Default OFF, отдельный consent и preview отправляемого payload; cancel/timeout/usage/cost limits, возможность отключить. Offline Security Center полностью работает без advisor.
3. До implementation выбрать проверяемую схему доступа: user-supplied key с Keystore protection либо ограниченный backend proxy. **Нельзя вшить общий API key в APK.** Backend означает отдельную эксплуатационную/privacy ответственность; не добавлять его молча.
4. Текущие Claude API условия, retention и startup-program requirements проверить по официальным источникам на дату будущей реализации. Не предполагать бесплатность или eligibility.
5. Ответы advisory only: без tools к vault, автоматического изменения credentials, авторизации операций и claims «проверено Claude». Защита от prompt injection через metadata — metadata вообще не входит в DTO.

**Deliverables:** privacy contract/data schema, threat model, disabled-by-default optional UI и tests/redaction assertions; либо обоснованный defer документ вместо интеграции.

**Exit gate:** capture synthetic request подтверждает whitelist DTO; никакого общего ключа в artifact; отказ/сеть/отключение не мешают vault; verified current API terms. Без этого phase deferred, не blocker локального v1.1.

**Prompt:** «Подготовь Phase 4 feasibility и безопасный payload contract для optional Claude advisor; сначала сравни key/transport модели и privacy costs. Интеграцию делай только после конкретного выбора, никогда не отправляй secret или vault identity metadata».

### Phase 5 — website + public/security documentation

**Цель:** публичная информация соответствует реально поддерживаемому поведению. Оценка **4–6 дней**, после стабилизации capabilities; draft можно готовить раньше.

- Создать SECURITY.md (scope/threat model/disclosure), PRIVACY.md (HIBP/favicon/SAF/backup/optional AI), CONTRIBUTING, безопасные issue/PR templates и release/upgrade checklist. Contact — только реальный согласованный адрес, не выдуманный.
- Согласовать README EN/RU/UK, changelog и metadata: verified capabilities/experimental limits, supported Android/browser versions, software vs hardware keys, recovery limitations; убрать неподтверждённые claims.
- Подготовить synthetic screenshots и короткое демо фактически рабочих journeys. Никаких реальных account data в картинках, видео, design assets или issue examples.
- Сайт: небольшой static source, описание/limitations, downloads/security/privacy/source links. Analytics/cookies не включать по умолчанию. Проверить actual domain ownership при наличии данных пользователя; не покупать, не менять DNS и не deploy без отдельной команды.
- Download hashes/cert/release ссылки привязывать к проверенному artifact после Phase 6, временные placeholders явно обозначать в draft.

**Deliverables:** docs, локально проверенный website draft, screenshots и список metadata edits; deployment/publication — отдельная авторизованная операция.

**Exit gate:** каждая security claim имеет code/test evidence или явное ограничение; no secret fixtures; актуальные ссылки, доступная light/dark typography, mobile layout; privacy описание соответствует проверенной сетевой политике.

**Prompt:** «Выполни Phase 5 локально: website draft и SECURITY/PRIVACY/CONTRIBUTING/templates/README по verified capabilities. Используй synthetic screenshots, не публикуй, не меняй DNS, не придумывай контакты, компанию или traction».

### Phase 6 — release verification + startup application material

**Цель:** release candidate с воспроизводимыми доказательствами и честный пакет заявки. Оценка **6–10 дней** после исправлений, дополнительные OEM/protocol regressions могут увеличить срок.

1. Fresh checkout/output: debug/release compilation, 139+ релевантных tests, lint без errors, R8; dependency review/SBOM/licenses, artifact SHA-256 и actual signing certificate. Не применять blanket lint baseline вместо исправления errors.
2. Upgrade fixtures от каждой поддерживаемой published версии: master/legacy unlock, schema migration, WAL/crash/low disk; update install с фактической signing continuity. Восстановление encrypted backup на новой установке, corruption/wrong password, точные password strings, TOTP-only/passkey links.
3. Device matrix: API 26/28/30/33/34 и актуальная target SDK ветка, хотя бы один реальный OEM; bio unavailable/changed, process death, background/relock, lock↔warmup races, clipboard, screenshots/Recents и TalkBack/fontScale. Где тест недоступен — явно ограничить поддержку, не утверждать успех.
4. Autofill native/browser negative-domain/caller tests; Credential Provider password flows. Если passkeys включены — строгая registration/assertion с реальным test RP, trusted origin/certificates, wrong RP/challenge/allowCredentials и cancellation/concurrency.
5. Native SQLCipher/Argon2 ABI и 16 KB page-size проверки; локальная synthetic traffic/log inspection; отсутствие secrets/debug features/shared API keys в release artifact.
6. Release evidence содержит commit, environment, commands, results, devices, hashes/cert, known issues, restore/upgrade instructions и go/no-go по каждому S01–S11. Никаких секретов в evidence. Публикация только по отдельной прямой команде.
7. Startup dossier: one-pager, проблема/аудитория как гипотеза, проверяемые features, threat model, roadmap, demo, founder info из подтверждённых данных, расходы/план применения Claude при необходимости. Traction — реальные числа либо «данных пока нет»; заявка не подразумевает юридическое лицо/финансирование/партнёрство. Проверить актуальные официальные условия программы, не обещать принятие.

**Deliverables:** tested release candidate, release verification report, чеклист публикации, startup application draft; без автоматической отправки заявки/публикации.

**Exit gate:** нет открытых Critical; High исправлены либо функция безопасно исключена с явным ограничением; upgrade/recovery/auth verified, release cert continuity подтверждена. Passing unit tests и красивый сайт по отдельности недостаточны.

**Prompt:** «Выполни Phase 6 verification на synthetic данных и доступной device matrix, сохрани точные результаты и blockers. Подготовь candidate/evidence/startup draft с реальными фактами. Ничего не публикуй и не отправляй заявку без отдельной команды».

**Рекомендуемый старт следующей работы:** Phase 2, checkpoint **2A — воспроизводимый lock/autofill regression и безопасная DB migration**, затем fail-closed crypto и clean build/lint. Stitch/design system начинается после этого checkpoint; optional AI не отвлекает от сохранности vault.
