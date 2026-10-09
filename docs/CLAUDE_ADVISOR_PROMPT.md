# Decryptum Advisor: системный prompt-кандидат

Версия: **`aggregate-v2-candidate`**, 2026-10-09. Это кандидат для debug/синтетической оценки, **не production-ready интеграция**. Release остаётся отключённым; фактическая полезность и соблюдение правил моделью ещё не оценены live.

Канонический полный текст находится в [ClaudeAdvisorPrompts.kt](../advisor-evaluation/src/main/java/com/doffi4/doffisecure/data/advisor/ClaudeAdvisorPrompts.kt), блок `candidateV2`. Клиент получает этот текст через `ClaudeAdvisorPrompts.system()`, затем фиксированный суффикс `Respond in English.` или `Respond in Russian.`. Документ объясняет решения и историю; отдельной редактируемой копии prompt нет.

## Роль

Переводить уже рассчитанные **локально** password-hygiene findings в понятные ручные действия. Модель не выполняет анализ, сканирование, breach lookup или изменения vault. Она видит ровно четыре целочисленных агрегата; никаких секретов и записей.

Числа означают affected entries, не пароли, группы или проверенные сайты. Weak — локальная эвристика, не измерение entropy. Reuse — одинаковый пароль в записях с разными локальными account labels, не доказанная идентичность сайтов. Duplicate — все записи группы копий, включая оригинал/копии, без знания того, какую нужно оставить. Категории могут пересекаться; пересечение и число уникально затронутых записей неизвестны.

## Что кандидат запрещает

- Заявлять, что модель просмотрела пароли/аккаунты, запустила scanner или проверила утечки; объявлять пароль breached/unbreached или vault безопасным.
- Выдумывать accounts/services/domains/URLs, password examples, возраст пароля, MFA/passkey coverage или важность аккаунтов.
- Оценивать entropy, время взлома, длину/состав пароля, универсальный security score, risk percentage или security grade по этим агрегатам.
- Просить credentials, TOTP seeds/codes, passkey material, hashes/HIBP prefixes, usernames/emails, vault files, master/recovery material или encryption keys.
- Советовать unsafe/plaintext export, загрузку/передачу vault, сбор секретов для анализа, отключение защит или операции с криптографическими ключами.
- Автоматически удалять duplicates или представлять удаление копий как исправление reuse/weakness.

Это инструкции модели, не гарантия её поведения. Структурный whitelist, строгий parser, согласие пользователя, cancellation/auth checks и release gate остаются отдельными обязательными механизмами.

## Полезное поведение

Приоритет по умолчанию: уникальные сильные замены для reuse через собственные настройки соответствующих сервисов; усиление locally flagged weak passwords; ручной review нужных duplicate copies. Пользователь находит записи в локальных findings Decryptum; модель не называет аккаунты. Для больших чисел — разумные этапы, без требования мгновенно изменить всё.

Нули означают отсутствие findings только в этих проверках, не безопасность. N=0 означает отсутствие включённых в анализ записей с непустым паролем, не пустой vault. Invalid/missing data не превращается в нули. Реальный client/harness должен блокировать незаконченный локальный анализ до запроса; prompt не заменяет gate.

Ответ: только JSON `{ "overview": "...", "checklist": ["..."] }`, максимум 600 символов в overview, 1–6 шагов по 400 символов. Спокойные краткие глаголы действия, без alarmism, ссылок, Markdown fences или вопросов о приватных данных. Overview коротко сообщает aggregate-only scope и отсутствие breach assessment. Ключи JSON всегда English, пользовательский текст — выбранный EN/RU. Response schema, max_tokens=1200 и четыре input поля не менялись.

## История и аудит

| Версия | Статус | Изменение |
| --- | --- | --- |
| `aggregate-v1` | Неизменяемый baseline, live quality pending | Первоначальный короткий aggregate-only prompt сохранён byte-for-byte |
| `aggregate-v2-candidate` | Текущий debug/evaluation default, live quality pending | Явные role/input/limits/actions/output; entropy/export/score bans; различие zero findings / N=0; понятный manual priority |

SHA-256 **полного system message**, UTF-8, включая точный language suffix:

| Версия / язык | SHA-256 |
| --- | --- |
| v1 EN | `eac9aa664e7d7f3a77db427b90d104d213b4dc3751c7374d697a2dbf868c972c` |
| v1 RU | `c173ccd3e1d282279068e0da90e555a0cc2268e3c2f63f2a2f02cf59fb18b295` |
| v2 candidate EN | `a2af752a681cb4430c54c91045bb066863d9639f92018a48f2f862cc06bffd69` |
| v2 candidate RU | `880722cec8088e1c180b20bf0beca2e5342608e9e8ce88b40e2801147fd6bd23` |

Snapshot tests фиксируют эти байты; изменение текста под старым ID ломает тест. Harness дополнительно записывает SHA-256 **system + newline + canonical output_config**, поэтому его prompt_sha256 отличается от таблицы выше. Версия и hashes — локальные evaluation metadata, не дополнительные поля агрегата и не отдельный API field.

Правило следующего изменения: сохранить старый блок, добавить новый ID/блок, описать rationale и конкретную проблему, добавить новые snapshot expectations, прогнать прежний synthetic dataset с обеими версиями на одной выбранной модели, вручную проверить EN/RU и критические нарушения. Старые runs/reviews не переименовывать и не перезаписывать. Если изменится response schema, её изменение также обязательно фиксировать. Git diff/commit history после отдельного разрешённого сохранения дополняет hashes; hashes не являются криптографической подписью или доказательством безопасности.

Выбор версий ограничен `AdvisorPromptVersion`: произвольный prompt из UI, vault, runtime config или файла не принимается. Debug-приложение использует candidate; baseline выбирается явно в standalone evaluation. API-key/model lifecycle и release isolation не менялись.

## Проверка и пределы

Механические тесты проверяют неизменность v1/v2, наличие обязательных правил в фактическом system message, EN/RU suffix, одинаковую схему и четыре counts, совпадение selected version с реальным HTTP request и metadata стенда. Они **не доказывают**, что Claude выполнит инструкции или даст лучший ответ.

Синтетическая оценка обеих версий:

```powershell
# Use your installed compatible JDK for Gradle.
& .\scripts\run-advisor-eval.ps1 -PromptVersion aggregate-v1
& .\scripts\run-advisor-eval.ps1 -PromptVersion aggregate-v2-candidate
```

Это dry без API calls; каждый запуск создаёт отдельный каталог. Для будущего live сравнения использовать те же model ID, cases, языки/repeats и согласованный бюджет, указывая `-PromptVersion`. Больший prompt может увеличить input-token расходы; качественное улучшение, цена или количество tokens здесь не измерялись. Фактические live responses, модели и semantic scores пока отсутствуют. Production enablement требует отдельной оценки и решения; prompt сам по себе не является защитой vault.
