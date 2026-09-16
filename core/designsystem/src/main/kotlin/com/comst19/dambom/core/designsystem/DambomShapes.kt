package com.comst19.dambom.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object DambomShapes {
    val Media = RoundedCornerShape(12.dp)
    val Control = RoundedCornerShape(16.dp)
    val Card = RoundedCornerShape(20.dp)
    val Summary = RoundedCornerShape(20.dp)
    val Theme = Shapes(small = Media, medium = Control, large = Card)
}
