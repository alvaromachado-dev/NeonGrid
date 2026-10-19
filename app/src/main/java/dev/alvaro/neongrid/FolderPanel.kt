package dev.alvaro.neongrid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pasta aberta: painel de vidro no centro, nome editável e os apps em grade. */
@Composable
fun FolderPanel(
    folder: Folder,
    apps: List<AppEntry>,
    iconStyle: IconStyle,
    startRenaming: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onRename: (String) -> Unit,
    onClose: () -> Unit,
) {
    val neon = LocalNeon.current
    val keyboard = LocalSoftwareKeyboardController.current
    var editing by remember(folder.id) { mutableStateOf(startRenaming) }
    var name by remember(folder.id) {
        mutableStateOf(TextFieldValue(folder.name, TextRange(0, folder.name.length)))
    }
    val focus = remember { FocusRequester() }

    fun commit() {
        if (editing) {
            onRename(name.text)
            editing = false
            keyboard?.hide()
        }
    }

    LaunchedEffect(editing) {
        if (editing) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .noRippleClickable {
                commit()
                onClose()
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .glass(neon, PanelShape)
                .background(Color(0xCC0E0A1C))
                .border(1.dp, neon.accent.copy(alpha = 0.35f), PanelShape)
                .noRippleClickable { commit() } // tocar dentro não fecha
                .padding(vertical = 18.dp, horizontal = 10.dp),
        ) {
            if (editing) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(color = neon.text, fontFamily = Sans, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(neon.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .focusRequester(focus),
                )
                Spacer(Modifier.height(2.dp))
                NeonDivider(Modifier.padding(horizontal = 40.dp))
            } else {
                NeonText(
                    folder.name,
                    color = neon.text,
                    size = 20.sp,
                    family = Sans,
                    weight = FontWeight.SemiBold,
                    maxLines = 1,
                    align = TextAlign.Center,
                    modifier = Modifier.noRippleClickable { editing = true },
                )
                NeonText("toque no nome pra renomear", color = neon.dim, size = 10.sp, family = Sans)
            }
            Spacer(Modifier.height(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                apps.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { app ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                AppTile(
                                    app = app,
                                    iconStyle = iconStyle,
                                    showLabel = true,
                                    iconSize = 50.dp,
                                    onClick = { onLaunch(app) },
                                    onLongClick = { onLongPress(app) },
                                )
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
