# Plan: Migrate `App.kt` to a Dedicated State Holder (ViewModel / UDF)

## Findings that shape the plan

- **State in `App()`** (`App.kt:45–88`): `isConfigMode`, `isStatusMode`, `statusLeverIndex`, `configVersion`, `tabs`, `selectedTabIndex`, `allLeverStates` (snapshot list of `BooleanArray`), `allManualLocks`, `errorMessage`.
- **Side effects inside the Composable** (must move out):
  - `LaunchedEffect(Unit) { LccNode.initialize() }` — `App.kt:50`
  - `LaunchedEffect(configVersion) { tabs = ConfigManager.parseConfig(...) }` — `App.kt:57`
  - `LaunchedEffect(allLeverStates.toList()) { ConfigManager.saveCurrentLeverStates(...) }` — `App.kt:82` (re-snapshots the whole collection on every mutation)
  - `LaunchedEffect(configVersion) { LccNode.externalEvents.collect {...} }` — `App.kt:90`, contains the `conflict_policy` rules interleaved with tab/lever iteration.
- **Singletons**: `ConfigManager` (`currentConfig: JsonConfig` as `mutableStateOf`) and `LccNode` (`externalEvents: SharedFlow<String>`). UI currently reads `conflict_policy` directly (`App.kt:206`) and mutates config inline (`App.kt:280–289`).
- **Interlocking.evaluate / getConflictingLevers** are pure — ideal for unit tests once the policy dispatch (`App.kt:117–124`) is extracted.
- **Deps already present** in `shared/build.gradle.kts`: `libs.androidx.lifecycle.viewmodelCompose` and `runtimeCompose` are in `commonMain`. `ViewModel` + `viewModel()` + `collectAsStateWithLifecycle` work on Android, JVM, iOS **without new deps**.
- **Tests** live under `shared/src/commonTest/` (e.g. `InterlockingTest.kt`); a new `LeverFramePolicyTest` goes there so it runs on all targets via `./gradlew check`. Pure UDF logic needs no Compose.
- **AGENTS.md constraints**: keep code in `commonMain` unless platform-specific; any new `expect` would need 3 actuals (none expected here).

## Step-by-step

### Step 1 — Define immutable UI state + intent model
Create `shared/src/commonMain/kotlin/org/example/project/LeverFrameState.kt`:

```kotlin
data class LeverFrameUiState(
    val tabs: List<Pair<String, TabDef>> = emptyList(),
    val selectedTabIndex: Int = 0,
    val leverStates: List<BooleanArray> = emptyList(),
    val manualLocks: List<BooleanArray> = emptyList(),
    val isConfigMode: Boolean = false,
    val isStatusMode: Boolean = false,
    val statusLeverIndex: Int? = null,
    val errorMessage: String? = null,
    val conflictingLevers: List<Int> = emptyList(),
    val configVersion: Int = 0,
)

sealed interface LeverFrameIntent {
    data class TabSelected(val index: Int) : LeverFrameIntent
    data class ToggleLever(val tabIndex: Int, val leverIndex: Int) : LeverFrameIntent
    data class ToggleManualLock(val tabIndex: Int, val leverIndex: Int) : LeverFrameIntent
    data class LeverLabelClicked(val leverIndex: Int) : LeverFrameIntent
    object EnterConfigMode : LeverFrameIntent
    object ExitConfigMode : LeverFrameIntent
    object ConfigSaved : LeverFrameIntent
    object EnterStatusMode : LeverFrameIntent
    object ExitStatusMode : LeverFrameIntent
    object DismissStatusLever : LeverFrameIntent
    data class SetLeverLccEnabled(val tabIndex: Int, val leverIndex: Int, val enabled: Boolean) : LeverFrameIntent
}
```

### Step 2 — Create `AppViewModel : ViewModel()`
Create `shared/src/commonMain/kotlin/org/example/project/AppViewModel.kt`:

- Own `_tabs`, `_allLeverStates`, `_allManualLocks` (rebuilt on `configVersion` change, mirroring `App.kt:64–80`).
- `init { viewModelScope.launch { LccNode.initialize(); LccNode.externalEvents.collect { handleExternalEvent(it) } } }` — moves `App.kt:50` and `App.kt:90–135`.
- Extract `conflict_policy` dispatch into a pure `applyExternalLeverChange(...)` (Step 3).
- Replace the wasteful `LaunchedEffect(allLeverStates.toList())` persistence with a small `persistLeverStatesIfEnabled()` called after each successful mutation.
- Expose `val uiState: StateFlow<LeverFrameUiState>` (`stateIn(viewModelScope, SharingStarted.Eagerly, initial)`).
- Expose `fun dispatch(intent: LeverFrameIntent)`.

