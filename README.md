# KidLock Remote

Родительский контроль для Android TV с управлением из локальной сети (телефон/ПК) — без облака,
подписок и Google Family Link. Полностью open source (AGPL-3.0).

Форк [Lodoapoza/L-Lock](https://github.com/Lodoapoza/L-Lock), выросшего из
[yanmasharski/kidlock-tv](https://github.com/yanmasharski/kidlock-tv). Полная цепочка
происхождения — в [NOTICE.md](NOTICE.md), план развития — в [ROADMAP.md](ROADMAP.md).

## Возможности

- **Дневной лимит экранного времени** на весь телевизор (таймер)
- **Расписания**: блокировка/разрешение по часам и дням недели, с выбором приложений
- **Лимиты по приложениям** (этап 1): «YouTube — 30 мин/день» независимо от общего лимита
- **Веб-панель** (этап 2): управление с телефона/ПК браузером — статус, дать время,
  «открыть до завтра», лимиты, расписание. Встроенный сервер NanoHTTPD, порт 8080,
  вход по PIN с ограничением попыток. Никаких внешних сервисов
- **Защита от обмана**: PIN на настройки, device admin против удаления, анти-тампер
  accessibility-сервиса, гард на Settings/Play Store при активной блокировке
- **Коды-разблокировки**: одноразовые 6-значные коды для «ещё 15 минут»
- **Русский интерфейс** по умолчанию (полная локализация), также EN/FR

## Установка на ТВ

1. Скопировать `app-debug.apk` на флешку, открыть файловым менеджером на ТВ и установить
   (если «Приложение заблокировано для защиты» — включить «Неизвестные источники»
   в Настройки → Безопасность и ограничения)
2. Выдать разрешения (adb с ПК, TV по сети):

```bash
adb connect <tv-ip>:5555
adb shell pm grant uk.telegramgames.kidlock android.permission.PACKAGE_USAGE_STATS
adb shell appops set uk.telegramgames.kidlock android:get_usage_stats allow
adb shell settings put secure enabled_accessibility_services \
  uk.telegramgames.kidlock/uk.telegramgames.kidlock.ScreenTimeAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell appops set uk.telegramgames.kidlock SYSTEM_ALERT_WINDOW allow
adb shell dpm set-active-admin uk.telegramgames.kidlock/uk.telegramgames.kidlock.KidLockDeviceAdminReceiver
adb shell am start -n uk.telegramgames.kidlock/.MainActivity
```

3. Первым делом сменить PIN: по умолчанию `000000`.

## Веб-панель

- Раздел «Система» → «Веб-панель (телефон/ПК)» → включить; адрес подскажет приложение
- С телефона/ПК: `http://<ip-ТВ>:8080`, вход по родительскому PIN
- Для доступа из интернета — только через туннель (Tailscale/WireGuard), см. ROADMAP.md.
  Проброс порта 8080 не использовать

## Сборка

```bash
JAVA_HOME=<jdk-17+> ./gradlew assembleDebug --no-daemon
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Требуется Android SDK (platform 34, build-tools 35); путь к SDK — в `local.properties`.
Release-подпись авторов не публикуется — собирайте debug.

## Известные ограничения

- Приёмные TCP-потоки на части старых ТВ (в т.ч. Sony Bravia 2015, Android 7) нестабильны:
  `adb install/push` больших файлов может зависать — APK доставляйте файловым менеджером,
  adb используйте только для мелких команд
- Веб-панель: HTTP без TLS (только доверенная сеть), порт фиксирован (8080)
- Без разрешения Usage Stats лимиты не учитываются (приложение подскажет)

## Лицензия

AGPL-3.0 — см. [LICENSE](LICENSE) и [NOTICE.md](NOTICE.md).
