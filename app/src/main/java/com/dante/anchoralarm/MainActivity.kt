package com.dante.anchoralarm

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Date

class MainActivity : ComponentActivity() {

    private var tick by mutableIntStateOf(0)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { AnchorTheme { Root(tick) } }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }
}

@Composable
fun Root(tick: Int) {
    var openId by rememberSaveable { mutableStateOf<Int?>(null) }
    val id = openId
    if (id == null) {
        HomeScreen(tick) { openId = it }
    } else {
        BackHandler { openId = null }
        EditorScreen(tick, id) { openId = null }
    }
}

// =====================================================================
//  HOME — list of all checklists
// =====================================================================
@Composable
fun HomeScreen(tick: Int, onOpen: (Int) -> Unit) {
    val ctx = LocalContext.current
    val plans = remember(tick) { Store.loadAll(ctx) }
    val now = System.currentTimeMillis()
    val timeFmt = DateFormat.getTimeFormat(ctx)

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("ANCHOR", fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
        Text(
            "Each checklist has its own anchor and rings.",
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp),
        )

        Label("YOUR CHECKLISTS")

        if (plans.isEmpty()) {
            Text("None yet — add your first one below.", fontSize = 15.sp)
        }

        plans.forEach { p ->
            val armed = Store.loadRings(ctx, p.id).any { it > now }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .border(2.dp, Ink)
                    .tap { onOpen(p.id) }
                    .padding(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.name.ifBlank { "Untitled" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                    )
                    if (armed) {
                        Text("● ARMED", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                }
                Text(
                    timeFmt.format(Date(Scheduler.anchorMillis(p.hour, p.minute))) +
                        "  ·  " + (if (p.after) "AFTER" else "BEFORE"),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "${p.count} rings every ${p.intervalMin} min  ·  " +
                        "${p.items.count { it.done }}/${p.items.size} done",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SolidButton("+ NEW CHECKLIST") {
            val id = Store.nextId(ctx)
            Store.save(ctx, Plan(id = id, name = "Checklist ${plans.size + 1}"))
            onOpen(id)
        }
        Spacer(Modifier.height(40.dp))
    }
}

// =====================================================================
//  EDITOR — one checklist + its anchor / pattern
// =====================================================================
@Composable
fun EditorScreen(tick: Int, id: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var plan by remember(tick, id) { mutableStateOf(Store.load(ctx, id) ?: Plan(id = id)) }
    var rings by remember(tick, id) { mutableStateOf(Store.loadRings(ctx, id)) }
    var draft by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val armed = rings.any { it > now }
    val timeFmt = DateFormat.getTimeFormat(ctx)

    fun update(p: Plan) {
        plan = p
        Store.save(ctx, p)
    }

    fun addItem() {
        val t = draft.trim()
        if (t.isNotEmpty()) {
            update(plan.copy(items = plan.items + Item(t)))
            draft = ""
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Paper,
            title = { Text("Delete this checklist?", fontWeight = FontWeight.Bold) },
            text = { Text("Its items and rings will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    Scheduler.cancel(ctx, id)
                    Store.delete(ctx, id)
                    confirmDelete = false
                    onBack()
                }) { Text("DELETE", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("CANCEL", fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            "← ALL CHECKLISTS",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.tap { onBack() }.padding(vertical = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = plan.name,
            onValueChange = { update(plan.copy(name = it)) },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // ---------------- 1. Checklist ----------------
        Label("1 · CHECKLIST")
        plan.items.forEachIndexed { i, item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.done,
                    onCheckedChange = { c ->
                        update(plan.copy(items = plan.items.mapIndexed { j, x ->
                            if (j == i) x.copy(done = c) else x
                        }))
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Ink,
                        uncheckedColor = Ink,
                        checkmarkColor = Paper,
                    ),
                )
                Text(
                    item.text,
                    fontSize = 17.sp,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "✕",
                    fontSize = 16.sp,
                    modifier = Modifier
                        .tap { update(plan.copy(items = plan.items.filterIndexed { j, _ -> j != i })) }
                        .padding(14.dp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Add an item") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addItem() }),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(56.dp).border(2.dp, Ink).tap { addItem() },
                contentAlignment = Alignment.Center,
            ) { Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        }

        // ---------------- 2. Anchor ----------------
        Label("2 · ANCHOR TIME")
        Text(
            timeFmt.format(Date(Scheduler.anchorMillis(plan.hour, plan.minute))),
            fontSize = 56.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.tap {
                TimePickerDialog(
                    ctx,
                    { _, h, m -> update(plan.copy(hour = h, minute = m)) },
                    plan.hour,
                    plan.minute,
                    DateFormat.is24HourFormat(ctx),
                ).show()
            },
        )
        Text("tap the time to change it", fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp))
        Seg(listOf("BEFORE", "AFTER"), if (plan.after) 1 else 0) { update(plan.copy(after = it == 1)) }

        // ---------------- 3. Pattern ----------------
        Label("3 · PATTERN")
        Text("Ring every … minutes", fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 5, 10, 15, 30).forEach { v ->
                Chip("$v", plan.intervalMin == v) { update(plan.copy(intervalMin = v)) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Number of rings", fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).border(2.dp, Ink).tap {
                    if (plan.count > 1) update(plan.copy(count = plan.count - 1))
                },
                contentAlignment = Alignment.Center,
            ) { Text("−", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            Text(
                "${plan.count}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(80.dp),
            )
            Box(
                Modifier.size(52.dp).border(2.dp, Ink).tap {
                    if (plan.count < 30) update(plan.copy(count = plan.count + 1))
                },
                contentAlignment = Alignment.Center,
            ) { Text("+", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        }
        Text(
            if (plan.after) "Rings start at the anchor and continue after it."
            else "Rings build up and the last one lands on the anchor.",
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 10.dp),
        )

        Spacer(Modifier.height(24.dp))
        SolidButton("SET RINGS") {
            val reset = plan.copy(items = plan.items.map { it.copy(done = false) })
            update(reset)
            val times = Scheduler.ringTimes(reset)
            Scheduler.schedule(ctx, reset.id, times)
            Store.saveRings(ctx, reset.id, times)
            rings = times
            Toast.makeText(ctx, "${times.size} rings set", Toast.LENGTH_SHORT).show()
        }
        if (armed) {
            Spacer(Modifier.height(12.dp))
            LineButton("CANCEL RINGS") {
                Scheduler.cancel(ctx, plan.id)
                Store.saveRings(ctx, plan.id, emptyList())
                rings = emptyList()
            }
        }
        Spacer(Modifier.height(28.dp))
        LineButton("DELETE CHECKLIST") { confirmDelete = true }
        Spacer(Modifier.height(40.dp))
    }
}
