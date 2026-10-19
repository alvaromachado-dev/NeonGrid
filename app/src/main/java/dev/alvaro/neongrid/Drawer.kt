package dev.alvaro.neongrid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Gaveta: fundo opaco, busca de vidro, grid sem molduras e índice A–Z na lateral. */
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
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.normalized().trim()
        if (q.isEmpty()) apps else apps.filter { it.searchKey.contains(q) }
    }
    // letra -> posição do primeiro app com ela
    val index = remember(filtered) {
        val out = mutableListOf<Pair<Char, Int>>()
        filtered.forEachIndexed { i, app ->
            val l = app.indexLetter
            if (out.none { it.first == l }) out += l to i
        }
        out.sortedBy { if (it.first == '#') ' ' else it.first }
    }
    var activeLetter by remember { mutableStateOf<Char?>(null) }

    val threshold = with(LocalDensity.current) { 60.dp.toPx() }
    val close by rememberUpdatedState(onClose)
    val columns = if (settings.columns == 5) 5 else 4
    val icon = if (columns == 5) 46.dp else 52.dp

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C0818), Color(0xFF06050C), Color(0xFF06050C))
                )
            )
            .noRippleClickable { keyboard?.hide() } // bloqueia toques de vazarem pra home
    ) {
        // brilho neon sutil no topo
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(listOf(neon.alt.copy(alpha = 0.10f), Color.Transparent))
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
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
                    .padding(top = 10.dp, bottom = 14.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(ChipShape)
                            .background(Color.White.copy(alpha = 0.25f))
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    NeonText("Apps", color = neon.text, size = 26.sp, family = Sans, weight = FontWeight.SemiBold, spacing = 0.sp)
                    Spacer(Modifier.width(10.dp))
                    NeonText(
                        "${filtered.size}",
                        color = neon.accent,
                        size = 13.sp,
                        family = Mono,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .glass(neon, ChipShape)
                            .noRippleClickable(onClose),
                    ) {
                        Canvas(Modifier.size(11.dp)) {
                            val s = Stroke(width = 2.2f, cap = StrokeCap.Round)
                            drawLine(neon.text, Offset.Zero, Offset(size.width, size.height), strokeWidth = s.width, cap = StrokeCap.Round)
                            drawLine(neon.text, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = s.width, cap = StrokeCap.Round)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
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

            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp, end = if (query.isEmpty()) 20.dp else 0.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filtered, key = { it.key }) { app ->
                        Box(contentAlignment = Alignment.Center) {
                            AppTile(
                                app = app,
                                neonIcons = settings.neonIcons,
                                showLabel = true,
                                iconSize = icon,
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
                        "Nenhum app encontrado pra \"$query\"",
                        color = neon.dim,
                        size = 14.sp,
                        family = Sans,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 40.dp),
                    )
                }

                if (query.isEmpty() && index.size > 1) {
                    LetterIndex(
                        letters = index.map { it.first },
                        active = activeLetter,
                        onPick = { i ->
                            val (letter, position) = index[i]
                            if (letter != activeLetter) {
                                activeLetter = letter
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { gridState.scrollToItem(position) }
                            }
                        },
                        onRelease = { activeLetter = null },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                    )
                }

                activeLetter?.let { letter ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(84.dp)
                            .glass(neon, PanelShape)
                            .border(1.dp, neon.accent.copy(alpha = 0.6f), PanelShape),
                    ) {
                        NeonText(letter.toString(), color = neon.accent, size = 40.sp, family = Sans, weight = FontWeight.Light, glow = true)
                    }
                }
            }
        }
    }
}

/** Coluna de letras: tocar ou arrastar pula pra letra. */
@Composable
private fun LetterIndex(
    letters: List<Char>,
    active: Char?,
    onPick: (Int) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val neon = LocalNeon.current
    val pick by rememberUpdatedState(onPick)
    val release by rememberUpdatedState(onRelease)
    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(22.dp)
            .padding(vertical = 8.dp)
            .pointerInput(letters) {
                awaitEachGesture {
                    fun at(y: Float) = ((y / size.height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
                    val down = awaitFirstDown()
                    down.consume()
                    pick(at(down.position.y))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        pick(at(change.position.y))
                    }
                    release()
                }
            },
    ) {
        letters.forEach { l ->
            NeonText(
                l.toString(),
                color = if (l == active) neon.accent else neon.dim,
                size = 10.sp,
                family = Sans,
                weight = if (l == active) FontWeight.Bold else FontWeight.Medium,
                align = TextAlign.Center,
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
        textStyle = TextStyle(color = neon.text, fontFamily = Sans, fontSize = 16.sp),
        cursorBrush = SolidColor(neon.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(neon, ChipShape)
                    .border(1.dp, if (value.isEmpty()) Color.Transparent else neon.accent.copy(alpha = 0.7f), ChipShape)
                    .padding(horizontal = 18.dp, vertical = 13.dp),
            ) {
                Canvas(Modifier.size(16.dp)) {
                    val r = size.minDimension * 0.36f
                    val c = Offset(r + 1f, r + 1f)
                    drawCircle(neon.accent, radius = r, center = c, style = Stroke(width = 2.2f))
                    drawLine(
                        neon.accent,
                        start = Offset(c.x + r * 0.72f, c.y + r * 0.72f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 2.6f,
                        cap = StrokeCap.Round,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        NeonText("Buscar apps", color = neon.dim, size = 16.sp, family = Sans)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    NeonText(
                        "✕",
                        color = neon.dim,
                        size = 15.sp,
                        family = Sans,
                        modifier = Modifier
                            .noRippleClickable { onValueChange("") }
                            .padding(start = 8.dp),
                    )
                }
            }
        },
    )
}
