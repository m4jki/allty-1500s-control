# ALLTY Control

Native Android controller for the Magicshine ALLTY 1500S (`M1-B3`) using only the confirmed local BLE protocol. The app has no account, analytics, ads, cloud integration, Google Play Services, or `INTERNET` permission.

## Build

Open the project in Android Studio, or run:

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Protocol safety

- `AlltyProtocol` is pure Kotlin and covered by exact-byte unit tests for every confirmed frame.
- Every custom mode uses its own firmware slot (`which`) from 1 through 20. Slot 1 remains the default for the original confirmed frame examples.
- The lamp exposes no confirmed custom-mode read command, so the displayed mode list is local DataStore state.
- A mode is persisted only after Android reports a successful characteristic write.
- Adding a mode rewrites the locally known configuration to its assigned slots first. The Synchronize button can repair a lamp programmed by older builds that always wrote slot 1.
- The factory restore flow sends confirmed `A2 / AA` deletes for locally known modes. It does not use the unconfirmed `A6` command.
- Battery and internal temperature use the Magicshine `A4/B4` and `A1/B1` query/notification pairs. Incoming frames are accepted only when their framing, declared length, and checksum are valid.

## BLE behavior

- Service: `0000FFE1-0000-1000-8000-00805F9B34FB`
- Characteristic: `0000FFE0-0000-1000-8000-00805F9B34FB`
- GATT writes are serialized and timeout after five seconds.
- Battery and internal temperature are queried after connection and every 60 seconds; the main screen also provides manual refresh. Queries use the timing observed in the Magicshine app, and a missing `B4` battery response triggers one standalone retry.
- The advanced force-clear flow sends 300 delete frames with a 60 ms delay and supports cancellation.
- Android 12+ uses `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`; older versions use location permission for BLE discovery.
