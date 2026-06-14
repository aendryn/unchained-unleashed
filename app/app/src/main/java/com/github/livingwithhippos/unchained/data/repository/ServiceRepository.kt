package com.github.livingwithhippos.unchained.data.repository

import com.github.livingwithhippos.unchained.data.local.CompleteRemoteService
import com.github.livingwithhippos.unchained.data.local.CompleteRemoteServiceDao
import com.github.livingwithhippos.unchained.data.local.RemoteDevice
import com.github.livingwithhippos.unchained.data.local.SecretCipher
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ServiceRepository
@Inject
constructor(
    private val serviceDao: CompleteRemoteServiceDao,
    private val secretCipher: SecretCipher,
) {

    suspend fun insertService(service: CompleteRemoteService): Long =
        serviceDao.insertService(service.withEncryptedSecrets())

    suspend fun upsertService(service: CompleteRemoteService): Long =
        serviceDao.upsertService(service.withEncryptedSecrets())

    suspend fun deleteService(service: CompleteRemoteService) = serviceDao.deleteService(service)

    suspend fun deleteService(serviceID: Int) = serviceDao.deleteService(serviceID)

    suspend fun insertAllServices(list: List<CompleteRemoteService>): List<Long> =
        serviceDao.insertAllServices(list.map { it.withEncryptedSecrets() })

    suspend fun getServices(): List<CompleteRemoteService> =
        serviceDao.getServices().map { it.withDecryptedSecrets() }

    suspend fun getServicesTypes(types: List<Int>): List<CompleteRemoteService> =
        serviceDao.getServicesTypes(types).map { it.withDecryptedSecrets() }

    fun getServicesTypesFlow(types: List<Int>): Flow<List<CompleteRemoteService>> =
        serviceDao.getServicesTypesFlow(types).map { list ->
            list.map { it.withDecryptedSecrets() }
        }

    suspend fun getServiceIDByRow(rowId: Long): Int? = serviceDao.getServiceIDByRow(rowId)

    suspend fun getService(serviceID: Int): CompleteRemoteService? =
        serviceDao.getService(serviceID)?.withDecryptedSecrets()

    suspend fun deleteAll() = serviceDao.deleteAll()

    suspend fun removeService(id: Int) = serviceDao.removeService(id)

    suspend fun getDefaultService(): RemoteDevice? = serviceDao.getDefaultService()

    suspend fun setDefaultService(id: Int) = serviceDao.setDefaultService(id)

    suspend fun setDefault(name: String) = serviceDao.setDefault(name)

    suspend fun enableService(id: Int, enabled: Boolean) = serviceDao.enableService(id, enabled)

    suspend fun getEnabledServicesTypes(types: List<Int>): List<CompleteRemoteService> =
        withContext(Dispatchers.IO) {
            return@withContext serviceDao.getEnabledServicesTypes(types).map {
                it.withDecryptedSecrets()
            }
        }

    /**
     * The password and API token are credentials, so they are encrypted before they reach the
     * database (see [SecretCipher]) and decrypted on the way out. [CompleteRemoteService] isn't a
     * data class, so we rebuild it instead of using copy().
     */
    private fun CompleteRemoteService.withEncryptedSecrets(): CompleteRemoteService =
        copyWithSecrets(secretCipher.encrypt(password), secretCipher.encrypt(apiToken).orEmpty())

    private fun CompleteRemoteService.withDecryptedSecrets(): CompleteRemoteService =
        copyWithSecrets(secretCipher.decrypt(password), secretCipher.decrypt(apiToken).orEmpty())

    private fun CompleteRemoteService.copyWithSecrets(
        password: String?,
        apiToken: String,
    ): CompleteRemoteService =
        CompleteRemoteService(
            id = id,
            name = name,
            address = address,
            username = username,
            password = password,
            type = type,
            isDefault = isDefault,
            apiToken = apiToken,
            enabled = enabled,
            fieldOne = fieldOne,
            fieldTwo = fieldTwo,
            fieldThree = fieldThree,
        )
}
