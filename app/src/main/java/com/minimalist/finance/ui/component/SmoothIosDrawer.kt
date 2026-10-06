package com.minimalist.finance.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minimalist.finance.ui.theme.DarkBackground
import com.minimalist.finance.ui.theme.DarkSurface
import com.minimalist.finance.ui.theme.LightSurface

/**
 * 极简且具 iOS 般丝滑体验的抽屉菜单容器 (Smooth iOS-Style Drawer)
 * 使用硬件加速的 RenderNode 平移动画与 iOS 专属贝塞尔减速曲线，
 * 彻底消除 Android 默认抽屉的生硬阻尼感与卡顿，带来如同黄油般柔顺的滑动质感。
 */
@Composable
fun SmoothIosDrawer(
    isOpen: Boolean,
    onClose: () -> Unit,
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    drawerWidth: Dp = 300.dp,
    onOpen: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val density = LocalDensity.current
    val drawerWidthPx = with(density) { drawerWidth.toPx() }

    // iOS 专属高阶减速曲线：启动敏捷、滑行轻盈、减速柔和贴合，无任何突兀阻尼
    val drawerProgress by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isOpen) 320 else 260,
            easing = if (isOpen) CubicBezierEasing(0.16f, 1f, 0.3f, 1f) else CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
        ),
        label = "iosDrawerAnimation"
    )

    // 系统返回键拦截：抽屉展开时优先平滑收起抽屉
    BackHandler(enabled = isOpen) {
        onClose()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 主内容界面 (支持屏幕左侧轻微边缘滑动唤出抽屉)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isOpen) {
                    if (!isOpen && onOpen != null) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            if (change.position.x < 70f && dragAmount > 16f) {
                                change.consume()
                                onOpen()
                            }
                        }
                    }
                }
        ) {
            content()
        }

        // 仅在抽屉有可见度或开启时渲染遮罩与菜单层，关闭后 0 额外渲染开销
        if (drawerProgress > 0.001f || isOpen) {
            // 柔和暗色背景遮罩 (随着进度淡入淡出，点击空白处平滑关闭)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = drawerProgress * 0.45f
                    }
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose
                    )
            )

            // 抽屉卡片面板 (GPU 硬件加速位移 + 圆润右侧边角 + 细腻景深投影)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .graphicsLayer {
                        translationX = -(1f - drawerProgress) * drawerWidthPx
                    }
                    .shadow(
                        elevation = (drawerProgress * 16).dp,
                        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                        clip = false
                    )
                    .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                    .background(if (isDark) DarkSurface else LightSurface)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            // 用户向左轻拂滑动手势平滑收起
                            if (dragAmount < -12f) {
                                change.consume()
                                onClose()
                            }
                        }
                    }
            ) {
                drawerContent()
            }
        }
    }
}
