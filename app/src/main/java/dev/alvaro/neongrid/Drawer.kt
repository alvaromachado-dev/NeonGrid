package dev.alvaro.neongrid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Gaveta com todos os apps, busca estilo terminal e grid neon. */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    settings: LauncherSettings,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    val neon = LocalNeon.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.normalized().trim()
        if (q.isEmpty()) apps else apps.filter { it.searchKey.contains(q) }
    }
    val threshold = with(LocalDensity.current) { 60.dp.toPx() }
    val close by rememberUpdatedState(onClose)
    val tile = if (settings.columns == 5) 50.dp else 56.dp

    Column(
        Modifier
            .fillMaxSize()
            .background(neon.bg.copy(alpha = 0.95f))
            .noRippleClickable { keyboard?.hide() } // impede toques de vazarem pra home
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        // Cabeçalho: arrastar pra baixo fecha a gaveta
        Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > threshold) close() },
                        onVerticalDrag = { change, amount ->
                            total += amount
                            change.consume()
                        },
                    )
                }
                .padding(top = 14.dp, bottom = 12.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(44.dp)
                        .height(3.dp)
                        .background(neon.accent.copy(alpha = 0.6f))
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeonText("> APPS", color = neon.accent, size = 16.sp, weight = FontWeight.Bold, spacing = 3.sp, glow = true)
                NeonText("  [${filtered.size}]", color = neon.dim, size = 13.sp)
                Spacer(Modifier.weight(1f))
                NeonText(
                    "[ X ]",
                    color = neon.accent,
                    size = 13.sp,
                    weight = FontWeight.Bold,
                    modifier = Modifier
                        .noRippleClickable(onClose)
                        .padding(6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            SearchField(
                value = query,
                onValueChange = { query = it },
                onGo = {
                    filtered.firstOrNull()?.let {
                        keyboard?.hide()
                        onLaunch(it)
                    }
                },
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(settings.columns),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            items(filtered, key = { it.key }) { app ->
                Box(contentAlignment = Alignment.Center) {
                    AppTile(
                        app = app,
                        neonIcons = settings.neonIcons,
                        showLabel = true,
                        tileSize = tile,
                        onClick = {
                            keyboard?.hide()
                            onLaunch(app)
                        },
                        onLongClick = { onLongPress(app) },
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            NeonText(
                "> nenhum resultado para \"$query\"",
                color = neon.dim,
                size = 12.sp,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, onGo: () -> Unit) {
    val neon = LocalNeon.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = neon.text, fontFamily = Mono, fontSize = 15.sp, letterSpacing = 1.sp),
        cursorBrush = SolidColor(neon.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TileShape)
                    .background(neon.panel)
                    .border(1.dp, neon.accent.copy(alpha = 0.6f), TileShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                NeonText(">", color = neon.accent, size = 15.sp, weight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        NeonText("buscar módulo_", color = neon.dim, size = 15.sp, spacing = 1.sp)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    NeonText(
                        "✕",
                        color = neon.dim,
                        size = 14.sp,
                        modifier = Modifier
                            .noRippleClickable { onValueChange("") }
                            .padding(start = 8.dp),
                    )
                }
            }
        },
    )
}
