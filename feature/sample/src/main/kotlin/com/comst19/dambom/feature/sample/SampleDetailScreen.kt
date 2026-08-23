package com.comst19.dambom.feature.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.designsystem.sampleDetailBackground
import com.comst19.dambom.feature.sample.contract.SampleDetailState

@Composable
internal fun SampleDetailRoute(viewModel: SampleDetailViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SampleDetailScreen(state)
}

@Composable
private fun SampleDetailScreen(state: SampleDetailState) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // 전체 화면 배경은 시스템 바 뒤까지 그립니다.
                .background(MaterialTheme.colorScheme.sampleDetailBackground),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    // 버튼과 텍스트 같은 조작 영역만 안전 영역 안에 배치합니다.
                    .safeDrawingPadding()
                    .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Full-screen sample")
            Text("Background draws behind the system bars.")
            Text(state.title ?: "Sample ${state.id}")
            Text(state.description ?: "Not found")
        }
    }
}
