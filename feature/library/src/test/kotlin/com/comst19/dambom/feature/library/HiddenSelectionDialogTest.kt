package com.comst19.dambom.feature.library

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.feature.library.component.DeleteSelectedVideosDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HiddenSelectionDialogTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `confirmation includes selected and hidden item counts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.setContent {
            MaterialTheme {
                DeleteSelectedVideosDialog(count = 5, hiddenCount = 3, onDismiss = {}, onConfirm = {})
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.library_delete_selected_hidden_description, 5, 3))
            .assertIsDisplayed()
    }
}
