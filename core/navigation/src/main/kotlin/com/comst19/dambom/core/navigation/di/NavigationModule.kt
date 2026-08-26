package com.comst19.dambom.core.navigation.di

import com.comst19.dambom.core.navigation.ChannelNavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationDispatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Navigation dispatcher 계약과 Channel 구현을 연결하는 앱 전역 Hilt module입니다. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationModule {
    /** feature에는 [NavigationDispatcher] 계약만 노출하고 앱 전체에서 하나의 Channel 구현을 공유합니다. */
    @Binds
    @Singleton
    abstract fun bindNavigationDispatcher(implementation: ChannelNavigationDispatcher): NavigationDispatcher
}
