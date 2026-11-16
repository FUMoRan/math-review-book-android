package com.fumoran.mathreview.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fumoran.mathreview.AppViewModel
import com.fumoran.mathreview.data.LibType
import com.fumoran.mathreview.ui.components.toast
import com.fumoran.mathreview.ui.theme.MathReviewTheme

sealed interface Screen {
    data object Library : Screen
    data class Detail(val type: LibType, val id: String) : Screen
    data class Editor(val type: LibType, val id: String?) : Screen
    data object Collections : Screen
    data object AiRecognize : Screen
    data class AiOrganize(val type: LibType, val id: String) : Screen
    data object Settings : Screen
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Library) }
    val context = LocalContext.current

    fun push(screen: Screen) {
        backStack.add(screen)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    LaunchedEffect(vm.loadError) {
        vm.loadError?.let { toast(context, it) }
    }

    MathReviewTheme(vm.theme) {
        if (vm.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@MathReviewTheme
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val screen = backStack.last()) {
                Screen.Library -> LibraryScreen(
                    vm = vm,
                    onOpen = { type, id -> push(Screen.Detail(type, id)) },
                    onAdd = { type -> push(Screen.Editor(type, null)) },
                    onCollections = { push(Screen.Collections) },
                    onSettings = { push(Screen.Settings) },
                    onAiRecognize = { push(Screen.AiRecognize) },
                    onOrganize = { type, id -> push(Screen.AiOrganize(type, id)) }
                )

                is Screen.Detail -> DetailScreen(
                    vm = vm,
                    type = screen.type,
                    id = screen.id,
                    onBack = { pop() },
                    onEdit = { push(Screen.Editor(screen.type, screen.id)) },
                    onOrganize = { push(Screen.AiOrganize(screen.type, screen.id)) },
                    onOpenKnowledge = { kid -> push(Screen.Detail(LibType.K, kid)) }
                )

                is Screen.Editor -> EditorScreen(
                    vm = vm,
                    type = screen.type,
                    id = screen.id,
                    onBack = { pop() },
                    onSaved = { pop() }
                )

                Screen.Collections -> CollectionsScreen(vm = vm, onBack = { pop() })

                Screen.AiRecognize -> AiRecognizeScreen(
                    vm = vm,
                    onBack = { pop() },
                    onDone = { pop() }
                )

                is Screen.AiOrganize -> AiOrganizeScreen(
                    vm = vm,
                    type = screen.type,
                    id = screen.id,
                    onBack = { pop() }
                )

                Screen.Settings -> SettingsScreen(vm = vm, onBack = { pop() })
            }
        }

        BackHandler(enabled = backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}
