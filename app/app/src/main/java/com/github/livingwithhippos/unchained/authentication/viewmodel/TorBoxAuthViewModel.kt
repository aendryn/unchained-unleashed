package com.github.livingwithhippos.unchained.authentication.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.livingwithhippos.unchained.data.remote.TorBoxApiHelper
import com.github.livingwithhippos.unchained.data.repository.TorBoxKeyRepository
import com.github.livingwithhippos.unchained.utilities.Event
import com.github.livingwithhippos.unchained.utilities.postEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Handles connecting/disconnecting a TorBox account. TorBox uses a single permanent API key, so
 * "logging in" is: validate the key against `GET /user/me`, and if it's accepted, persist it via
 * [TorBoxKeyRepository].
 */
@HiltViewModel
class TorBoxAuthViewModel
@Inject
constructor(
    private val torBoxApiHelper: TorBoxApiHelper,
    private val keyRepository: TorBoxKeyRepository,
) : ViewModel() {

    val authResult = MutableLiveData<Event<TorBoxAuthResult>>()

    fun isAuthenticated(): Boolean = keyRepository.isAuthenticated()

    fun getMaskedKey(): String? =
        keyRepository.getApiKey()?.let { key ->
            if (key.length <= 8) "•".repeat(key.length) else "${key.take(4)}…${key.takeLast(4)}"
        }

    /** Validate [rawKey] against the API and, on success, save it. */
    fun verifyAndSave(rawKey: String) {
        val key = rawKey.trim()
        if (key.isBlank()) {
            authResult.postEvent(TorBoxAuthResult.EmptyKey)
            return
        }
        viewModelScope.launch {
            val result =
                try {
                    withContext(Dispatchers.IO) { torBoxApiHelper.getUser("Bearer $key") }
                } catch (e: Exception) {
                    Timber.e(e, "TorBox key verification network error")
                    null
                }

            when {
                result == null -> authResult.postEvent(TorBoxAuthResult.NetworkError)
                result.code() == 401 || result.code() == 403 ->
                    authResult.postEvent(TorBoxAuthResult.InvalidKey)
                result.isSuccessful && result.body()?.success == true -> {
                    keyRepository.setApiKey(key)
                    val email = result.body()?.data?.email
                    authResult.postEvent(TorBoxAuthResult.Authenticated(email))
                }
                else -> {
                    val detail = result.body()?.detail ?: result.body()?.error
                    authResult.postEvent(TorBoxAuthResult.Failure(detail))
                }
            }
        }
    }

    fun disconnect() {
        keyRepository.clear()
        authResult.postEvent(TorBoxAuthResult.Disconnected)
    }
}

sealed class TorBoxAuthResult {
    /** Key accepted and saved. [email] is shown as confirmation if the API returned it. */
    data class Authenticated(val email: String?) : TorBoxAuthResult()

    data object EmptyKey : TorBoxAuthResult()

    data object InvalidKey : TorBoxAuthResult()

    data object NetworkError : TorBoxAuthResult()

    data class Failure(val detail: String?) : TorBoxAuthResult()

    data object Disconnected : TorBoxAuthResult()
}
