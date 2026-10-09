package com.dante.anchoralarm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color.Black
val Paper = Color.White

@Composable
fun AnchorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink, onPrimary = Paper,
            secondary = Ink, onSecondary = Paper,
            background = Paper, onBackground = Ink,
            surface = Paper, onSurface = Ink,
            surfaceVariant = Paper, onSurfaceVariant = Ink,
            outline = Ink, outlineVariant = Ink,
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Paper,
            contentColor = Ink,
            content = content,
        )
    }
}

/** Clickable with no ripple — keeps everything strictly black on white. */
@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

@Composable
fun Label(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 10.dp)) {
        HorizontalDivider(color = Ink, thickness = 2.dp)
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
fun SolidButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().background(Ink).tap(onClick).padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Paper, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
    }
}

@Composable
fun LineButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().border(2.dp, Ink).tap(onClick).padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
    }
}

@Composable
fun Seg(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().border(2.dp, Ink)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f)
                    .background(if (on) Ink else Paper)
                    .tap { onSelect(i) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    o,
                    color = if (on) Paper else Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}

@Composable
fun RowScope.Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f)
            .border(2.dp, Ink)
            .background(if (selected) Ink else Paper)
            .tap(onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Paper else Ink,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}
