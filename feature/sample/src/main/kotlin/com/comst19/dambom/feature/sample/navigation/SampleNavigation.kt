package com.comst19.dambom.feature.sample.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.SampleDetailKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.SampleMviKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.SampleMvvmKey
import com.comst19.dambom.core.navigation.contract.SampleMatchingGraph.SampleMatchingDetailKey
import com.comst19.dambom.core.navigation.contract.SampleMatchingGraph.SampleMatchingKey
import com.comst19.dambom.core.navigation.contract.SampleMatchingGraph.SampleMatchingProfileEditKey
import com.comst19.dambom.core.navigation.contract.SampleProfileGraph.SampleProfileEditKey
import com.comst19.dambom.core.navigation.contract.SampleProfileGraph.SampleProfileKey
import com.comst19.dambom.feature.sample.SampleDetailRoute
import com.comst19.dambom.feature.sample.SampleDetailViewModel
import com.comst19.dambom.feature.sample.SampleMatchingDetailRoute
import com.comst19.dambom.feature.sample.SampleMatchingProfileEditRoute
import com.comst19.dambom.feature.sample.SampleMatchingRoute
import com.comst19.dambom.feature.sample.SampleMviRoute
import com.comst19.dambom.feature.sample.SampleMvvmRoute
import com.comst19.dambom.feature.sample.SampleProfileEditRoute
import com.comst19.dambom.feature.sample.SampleProfileRoute

/** Sample feature의 일반 화면과 두 가지 Profile Edit Back 문맥을 entry provider에 등록합니다. */
fun EntryProviderScope<NavKey>.sampleEntries() {
    entry<SampleMvvmKey> { SampleMvvmRoute() }
    entry<SampleMviKey> { SampleMviRoute() }
    entry<SampleMatchingKey> { SampleMatchingRoute() }
    entry<SampleMatchingDetailKey> { SampleMatchingDetailRoute() }
    entry<SampleMatchingProfileEditKey> { SampleMatchingProfileEditRoute() }
    entry<SampleProfileKey> { SampleProfileRoute() }
    entry<SampleProfileEditKey> { SampleProfileEditRoute() }
    entry<SampleDetailKey> { key ->
        SampleDetailRoute(
            viewModel =
                hiltViewModel<SampleDetailViewModel, SampleDetailViewModel.Factory>(
                    creationCallback = { factory -> factory.create(key) },
                ),
        )
    }
}
