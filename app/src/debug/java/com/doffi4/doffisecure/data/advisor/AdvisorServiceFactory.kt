package com.doffi4.doffisecure.data.advisor

import android.content.Context
import com.doffi4.doffisecure.domain.advisor.SecurityAdvisorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties

/** Disposable developer key on a test device only. No Gradle/environment/APK key injection. */
object AdvisorServiceFactory {
    fun create(context: Context, canSend: () -> Boolean): SecurityAdvisorService {
        val configFile = File(context.filesDir, "claude-advisor.properties")
        return ClaudeSecurityAdvisorService(configuration = {
            withContext(Dispatchers.IO) {
                if (!configFile.isFile) null else {
                    require(configFile.length() in 1..4096)
                    val properties = Properties().apply { configFile.inputStream().use { load(it) } }
                    val key = properties.getProperty("api_key")?.trim()
                    val model = properties.getProperty("model")?.trim()
                    if (key.isNullOrBlank() || model.isNullOrBlank()) null else ClaudeDeveloperConfig(key, model)
                }
            }
        }, canSend = canSend)
    }
}
