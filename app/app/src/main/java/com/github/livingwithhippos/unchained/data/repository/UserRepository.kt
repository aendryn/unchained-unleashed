package com.github.livingwithhippos.unchained.data.repository

import com.github.livingwithhippos.unchained.data.local.ProtoStore
import com.github.livingwithhippos.unchained.data.model.UnchainedNetworkException
import com.github.livingwithhippos.unchained.data.model.User
import com.github.livingwithhippos.unchained.data.remote.UserApi
import com.github.livingwithhippos.unchained.utilities.EitherResult
import javax.inject.Inject

class UserRepository @Inject constructor(protoStore: ProtoStore, private val userApi: UserApi) :
    BaseRepository(protoStore) {

    suspend fun getUserInfo(token: String): User? {

        val userResponse =
            safeApiCall(
                call = { userApi.getUser("Bearer $token") },
                errorMessage = "Error Fetching User Info",
            )

        return userResponse
    }

    suspend fun getUserOrError(token: String): EitherResult<UnchainedNetworkException, User> {

        val userResponse =
            eitherApiResult(
                call = { userApi.getUser("Bearer $token") },
                errorMessage = "Error Fetching User Info",
            )

        return userResponse
    }
}
