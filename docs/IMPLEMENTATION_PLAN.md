# KitchenTimer: план реализации MVP

План составлен на основе продуктового описания из [`PRODUCT.md`](PRODUCT.md) и текущего состояния KMP-проекта.

## 0. Зафиксировать продуктовые решения

Перед platform-интеграциями принять следующие решения для первой версии:

- приложение поддерживает один таймер;
- доступны фиксированные пресеты: 1, 3, 5, 10, 15 и 30 минут;
- пользовательские пресеты и действие `+1 мин` не входят в MVP;
- сигнал завершения воспроизводится ограниченное время, например 30–60 секунд;
- корректная работа при переходах background/foreground входит в MVP;
- восстановление таймера после полного завершения процесса реализуется отдельным этапом после базового MVP;
- изменение выбранного времени во время `Running` запрещено: сначала пользователь должен остановить или сбросить таймер.

## 1. Реализовать timer domain

Размещение:

```text
shared/src/commonMain/kotlin/com/maxim/kitchentimer/timer/
```

Основные модели:

- `TimerStatus`: `Idle`, `Running`, `Paused`, `Finished`;
- `TimerPreset`: стабильный id, label и duration;
- `TimerState`: исходная и оставшаяся длительность, status и progress;
- `TimerIntent`: изменение времени, выбор пресета, start, pause, resume, stop, reset и restart;
- `TimerEvent`: одноразовое событие завершения.

Ключевая механика:

- хранить deadline активного таймера, а не уменьшать остаток исключительно по UI-тикам;
- использовать абстракцию монотонного времени;
- на каждом tick и при возвращении из background вычислять `remaining = max(0, deadline - now)`;
- гарантировать один countdown job на один запуск;
- эмитить completion event ровно один раз;
- при `Pause` фиксировать фактический остаток;
- при `Reset` возвращать исходную длительность и состояние `Idle`;
- при `Restart` из `Finished` запускать ту же длительность заново;
- не допускать перехода нулевой длительности в `Running`.

Результат этапа: чистая timer state machine без Android/iOS API.

## 2. Добавить state holder и deterministic tests

Создать общий `TimerViewModel` или `TimerStore`, который отвечает за:

- `StateFlow<TimerState>`;
- обработку `TimerIntent`;
- lifecycle ticker-а;
- одноразовые `TimerEvent`;
- injectable clock/ticker.

Тесты разместить в:

```text
shared/src/commonTest/kotlin/com/maxim/kitchentimer/timer/
```

Минимальный test matrix:

- запуск с ненулевой и нулевой длительностью;
- защита от двойного запуска;
- pause/resume без потери времени;
- stop и reset из `Running` и `Paused`;
- restart после завершения;
- tick через ноль с clamp до `0`;
- completion event только один раз;
- пересчёт после длительного отсутствия UI-тиков;
- смена пресета в допустимых состояниях;
- форматирование `MM:SS` и `HH:MM:SS`.

Проверка:

```shell
./gradlew :shared:allTests
```

## 3. Реализовать shared Compose UI

Размещение:

```text
shared/src/commonMain/kotlin/com/maxim/kitchentimer/ui/
shared/src/commonMain/kotlin/com/maxim/kitchentimer/ui/components/
shared/src/commonMain/kotlin/com/maxim/kitchentimer/ui/theme/
```

Один основной экран должен содержать:

- крупное и стабильное отображение оставшегося времени;
- круговой или линейный progress indicator;
- ручной ввод часов, минут и секунд;
- ряд или сетку пресетов;
- primary action, зависящий от состояния;
- отдельные stop/reset actions;
- явно различимое состояние `Finished`;
- кнопки остановки сигнала и повторного запуска.

Маппинг UI по состояниям:

- `Idle`: ввод времени, пресеты и Start;
- `Running`: Pause и Stop/Reset, редактирование времени отключено;
- `Paused`: Resume и Stop/Reset;
- `Finished`: Stop signal/Dismiss и Restart.

UX-требования:

- крупные touch targets;
- отсутствие layout jitter при смене цифр;
- передача состояния текстом или иконкой, а не только цветом;
- `contentDescription` для icon-only controls;
- поддержка увеличенного системного шрифта;
- корректная компоновка на небольших экранах.