### Step 3 — Extract pure interlocking/policy logic
Add to `Interlocking.kt` (or new `LeverFramePolicy.kt`) — pure, no Compose, no ConfigManager:

```kotlin
enum class ConflictPolicy(val id: Int) { STRICT(1), PERMISSIVE(2), ALARM(3);
    companion object { fun of(id: Int) = entries.firstOrNull { it.id == id } ?: PERMISSIVE }
}

fun shouldApplyExternalEvent(policy: ConflictPolicy, isValid: Boolean): Boolean =
    !(policy == STRICT && !isValid)

fun attemptToggle(tabDef: TabDef, states: BooleanArray, leverIndex: Int, target: Boolean): BooleanArray?
```

Note: current code (`App.kt:117–124`) only implements STRICT-ignore vs. else-apply; ALARM falls into the `else` and applies while `getConflictingLevers` (`App.kt:207`) provides the visual flag. Preserve this in Step 3, pin it with a test.

### Step 4 — Rewire `App()` to consume state
- `val vm: AppViewModel = viewModel { AppViewModel() }` and `val state by vm.uiState.collectAsStateWithLifecycle()`.
- Delete the four `LaunchedEffect` blocks (`App.kt:50`, `57`, `82`, `90`).
- Replace inline callbacks with `vm.dispatch(...)` calls.
- `LeverComponent` stays presentational — no signature change beyond receiving already-computed flags.

### Step 5 — Move config-mutation callbacks through the VM
`App.kt:280–289` currently mutates `ConfigManager.currentConfig`, calls `saveConfigToFile`, re-reads `tabs` in the UI. Wrap as `LeverFrameIntent.SetLeverLccEnabled`; VM does the copy + persist + `configVersion++` so the StateFlow re-derives `tabs`. (Same refactor for `ConfigurationScreen`/`SystemStatusScreen` is out of scope but noted.)

### Step 6 — ViewModel factory
Default `viewModel { }` factory is fine — `AppViewModel` has no constructor args. No new code unless you later inject deps.

### Step 7 — Unit tests in `commonTest`
Create `shared/src/commonTest/kotlin/org/example/project/LeverFramePolicyTest.kt` mirroring `InterlockingTest.kt`:

- `testStrictPolicyRejectsInvalidExternalEvent`
- `testPermissivePolicyAppliesExternalEvent`
- `testAlarmPolicyAppliesButFlagsConflict`
- `testAttemptToggleRespectsInterlocking`
- (optional) `testExternalEventMatchesCorrectLeverByHexId`

Run: `./gradlew :shared:jvmTest --tests "org.example.project.LeverFramePolicyTest"` and `./gradlew check`.

### Step 8 — Verification (per AGENTS.md order)
1. `./gradlew :shared:compileKotlinJvm`, `:shared:compileKotlinIosSimulatorArm64`, `:androidApp:compileDebugKotlin`.
2. `./gradlew :shared:jvmTest --tests "org.example.project.LeverFramePolicyTest"`.
3. `./gradlew check`.
4. `./gradlew :desktopApp:run` — smoke: toggle levers, manual lock, config save (forces `configVersion++`), system status, lever label → status screen → toggle `lcc_enabled`.
5. `./gradlew :desktopApp:hotRun --auto` for iteration.

### Step 9 — Cleanup
- Remove dead `remember`/`LaunchedEffect` blocks from `App.kt` (`50–135`).
- Leave `ConfigManager`/`LccNode` singletons untouched this step (injectable follow-up).
- Update `AGENTS.md` only if the new VM/policy files change canonical layout, after verification passes.

## Open questions (non-blocking — defaults above preserve current behavior)
1. UDF style: intent sealed interface + `dispatch()` vs. plain public methods? *(Default: intent sealed interface.)*
2. ALARM policy: apply + flag (current) vs. reject + flag? *(Default: apply + flag, pinned by test.)*
3. Inject `LccNode`/`ConfigManager` for testability or keep singletons? *(Default: keep singletons this step.)*