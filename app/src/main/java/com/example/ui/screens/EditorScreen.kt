package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.QuizCatalog
import com.example.model.QuizQuestion
import com.example.ui.components.KsbDropdown
import com.example.ui.components.KsbPill
import com.example.ui.components.KsbSectionCard
import com.example.ui.theme.KsbColors
import com.example.util.BulkImportParser

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    questions: List<QuizQuestion>,
    uiTheme: String,
    isWideScreen: Boolean,
    onAddRows: (Int) -> Unit,
    onUpdateQuestion: (Int, (QuizQuestion) -> QuizQuestion) -> Unit,
    onDeleteQuestion: (Int) -> Unit,
    onDeleteAll: () -> Unit,
    onBulkImport: (String) -> Pair<Boolean, String>,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    var showImportBox by remember { mutableStateOf(false) }
    var importTxt by remember { mutableStateOf("") }
    var importAlertMsg by remember { mutableStateOf<String?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var exportFmt by remember { mutableStateOf("txt") }

    val isDark = when (uiTheme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            val content = when (exportFmt) {
                "csv" -> BulkImportParser.toCsv(questions)
                "json" -> BulkImportParser.toJson(questions)
                else -> BulkImportParser.toTxt(questions)
            }
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
                true
            }.getOrDefault(false)
            onShowToast(if (ok) "Questions exported successfully" else "Export failed")
        }
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("Delete all questions?") },
            text = { Text("This will remove all ${questions.size} questions from the table.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllConfirm = false
                        onDeleteAll()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KsbColors.DangerRed)
                ) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text("Cancel") }
            }
        )
    }

    importAlertMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { importAlertMsg = null },
            title = { Text("Bulk Import") },
            text = { Text(msg) },
            confirmButton = {
                Button(onClick = { importAlertMsg = null }) { Text("OK") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            KsbSectionCard(
                title = "Question Table",
                trailing = { KsbPill(text = "${questions.size} questions") }
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onAddRows(1) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("add_1_row_button")
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("1 Row")
                    }
                    OutlinedButton(
                        onClick = { onAddRows(20) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("add_20_rows_button")
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("20 Blank Rows")
                    }
                    Button(
                        onClick = { showImportBox = !showImportBox },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("bulk_import_toggle")
                    ) {
                        Icon(Icons.Filled.UploadFile, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bulk Import")
                    }
                    Button(
                        onClick = { if (questions.isNotEmpty()) showDeleteAllConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = KsbColors.DangerRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("delete_all_button")
                    ) {
                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete All")
                    }
                }

                AnimatedVisibility(visible = showImportBox) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Paste Excel / Google Sheet / Word / CSV / Markdown table here (answers A/B/C/D, 1-4, or option text all work). Format: Question | A | B | C | D | Answer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = importTxt,
                                onValueChange = { importTxt = it },
                                placeholder = { Text("Paste table text here...") },
                                minLines = 5,
                                maxLines = 10,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("bulk_import_input")
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val (ok, msg) = onBulkImport(importTxt)
                                    if (ok) {
                                        importTxt = ""
                                        showImportBox = false
                                    }
                                    importAlertMsg = msg
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("bulk_import_confirm")
                            ) {
                                Text("Add to table")
                            }
                        }
                    }
                }
            }
        }

        if (questions.isEmpty()) {
            item {
                KsbSectionCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No questions yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Tap + 1 Row or Bulk Import to add quiz questions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (isWideScreen) {
            item {
                WideQuestionTableHeader()
            }
            itemsIndexed(questions, key = { idx, item -> "${item.id}_$idx" }) { idx, q ->
                WideQuestionRow(
                    index = idx,
                    question = q,
                    isDark = isDark,
                    onUpdate = { transform -> onUpdateQuestion(idx, transform) },
                    onDelete = { onDeleteQuestion(idx) }
                )
            }
        } else {
            itemsIndexed(questions, key = { idx, item -> "${item.id}_$idx" }) { idx, q ->
                CompactQuestionCard(
                    index = idx,
                    question = q,
                    isDark = isDark,
                    onUpdate = { transform -> onUpdateQuestion(idx, transform) },
                    onDelete = { onDeleteQuestion(idx) }
                )
            }
        }

        // Export Bar
        item {
            KsbSectionCard(title = "Download Questions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KsbDropdown(
                        label = "Format",
                        selectedText = when (exportFmt) {
                            "csv" -> "CSV (Excel)"
                            "json" -> "JSON"
                            else -> "TXT (Bulk Import format)"
                        },
                        options = listOf(
                            "txt" to "TXT (Bulk Import format)",
                            "csv" to "CSV (Excel)",
                            "json" to "JSON"
                        ),
                        onSelect = { exportFmt = it },
                        modifier = Modifier.weight(1f),
                        testTag = "download_format_select"
                    )
                    Button(
                        onClick = {
                            if (questions.isEmpty()) {
                                onShowToast("No questions to download.")
                            } else {
                                createDocLauncher.launch("gk-quiz.$exportFmt")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KsbColors.OkGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("download_all_button")
                    ) {
                        Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download All")
                    }
                }
            }
        }
    }
}

