package com.dante.anchoralarm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class RingActivity : ComponentActivity() {

    private var info by mutableStateOf("")
    private var planId by mutableIntStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        planId = intent.getIntExtra("planId", -1)
        info = describe(intent)
        setContent {
            AnchorTheme {
                RingScreen(
                    planId = planId,
                    info = info,
                    onSilence = {
                        stopService(Intent(this@RingActivity, RingService::class.java))
                        finish()
                    },
                    onDone = {
                        Scheduler.cancel(this@RingActivity, planId)
                        Store.saveRings(this@RingActivity, planId, emptyList())
                        stopService(Intent(this@RingActivity, RingService::class.java))
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        planId = intent.getIntExtra("planId", -1)
        info = describe(intent)
    }

    private fun describe(i: Intent): String =
        "RING ${i.getIntExtra("index", 0) + 1} / ${i.getIntExtra("total", 1)}"
}

@Composable
fun RingScreen(planId: Int, info: String, onSilence: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var plan by remember(planId) { mutableStateOf(Store.load(ctx, planId) ?: Plan(id = planId)) }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
        Text(
            plan.name.ifBlank { "Anchor" }.uppercase(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
        )
        Text(info, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Text("Check everything off.", fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(20.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            plan.items.forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = item.done,
                        onCheckedChange = { c ->
                            plan = plan.copy(items = plan.items.mapIndexed { j, x ->
                                if (j == i) x.copy(done = c) else x
                            })
                            Store.save(ctx, plan)
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Ink,
                            uncheckedColor = Ink,
                            checkmarkColor = Paper,
                        ),
                    )
                    Text(
                        item.text,
                        fontSize = 22.sp,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    )
                }
            }
        }

        LineButton("SILENCE", onClick = onSilence)
        Spacer(Modifier.height(12.dp))
        SolidButton("ALL DONE · CANCEL REST", onClick = onDone)
    }
}
