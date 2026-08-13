package com.stairsclub.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class StairRecord(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val floors: Int = 0,
    val date: String = "",
    val memo: String = "",
    val createdAt: Timestamp? = null
)

data class RankRow(
    val name: String,
    val floors: Int,
    val count: Int
)

enum class RankMode { MONTH, ALL, TODAY }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StairsClubApp(this) }
    }
}

private val Bg = Color(0xFFF6F7F9)
private val CardBg = Color.White
private val Ink = Color(0xFF17191F)
private val Muted = Color(0xFF858A94)
private val Soft = Color(0xFFF0F1F4)
private val Line = Color(0xFFE7E9ED)
private val Accent = Color(0xFF17191F)
private val Success = Color(0xFF2F7D65)

@Composable
fun StairsClubApp(context: Context) {
    val firebaseReady = remember {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) { false }
    }

    if (!firebaseReady) {
        FirebaseSetupScreen()
        return
    }

    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val records = remember { mutableStateListOf<StairRecord>() }

    var uid by remember { mutableStateOf<String?>(auth.currentUser?.uid) }
    var profileName by remember {
        mutableStateOf(
            context.getSharedPreferences("stairs", Context.MODE_PRIVATE)
                .getString("name", "") ?: ""
        )
    }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showProfile by remember { mutableStateOf(profileName.isBlank()) }
    var rankMode by remember { mutableStateOf(RankMode.MONTH) }
    var myHistoryOnly by remember { mutableStateOf(false) }
    var listener by remember { mutableStateOf<ListenerRegistration?>(null) }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
        uid = auth.currentUser?.uid
    }

    DisposableEffect(uid) {
        listener?.remove()
        listener = if (uid != null) {
            db.collection("records")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snap, _ ->
                    records.clear()
                    snap?.documents?.forEach { d ->
                        records += StairRecord(
                            id = d.id,
                            uid = d.getString("uid") ?: "",
                            name = d.getString("name") ?: "",
                            floors = (d.getLong("floors") ?: 0L).toInt(),
                            date = d.getString("date") ?: "",
                            memo = d.getString("memo") ?: "",
                            createdAt = d.getTimestamp("createdAt")
                        )
                    }
                }
        } else null

        onDispose { listener?.remove() }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            background = Bg,
            surface = CardBg,
            onSurface = Ink
        )
    ) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                BottomBar(
                    onCalendar = { month = YearMonth.now() },
                    onAdd = { selectedDate = LocalDate.now() },
                    onRanking = { rankMode = RankMode.MONTH }
                )
            }
        ) { pad ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .background(Bg),
                contentPadding = PaddingValues(14.dp, 18.dp, 14.dp, 110.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Header(
                        profileName = profileName,
                        onProfile = { showProfile = true }
                    )
                }

                item {
                    CalendarCard(
                        month = month,
                        uid = uid,
                        records = records,
                        onPrev = { month = month.minusMonths(1) },
                        onNext = { month = month.plusMonths(1) },
                        onToday = { month = YearMonth.now() },
                        onDateClick = { selectedDate = it }
                    )
                }

                item {
                    SummaryCards(month, uid, records)
                }

                item {
                    RankingCard(
                        month = month,
                        records = records,
                        mode = rankMode,
                        onMode = { rankMode = it }
                    )
                }

                item {
                    HistoryCard(
                        uid = uid,
                        records = records,
                        myOnly = myHistoryOnly,
                        onToggle = { myHistoryOnly = !myHistoryOnly }
                    )
                }
            }
        }

        if (showProfile) {
            ProfileSheet(
                initial = profileName,
                onDismiss = { if (profileName.isNotBlank()) showProfile = false },
                onSave = {
                    profileName = it
                    context.getSharedPreferences("stairs", Context.MODE_PRIVATE)
                        .edit().putString("name", it).apply()
                    showProfile = false
                }
            )
        }

        selectedDate?.let { date ->
            val ownRecord = records.firstOrNull { it.uid == uid && it.date == date.toString() }
            RecordSheet(
                date = date,
                name = profileName,
                existing = ownRecord,
                onDismiss = { selectedDate = null },
                onSave = { name, floors, memo ->
                    val userId = uid ?: return@RecordSheet
                    profileName = name
                    context.getSharedPreferences("stairs", Context.MODE_PRIVATE)
                        .edit().putString("name", name).apply()

                    val data = hashMapOf(
                        "uid" to userId,
                        "name" to name,
                        "floors" to floors,
                        "date" to date.toString(),
                        "memo" to memo,
                        "createdAt" to (ownRecord?.createdAt ?: Timestamp.now())
                    )

                    if (ownRecord == null) db.collection("records").add(data)
                    else db.collection("records").document(ownRecord.id).set(data)

                    selectedDate = null
                },
                onDelete = if (ownRecord != null) {
                    {
                        db.collection("records").document(ownRecord.id).delete()
                        selectedDate = null
                    }
                } else null
            )
        }
    }
}