@Composable
private fun WideQuestionTableHeader() {
    Surface(
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        color = KsbColors.TableHeaderLight
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("#", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(34.dp))
            Text("Question", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(260.dp))
            Text("A", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(140.dp))
            Text("B", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(140.dp))
            Text("C", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(140.dp))
            Text("D", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(140.dp))
            Text("Answer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(96.dp))
            Text("Font", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(160.dp))
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun WideQuestionRow(
    index: Int,
    question: QuizQuestion,
    isDark: Boolean,
    onUpdate: ((QuizQuestion) -> QuizQuestion) -> Unit,
    onDelete: () -> Unit
) {
    val rowBg = when {
        question.unsure -> if (isDark) KsbColors.UnsureRowDark else KsbColors.UnsureRowLight
        else -> MaterialTheme.colorScheme.surface
    }
    val okBg = Color(0x2916A34A)
    val ans = question.normalizedAns()
    val fontLabel = if (question.font.isBlank()) "Default" else QuizCatalog.fontByKey(question.font).label

    Surface(
        color = rowBg,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${index + 1}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(34.dp)
            )
            OutlinedTextField(
                value = question.q,
                onValueChange = { v -> onUpdate { it.copy(q = v) } },
                singleLine = true,
                modifier = Modifier.width(260.dp)
            )
            listOf("A" to question.a, "B" to question.b, "C" to question.c, "D" to question.d).forEach { (key, value) ->
                val isOk = key == ans
                OutlinedTextField(
                    value = value,
                    onValueChange = { v ->
                        onUpdate {
                            when (key) {
                                "A" -> it.copy(a = v)
                                "B" -> it.copy(b = v)
                                "C" -> it.copy(c = v)
                                else -> it.copy(d = v)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = if (isOk) okBg else Color.Transparent,
                        unfocusedContainerColor = if (isOk) okBg else Color.Transparent,
                        unfocusedBorderColor = if (isOk) KsbColors.OkGreen else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.width(140.dp)
                )
            }
            KsbDropdown(
                label = "",
                selectedText = ans,
                options = listOf("A" to "A", "B" to "B", "C" to "C", "D" to "D"),
                onSelect = { a -> onUpdate { it.copy(ans = a, unsure = false) } },
                modifier = Modifier.width(96.dp)
            )
            KsbDropdown(
                label = "",
                selectedText = fontLabel,
                options = listOf("" to "Default") + QuizCatalog.fonts.map { it.key to it.label },
                onSelect = { f -> onUpdate { it.copy(font = f) } },
                modifier = Modifier.width(160.dp)
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Delete row ${index + 1}", tint = KsbColors.DangerRed)
            }
        }
    }
}

@Composable
private fun CompactQuestionCard(
    index: Int,
    question: QuizQuestion,
    isDark: Boolean,
    onUpdate: ((QuizQuestion) -> QuizQuestion) -> Unit,
    onDelete: () -> Unit
) {
    val cardBg = when {
        question.unsure -> if (isDark) KsbColors.UnsureRowDark else KsbColors.UnsureRowLight
        else -> MaterialTheme.colorScheme.surface
    }
    val okBg = Color(0x2916A34A)
    val ans = question.normalizedAns()
    val fontLabel = if (question.font.isBlank()) "Default" else QuizCatalog.fontByKey(question.font).label

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(1.dp, if (question.unsure) KsbColors.PendingAmber else MaterialTheme.colorScheme.outline),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "#${index + 1}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (question.unsure) {
                        KsbPill(
                            text = "Check answer",
                            containerColor = Color(0x33D97706),
                            contentColor = KsbColors.PendingAmber
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp).testTag("delete_row_$index")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Delete question ${index + 1}", tint = KsbColors.DangerRed)
                }
            }

            OutlinedTextField(
                value = question.q,
                onValueChange = { v -> onUpdate { it.copy(q = v) } },
                label = { Text("Question") },
                minLines = 1,
                maxLines = 3,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionCell("A", question.a, ans == "A", okBg, Modifier.weight(1f)) { v -> onUpdate { it.copy(a = v) } }
                OptionCell("B", question.b, ans == "B", okBg, Modifier.weight(1f)) { v -> onUpdate { it.copy(b = v) } }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionCell("C", question.c, ans == "C", okBg, Modifier.weight(1f)) { v -> onUpdate { it.copy(c = v) } }
                OptionCell("D", question.d, ans == "D", okBg, Modifier.weight(1f)) { v -> onUpdate { it.copy(d = v) } }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KsbDropdown(
                    label = "Answer",
                    selectedText = ans,
                    options = listOf("A" to "A", "B" to "B", "C" to "C", "D" to "D"),
                    onSelect = { a -> onUpdate { it.copy(ans = a, unsure = false) } },
                    modifier = Modifier.weight(0.35f)
                )
                KsbDropdown(
                    label = "Font",
                    selectedText = fontLabel,
                    options = listOf("" to "Default") + QuizCatalog.fonts.map { it.key to it.label },
                    onSelect = { f -> onUpdate { it.copy(font = f) } },
                    modifier = Modifier.weight(0.65f)
                )
            }
        }
    }
}

@Composable
private fun OptionCell(
    label: String,
    value: String,
    isCorrect: Boolean,
    okBg: Color,
    modifier: Modifier,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Option $label") },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = if (isCorrect) okBg else Color.Transparent,
            unfocusedContainerColor = if (isCorrect) okBg else Color.Transparent,
            unfocusedBorderColor = if (isCorrect) KsbColors.OkGreen else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
    )
}
