@copilot: Es ist nicht erlaubt, diesen Plan ohne Rücksprache mit dem Entwickler zu ändern. Alle Änderungen müssen in enger Abstimmung erfolgen, um die Komplexität und die Auswirkungen auf bestehende Funktionalitäten zu minimieren. Bitte kontaktiere den Entwickler, bevor du Änderungen vornimmst.
@copilot: Bitte behalte den aktuellen Branch bei und erstelle einen neuen Branch für die Implementierung dieses Plans, um die Übersicht zu behalten und die Integration zu erleichtern.




# Plan: STMCapable Interface Architecture

**Problem:** FSB14-spezifischer Code ist im `EnOceanBaseActuatorHandler` verstreut. Zwei EEPs (A5_3F_7F_EltakoFSB für Senden, PTM200Message für Empfangen) teilen sich eine STM mit enger Handler-Kopplung.

**Ziel:** EEPs deklarieren ihre STM-Fähigkeiten via Interface, der BaseActuatorHandler bleibt generisch.

---

## Steps

### 1. `STMCapable` Interface erstellen
- Neues Interface in `internal/eep/STMCapable.java`
- Methoden:
  - `STMTransitionConfiguration getTransitionConfiguration()`
  - `STMState getInitialState()`
  - `Set<STMAction> getRequiredCallbackActions()`
  - `void initializeChannels(ThingBuilder thingBuilder, Consumer<Thing> updateThing)`

### 2. `A5_3F_7F_EltakoFSB` anpassen
- Implementiert `STMCapable`
- `getTransitionConfiguration()` gibt `BLINDS` oder `ROLLERSHUTTER` zurück (aus ConfigMode)
- `getRequiredCallbackActions()` gibt benötigte Callbacks zurück
- `initializeChannels()` enthält Channel-Entfernung (DIMMER etc.)
- `handler.setState()` Aufrufe entfernen (nicht mehr nötig)

### 3. `EnOceanBaseActuatorHandler` anpassen
- FSB14-spezifischen Code durch generischen STMCapable-Code ersetzen:
```java
if (eep instanceof STMCapable stmEEP) {
    stm = STMStateMachine.build(
        stmEEP.getTransitionConfiguration(),
        stmEEP.getInitialState(),
        scheduler, this::onStateChanged
    );
    stmEEP.getRequiredCallbackActions().forEach(a -> stm.register(a, this::processStoredCommand));
    stmEEP.initializeChannels(editThing(), this::updateThing);
    restoreStateMachineState();
}
```
- `onStateChanged()`, `restoreStateMachineState()`, `processStoredCommand()` bleiben unverändert

### 4. PTM200Message STM-Kopplung lösen
- Statt direktem `stm.apply()` Aufruf: Return eines `STMActionHint` in der Response
- Handler interpretiert den Hint und ruft `stm.apply()` auf
- Alternative: `STMActionProvider` Interface für empfangende EEPs

### 5. `EEPType.usesSTM` Flag entfernen
- Ersetzt durch `instanceof STMCapable` Prüfung
- Aufräumen in `EEPType.java`

---

## Verification

- Build mit `mvn clean install -DskipTests`
- Manueller Test: FSB14 Thing erstellen, Position fahren, Feedback empfangen
- State-Persistence testen (Neustart, State aus Thing Properties)
- `mvn spotless:apply` für Formatierung

---

## Decisions

- **Kein separater Handler:** STM-Code bleibt im BaseActuatorHandler (~15 Zeilen generischer Code)
- **Zwei-EEP-Problem:** Handler orchestriert STM, EEPs liefern nur Hints/Konfiguration
- **Backward Compatibility:** Kein neuer Thing-Type nötig, keine Factory-Änderungen
- **Scheduler-Entkopplung:** Später adressieren oder als separater Callback

---

## Key Files

| File | Action |
|------|--------|
| `STMCapable.java` | NEW - Interface defining STM configuration methods |
| `A5_3F_7F_EltakoFSB.java` | MODIFY - Implement STMCapable |
| `EnOceanBaseActuatorHandler.java` | MODIFY - Replace FSB14-specific code with generic STMCapable code |
| `PTM200Message.java` | MODIFY - Return action hints instead of direct STM calls |
| `EEPType.java` | MODIFY - Remove usesSTM flag |

---

## Challenges

1. **Two-EEP coordination**: A5_3F_7F_EltakoFSB sends, PTM200/F6_00_00 receives. STM updates happen in both. Handler must coordinate.

2. **Channel management**: `CHANNEL_DIMMER` and `CHANNEL_STATEMACHINESTATE` sind conditional - Logik wandert ins EEP via `initializeChannels()`.

3. **Receiving EEP STM access**: `PTM200Message.convertToStateImpl()` manipuliert STM direkt. Muss über Hints/Interface entkoppelt werden.