@Composable
private fun FirebaseSetupScreen() {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Accent, background = Bg, surface = CardBg)
    ) {
        Box(
            Modifier.fillMaxSize().background(Bg).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 2.dp
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🪜", fontSize = 42.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("계단모임", fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "앱 빌드는 정상적으로 완료됐어요.\n실시간 공유를 사용하려면 Firebase 설정 파일을 연결한 뒤 APK를 다시 빌드해 주세요.",
                        textAlign = TextAlign.Center,
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(profileName: String, onProfile: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("STAIRS CLUB", fontSize = 10.sp, color = Muted, fontWeight = FontWeight.Black, letterSpacing = 1.7.sp)
            Text("계단모임", fontSize = 29.sp, fontWeight = FontWeight.Black, color = Ink)
            Text("한 층씩, 같이 쌓는 기록", fontSize = 12.sp, color = Muted)
        }
        Surface(
            onClick = onProfile,
            color = CardBg,
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(profileName.ifBlank { "이름 설정" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CalendarCard(
    month: YearMonth,
    uid: String?,
    records: List<StairRecord>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onDateClick: (LocalDate) -> Unit
) {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value % 7
    val days = month.lengthOfMonth()
    val totalCells = 42
    val today = LocalDate.now()
    val mine = records.filter { it.uid == uid }

    AppCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) { Icon(Icons.Default.KeyboardArrowLeft, null) }
            TextButton(onClick = onToday) {
                Text("${month.year}년 ${month.monthValue}월", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Ink)
            }
            IconButton(onClick = onNext) { Icon(Icons.Default.KeyboardArrowRight, null) }
        }

        Row(Modifier.fillMaxWidth()) {
            listOf("일","월","화","수","목","금","토").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = Muted)
            }
        }

        Spacer(Modifier.height(7.dp))

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(6) { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(7) { dow ->
                        val cell = week * 7 + dow
                        val dayNumber = cell - offset + 1
                        if (dayNumber in 1..days) {
                            val date = month.atDay(dayNumber)
                            val dayRecords = mine.filter { it.date == date.toString() }
                            val floor = dayRecords.sumOf { it.floors }
                            val isToday = date == today

                            Surface(
                                onClick = { onDateClick(date) },
                                modifier = Modifier.weight(1f).height(72.dp),
                                color = if (floor > 0) Soft else Color(0xFFFAFAFB),
                                shape = RoundedCornerShape(14.dp),
                                border = if (isToday) androidx.compose.foundation.BorderStroke(1.7.dp, Ink) else null
                            ) {
                                Box(Modifier.fillMaxSize().padding(8.dp)) {
                                    Text("$dayNumber", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    if (floor > 0) {
                                        Text(
                                            "${floor}층",
                                            modifier = Modifier.align(Alignment.BottomStart),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Box(
                                            Modifier.align(Alignment.TopEnd).size(6.dp)
                                                .clip(CircleShape).background(Ink)
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f).height(72.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot("기록 있음", Ink)
            LegendDot("오늘", Color.Transparent, true)
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color, outlined: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(7.dp),
            shape = CircleShape,
            color = color,
            border = if (outlined) androidx.compose.foundation.BorderStroke(1.dp, Ink) else null
        ) {}
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = Muted)
    }
}

@Composable
private fun SummaryCards(month: YearMonth, uid: String?, records: List<StairRecord>) {
    val monthKey = month.toString()
    val today = LocalDate.now().toString()
    val myMonth = records.filter { it.uid == uid && it.date.startsWith(monthKey) }.sumOf { it.floors }
    val clubMonth = records.filter { it.date.startsWith(monthKey) }.sumOf { it.floors }
    val clubToday = records.filter { it.date == today }.sumOf { it.floors }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Summary("내 이번 달", myMonth, Modifier.weight(1f))
        Summary("모임 이번 달", clubMonth, Modifier.weight(1f))
        Summary("오늘 전체", clubToday, Modifier.weight(1f))
    }
}

@Composable
private fun Summary(title: String, value: Int, modifier: Modifier) {
    Surface(modifier = modifier, color = CardBg, shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp) {
        Column(Modifier.padding(vertical = 13.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 10.sp, color = Muted)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%,d".format(value), fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text(" 층", fontSize = 10.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun RankingCard(month: YearMonth, records: List<StairRecord>, mode: RankMode, onMode: (RankMode) -> Unit) {
    val today = LocalDate.now().toString()
    val monthKey = month.toString()
    val filtered = when(mode) {
        RankMode.MONTH -> records.filter { it.date.startsWith(monthKey) }
        RankMode.ALL -> records
        RankMode.TODAY -> records.filter { it.date == today }
    }

    val ranks = filtered.groupBy { it.name }.map { (name, rs) ->
        RankRow(name, rs.sumOf { it.floors }, rs.size)
    }.sortedByDescending { it.floors }

    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("🏆 랭킹", fontSize = 18.sp, fontWeight = FontWeight.Black)
            Row(
                Modifier.background(Soft, RoundedCornerShape(11.dp)).padding(3.dp)
            ) {
                RankChip("이번 달", mode == RankMode.MONTH) { onMode(RankMode.MONTH) }
                RankChip("누적", mode == RankMode.ALL) { onMode(RankMode.ALL) }
                RankChip("오늘", mode == RankMode.TODAY) { onMode(RankMode.TODAY) }
            }
        }

        Spacer(Modifier.height(9.dp))
        if (ranks.isEmpty()) Empty("아직 기록이 없어요.")
        else ranks.take(20).forEachIndexed { index, row ->
            RankItem(index, row)
            if (index != ranks.lastIndex && index < 19) HorizontalDivider(color = Line)
        }
    }
}

@Composable
private fun RankChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) CardBg else Color.Transparent,
        shape = RoundedCornerShape(9.dp),
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Ink else Muted
        )
    }
}

@Composable
private fun RankItem(index: Int, row: RankRow) {
    val rank = when(index) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${index+1}" }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rank, Modifier.width(44.dp), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Column(Modifier.weight(1f)) {
            Text(row.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("${row.count}회 기록", fontSize = 10.sp, color = Muted)
        }
        Text("%,d층".format(row.floors), fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}

@Composable
private fun HistoryCard(uid: String?, records: List<StairRecord>, myOnly: Boolean, onToggle: () -> Unit) {
    val list = if (myOnly) records.filter { it.uid == uid } else records

    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("최근 기록", fontSize = 18.sp, fontWeight = FontWeight.Black)
            TextButton(onClick = onToggle) {
                Text(if (myOnly) "전체 기록" else "내 기록만", fontSize = 11.sp, color = Muted)
            }
        }
        if (list.isEmpty()) Empty("표시할 기록이 없어요.")
        else list.take(25).forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        if (r.memo.isBlank()) r.date else "${r.date} · ${r.memo}",
                        fontSize = 10.sp, color = Muted
                    )
                }
                Text("%,d층".format(r.floors), fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
            if (i != list.take(25).lastIndex) HorizontalDivider(color = Line)
        }
    }
}

