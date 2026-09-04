package com.doffi4.doffisecure.ui.password

import com.doffi4.doffisecure.domain.model.Password

sealed interface PasswordUiState {
    object Loading : PasswordUiState
    data class Success(val passwords: List<Password>) : PasswordUiState
    data class Error(val message: String) : PasswordUiState
}

sealed interface PasswordUiEvent {
    data class ShowToast(val message: String) : PasswordUiEvent
}
