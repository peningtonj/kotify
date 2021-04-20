package com.dzirbel.kotify

import androidx.compose.runtime.mutableStateOf
import com.dzirbel.kotify.Settings.SettingsData
import com.dzirbel.kotify.ui.theme.Colors
import com.dzirbel.kotify.ui.util.assertNotOnUIThread
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.FileReader
import java.nio.file.Files
import java.util.concurrent.Executors

/**
 * Saves global settings as [SettingsData] objects in JSON.
 *
 * TODO handle deserialization failures properly, especially for changes in the SettingsData class between runs
 */
object Settings {
    @Serializable
    data class SettingsData(
        val colors: Colors = Colors.DARK
    )

    private val state by lazy { mutableStateOf(load() ?: SettingsData()) }

    private val ioCoroutineContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val settingsFile by lazy { Application.settingsDir.resolve("settings.json") }
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * The current state of [SettingsData], backed by a [androidx.compose.runtime.MutableState].
     */
    val current: SettingsData
        get() = state.value

    /**
     * Changes the current [SettingsData] as applied by [block], and saves the resulting value to disk in the
     * background.
     */
    fun mutate(block: SettingsData.() -> SettingsData) {
        val original = state.value
        val new = original.block()
        if (new != original) {
            state.value = new
            save(data = new)
        }
    }

    private fun load(): SettingsData? {
        assertNotOnUIThread()
        return try {
            settingsFile
                .takeIf { it.isFile }
                ?.let { FileReader(it) }
                ?.use { it.readLines().joinToString(separator = " ") }
                ?.let { json.decodeFromString<SettingsData>(it) }
                ?.also { println("Loaded settings from ${settingsFile.absolutePath}") }
        } catch (ex: Throwable) {
            System.err.println("Error loading settings from ${settingsFile.absolutePath}; reverting to defaults")
            ex.printStackTrace()
            null
        }
    }

    private fun save(data: SettingsData) {
        GlobalScope.launch(context = ioCoroutineContext) {
            assertNotOnUIThread()

            try {
                val content = json.encodeToString(data)
                Files.writeString(settingsFile.toPath(), content)
                println("Saved settings to ${settingsFile.absolutePath}")
            } catch (ex: Throwable) {
                System.err.println("Error saving settings to ${settingsFile.absolutePath}")
                ex.printStackTrace()
            }
        }
    }
}
