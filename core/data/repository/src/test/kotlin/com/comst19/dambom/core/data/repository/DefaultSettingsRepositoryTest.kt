package com.comst19.dambom.core.data.repository

import app.cash.turbine.test
import com.comst19.dambom.core.datastore.SettingsDataSource
import com.comst19.dambom.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultSettingsRepositoryTest {
    @Test
    fun `settings map DataStore values to domain and persist updates`() =
        runTest {
            val dataSource = FakeSettingsDataSource()
            val repository = DefaultSettingsRepository(dataSource)

            repository.settings.test {
                assertEquals(ThemeMode.SYSTEM, awaitItem().themeMode)

                repository.setThemeMode(ThemeMode.DARK)

                assertEquals(ThemeMode.DARK, awaitItem().themeMode)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private class FakeSettingsDataSource : SettingsDataSource {
    private val themeModeState = MutableStateFlow("SYSTEM")

    override val themeMode: Flow<String> = themeModeState

    override suspend fun setThemeMode(value: String) {
        themeModeState.value = value
    }
}
