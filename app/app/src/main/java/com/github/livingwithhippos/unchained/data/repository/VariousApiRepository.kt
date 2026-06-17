package com.github.livingwithhippos.unchained.data.repository

import com.github.livingwithhippos.unchained.data.local.ProtoStore
import com.github.livingwithhippos.unchained.data.remote.VariousApi
import javax.inject.Inject

class VariousApiRepository
@Inject
constructor(protoStore: ProtoStore, private val variousApi: VariousApi) :
    BaseRepository(protoStore) {

    suspend fun disableToken(): Unit? {

        val response =
            safeApiCall(
                call = { variousApi.disableToken(token = "Bearer ${getToken()}") },
                errorMessage = "Error disabling token",
            )

        return response
    }
}
