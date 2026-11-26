# Защита от BaseFinder и StorageESP

Модуль пакетного скрытия хранилищ и спавнеров, настраивается в `plugins/AntiXray/basefinder.yml`.

## Принцип работы

1. При отправке чанка (`CHUNK_DATA`) AntiXray проверяет блоки из `hidden-blocks` (`CHEST`, `BARREL`, `SPAWNER`, `SHULKER_BOX` и др.).
2. Если все 6 соседей являются глухими блоками (`OccTbl`), блок и его tile entity подменяются на частый соседний блок (`use-neighbor-camouflage`) или запасной блок мира (`above-replacement` / `below-replacement`).
3. Пакеты `BLOCK_ENTITY_DATA`, `BLOCK_ACTION`, `BLOCK_CHANGE` и `MULTI_BLOCK_CHANGE` для скрытых координат фильтруются до раскрытия блока.

## Триггеры раскрытия

Скрытые блоки автоматически раскрываются (включая NBT и вторую половину двойного сундука) при:
- начале копания (`on-start-dig`)
- поломке соседа (`on-block-break`)
- взрывах (`on-explosion`), поршнях (`on-piston`), сгорании (`on-block-burn`) или клике (`on-interact`)

## Обход

- Право `antixray.storage.bypass` при `bypass.permission-enabled: true`
- Режим креатива (`bypass.ignore-creative`)
- Режим наблюдателя (`bypass.ignore-spectator`) или модераторы с правом `antixray.storage.view` (`bypass.ignore-spectator-moderators`)

[English version](basefinder.md)
