package app.jonaki

import android.app.Application
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.run.AgentRunner
import app.jonaki.settings.AppSettings
import app.jonaki.settings.SecretStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Builds the app's long-lived parts once; screens and the service read them from here. */
class JonakiApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: JonakiDatabase
        private set
    lateinit var settings: AppSettings
        private set
    lateinit var secrets: SecretStore
        private set
    lateinit var runner: AgentRunner
        private set

    override fun onCreate() {
        super.onCreate()
        database = JonakiDatabase.open(this)
        settings = AppSettings(this)
        secrets = SecretStore(this)
        // One client for every call, so connections and threads are shared.
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // Streaming replies can pause while a model thinks; the agent loop's own limits apply on top.
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        runner = AgentRunner(this, database, settings, secrets, httpClient, applicationScope)
        applicationScope.launch {
            // A run cannot survive a killed process; mark what it left half-done.
            database.messageDao().closeInterrupted()
            database.stepDao().stopInterrupted()
        }
    }
}
