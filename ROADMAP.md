# KidLock Remote — план доработок форка

> Форк [Lodoapoza/L-Lock](https://github.com/Lodoapoza/L-Lock) (AGPL-3.0), выросшего из
> [yanmasharski/kidlock-tv](https://github.com/yanmasharski/kidlock-tv). Полная цепочка
> происхождения — в [NOTICE.md](NOTICE.md). Цель: родительский контроль на Android TV
> с управлением с телефона/ПК, без облака и подписок.

## Текущее состояние (2026-11-15)

- ✅ Окружение сборки на ПК: JDK 17 (`D:\tools\jdk-17.0.21+9`), Android SDK (`D:\Android\Sdk`),
  проект `D:\Projects\LLock`; `gradlew assembleDebug --no-daemon` работает
- ✅ Русская локализация: `values-ru` (201/201 строк; 125 восстановлены из старого релизного APK,
  76 переведены вручную), в `build.gradle.kts` включён `localeFilters += "ru"`
- ✅ Ребейзинг: чистая git-история (`main`: baseline → rename), удалённый
  `github.com/Aliseiko/kidlock-remote`
- ✅ ТВ: Sony Bravia 2015, Android 7.0, `192.168.0.100`, приложение установлено, все права выданы
- ⏳ Осталось настроить на ТВ: сменить PIN (`000000`), выбрать режим, задать лимит

**Особенности инфраструктуры (важно для всех этапов):**

- Приёмные потоки adb на ТВ (и исходящие TCP с ПК в целом) нестабильны: `adb install`/`push`
  больших файлов зависает. **APK доставлять через файловый менеджер ТВ** (цикл: сборка →
  `KidLock-ru.apk` на флешку → установка). Мелкие adb-команды (shell, grant, settings, dpm)
  работают всегда.
- Никогда не делать `am force-stop` приложению: на Android 7 это стирает привязку
  accessibility-сервиса. Восстановление: `settings put secure enabled_accessibility_services
  uk.telegramgames.kidlock/uk.telegramgames.kidlock.ScreenTimeAccessibilityService` +
  `settings put secure accessibility_enabled 1`.
- После каждой установки — пакет выдачи прав (см. [Приложение A](#приложение-a--пакет-выдачи-прав-после-установки)).

---

## Этап 1. Лимиты времени по отдельным приложениям (~1–2 вечера)

**Цель:** «YouTube — не больше 30 мин/день», «IPTV — без лимита», для остальных — общий лимит.
Сейчас есть только глобальный дневной счётчик; по-приложенные ограничения возможны лишь через
расписания (`TimeWindow.blockedApps`).

### 1.1 Модель данных — `DataRepository`

```kotlin
// JSON в SharedPreferences: {"com.google.android.youtube": 30, "com.example.game": 15}
private const val KEY_APP_LIMITS = "per_app_limits"

fun getAppLimits(): Map<String, Int>          // pkg -> минут в день; отсутствует в карте = нет своего лимита
fun setAppLimit(pkg: String, minutes: Int?)   // null убирает лимит
```

Дейли-сброс не нужен: остаток вычисляется из `UsageStatsManager` (данные за «сегодня»
обновляются сами). Существующий `ensureDailyResetIfNeeded()` не трогаем.

### 1.2 Учёт — `UsageStatsHelper`

```kotlin
// один запрос INTERVAL_DAILY за окно [TimeManager.getTodayStartTime(), now]
fun getTodayUsagePerApp(): Map<String, Long>  // pkg -> totalTimeInForeground (мс)
```

Глобальный `getTodayUsageTimeMillis()` переводим на внутреннее использование этого же
запроса (меньше вызовов UsageStatsManager — он «дорогой»).

### 1.3 Логика блокировки — `ScreenTimeAccessibilityService`

В периодическом тике (5 с) и в `handleWindowStateChanged` порядок проверок:

1. **Расписание** (существующий `shouldBlockBySchedule`) — без изменений
2. **НОВОЕ: свой бюджет приложения** — если у foreground-пакета есть лимит в карте
   и `usage(pkg) >= limit` → блок с причиной `APP_LIMIT`
3. **Глобальный таймер** — существующая логика, только для приложений **без** своего лимита

Мастер-выключатель `isBlockingEnabled()` действует на всё. Свои пакеты, системные и лаунчер —
через существующий `isIgnoredPackage`/`isSystemPackage`. Разблокировка кодами в v1 действует
только на глобальный таймер (снять по-приложенный блок — повышением лимита в админке);
решение задокументировать в README.

### 1.4 Экран блокировки

Новая причина блокировки: `MainActivity` intent extra `BLOCK_REASON=APP_LIMIT` (+ имя пакета),
текст: «Лимит времени для приложения закончился». Отображаемое имя — через PackageManager.

### 1.5 Админ-панель

- Новый раздел «Лимиты приложений» (секции в `AdminActivity`/`AdminSectionAdapter`)
- Список установленных запускаемых приложений — переиспользовать `AppInfo`/`AppSelectorAdapter`
- В строке: имя, «лимит: N минут | без лимита», пресеты 5/10/15/30/60/90/120
- В «Мониторинге» (Dashboard): остаток по каждому за-лимиченному приложению на сегодня

### 1.6 Локализация

Новые ключи → `values-ru`, `values-en`, `values` (fr) одновременно — правило форка:
русская локализация полная с первого дня.

### Чек-лист этапа 1

- [ ] Лимит 30 мин на YouTube → YouTube закрывается по достижении, другие приложения живут
- [ ] Приложение без лимита подчиняется только глобальному таймеру
- [ ] Повышение лимита из админки снимает блок в течение 5 с
- [ ] Перезагрузка ТВ — учёт не обнуляется (UsageStats переживает ребут в рамках дня)
- [x] пер-app лимиты независимы от переключателя режимов: работают даже при
      выключенном таймере/расписании (фикс после полевых испытаний)

---

## Этап 2. HTTP API + веб-панель для телефона/ПК (~3–5 вечеров)

**Цель:** родитель управляет лимитами/расписанием с телефона или ПК браузером:
`http://192.168.0.100:8080`. Без отдельного приложения, без облака.

### 2.1 Сервер

- Зависимость: `org.nanohttpd:nanohttpd:2.3.1` (лёгкая, Maven Central, API 24 совместима)
- Новый класс `RemoteHttpServer : NanoHTTPD(0.0.0.0, port)`; порт — настройка
  (`KEY_HTTP_PORT`, по умолчанию 8080), тумблер «Веб-панель включена» (default ON)
- Жизненный цикл: запуск в `ScreenTimeAccessibilityService.onServiceConnected()`
  (стабильный процесс + автостарт уже есть через `BootReceiver`), остановка в `onDestroy()`

### 2.2 Аутентификация

- `POST /api/login {"pin":"123456"}` → `{"token": "...", "expiresAt": ...}` (сессии в памяти, TTL 8 ч)
- Проверка PIN — существующие `DataRepository.verifyPin` (хэш уже хранится)
- Rate limit — существующие `pinAttempts`/`pinLockoutUntil` (5 неудач → 60 с; счётчик общий
  с экраном PIN на ТВ — задокументировать)
- Все `/api/*` кроме `/api/login` требуют `Authorization: Bearer <token>`

### 2.3 Эндпоинты (JSON)

| Метод | Путь | Действие |
|---|---|---|
| GET | `/api/status` | режим, глобальный остаток, per-app остатки, активное окно расписания, `locked`+`reason`, foreground-приложение |
| GET | `/api/apps` | установленные запускаемые приложения (label, package) |
| GET/POST | `/api/limits` | глобальный дневной лимит (`{"minutes": 90}`) |
| GET/POST/DELETE | `/api/app-limits` | per-app бюджеты (`{"package": ..., "minutes": 30}`) |
| GET/POST | `/api/schedule` | окна расписания (валидация — существующая `canAddTimeWindow`) |
| POST | `/api/unlock` | `{"minutes": 15}` — добавить время; `{"untilTomorrow": true}` |
| POST | `/api/reset-today` | обнулить дневной счётчик |
| GET | `/` | статическая веб-панель из assets |

### 2.4 Потоки и конкурентность

- NanoHTTPD работает в рабочих потоках; **все записи в `DataRepository` постить на main
  handler** (паттерн уже есть в сервисе). Чтения — напрямую
- 5-секундный цикл сервиса подхватывает изменения автоматически — IPC не нужен
- Конфликт правок «ТВ-панель ↔ веб-панель»: last-write-wins, приемлемо

### 2.5 Веб-панель

- `app/src/main/assets/www/`: `index.html`, `app.js`, `style.css` — vanilla JS + fetch,
  **без CDN** (ТВ-сеть уже чудит; панель должна работать полностью локально)
- Экраны: Статус (остатки + прогресс-бары), Лимиты (глобальный + per-app), Расписание
  (список/редактор окон), Разблокировка (дать N минут / до завтра)
- Русский язык с первого дня; вёрстка под телефон и ПК

### 2.6 Безопасность

- Порт не пробрасывать наружу (для интернета — только туннель, этап 3)
- HTTPS v1 нет (self-signed в браузерах = предупреждения); PIN + rate limit достаточно для LAN
- Логировать только факты обращений без содержимого

### Чек-лист этапа 2

- [ ] С телефона: логин PIN → статус → выдать 15 минут → на ТВ блок снялась в течение 5 с
- [ ] 5 неверных PIN → блокировка 60 с (и на ТВ тоже)
- [ ] Перезагрузка ТВ → сервер поднялся сам (BootReceiver → сервис)
- [ ] Изменение расписания с телефона применяется и ловится анти-тампером сервиса
- [ ] Доступ с чужого устройства без PIN невозможен

---

## Этап 3. Управление из интернета (~1 вечер, без изменения кода)

**Цель:** тот же `http://192.168.0.100:8080` снаружи дома, без проброса портов.

- **Вариант A (рекомендуемый): Tailscale.** Установить на ПК → включить «subnet router»
  (advertise `192.168.0.0/24`) → на телефоне установить Tailscale и войти в тот же tailnet.
  Телефон обращается к ТВ-IP как дома. Минус: ПК должен быть включён.

  Конкретные шаги (вариант A):
  1. Установить Tailscale на ПК и телефон, войти под одним аккаунтом
  2. На ПК включить subnet-роутер (powershell от админа):
     `tailscale up --advertise-routes=192.168.0.0/24`
  3. В админ-консоли Tailscale (login.tailscale.com/admin/routes) — «Approve» маршрут
  4. На телефоне включить VPN-переключатель Tailscale → открыть `http://192.168.0.100:8080`
  5. PIN на панели остаётся обязательным; порт 8080 наружу не пробрасывать

- **Вариант B: WireGuard на роутере** (если роутер это умеет — OpenWrt/MikroTik/Kinetic):
  peer на телефон, маршрут в LAN. ПК выключать можно.
- **Вариант C: свой сервер (Headscale/WireGuard на VPS)** — полностью само-хостинг, сложнее.
- ❌ **Не делаем:** проброс 8080 на ТВ напрямую — прошивка 2015 года не должна светиться в интернете.
- PIN на панели остаётся обязательным даже внутри туннеля.

---

## Этап 4. Полировка

- README.md на русском: установка, настройка, скриншоты, ссылки на апстримы
- NOTICE.md актуализировать по мере добавления фич
- Регресс-тесты перед каждым «релизом» (установка на ТВ):
  - [ ] перезагрузка ТВ → сервис + веб-сервер поднялись, права не слетели
  - [ ] смена настроек с телефона во время активной блокировки
  - [ ] попытка удалить приложение без деактивации админа — блокируется
  - [ ] child не выходит из KidLock: HOME/BACK не выключают сервис (анти-тампер уже есть)
- Версионирование: `versionCode +1`, `versionName` — даты вместо «1.0» (`2026.11.16`)
- CI: `.github/workflows/android-build.yml` уже есть в апстриме — проверить, что собирает
  (ru locale в `localeFilters` уже учтён)

---

## Приложение A — пакет выдачи прав после установки

```bash
adb connect 192.168.0.100:5555

# 1. Usage Access (на Android 7 мало pm grant — нужен appops!)
adb shell pm grant uk.telegramgames.kidlock android.permission.PACKAGE_USAGE_STATS
adb shell appops set uk.telegramgames.kidlock android:get_usage_stats allow

# 2. Специальные возможности (привязка сервиса)
adb shell settings put secure enabled_accessibility_services \
  uk.telegramgames.kidlock/uk.telegramgames.kidlock.ScreenTimeAccessibilityService
adb shell settings put secure accessibility_enabled 1

# 3. Наложение окон (новое разрешение этой сборки)
adb shell appops set uk.telegramgames.kidlock SYSTEM_ALERT_WINDOW allow

# 4. Защита от удаления (device admin, политик нет)
adb shell dpm set-active-admin uk.telegramgames.kidlock/uk.telegramgames.kidlock.KidLockDeviceAdminReceiver

# 5. Запуск
adb shell am start -n uk.telegramgames.kidlock/.MainActivity
```

Проверка привязки: `adb shell dumpsys accessibility | grep -A4 services:` — должен быть
`Service[label=KidLock...]`.

## Приложение B — известные ограничения инфраструктуры

| Симптом | Причина | Обход |
|---|---|---|
| `adb install/push` виснет | приёмные TCP-потоки ТВ/исходящие ПК деградируют; убитые передачи клинят канал | APK через файловый менеджер ТВ; мелкие adb-команды — ок |
| «Приложение заблокировано» при установке APK из менеджера | unknown sources | Настройки → Безопасность и ограничения → Неизвестные источники |
| Accessibility «выключен» после force-stop | Android 7 стирает привязку | восстановление через `settings put secure` (см. выше), не использовать force-stop |
| PIN-лимит попыток общий | pinAttempts в SharedPreferences | осознанное решение, документируется |

## Порядок и статусы

| Этап | Статус | Оценка |
|---|---|---|
| 0. Сборка + RU + установка + права | ✅ готово | — |
| 1. Per-app бюджеты | ✅ `160b2ac` | 1–2 вечера |
| 2. HTTP API + веб-панель | ✅ `b05bfe1` | 3–5 вечеров |
| 3. Туннель из интернета | ⬜ нужен доступ к роутеру/ПК+телефону | 1 вечер |
| 4. Полировка (README, версии) | ✅ | по ходу |

Каждый этап — отдельный коммит; после этапа — сборка, установка через файловый менеджер,
пакет прав, чек-лист.
