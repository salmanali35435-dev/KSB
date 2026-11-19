package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.MainTab
import com.example.ui.components.KsbBrandMark
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.KsbColors
import com.example.ui.theme.KsbTheme
import com.example.viewmodel.KsbUiState
import com.example.viewmodel.KsbViewModel

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val isWideScreen = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
            val vm: KsbViewModel = viewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()

            KsbTheme(uiThemeMode = state.settings.uiTheme) {
                KsbRootApp(
                    state = state,
                    vm = vm,
                    isWideScreen = isWideScreen
                )
            }
        }
    }
}

@Composable
private fun KsbRootApp(
    state: KsbUiState,
    vm: KsbViewModel,
    isWideScreen: Boolean
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.toastMessage) {
        val msg = state.toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vm.clearToast()
        }
    }

    if (state.activeTab != MainTab.EDITOR) {
        BackHandler { vm.selectTab(MainTab.EDITOR) }
    }
    val isDark = when (state.settings.uiTheme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            KsbTopHeader(
                state = state,
                vm = vm,
                isDark = isDark
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (state.activeTab) {
                MainTab.EDITOR -> EditorScreen(
                    questions = state.questions,
                    uiTheme = state.settings.uiTheme,
                    isWideScreen = isWideScreen,
                    onAddRows = { vm.addRows(it) },
                    onUpdateQuestion = { idx, tr -> vm.updateQuestion(idx, tr) },
                    onDeleteQuestion = { vm.deleteQuestion(it) },
                    onDeleteAll = { vm.deleteAllQuestions() },
                    onBulkImport = { vm.bulkImport(it) },
                    onShowToast = { vm.showToast(it) }
                )
                MainTab.STUDIO -> StudioScreen(
                    state = state,
                    vm = vm,
                    isWideScreen = isWideScreen
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KsbTopHeader(
    state: KsbUiState,
    vm: KsbViewModel,
    isDark: Boolean
) {
    val hdrBrush = if (isDark) {
        Brush.linearGradient(listOf(KsbColors.HeaderStartDark, KsbColors.HeaderEndDark))
    } else {
        Brush.linearGradient(listOf(KsbColors.HeaderStartLight, KsbColors.HeaderEndLight))
    }

    Surface(
        shadowElevation = 6.dp,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(hdrBrush)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KsbBrandMark(size = 40.dp, cornerRadius = 11.dp)
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = stringResource(R.string.app_subtitle),
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { vm.toggleUiTheme(isDark) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .testTag("header_theme_button")
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = stringResource(R.string.cd_toggle_theme),
                            tint = Color.White
                        )
                    }
                }
            }

            // Navigation Tabs
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.12f)
            ) {
                FlowRow(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HeaderTabButton(
                        label = stringResource(R.string.tab_editor),
                        icon = Icons.Filled.Edit,
                        selected = state.activeTab == MainTab.EDITOR,
                        testTag = "nav_tab_editor",
                        onClick = { vm.selectTab(MainTab.EDITOR) }
                    )
                    HeaderTabButton(
                        label = stringResource(R.string.tab_studio),
                        icon = Icons.Filled.Movie,
                        selected = state.activeTab == MainTab.STUDIO,
                        testTag = "nav_tab_studio",
                        onClick = { vm.selectTab(MainTab.STUDIO) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderTabButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 42.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) KsbColors.TabYellow else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) KsbColors.TabYellowText else Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = if (selected) KsbColors.TabYellowText else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
