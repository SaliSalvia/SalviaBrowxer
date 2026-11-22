package com.example.engine

import android.content.Context
import com.example.data.local.BrowserDatabase
import com.example.data.local.BrowserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Process-wide download runtime.
 *
 * Download execution must not be a child of BrowserViewModel.viewModelScope:
 * Compose/Activity recreation must not cancel transfers. The durable task itself
 * is stored in Room and can be re-attached to WorkManager or JobScheduler after
 * process recreation.
 */
object DownloadRuntime {

    private val lock = Any()

    @Volatile
    private var repositoryInstance: BrowserRepository? = null

    @Volatile
    private var managerInstance: MediaDownloadManager? = null

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun repository(context: Context): BrowserRepository {
        repositoryInstance?.let { return it }
        return synchronized(lock) {
            repositoryInstance ?: BrowserRepository(
                BrowserDatabase.getInstance(context.applicationContext),
            ).also { repositoryInstance = it }
        }
    }

    fun manager(context: Context): MediaDownloadManager {
        managerInstance?.let { return it }
        return synchronized(lock) {
            managerInstance ?: MediaDownloadManager(
                context.applicationContext,
                repository(context.applicationContext),
                applicationScope,
            ).also { managerInstance = it }
        }
    }

    fun launchMaintenance(
        block: suspend CoroutineScope.() -> Unit,
    ): Job = applicationScope.launch(Dispatchers.IO, block = block)
}
