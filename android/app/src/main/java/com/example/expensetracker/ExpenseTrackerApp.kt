package com.example.expensetracker

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensetracker.data.AnalysisEntry
import com.example.expensetracker.data.Expense
import com.example.expensetracker.ui.ExpenseUiState
import com.example.expensetracker.ui.ExpenseViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTrackerApp(viewModel: ExpenseViewModel = viewModel(factory = rememberExpenseViewModelFactory())) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val resolver = context.contentResolver

    LaunchedEffect(uiState.lastAnalysisError) {
        uiState.lastAnalysisError?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(text = context.getString(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ApiKeyCard(
                    uiState = uiState,
                    onSave = viewModel::saveApiKey,
                    onClear = viewModel::clearApiKey
                )
            }
            item {
                ExpenseForm { title, amount, category, date, notes ->
                    viewModel.addExpense(title, amount, category, date, notes)
                    coroutineScope.launch { snackbarHostState.showSnackbar("Išlaidos įrašas išsaugotas") }
                }
            }
            item { MonthlySummaryCard(uiState) }
            item {
                AnalysisCard(
                    uiState = uiState,
                    resolver = resolver,
                    onAnalyze = { uri, name -> viewModel.analyzeFile(resolver, uri, name) }
                )
            }
            if (uiState.analysisHistory.isNotEmpty()) {
                item { AnalysisHistory(uiState.analysisHistory) }
            }
            if (uiState.expenses.isNotEmpty()) {
                item { Text("Išlaidų įrašai", style = MaterialTheme.typography.titleMedium) }
                items(uiState.expenses, key = { it.id }) { expense ->
                    ExpenseRow(expense)
                }
            }
        }
    }
}

@Composable
fun ApiKeyCard(
    uiState: ExpenseUiState,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var apiKey by remember(uiState.apiKey) { mutableStateOf(uiState.apiKey ?: "") }
    var showValue by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "OpenAI API raktas", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API raktas") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showValue) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showValue = !showValue }) {
                        Text(if (showValue) "Slėpti" else "Rodyti")
                    }
                }
            )
            RowButtons(uiState.apiKey, onSave = { onSave(apiKey) }, onClear = onClear)
        }
    }
}

@Composable
private fun RowButtons(
    storedKey: String?,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSave) { Text("Išsaugoti") }
        if (!storedKey.isNullOrEmpty()) {
            OutlinedButton(onClick = onClear) { Text("Pašalinti") }
        }
    }
}

@Composable
fun ExpenseForm(
    onSubmit: (String, Double, String, LocalDate, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Naujas įrašas", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Pavadinimas") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Suma") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Kategorija") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Pastabos") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                val numericAmount = amount.toDoubleOrNull() ?: return@Button
                onSubmit(title, numericAmount, category, LocalDate.now(), notes.ifBlank { null })
                title = ""
                amount = ""
                category = ""
                notes = ""
            }) {
                Text("Pridėti išlaidas")
            }
        }
    }
}

@Composable
fun MonthlySummaryCard(uiState: ExpenseUiState) {
    val currency = remember { NumberFormat.getCurrencyInstance() }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${uiState.selectedMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} mėn. suvestinė",
                style = MaterialTheme.typography.titleMedium
            )
            Text("Suma: ${currency.format(uiState.monthlyTotal)}")
            if (uiState.yearlyTotals.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Metiniai rezultatai pagal kategoriją", style = MaterialTheme.typography.titleSmall)
                uiState.yearlyTotals.forEach { (category, total) ->
                    Text("• $category – ${currency.format(total)}")
                }
            }
        }
    }
}

@Composable
fun AnalysisCard(
    uiState: ExpenseUiState,
    resolver: ContentResolver,
    onAnalyze: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.getFileName(resolver, uri) ?: "failas"
            onAnalyze(uri, name)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Failų analizė", style = MaterialTheme.typography.titleMedium)
            Text("Pasirinkite CSV, PDF ar Excel failą. Interneto ryšys būtinas analizei atlikti.")
            Button(
                onClick = {
                    launcher.launch(
                        arrayOf(
                            "text/csv",
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel"
                        )
                    )
                },
                enabled = uiState.isOnline && !uiState.apiKey.isNullOrEmpty() && !uiState.isAnalyzing
            ) {
                Text(if (uiState.isAnalyzing) "Analizuojama..." else "Įkelti ir analizuoti")
            }
            if (!uiState.isOnline) {
                Text("Šiuo metu esate offline. Analizė bus pasiekiama prisijungus.", color = MaterialTheme.colorScheme.error)
            }
            if (uiState.apiKey.isNullOrEmpty()) {
                Text("Įveskite API raktą, kad suaktyvintumėte analizę.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun AnalysisHistory(history: List<AnalysisEntry>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Paskutinės analizės", style = MaterialTheme.typography.titleMedium)
            history.forEach { entry ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.fileName, style = MaterialTheme.typography.titleSmall)
                    Text(entry.summary)
                }
            }
        }
    }
}

@Composable
fun ExpenseRow(expense: Expense) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val currency = remember { NumberFormat.getCurrencyInstance() }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(expense.title, style = MaterialTheme.typography.titleMedium)
            Text("${formatter.format(expense.occurredOn)} – ${currency.format(expense.amount)}")
            Text("Kategorija: ${expense.category}")
            expense.notes?.let { Text(it) }
        }
    }
}

@Composable
fun rememberExpenseViewModelFactory(): ExpenseViewModelFactory {
    val context = LocalContext.current.applicationContext
    return remember { ExpenseViewModelFactory(context) }
}

class ExpenseViewModelFactory(private val context: Context) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            val database = com.example.expensetracker.data.ExpenseDatabase.get(context)
            val repository = com.example.expensetracker.data.ExpenseRepository(database.expenseDao())
            val dataStore = com.example.expensetracker.data.AppDataStore(context)
            val analyzer = com.example.expensetracker.data.OpenAIFileAnalyzer()
            @Suppress("UNCHECKED_CAST")
            return ExpenseViewModel(repository, dataStore, analyzer) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

private fun Context.getFileName(resolver: ContentResolver, uri: Uri): String? =
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    }
