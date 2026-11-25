package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.datastore.AppLanguageDataSource
import com.comst19.dambom.core.datastore.StoredAppLanguage
import com.comst19.dambom.core.domain.model.AppLanguagePreference
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultAppLanguagePreferenceStoreTest {
    @Test
    fun `delegates automatic and manual state updates to data source`() =
        runTest {
            val dataSource = FakeAppLanguageDataSource()
            val store = DefaultAppLanguagePreferenceStore(dataSource)

            assertEquals(AppLanguagePreference(), store.read())

            store.setAutomatic("hi")
            assertEquals(AppLanguagePreference(followSystem = true, automaticTag = "hi"), store.read())

            store.clearAutomatic()
            assertEquals(AppLanguagePreference(followSystem = false, automaticTag = null), store.read())
        }
}

private class FakeAppLanguageDataSource : AppLanguageDataSource {
    private var state = StoredAppLanguage()

    override suspend fun read(): StoredAppLanguage = state

    override suspend fun setAutomatic(languageTag: String) {
        state = StoredAppLanguage(followSystem = true, automaticTag = languageTag)
    }

    override suspend fun clearAutomatic() {
        state = StoredAppLanguage(followSystem = false, automaticTag = null)
    }
}
