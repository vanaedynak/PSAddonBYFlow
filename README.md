# PSAddonBYFlow

Набор аддонов для [ProtectionStones](https://github.com/espidev/ProtectionStones) на Paper 1.21.1. Gradle-проект собирает два отдельных плагина:

- **PSAddonRegions** — расширяет механику регионов ProtectionStones системой жизней, голограммами и защитой от наложений.
- **PSAddonCustomTnt** — добавляет настраиваемые типы динамита, которые можно использовать для разрушения регионов и специальных блоков.

## Сборка
1. Убедитесь, что установлены JDK 21 и Gradle 8+. Выполните `gradle assemble` (или `./gradlew assemble`, если используете локальный wrapper).
2. После сборки заберите артефакты из каталогов модулей: `region-addon/build/libs/PSAddonRegions-{version}.jar` и `custom-tnt/build/libs/PSAddonCustomTnt-{version}.jar`.
3. Поместите оба плагина в папку `plugins` сервера вместе с установленным ProtectionStones и перезапустите сервер.

## Плагин PSAddonRegions
### Возможности
- Персистентный запас «жизней» у каждого региона (по умолчанию 3), хранящийся в `plugins/PSAddonRegions/data.yml`.
- Переопределение настроек для каждого приват-блока: количество жизней, урон за взрыв, тексты голограмм и смещения.
- Поддержка собственных голограмм через `TextDisplay` и интеграция с [DecentHolograms](https://www.spigotmc.org/resources/decent-holograms.96927/) при наличии плагина.
- HEX‑цвета, многострочные подписи, плейсхолдеры `{owner}`, `{world}`, координаты и другие значения.
- Защита от стэкинга приватных блоков и от разрушения визером.
- Взрывы учитываются только от кастомных TNT, перечисленных в `allowed-custom-tnt`, что исключает нанесение урона обычными динамитами.

### Конфигурация `plugins/PSAddonRegions/config.yml`
```yaml
default:
  lives: 3
  damage-per-explosion: 1
  tnt-only: true
  allowed-custom-tnt:
    - region_breaker
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
```

- `allowed-custom-tnt` — список идентификаторов из конфигурации плагина кастомных TNT, которые могут наносить урон региону. Если список пуст, регион не будет получать урон даже от кастомного динамита.
- Значение `tnt-only: true` полностью блокирует любой другой источник взрывов.
- Переопределения в `blocks` работают как и прежде; не указанные параметры берут значения из `default`.

После изменения конфигурации перезапустите сервер, чтобы настройки применились.

## Плагин PSAddonCustomTnt
### Возможности
- Полностью конфигурируемые предметы динамита: материал, модель, название, лор, длительность фитиля и мощность взрыва.
- Режимы размещения: можно оставить блок до ручного поджога или сразу спавнить зажжённый TNT.
- Настройки, влияющие на поведение: работа под водой, пробитие только указанных блоков, принудительное разрушение обсидиана/бедрока, выбор — повреждать регион или блоки.
- Выдача через команду `/customtnt give <игрок> <тип> [кол-во]` (право `psaddon.customtnt.give`).
- Привязка к регионам: только типы с `affects-regions: true` и совпадающим идентификатором из `allowed-custom-tnt` у блока смогут снимать жизни привата.

### Конфигурация `plugins/PSAddonCustomTnt/config.yml`
```yaml
tnts:
  region_breaker:
    display-name: "&cRegion Breaker"
    lore:
      - "&7Ломает только регионы ProtectionStones"
      - "&7и не повреждает блоки"
    material: TNT
    auto-ignite-when-placed: false
    drop-when-broken: true
    ignite-in-water: true
    fuse-ticks: 80
    explosion-power: 4.0
    affects-regions: true
    only-damage-regions: true
    block-damage:
      mode: NONE

  obsidian_miner:
    display-name: "&6Obsidian Miner"
    material: TNT
    auto-ignite-when-placed: true
    ignite-in-water: true
    fuse-ticks: 60
    explosion-power: 5.0
    affects-regions: false
    only-damage-regions: false
    block-damage:
      mode: ALLOW_LIST
      blocks:
        - OBSIDIAN
        - CRYING_OBSIDIAN
        - ANCIENT_DEBRIS
      force-break:
        - OBSIDIAN
        - CRYING_OBSIDIAN
        - ANCIENT_DEBRIS

  bedrock_cracker:
    display-name: "&5Bedrock Cracker"
    material: TNT
    auto-ignite-when-placed: true
    ignite-in-water: false
    fuse-ticks: 40
    explosion-power: 3.5
    affects-regions: false
    only-damage-regions: false
    block-damage:
      mode: ALLOW_LIST
      blocks:
        - BEDROCK
      force-break:
        - BEDROCK
```

- `auto-ignite-when-placed` — при `true` блок не ставится, а сразу создаётся зажжённый TNT.
- `affects-regions` и `only-damage-regions` управляют взаимодействием с регионом и окружающими блоками.
- `block-damage.mode`: `ALL`, `NONE`, `ALLOW_LIST`, `DENY_LIST`. Раздел `force-break` позволяет принудительно разрушить блоки, которые ванильный взрыв обычно не ломает (например, обсидиан или бедрок).
- Идентификатор типа (`region_breaker`, `obsidian_miner`, …) используется в `allowed-custom-tnt` плагина регионов.

При любых изменениях в конфигурации плагина кастомного динамита перезапускайте сервер, чтобы обновить зарегистрированные типы.
