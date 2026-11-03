package com.hmx.arena.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmx.arena.core.util.Validation
import com.hmx.arena.data.repository.AuthRepository
import com.hmx.arena.ui.common.hmxMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUi(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(LoginUi())
    val ui: StateFlow<LoginUi> = _ui.asStateFlow()

    fun setEmail(value: String) = _ui.update { it.copy(email = value, emailError = null, error = null) }
    fun setPassword(value: String) = _ui.update { it.copy(password = value, passwordError = null, error = null) }

    fun submit() {
        val current = _ui.value
        val emailError = if (!Validation.isEmailValid(current.email)) "Enter a valid email address." else null
        val passwordError = if (current.password.isBlank()) "Enter your password." else null
        if (emailError != null || passwordError != null) {
            _ui.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { auth.signIn(current.email, current.password) }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }
}

data class SignUpUi(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val confirm: String = "",
    val accepted: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val usernameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmError: String? = null,
    val verifyEmail: String? = null,
)

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(SignUpUi())
    val ui: StateFlow<SignUpUi> = _ui.asStateFlow()

    fun setUsername(value: String) = _ui.update { it.copy(username = value, usernameError = null, error = null) }
    fun setEmail(value: String) = _ui.update { it.copy(email = value, emailError = null, error = null) }
    fun setPassword(value: String) = _ui.update { it.copy(password = value, passwordError = null, error = null) }
    fun setConfirm(value: String) = _ui.update { it.copy(confirm = value, confirmError = null, error = null) }
    fun setAccepted(value: Boolean) = _ui.update { it.copy(accepted = value, error = null) }

    fun submit() {
        val current = _ui.value
        val usernameError = if (!Validation.isUsernameValid(current.username)) {
            "3-24 letters, numbers or underscores."
        } else null
        val emailError = if (!Validation.isEmailValid(current.email)) "Enter a valid email address." else null
        val passwordError = if (!Validation.isPasswordValid(current.password)) {
            "Use at least 8 characters."
        } else null
        val confirmError = if (current.confirm != current.password) "Passwords do not match." else null
        if (usernameError != null || emailError != null || passwordError != null || confirmError != null) {
            _ui.update {
                it.copy(
                    usernameError = usernameError,
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmError = confirmError,
                )
            }
            return
        }
        if (!current.accepted) {
            _ui.update { it.copy(error = "Accept the Terms and Fair Play policy to continue.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { auth.signUp(current.username, current.email, current.password) }
                .onSuccess { result ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            verifyEmail = if (result.needsVerification) result.email else null,
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun consumeVerifyEmail() = _ui.update { it.copy(verifyEmail = null) }
}

data class VerifyUi(
    val email: String,
    val token: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val tokenError: String? = null,
    val resendSeconds: Int = 0,
    val message: String? = null,
)

@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(VerifyUi(email = ""))
    val ui: StateFlow<VerifyUi> = _ui.asStateFlow()

    fun start(email: String) {
        if (_ui.value.email == email) return
        _ui.value = VerifyUi(email = email)
    }

    fun setToken(value: String) = _ui.update {
        it.copy(token = value.filter(Char::isDigit).take(6), tokenError = null, error = null)
    }

    fun submit() {
        val current = _ui.value
        if (!Validation.isOtpValid(current.token)) {
            _ui.update { it.copy(tokenError = "Enter the 6-digit code from your email.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { auth.verifyEmail(current.email, current.token) }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun resend() {
        val current = _ui.value
        if (current.email.isBlank() || current.resendSeconds > 0) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, message = null) }
            runCatching { auth.resendVerification(current.email) }
                .onSuccess {
                    _ui.update { it.copy(loading = false, message = "A new code is on its way.", resendSeconds = 60) }
                    countdown()
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    private fun countdown() {
        viewModelScope.launch {
            while (_ui.value.resendSeconds > 0) {
                delay(1000)
                _ui.update { it.copy(resendSeconds = (it.resendSeconds - 1).coerceAtLeast(0)) }
            }
        }
    }
}

data class ForgotUi(
    val email: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val emailError: String? = null,
    val sent: Boolean = false,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(ForgotUi())
    val ui: StateFlow<ForgotUi> = _ui.asStateFlow()

    fun setEmail(value: String) = _ui.update { it.copy(email = value, emailError = null, error = null) }

    fun submit() {
        val current = _ui.value
        if (!Validation.isEmailValid(current.email)) {
            _ui.update { it.copy(emailError = "Enter a valid email address.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { auth.requestPasswordReset(current.email) }
                .onSuccess { _ui.update { it.copy(loading = false, sent = true) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }
}

data class RecoveryUi(
    val preparing: Boolean = true,
    val ready: Boolean = false,
    val password: String = "",
    val confirm: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val passwordError: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class RecoveryResetViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(RecoveryUi())
    val ui: StateFlow<RecoveryUi> = _ui.asStateFlow()

    fun prepare(tokenHash: String?) {
        if (tokenHash.isNullOrBlank()) {
            _ui.update { it.copy(preparing = false, ready = true) }
            return
        }
        viewModelScope.launch {
            runCatching { auth.completeRecovery(tokenHash) }
                .onSuccess { _ui.update { it.copy(preparing = false, ready = true) } }
                .onFailure { e ->
                    _ui.update {
                        it.copy(
                            preparing = false,
                            ready = false,
                            error = "${e.hmxMessage()} Request a new reset link.",
                        )
                    }
                }
        }
    }

    fun setPassword(value: String) = _ui.update {
        it.copy(password = value, passwordError = null, error = null)
    }

    fun setConfirm(value: String) = _ui.update { it.copy(confirm = value, error = null) }

    fun submit() {
        val current = _ui.value
        if (!Validation.isPasswordValid(current.password)) {
            _ui.update { it.copy(passwordError = "Use at least 8 characters.") }
            return
        }
        if (current.confirm != current.password) {
            _ui.update { it.copy(passwordError = "Passwords do not match.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { auth.updatePassword(current.password) }
                .onSuccess {
                    auth.signOut()
                    _ui.update { it.copy(loading = false, success = true) }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }
}
