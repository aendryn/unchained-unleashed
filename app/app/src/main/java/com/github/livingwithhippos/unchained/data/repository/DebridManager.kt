package com.github.livingwithhippos.unchained.data.repository

import com.github.livingwithhippos.unchained.data.local.ProtoStore
import com.github.livingwithhippos.unchained.data.model.DebridService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "which debrid services is the user signed in to".
 *
 * RealDebrid auth lives in the protobuf credential store ([ProtoStore]); TorBox auth is just an API
 * key in [TorBoxKeyRepository]. Centralizing the check here keeps the rest of the app (startup
 * gating, the per-download service selector, the merged torrents list) from having to know where
 * each credential is kept.
 */
@Singleton
class DebridManager
@Inject
constructor(
    private val protoStore: ProtoStore,
    private val torBoxKeyRepository: TorBoxKeyRepository,
) {

    /** True if RealDebrid has a usable (non-blank) access token saved. */
    suspend fun isRealDebridAuthenticated(): Boolean =
        protoStore.getCredentials().accessToken.isNotBlank()

    /** True if a TorBox API key has been saved. The key is validated separately on entry. */
    fun isTorBoxAuthenticated(): Boolean = torBoxKeyRepository.isAuthenticated()

    /** The set of services the user can currently use. May be empty (fresh install). */
    suspend fun authenticatedServices(): Set<DebridService> = buildSet {
        if (isRealDebridAuthenticated()) add(DebridService.REAL_DEBRID)
        if (isTorBoxAuthenticated()) add(DebridService.TORBOX)
    }

    /** True if at least one service is usable, i.e. the app may show its main content. */
    suspend fun isAnyServiceAuthenticated(): Boolean = authenticatedServices().isNotEmpty()
}