Проверка:

```shell
./gradlew :androidApp:assembleDebug
```

## 4. Ввести common-контракты platform services

Размещение:

```text
shared/src/commonMain/kotlin/com/maxim/kitchentimer/platform/
```

Контракты:

- `MonotonicClock`;
- `TimerSoundPlayer`;
- `TimerHaptics`;
- `TimerNotifier`;
- при необходимости `AppLifecycleObserver`.

Platform effects запускаются обработчиком `TimerEvent`, а не из composable или reducer. Это предотвращает повторный звук и повторные уведомления при recomposition.

Единый coordinator должен:

- при запуске таймера планировать системное уведомление;
- при pause/reset/stop отменять его;
- при completion запускать foreground sound/haptic;
- при dismiss останавливать сигнал;
- превращать отсутствие permission или недоступность API в безопасный no-op.

## 5. Реализовать Android adapters

Размещение:

```text
shared/src/androidMain/kotlin/com/maxim/kitchentimer/platform/
```

Работы:

- использовать `SystemClock.elapsedRealtime()` как монотонный источник времени;
- создать notification channel;
- обработать runtime permission `POST_NOTIFICATIONS`;
- планировать completion notification;
- реализовать sound и vibration/haptic;
- отменять или обновлять notification при изменениях таймера;
- выполнять reconciliation при возвращении приложения в foreground.

`MainActivity` остаётся тонким composition root: создаёт platform adapters и передаёт зависимости в shared `App`.

Ручные проверки:

- completion в foreground;
- completion в background;
- завершение при заблокированном экране;
- отказ в notification permission;
- pause/reset до срабатывания уведомления;
- повторный запуск без старого pending notification.

## 6. Реализовать iOS adapters

Размещение:

```text
shared/src/iosMain/kotlin/com/maxim/kitchentimer/platform/
```

Работы:

- реализовать монотонный clock;
- использовать `UNUserNotificationCenter` для local notifications;
- запросить notification permission;
- реализовать haptic feedback и completion sound;
- отменять scheduled notification;
- выполнять reconciliation при переходах active/background.

`MainViewController` и SwiftUI entry point остаются тонкими composition roots.

Ручные проверки:

- completion в foreground/background;
- завершение при заблокированном экране;
- отказ в permission;
- pause/reset отменяет старое уведомление;
- completion effects не дублируются после возврата в приложение.

## 7. Обеспечить корректную работу в background

Обязательная часть MVP:

- при переходе в background сохранить deadline активного таймера;
- запланировать системное уведомление;
- при foreground немедленно пересчитать остаток;
- если deadline уже прошёл, перейти в `Finished` ровно один раз;
- использовать UI ticker только для обновления экрана, но не как источник истины.

Отдельная post-MVP задача:

- сохранять snapshot активного таймера;
- восстанавливать состояние после process death или перезагрузки;
- определить политику обработки reboot, поскольку монотонная временная база сбрасывается.

## 8. Финальная стабилизация

Acceptance-сценарий для Android и iOS:

1. Установить время вручную.
2. Запустить таймер и несколько раз выполнить pause/resume.
3. Свернуть приложение и дождаться завершения.
4. Получить системное уведомление.
5. Вернуться в приложение и увидеть `Finished`.
6. Остановить сигнал.
7. Перезапустить ту же длительность.
8. Сбросить таймер.
9. Повторить сценарий с запрещёнными уведомлениями.

Финальный verification pipeline:

```shell
./gradlew :shared:allTests
./gradlew :shared:assemble
./gradlew :androidApp:assembleDebug
```

iOS дополнительно собирается и проходит smoke-тестирование через Xcode.

## Milestones

1. Domain, fake time и common tests.
2. Полностью рабочий foreground UI.
3. Android sound, haptic, notifications и background lifecycle.
4. iOS platform integrations.
5. Accessibility, edge cases и release stabilization.
6. После MVP: восстановление после process death.

## Архитектурный принцип

Deadline и timer state находятся в shared domain, Compose только отображает state и отправляет intents, а Android/iOS adapters отвечают за системные side effects. Такой контракт сохраняет одинаковую семантику таймера на обеих платформах и снижает риск background-регрессий.
