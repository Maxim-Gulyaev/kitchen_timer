# Platform Services

## Typical Contracts

- `TimerSoundPlayer`: play completion sound.
- `TimerHaptics`: signal completion or button feedback.
- `TimerNotifier`: schedule, update, or cancel notification.
- `AppLifecycleObserver`: reconcile foreground/background transitions.

## Contract Shape

Prefer small interfaces or `expect` declarations with names that describe app intent, not platform APIs.

## Completion Flow

Pure timer logic should emit or expose a completion transition. Platform adapters should observe that transition and fire sound, haptics, or notifications exactly once.
