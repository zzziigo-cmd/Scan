package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyberCyan

@Composable
fun Cyber3DButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    bottomBevelColor: Color = primaryColor.copy(alpha = 0.5f),
    contentColor: Color = Color.White,
    icon: (@Composable () -> Unit)? = null,
    testTag: String = "cyber_3d_button"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pushOffset: Dp by animateDpAsState(
        targetValue = if (isPressed) 4.dp else 0.dp,
        animationSpec = spring(stiffness = 500f),
        label = "button3d_press"
    )

    Box(
        modifier = modifier
            .testTag(testTag)
            .height(56.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Bottom 3D shadow / bevel base
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (enabled) bottomBevelColor else Color.Gray.copy(alpha = 0.3f))
        )

        // Top face with gradient & elevation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .graphicsLayer {
                    translationY = pushOffset.toPx() - 4.dp.toPx()
                }
                .shadow(
                    elevation = if (isPressed) 2.dp else 8.dp,
                    shape = RoundedCornerShape(14.dp),
                    spotColor = primaryColor.copy(alpha = 0.6f)
                )
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (enabled) {
                        Brush.verticalGradient(
                            listOf(
                                primaryColor,
                                primaryColor.copy(red = (primaryColor.red * 0.85f).coerceIn(0f, 1f))
                            )
                        )
                    } else {
                        Brush.verticalGradient(listOf(Color.DarkGray, Color.Gray))
                    }
                )
                .border(
                    width = 1.dp,
                    color = if (enabled) CyberCyan.copy(alpha = 0.6f) else Color.Transparent,
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                if (icon != null) {
                    icon()
                }
                Text(
                    text = text,
                    color = contentColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
