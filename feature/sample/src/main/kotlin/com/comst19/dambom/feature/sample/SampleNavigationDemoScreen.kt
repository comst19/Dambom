package com.comst19.dambom.feature.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.designsystem.DambomTheme

@Composable
internal fun SampleMatchingRoute(viewModel: SampleNavigationDemoViewModel = hiltViewModel()) {
    SampleNavigationDemoScreen(
        title = "Matching root",
        description = "Top-level stack: Matching",
        expectedBack = "Open the detail screen to start the cross-top-level example.",
        actionLabel = "Open Matching Detail",
        onAction = viewModel::openMatchingDetail,
    )
}

@Composable
internal fun SampleMatchingDetailRoute(viewModel: SampleNavigationDemoViewModel = hiltViewModel()) {
    SampleNavigationDemoScreen(
        title = "Matching Detail",
        description = "Matching stack: Matching → Matching Detail",
        expectedBack = "Choose which screen Back should show after Profile Edit.",
        actionLabel = "Edit: Back to Matching Detail",
        onAction = viewModel::openProfileEditReturningToMatching,
        secondaryActionLabel = "Edit: Back to Profile",
        onSecondaryAction = viewModel::openProfileEditThroughProfile,
    )
}

@Composable
internal fun SampleProfileRoute(viewModel: SampleNavigationDemoViewModel = hiltViewModel()) {
    SampleNavigationDemoScreen(
        title = "Profile root",
        description = "Top-level stack: Profile",
        expectedBack = "Back to Profile demo: the next Back returns to the app Home.",
        actionLabel = "Open Profile Edit normally",
        onAction = viewModel::openProfileEdit,
    )
}

@Composable
internal fun SampleProfileEditRoute() {
    SampleNavigationDemoScreen(
        title = "Profile Edit",
        description = "Profile stack: Profile → Profile Edit",
        expectedBack = "Press Back: Profile Edit → Profile → app Home",
    )
}

@Composable
internal fun SampleMatchingProfileEditRoute() {
    SampleNavigationDemoScreen(
        title = "Profile Edit",
        description = "Matching stack: Matching → Matching Detail → Profile Edit",
        expectedBack = "Press Back: Profile Edit → Matching Detail",
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SampleNavigationDemoScreen(
    title: String,
    description: String,
    expectedBack: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    AppScreen(
        topBar = {
            TopAppBar(title = { Text(title) })
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title)
            Text(description)
            Text(expectedBack)
            if (actionLabel != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
            if (secondaryActionLabel != null) {
                Button(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SampleNavigationDemoScreenPreview() {
    DambomTheme {
        SampleNavigationDemoScreen(
            title = "Matching Detail",
            description = "Matching stack: Matching → Matching Detail",
            expectedBack = "Choose the Back destination.",
            actionLabel = "Edit: Back to Matching Detail",
            secondaryActionLabel = "Edit: Back to Profile",
        )
    }
}
