package com.github.livingwithhippos.unchained.data.repository

import com.github.livingwithhippos.unchained.data.local.ProtoStore
import com.github.livingwithhippos.unchained.data.model.Stream
import com.github.livingwithhippos.unchained.data.remote.StreamingApi
import javax.inject.Inject

class StreamingRepository
@Inject
constructor(protoStore: ProtoStore, private val streamingApi: StreamingApi) :
    BaseRepository(protoStore) {

    suspend fun getStreams(id: String): Stream? {

        val streamResponse =
            safeApiCall(
                call = { streamingApi.getStreams("Bearer ${getToken()}", id) },
                errorMessage = "Error Fetching Streaming Info",
            )

        return streamResponse
    }
}
