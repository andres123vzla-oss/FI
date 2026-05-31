package com.example.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.CategoryEntity
import com.example.data.entity.TransactionEntity
import com.example.ui.components.CategoryChip
import com.example.ui.components.EmptyState
import com.example.ui.components.MainTopBar
import com.example.ui.components.PrivacyAmountText
import com.example.ui.theme.ExcelGreen
import com.example.ui.theme.ExcelRed
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.FormatUtils
import com.example.util.IconMapper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovimientosScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val transactionList by viewModel.filteredTransactionsFlow.collectAsState()
    val rawCategories by viewModel.allCategories.collectAsState()

    // Filter controls
    val activeType by viewModel.filterType.collectAsState()
    val activeCategory by viewModel.filterCategory.collectAsState()
    val activeMonth by viewModel.filterMonth.collectAsState()
    val activeYear by viewModel.filterYear.collectAsState()
    val activeSearch by viewModel.searchQuery.collectAsState()

    // Dialog state
    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedTransactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<TransactionEntity?>(null) }

    val monthNames = listOf("Todos", "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    val yearsOptions = listOf("Todos", "2025", "2026", "2027")

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedTransactionToEdit = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_tx")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar Movimiento")
            }
        },
        topBar = {
            MainTopBar(
                title = "💸 Libro de Movimientos",
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // --- SEARCH BAR & TOGGLES ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Search bar text field
                    OutlinedTextField(
                        value = activeSearch,
                        onValueChange = { viewModel.searchQuery.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_input"),
                        placeholder = { Text("Buscar descripción, categoría...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (activeSearch.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Horizontal Scroll filter categories
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Type Filter
                        FilterChip(
                            selected = activeType == "ALL",
                            onClick = { viewModel.filterType.value = "ALL" },
                            label = { Text("Todos", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = activeType == "INCOME",
                            onClick = { viewModel.filterType.value = "INCOME" },
                            label = { Text("Ingresos", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = activeType == "EXPENSE",
                            onClick = { viewModel.filterType.value = "EXPENSE" },
                            label = { Text("Gastos", fontSize = 12.sp) }
                        )

                        // Spacer divider
                        Box(modifier = Modifier.size(width = 1.dp, height = 24.dp).background(Color.LightGray))

                        // Year Pick
                        yearsOptions.forEachIndexed { index, yr ->
                            val yrVal = yr.toIntOrNull() ?: 0
                            FilterChip(
                                selected = (yrVal == activeYear) || (yr == "Todos" && activeYear == 0),
                                onClick = { viewModel.filterYear.value = yrVal },
                                label = { Text(yr, fontSize = 12.sp) }
                            )
                        }

                        // Divider
                        Box(modifier = Modifier.size(width = 1.dp, height = 24.dp).background(Color.LightGray))

                        // Category Dropdown Filter Selection list
                        val availableFilterCats = listOf("ALL") + rawCategories.map { it.name }.distinct()
                        availableFilterCats.forEach { cat ->
                            FilterChip(
                                selected = cat == activeCategory,
                                onClick = { viewModel.filterCategory.value = cat },
                                label = { Text(if (cat == "ALL") "Tód. Cat." else cat, fontSize = 12.sp) }
                            )
                        }
                    }

                    // Month Horizontal row selection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        monthNames.forEachIndexed { index, mName ->
                            FilterChip(
                                selected = index == activeMonth,
                                onClick = { viewModel.filterMonth.value = index },
                                label = { Text(mName, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // --- RESUMEN DE LO FILTRADO ---
            val filteredIncome = transactionList.filter { it.type == "INCOME" }.sumOf { it.amount }
            val filteredExpense = transactionList.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val filteredBalance = filteredIncome - filteredExpense
            if (transactionList.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .testTag("movimientos_summary_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ingresos (filtro)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            PrivacyAmountText(
                                amount = FormatUtils.formatCLP(filteredIncome),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = ExcelGreen),
                                color = ExcelGreen,
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Gastos (filtro)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            PrivacyAmountText(
                                amount = FormatUtils.formatCLP(filteredExpense),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = ExcelRed),
                                color = ExcelRed,
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Balance (filtro)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            PrivacyAmountText(
                                amount = FormatUtils.formatCLP(filteredBalance),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (filteredBalance >= 0) ExcelGreen else ExcelRed
                                ),
                                color = if (filteredBalance >= 0) ExcelGreen else ExcelRed,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- LEDGER TRANSACTIONS LIST ---
            if (transactionList.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f),
                    message = "No se encontraron movimientos con los filtros seleccionados.",
                    icon = Icons.Default.ReceiptLong
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .testTag("transactions_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(transactionList, key = { it.id }) { tx ->
                        val categoryObj = rawCategories.firstOrNull { it.name == tx.categoryName }
                        val colHex = categoryObj?.colorHex ?: "#1F4E79"
                        val iconName = categoryObj?.iconName ?: "Category"

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTransactionToEdit = tx
                                    showAddEditDialog = true
                                }
                                .testTag("transaction_item_${tx.id}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Info / Column description
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Custom visual icon box
                                    val catColor = try {
                                        Color(android.graphics.Color.parseColor(colHex))
                                    } catch (e: Exception) {
                                        MaterialTheme.colorScheme.primary
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(catColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = IconMapper.mapToIcon(iconName),
                                            contentDescription = null,
                                            tint = catColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tx.description,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            CategoryChip(
                                                categoryName = tx.categoryName,
                                                colorHex = colHex,
                                                iconName = iconName
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.DateRange,
                                                    contentDescription = null,
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = tx.date,
                                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 11.sp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Amount details column
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    val formattedVal = FormatUtils.formatCLP(tx.amount)
                                    val amountText = if (tx.type == "INCOME") "+ $formattedVal" else "- $formattedVal"
                                    val amountColor = if (tx.type == "INCOME") ExcelGreen else ExcelRed

                                    PrivacyAmountText(
                                        amount = amountText,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = amountColor,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = amountColor,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                showDeleteConfirmDialog = tx
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Borrar",
                                                tint = ExcelRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal dialog for Creation / Editing
    if (showAddEditDialog) {
        val editingTx = selectedTransactionToEdit
        AddEditTransactionFormDialog(
            transaction = editingTx,
            categories = rawCategories,
            onDismiss = { showAddEditDialog = false },
            onSave = { type, date, categoryName, description, amount ->
                if (editingTx != null) {
                    viewModel.updateTransaction(editingTx.id, type, date, categoryName, description, amount)
                } else {
                    viewModel.addTransaction(type, date, categoryName, description, amount)
                }
                showAddEditDialog = false
            }
        )
    }

    // Modal dialogue for Delete Confirmations
    showDeleteConfirmDialog?.let { transactionToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("⚠️ Confirmación de Eliminación", fontWeight = FontWeight.Bold) },
            text = { Text("¿Estás seguro de que deseas eliminar permanentemente este movimiento por un monto de ${FormatUtils.formatCLP(transactionToDelete.amount)} ('${transactionToDelete.description}')?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction(transactionToDelete)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExcelRed)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun AddEditTransactionFormDialog(
    transaction: TransactionEntity?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (type: String, date: String, categoryName: String, description: String, amount: Double) -> Unit
) {
    var type by remember { mutableStateOf(transaction?.type ?: "EXPENSE") }
    var amountStr by remember { mutableStateOf(transaction?.amount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var descStr by remember { mutableStateOf(transaction?.description ?: "") }

    // Dropdown available categories based on currently chosen transaction type
    val filteredCategories = categories.filter { it.type == type }
    var selectedCategoryName by remember {
        mutableStateOf(
            if (transaction != null && transaction.type == type) {
                transaction.categoryName
            } else {
                filteredCategories.firstOrNull()?.name ?: ""
            }
        )
    }

    // Sync selected category if type switches
    LaunchedEffect(type) {
        if (transaction == null || transaction.type != type) {
            selectedCategoryName = categories.firstOrNull { it.type == type }?.name ?: ""
        } else {
            selectedCategoryName = transaction.categoryName
        }
    }

    var expandedCategoryDropdown by remember { mutableStateOf(false) }

    // Date picker setup
    val calendar = Calendar.getInstance()
    if (transaction != null) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        try {
            sdf.parse(transaction.date)?.let { calendar.time = it }
        } catch (e: Exception) {}
    } else {
        // May 29, 2026 default seed settings
        calendar.set(Calendar.YEAR, 2026)
        calendar.set(Calendar.MONTH, Calendar.MAY)
        calendar.set(Calendar.DAY_OF_MONTH, 29)
    }

    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    var dateStr by remember { mutableStateOf(formatter.format(calendar.time)) }

    val context = LocalContext.current
    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, y: Int, m: Int, d: Int ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, y)
                set(Calendar.MONTH, m)
                set(Calendar.DAY_OF_MONTH, d)
            }
            dateStr = formatter.format(cal.time)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (transaction == null) "➕ Nuevo Movimiento" else "📝 Editar Movimiento",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = ExcelRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }

                // Selector: Income vs Expense tabs
                TabRow(
                    selectedTabIndex = if (type == "INCOME") 0 else 1,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = type == "INCOME",
                        onClick = { type = "INCOME" },
                        text = { Text("Ingresos", fontWeight = FontWeight.Bold, color = if (type == "INCOME") ExcelGreen else Color.Gray) }
                    )
                    Tab(
                        selected = type == "EXPENSE",
                        onClick = { type = "EXPENSE" },
                        text = { Text("Gastos", fontWeight = FontWeight.Bold, color = if (type == "EXPENSE") ExcelRed else Color.Gray) }
                     )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Date Picker trigger button
                OutlinedButton(
                    onClick = { datePickerDialog.show() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Fecha: $dateStr")
                }

                // Category selection dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedCategoryDropdown = true },
                        modifier = Modifier.fillMaxWidth().testTag("category_select_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Categoría: ${selectedCategoryName.ifEmpty { "Seleccionar" }}")
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = expandedCategoryDropdown,
                        onDismissRequest = { expandedCategoryDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        filteredCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedCategoryName = cat.name
                                    expandedCategoryDropdown = false
                                }
                            )
                        }
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Monto (CLP)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("Ej: 20000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("amount_field"),
                    shape = RoundedCornerShape(8.dp)
                )

                // Description
                OutlinedTextField(
                    value = descStr,
                    onValueChange = { descStr = it },
                    label = { Text("Descripción") },
                    placeholder = { Text("Opcional") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("description_field"),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    if (amt == null || amt <= 0.0) {
                        errorMsg = "El monto debe ser un número entero mayor a 0."
                        return@Button
                    }
                    if (selectedCategoryName.isEmpty()) {
                        errorMsg = "La categoría es obligatoria."
                        return@Button
                    }
                    onSave(type, dateStr, selectedCategoryName, descStr.ifEmpty { "Movimiento" }, amt)
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (type == "INCOME") ExcelGreen else ExcelRed),
                modifier = Modifier.testTag("save_tx_btn")
            ) {
                Text("Confirmar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
