package com.comst19.dambom.feature.auth

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
internal fun LoginRoute(viewModel: LoginViewModel = hiltViewModel()) {
    LoginScreen(onLogin = viewModel::login)
}

@Composable
internal fun LoginScreen(onLogin: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .appScaffoldPadding()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.login_title))
        Button(onClick = onLogin) { Text(stringResource(R.string.login_continue)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    DambomTheme {
        LoginScreen(onLogin = {})
    }
}
