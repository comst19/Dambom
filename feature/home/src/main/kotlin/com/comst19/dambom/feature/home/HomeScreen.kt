package com.comst19.dambom.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.comst19.dambom.core.common.ui.appScaffoldPadding
import com.comst19.dambom.core.designsystem.DambomTheme

@Composable
internal fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    HomeScreen(
        onOpenSettings = viewModel::openSettings,
        onShowSnackbar = viewModel::showSnackbar,
    )
}

@Composable
internal fun HomeScreen(
    onOpenSettings: () -> Unit,
    onShowSnackbar: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                // 일반 화면은 앱 Scaffold가 전달한 전체 패딩을 적용하고 소비합니다.
                .appScaffoldPadding()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_title))
        Text(stringResource(R.string.home_description))
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.home_open_settings)) }
        Button(onClick = onShowSnackbar) { Text(stringResource(R.string.home_show_snackbar)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    DambomTheme {
        HomeScreen(
            onOpenSettings = {},
            onShowSnackbar = {},
        )
    }
}
