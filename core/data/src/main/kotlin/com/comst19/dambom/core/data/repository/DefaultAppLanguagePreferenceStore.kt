package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.datastore.AppLanguageDataSource
import com.comst19.dambom.core.domain.model.AppLanguagePreference
import com.comst19.dambom.core.domain.repository.AppLanguagePreferenceStore
import javax.inject.Inject

class DefaultAppLanguagePreferenceStore
    @Inject
    constructor(
        private val dataSource: AppLanguageDataSource,
    ) : AppLanguagePreferenceStore {
        override suspend fun read(): AppLanguagePreference {
            val stored = dataSource.read()
            return AppLanguagePreference(
                followSystem = stored.followSystem,
                automaticTag = stored.automaticTag,
            )
        }

        override suspend fun setAutomatic(languageTag: String) = dataSource.setAutomatic(languageTag)

        override suspend fun clearAutomatic() = dataSource.clearAutomatic()
    }