@Composable
private fun AppCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = CardBg, shape = RoundedCornerShape(22.dp), shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, fontSize = 12.sp, color = Muted)
}

@Composable
private fun BottomBar(onCalendar: () -> Unit, onAdd: () -> Unit, onRanking: () -> Unit) {
    Surface(color = CardBg, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(74.dp).padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomItem(Icons.Default.CalendarMonth, "달력", onCalendar)
            FloatingActionButton(
                onClick = onAdd,
                containerColor = Ink,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(30.dp)) }
            BottomItem(Icons.Default.EmojiEvents, "랭킹", onRanking)
        }
    }
}

@Composable
private fun BottomItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, modifier = Modifier.size(21.dp))
        Text(text, fontSize = 10.sp, color = Muted, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordSheet(
    date: LocalDate,
    name: String,
    existing: StairRecord?,
    onDismiss: () -> Unit,
    onSave: (String, Int, String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var inputName by remember(existing, name) { mutableStateOf(existing?.name ?: name) }
    var floors by remember(existing) { mutableStateOf(existing?.floors?.toString() ?: "") }
    var memo by remember(existing) { mutableStateOf(existing?.memo ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CardBg) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp)
        ) {
            Text("STAIR RECORD", fontSize = 10.sp, color = Muted, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Text(
                date.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)),
                fontSize = 23.sp, fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(18.dp))

            Text("이름", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = inputName, onValueChange = { inputName = it.take(20) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true
            )

            Spacer(Modifier.height(10.dp))
            Text("오른 층수", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = floors,
                onValueChange = { floors = it.filter(Char::isDigit).take(4) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                suffix = { Text("층", color = Muted) },
                singleLine = true
            )

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(10,20,25,30).forEach { n ->
                    FilledTonalButton(
                        onClick = { floors = ((floors.toIntOrNull() ?: 0) + n).toString() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Soft)
                    ) { Text("+$n", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = memo, onValueChange = { memo = it.take(80) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                placeholder = { Text("메모 (선택)", fontSize = 12.sp) }
            )

            Spacer(Modifier.height(13.dp))
            Button(
                onClick = {
                    val f = floors.toIntOrNull() ?: 0
                    if (inputName.isNotBlank() && f in 1..9999) onSave(inputName.trim(), f, memo.trim())
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) { Text(if (existing == null) "기록 저장" else "기록 수정", fontWeight = FontWeight.Black) }

            AnimatedVisibility(onDelete != null) {
                TextButton(
                    onClick = { onDelete?.invoke() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("이 날짜 내 기록 삭제", color = Color(0xFFB42318), fontSize = 12.sp) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSheet(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CardBg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("PROFILE", fontSize = 10.sp, color = Muted, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Text("내 이름 설정", fontSize = 23.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(15.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(20) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                placeholder = { Text("예: 밍리") }, singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (text.isNotBlank()) onSave(text.trim()) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) { Text("저장", fontWeight = FontWeight.Black) }
            Text(
                "익명 계정으로 연결되어 본인 기록만 수정·삭제할 수 있어요.",
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 10.sp, color = Muted
            )
        }
    }
}
