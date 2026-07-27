package io.github.quinnjr.sidekey.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.quinnjr.sidekey.bootstrap.BootstrapState
import io.github.quinnjr.sidekey.bootstrap.Bootstrapper
import io.github.quinnjr.sidekey.bootstrap.ShizukuEnv
import io.github.quinnjr.sidekey.core.AndroidGlobalSettings
import io.github.quinnjr.sidekey.core.Behavior
import io.github.quinnjr.sidekey.core.KeyBehaviorRepo
import io.github.quinnjr.sidekey.core.WriteResult
import io.github.quinnjr.sidekey.report.DeviceReport
import io.github.quinnjr.sidekey.report.DeviceReportCollector
import io.github.quinnjr.sidekey.report.FixOutcome
import io.github.quinnjr.sidekey.service.PinService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SetupUiState(
    val observed: Behavior? = null,
    val desired: Behavior = Behavior.PowerMenu,
    val bootstrap: BootstrapState = BootstrapState.ShizukuMissing,
    val lastResult: WriteResult? = null,
    val rawInput: String = "",
    val showReport: Boolean = false,
    val outcome: FixOutcome = FixOutcome.NotTried,
)

class SetupViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = AndroidGlobalSettings(app)
    private val repo = KeyBehaviorRepo(settings)
    private val store = DesiredBehaviorStore(app)
    private val env = ShizukuEnv(app)
    private val bootstrapper = Bootstrapper(env, app.packageName)
    private val collector = DeviceReportCollector(app, settings)

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        refresh()
        store.desired()?.let { saved -> _state.update { it.copy(desired = Behavior.fromInt(saved)) } }
    }

    fun refresh() = _state.update {
        it.copy(observed = repo.observed(), bootstrap = bootstrapper.state())
    }

    fun choose(behavior: Behavior) = _state.update { it.copy(desired = behavior) }

    fun onRawInput(text: String) = _state.update { it.copy(rawInput = text.filter(Char::isDigit)) }

    fun applyRaw() {
        val value = _state.value.rawInput.toIntOrNull() ?: return
        choose(Behavior.fromInt(value))
        applyNow()
    }

    /**
     * Writes the setting, persists the choice so [io.github.quinnjr.sidekey.service.BootReceiver]
     * can restore it, and starts the watcher so an immediate system override is caught.
     */
    fun applyNow() {
        val desired = _state.value.desired
        val result = repo.apply(desired)
        if (result == WriteResult.Ok) {
            store.setDesired(desired.pblp)
            PinService.start(getApplication(), desired.pblp)
        }
        _state.update { it.copy(lastResult = result, observed = repo.observed()) }
    }

    fun requestShizukuPermission() {
        env.requestPermission(SHIZUKU_REQUEST_CODE)
        refresh()
    }

    fun selfGrant() {
        bootstrapper.selfGrant()
        refresh()
    }

    fun setOutcome(outcome: FixOutcome) = _state.update { it.copy(outcome = outcome) }

    fun openReport() = _state.update { it.copy(showReport = true) }

    fun closeReport() = _state.update { it.copy(showReport = false) }

    fun report(): DeviceReport = collector.collect(_state.value.outcome)

    companion object {
        const val SHIZUKU_REQUEST_CODE = 4919
    }
}
