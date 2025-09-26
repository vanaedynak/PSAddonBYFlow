# PSAddonBYFlow

Аддон для [ProtectionStones](https://github.com/espidev/ProtectionStones), добавляющий систему прочности приватам на Paper 1.21.
Плагин хранит количество "жизней" для каждого региона, показывает их в голограмме и защищает приватные блоки от типичных эксплойтов.

## Возможности
- Настраиваемое количество жизней, урон от взрыва и смещение голограммы для каждого материала привата.
- Многострочные голограммы с плейсхолдерами `{lives}`, `{max}`, `{owner}`, координатами и HEX‑цветами.
- Автоматическое удаление голограмм при разрушении привата и поддержка плагина DecentHolograms.
- Защита от установки приватных блоков вплотную друг к другу и от разрушения визером.
- Интеграция с кастомными TNT по NBT-тегам (`customtntflow:tnt_type`, `customtntflow:traits` и произвольные маркеры из `primed.nbt-markers`):
  настраивайте урон, фильтр блоков и условия отмены взрыва для каждого типа.
- Настраиваемые сообщения об ошибках при попытке наложить приват.

## Сборка
1. Установите JDK 21.
2. Выполните `./gradlew build`. Готовый файл появится в `build/libs/PSAddonBYFlow-{version}.jar`.
3. Скопируйте `PSAddonBYFlow` и оригинальные ProtectionStones на Paper 1.21.1 и перезапустите сервер.

## Конфигурация `plugins/PSAddonBYFlow/config.yml`
```yaml
default:
  lives: 3
  damage-per-explosion: 1
  tnt-only: true
  hologram:
    enabled: true
    offset-y: 1.8
    lines:
      - "&cЖизни привата: &#ff5555{lives}&7/&f{max}"
      - "&7Владелец: &f{owner}"

blocks:
  DIAMOND_BLOCK:
    lives: 5
    hologram:
      lines:
        - "&bАлмазный приват"
        - "&f{lives}&7/&f{max} &8жизней"

prevent-stacking: true

messages:
  stack-block-denied: "&cНельзя ставить приват вплотную к другому приватному блоку!"

custom-tnt:
  enabled: true
  type-key: "customtntflow:tnt_type"
  traits-key: "customtntflow:traits"
  types:
    region_breaker:
      type: "region_breaker"
      traits:
        radius: 6
        drops: false
      nbt-markers:
        CustomTNTFlowMode: "marker_only"
      damage-override: 1
      only-region-blocks: true
      cancel-when-empty: true
```

- Раздел `default` определяет настройки по умолчанию для всех приватных блоков. Раздел `blocks` позволяет переопределить значения для конкретных материалов.
- Если `tnt-only: true`, урон региону наносят только взрывы TNT. Любые другие взрывы игнорируются.
- Блок `custom-tnt` описывает сопоставление по NBT: `type-key` и `traits-key` указывают, какие поля читать из PersistentDataContainer,
  а `nbt-markers` — прямые NBT-поля (например, из `primed.nbt-markers`), которые нужно сопоставить.
  Для каждого типа можно задать урон (`damage-override`), фильтрацию затронутых блоков (`only-region-blocks`) и отмену взрыва,
  если регионов рядом нет (`cancel-when-empty`).
- После изменения конфигурации перезапустите сервер, чтобы настройки применились.

## Хранение данных
Файл `plugins/PSAddonBYFlow/data.yml` содержит текущие жизни приватных регионов. Его можно безопасно удалить для сброса состояния — плагин создаст его заново при следующем запуске.
