# AntiXray

Эвристический анти-X-Ray с пакетными фантомными рудами и защитой от StorageESP/BaseFinder для Folia `26.2+`.

## Совместимость

| Параметр | Значение |
| --- | --- |
| Ядро | Folia, Paper (`api-version: '26.2'`, `folia-supported: true`) |
| Java | `25+` |
| Обязательно | `packetevents` |

## Сборка

```powershell
.\gradlew.bat shadowJar
```

Итоговый JAR: `build/libs/AntiXray-1.0.jar`.

## Команды

Основная команда: `/ax` (алиасы: `/antixray`, `/axr`).

| Команда | Действие | Право |
| --- | --- | --- |
| `/ax reload` | Перезагрузить `config.yml`, `basefinder.yml`, `message.yml` | `antixray.admin` |
| `/ax alerts` | Вкл/выкл алерты для модератора | `antixray.alerts` |
| `/ax check <игрок>` | Показать уровень подозрения и счётчики | `antixray.check` |
| `/ax spawn <игрок> [руда]` | Заспавнить фантомную жилу рядом с игроком | `antixray.spawn` |
| `/ax reset <игрок>` | Сбросить профиль и ловушки игрока | `antixray.admin` |
| `/ax history <игрок> [стр]` | История детектов из SQLite | `antixray.check` |
| `/ax update` | Скачать обновление с GitHub | `antixray.update` / `antixray.admin` |

## Права

| Право | Назначение | По умолчанию |
| --- | --- | --- |
| `antixray.admin` | Админ-команды и сброс профилей | op |
| `antixray.alerts` | Получение и переключение алертов | op |
| `antixray.check` | Команды `/ax check` и `/ax history` | op |
| `antixray.spawn` | Ручной спавн ловушки `/ax spawn` | op |
| `antixray.bypass` | Обход X-Ray проверок | false |
| `antixray.storage.bypass` | Обход скрытия сундуков и спавнеров | false |
| `antixray.storage.view` | Просмотр скрытых блоков в spectator | op |
| `antixray.update` | Уведомления и `/ax update` | op |

## Конфигурация

- `config.yml` — эвристики (`sharp-turn-to-ore`, `rapid-consecutive-veins`, `ore-ratio-window`, `darkness-mining`, `reaction-to-ore`), спад очков, фильтр пещер, фантомные ловушки, наказания, миры, SQLite `data.db`
- `basefinder.yml` — пакетное скрытие замурованных хранилищ и спавнеров; см. [модуль BaseFinder](basefinderru.md)
- `message.yml` — шаблоны алертов и ответы команд

[English documentation](README.md)
