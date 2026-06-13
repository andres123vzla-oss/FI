package com.example.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.CategoryEntity
import com.example.data.entity.TransactionEntity
import com.example.ui.components.CategoryChip
import com.example.ui.components.EmptyState
import com.example.ui.components.FinanceCard
import com.example.ui.components.LocalAmountsHidden
import com.example.ui.components.MainTopBar
import com.example.ui.components.PrivacyAmountText
import com.example.ui.components.maskAmount
import com.example.ui.components.pressScale
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.FinanceCalculator
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

    // UX2-01: tokens semánticos del tema en lugar de constantes Excel* acopladas a la marca.
    val finance = LocalFinanceColors.current

    // Dialog state — UX2-09: rememberSaveable para sobrevivir rotación/muerte de proceso.
    var showAddEditDialog by rememberSaveable { mutableStateOf(false) }
    // UX2-09: TransactionEntity no es Saveable; se persiste solo el id (Int) y se
    // re-resuelve la entidad desde el flow del ViewModel en cada composición.
    var selectedTransactionToEditId by rememberSaveable { mutableStateOf<Int?>(null) }
    var deleteConfirmTransactionId by rememberSaveable { mutableStateOf<Int?>(null) }
    val allTransactions by viewModel.allTransactions.collectAsState()
    val selectedTransactionToEdit: TransactionEntity? =
        selectedTransactionToEditId?.let { id -> allTransactions.firstOrNull { it.id == id } }
    val transactionToDelete: TransactionEntity? =
        deleteConfirmTransactionId?.let { id -> allTransactions.firstOrNull { it.id == id } }

    val monthNames = listOf("Todos", "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    // UX2-12: años derivados de los datos reales (incluye el año del filtro activo y
    // fallback al año actual) en vez de la lista cableada "2025/2026/2027".
    val years by viewModel.availableYears.collectAsState()
    val yearsOptions = listOf("Todos") + years.map { it.toString() }

    // UX2-07: ventana de asentamiento (mismo patrón que DashboardScreen): los flows de
    // Room/SQLCipher emiten vacío en el primer frame, lo que mostraba un parpadeo del
    // EmptyState a usuarios que sí tienen datos.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        settled = true
    }

    val transactionsListState = rememberLazyListState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            val fabInteraction = remember { MutableInteractionSource() }
            FloatingActionButton(
                onClick = {
                    selectedTransactionToEditId = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                interactionSource = fabInteraction,
                modifier = Modifier
                    .testTag("fab_add_tx")
                    .pressScale(target = 0.96f, interactionSource = fabInteraction)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar Movimiento")
            }
        },
        topBar = {
            MainTopBar(
                title = "Libro de Movimientos",
                elevated = transactionsListState.canScrollBackward
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
            FinanceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                contentPadding = PaddingValues(12.dp)
            ) {
                Column(
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
                        Box(modifier = Modifier.size(width = 1.dp, height = 24.dp).background(MaterialTheme.colorScheme.outlineVariant))

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
                        Box(modifier = Modifier.size(width = 1.dp, height = 24.dp).background(MaterialTheme.colorScheme.outlineVariant))

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
            // FIN2-02: suma defensiva que ignora valores no finitos (NaN/Infinity) para que
            // un dato corrupto no contamine todo el resumen.
            val filteredIncome = FinanceCalculator.sum(transactionList.filter { it.type == "INCOME" }.map { it.amount })
            val filteredExpense = FinanceCalculator.sum(transactionList.filter { it.type == "EXPENSE" }.map { it.amount })
            val filteredBalance = filteredIncome - filteredExpense
            if (transactionList.isNotEmpty()) {
                FinanceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .testTag("movimientos_summary_card"),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ingresos (filtro)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            PrivacyAmountText(
                                amount = FormatUtils.formatCLP(filteredIncome),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = finance.success),
                                color = finance.success,
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Gastos (filtro)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            PrivacyAmountText(
                                amount = FormatUtils.formatCLP(filteredExpense),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = finance.negative),
                                color = finance.negative,
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Balance (filtro)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            PrivacyAmountText(
                                amount = FormatUtils.formatCLP(filteredBalance),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (filteredBalance >= 0) finance.success else finance.negative
                                ),
                                color = if (filteredBalance >= 0) finance.success else finance.negative,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- LEDGER TRANSACTIONS LIST ---
            // UX2-07: mientras la base aún no emite (ventana de 300 ms), se muestra un
            // contenedor neutro en vez del EmptyState para evitar el parpadeo inicial.
            if (transactionList.isEmpty() && !settled) {
                Box(modifier = Modifier.weight(1f))
            } else if (transactionList.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f),
                    message = "No se encontraron movimientos con los filtros seleccionados.",
                    icon = Icons.Default.ReceiptLong
                )
            } else {
                LazyColumn(
                    state = transactionsListState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .testTag("transactions_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(transactionList, key = { it.id }) { tx ->
                        val categoryObj = rawCategories.firstOrNull { it.name == tx.categoryName }
                        // Fallback claro y legible (AccentBlue #4D8DFF, contraste ~5:1 sobre
                        // superficie oscura) en vez del antiguo #1F4E79, demasiado oscuro como tint. UX-09.
                        val colHex = categoryObj?.colorHex ?: "#4D8DFF"
                        val iconName = categoryObj?.iconName ?: "Category"

                        FinanceCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .testTag("transaction_item_${tx.id}"),
                            contentPadding = PaddingValues(12.dp),
                            onClick = {
                                selectedTransactionToEditId = tx.id
                                showAddEditDialog = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                            .clip(MaterialTheme.shapes.small)
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
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = tx.date,
                                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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
                                    val amountColor = if (tx.type == "INCOME") finance.success else finance.negative

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
                                        // Touch target accesible de 48dp (WCAG 2.5.5): el IconButton
                                        // de M3 mide 48dp por defecto; el ícono mantiene su tamaño. UX-05.
                                        IconButton(
                                            onClick = {
                                                deleteConfirmTransactionId = tx.id
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Borrar",
                                                tint = finance.negative,
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
                    // ARQ2-03: se pasa la entidad original completa para que el ViewModel
                    // preserve createdAt (y demás campos no editados) vía copy().
                    viewModel.updateTransaction(editingTx, type, date, categoryName, description, amount)
                } else {
                    viewModel.addTransaction(type, date, categoryName, description, amount)
                }
                showAddEditDialog = false
            }
        )
    }

    // Modal dialogue for Delete Confirmations
    transactionToDelete?.let { txToDelete ->
        // Respeta el modo privacidad global: si los montos están ocultos, se enmascara el monto
        // del diálogo para no exponerlo a un hombro que mira. UX-12.
        val amountsHidden = LocalAmountsHidden.current
        val maskedAmount = maskAmount(FormatUtils.formatCLP(txToDelete.amount), amountsHidden)
        AlertDialog(
            onDismissRequest = { deleteConfirmTransactionId = null },
            title = { Text("Confirmación de Eliminación", fontWeight = FontWeight.Bold) },
            text = { Text("¿Estás seguro de que deseas eliminar permanentemente este movimiento por un monto de $maskedAmount ('${txToDelete.description}')?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction(txToDelete)
                        deleteConfirmTransactionId = null
                    },
                    // UX2-01: token semántico + contentColor explícito para contraste estable.
                    colors = ButtonDefaults.buttonColors(containerColor = finance.negative, contentColor = Color.White)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTransactionId = null }) {
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
    // UX2-01: tokens semánticos del tema en lugar de constantes Excel*.
    val finance = LocalFinanceColors.current

    // UX2-09: campos del formulario en rememberSaveable (sobreviven rotación/muerte de
    // proceso) y keyed por transaction?.id para que abrir otro registro no reutilice
    // el estado del anterior.
    var type by rememberSaveable(transaction?.id) { mutableStateOf(transaction?.type ?: "EXPENSE") }
    var amountStr by rememberSaveable(transaction?.id) { mutableStateOf(transaction?.amount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var descStr by rememberSaveable(transaction?.id) { mutableStateOf(transaction?.description ?: "") }

    // Dropdown available categories based on currently chosen transaction type
    val filteredCategories = categories.filter { it.type == type }
    // UX2-09 (residuo documentado): selectedCategoryName queda en remember keyed por id;
    // hacerlo saveable no aporta porque el LaunchedEffect(type) de más abajo lo
    // re-sincroniza en cada nueva composición y pisaría el valor restaurado.
    var selectedCategoryName by remember(transaction?.id) {
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
    // UX2-03/FIN2-01: para un movimiento nuevo la fecha por defecto es HOY
    // (Calendar.getInstance()); se elimina la fecha semilla cableada 2026-05-29.
    val calendar = Calendar.getInstance()
    if (transaction != null) {
        // FIN2-11: Locale.US también en el PARSER. El formato yyyy-MM-dd usa dígitos
        // ASCII; con Locale.getDefault() en locales no latinos el parse fallaba y el
        // catch vacío reseteaba la fecha en silencio.
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        try {
            sdf.parse(transaction.date)?.let { calendar.time = it }
        } catch (e: Exception) {}
    }

    // FIN2-11: Locale.US en la escritura para persistir siempre dígitos ASCII.
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    // UX2-09: fecha saveable y keyed por transaction?.id.
    var dateStr by rememberSaveable(transaction?.id) { mutableStateOf(formatter.format(calendar.time)) }

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

    // UX2-09: mensaje de error saveable y keyed por transaction?.id.
    var errorMsg by rememberSaveable(transaction?.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (transaction == null) "Nuevo Movimiento" else "Editar Movimiento",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // UX2-08: el Text permanece siempre compuesto (string vacío) y con
                // liveRegion Polite para que TalkBack anuncie el error al aparecer.
                Text(
                    errorMsg,
                    color = finance.negative,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )

                // Selector: Income vs Expense tabs
                TabRow(
                    selectedTabIndex = if (type == "INCOME") 0 else 1,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = type == "INCOME",
                        onClick = { type = "INCOME" },
                        text = { Text("Ingresos", fontWeight = FontWeight.Bold, color = if (type == "INCOME") finance.success else MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                    Tab(
                        selected = type == "EXPENSE",
                        onClick = { type = "EXPENSE" },
                        text = { Text("Gastos", fontWeight = FontWeight.Bold, color = if (type == "EXPENSE") finance.negative else MaterialTheme.colorScheme.onSurfaceVariant) }
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
                    // FIN2-02: rechaza también NaN/Infinity (p.ej. "NaN" o "1e400"),
                    // que toDoubleOrNull acepta y corromperían los totales.
                    if (amt == null || !amt.isFinite() || amt <= 0.0) {
                        errorMsg = "El monto debe ser un número entero mayor a 0."
                        return@Button
                    }
                    if (selectedCategoryName.isEmpty()) {
                        errorMsg = "La categoría es obligatoria."
                        return@Button
                    }
                    onSave(type, dateStr, selectedCategoryName, descStr.ifEmpty { "Movimiento" }, amt)
                },
                // UX2-01: tokens semánticos + contentColor explícito para contraste estable.
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (type == "INCOME") finance.success else finance.negative,
                    contentColor = Color.White
                ),
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
