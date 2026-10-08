package com.doffi4.doffisecure.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.security.LocalSecurityAnalyzer
import com.doffi4.doffisecure.domain.security.SecuritySummary
import com.doffi4.doffisecure.domain.advisor.AdvisorSanitizer
import com.doffi4.doffisecure.domain.advisor.DisabledSecurityAdvisorService
import com.doffi4.doffisecure.domain.advisor.SecurityAdvisorService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SecurityCenterState {
    data object Inactive : SecurityCenterState
    data object Loading : SecurityCenterState
    data object Locked : SecurityCenterState
    data object Error : SecurityCenterState
    data class Ready(val summary: SecuritySummary) : SecurityCenterState
}

/** Screen-scoped analysis; never holds a source list or a plaintext password field. */
@OptIn(ExperimentalCoroutinesApi::class)
class SecurityCenterViewModel(
    private val passwords: () -> Flow<List<Password>>,
    private val locked: StateFlow<Boolean>,
    private val canRead: () -> Boolean,
    private val analyzer: LocalSecurityAnalyzer = LocalSecurityAnalyzer(),
    private val analysisDispatcher: CoroutineDispatcher = Dispatchers.Default,
    advisorService: SecurityAdvisorService = DisabledSecurityAdvisorService(),
) : ViewModel() {
    private val _state = MutableStateFlow<SecurityCenterState>(SecurityCenterState.Inactive)
    val state: StateFlow<SecurityCenterState> = _state.asStateFlow()
    private var collectionJob: Job? = null
    val advisor = SecurityAdvisorController(advisorService, viewModelScope) {
        _state.value is SecurityCenterState.Ready && !locked.value && canRead()
    }

    private fun publish(value: SecurityCenterState) {
        // Every local emission invalidates remote consent/results, even if counts match.
        advisor.setSummary((value as? SecurityCenterState.Ready)?.summary?.let(AdvisorSanitizer::sanitize))
        _state.value = value
    }

    fun start() {
        if (collectionJob?.isActive == true) return
        collectionJob = viewModelScope.launch {
            locked.collectLatest { isLocked ->
                if (isLocked || !canRead()) {
                    publish(SecurityCenterState.Locked)
                } else {
                    publish(SecurityCenterState.Loading)
                    try {
                        passwords().mapLatest { source ->
                            publish(SecurityCenterState.Loading)
                            withContext(analysisDispatcher) {
                                analyzer.analyze(source) { coroutineContext.ensureActive() }
                            }
                        }.collect { summary ->
                            publish(if (locked.value || !canRead()) SecurityCenterState.Locked
                            else SecurityCenterState.Ready(summary))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Do not expose exception text (which can contain vault input).
                        publish(if (locked.value || !canRead()) SecurityCenterState.Locked
                        else SecurityCenterState.Error)
                    }
                }
            }
        }
    }

    fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        publish(SecurityCenterState.Inactive)
    }

    fun retry() { stop(); start() }
    override fun onCleared() { stop() }
}
