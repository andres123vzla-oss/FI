package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.entity.CategoryEntity
import com.example.security.SecurityViewModel
import com.example.ui.components.CategoryChip
import com.example.ui.components.EmptyState
import com.example.ui.components.FinanceCard
import com.example.ui.components.MainTopBar
import com.example.ui.components.pressScale
import com.example.ui.security.ConfirmPinDialog
import com.example.ui.security.SecuritySettingsCard
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.IconMapper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier,
    securityViewModel: SecurityViewModel = viewModel()
) {
    val categories by viewModel.allCategories.collectAsState()
    val isPinSet by securityViewModel.isPinSet.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // UX2-01: tokens semánticos de color del tema (success/negative) en lugar de Excel*.
    val finance = LocalFinanceColors.current

    // UX2-09: estado de UI sobrevive a muerte de proceso/rotación con rememberSaveable.
    var tabIndex by rememberSaveable { mutableStateOf(0) } // 0: Gastos, 1: Ingresos

    var showAddCategoryDialog by rememberSaveable { mutableStateOf(false) }
    // UX2-09: la entidad no es saveable; se guarda solo el id y se re-resuelve desde allCategories.
    var selectedCategoryToEditId by rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedCategoryToEdit: CategoryEntity? =
        selectedCategoryToEditId?.let { id -> categories.find { it.id == id } }
    // UX2-04: id de la categoría pendiente de confirmación de borrado (saveable por la misma razón).
    var categoryToDeleteId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showResetSemillaConfirm by rememberSaveable { mutableStateOf(false) }
    var showDeleteAllConfirm by rememberSaveable { mutableStateOf(false) }

    // Reautenticación para acciones sensibles: si hay PIN, exige confirmarlo antes de ejecutar.
    // UX2-09: excepción — una lambda no es saveable; se mantiene en remember (residuo documentado).
    var reauthAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun runSensitive(action: () -> Unit) {
        if (isPinSet) reauthAction = action else action()
    }

    val expensesCats = categories.filter { it.type == "EXPENSE" }
    val incomeCats = categories.filter { it.type == "INCOME" }

    val contentScrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val fabInteraction = remember { MutableInteractionSource() }
            FloatingActionButton(
                onClick = {
                    selectedCategoryToEditId = null
                    showAddCategoryDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary, // UX2-01: color de tema, no Excel*
                contentColor = Color.White,
                interactionSource = fabInteraction,
                modifier = Modifier
                    .testTag("fab_add_category")
                    .pressScale(target = 0.96f, interactionSource = fabInteraction)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar Categoría")
            }
        },
        topBar = {
            MainTopBar(
                title = "Configuración y Categorías",
                elevated = contentScrollState.canScrollBackward
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(contentScrollState)
        ) {
            // --- TOP SECTIONS FOR CATEGORIES ---
            FinanceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column {
                    TabRow(
                        selectedTabIndex = tabIndex,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    ) {
                        Tab(
                            selected = tabIndex == 0,
                            onClick = { tabIndex = 0 },
                            text = { Text("Gastos (${expensesCats.size})", fontWeight = FontWeight.Bold, color = if (tabIndex == 0) finance.negative else MaterialTheme.colorScheme.onSurfaceVariant) } // UX2-01
                        )
                        Tab(
                            selected = tabIndex == 1,
                            onClick = { tabIndex = 1 },
                            text = { Text("Ingresos (${incomeCats.size})", fontWeight = FontWeight.Bold, color = if (tabIndex == 1) finance.success else MaterialTheme.colorScheme.onSurfaceVariant) } // UX2-01
                        )
                    }

                    val activeList = if (tabIndex == 0) expensesCats else incomeCats

                    if (activeList.isEmpty()) {
                        EmptyState(
                            message = "No hay categorías de este tipo registradas.",
                            icon = Icons.Default.Category
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp).testTag("categories_grid"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeList.forEach { cat ->
                              Surface(
                                  onClick = {
                                      selectedCategoryToEditId = cat.id
                                      showAddCategoryDialog = true
                                  },
                                  modifier = Modifier.fillMaxWidth(),
                                  color = MaterialTheme.colorScheme.surfaceContainer,
                                  shape = MaterialTheme.shapes.medium,
                                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                              ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Colored circle indicator
                                        val parseColor = try {
                                            Color(android.graphics.Color.parseColor(cat.colorHex))
                                        } catch (e: Exception) {
                                            MaterialTheme.colorScheme.primary
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(parseColor)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        CategoryChip(
                                            categoryName = cat.name,
                                            colorHex = cat.colorHex,
                                            iconName = cat.iconName
                                        )
                                        if (cat.isDefault) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Sist.",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold),
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    // UX2-04: sin Modifier.size(28.dp) — el IconButton recupera su
                                    // target táctil accesible de 48dp; el borrado ahora pide confirmación.
                                    IconButton(
                                        onClick = { categoryToDeleteId = cat.id },
                                        modifier = Modifier.testTag("delete_category_${cat.name}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar Categoría",
                                            tint = finance.negative, // UX2-01
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

            // --- SECURITY SECTION ---
            Text(
                "Seguridad",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
            )
            SecuritySettingsCard(securityViewModel = securityViewModel)

            // --- SYSTEM RECOVERY SECTION ---
            Text(
                "Administración y Datos",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
            )

            FinanceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        Text(
                            "Base de Datos SQLite Local",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Toda la información financiera se almacena localmente en el dispositivo. Protégela activando el bloqueo de la app con PIN y biometría.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Restore seeds button
                    Surface(
                        onClick = { showResetSemillaConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), // UX2-01
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Restaurar datos semilla",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    "Carga los valores de prueba de Mayo 2026 del Excel original.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Delete database button
                    Surface(
                        onClick = { showDeleteAllConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(finance.negative.copy(alpha = 0.1f)), // UX2-01
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = finance.negative)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Borrar todos los datos",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = finance.negative) // UX2-01
                                )
                                Text(
                                    "Limpia todas las transacciones, metas y portafolios del sistema.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Modal Dialogue to add/edit custom categorization groups
    if (showAddCategoryDialog || selectedCategoryToEdit != null) {
        val editing = selectedCategoryToEdit
        AddEditCategoryFormDialog(
            category = editing,
            defaultType = if (tabIndex == 0) "EXPENSE" else "INCOME",
            onDismiss = {
                showAddCategoryDialog = false
                selectedCategoryToEditId = null
            },
            onSave = { name, type, colorHex, iconName, onError ->
                // ARQ2-05/ARQ2-06: el ViewModel valida colisiones de nombre (índice único) y hace
                // rename en cascada; el diálogo solo se cierra si no hubo error.
                val onResult: (String?) -> Unit = { error ->
                    if (error == null) {
                        showAddCategoryDialog = false
                        selectedCategoryToEditId = null
                    } else {
                        onError(error)
                    }
                }
                if (editing != null) {
                    viewModel.updateCategory(
                        editing.copy(name = name, type = type, colorHex = colorHex, iconName = iconName),
                        oldName = editing.name,
                        onResult = onResult
                    )
                } else {
                    viewModel.addCategory(name, type, colorHex, iconName, onResult)
                }
            }
        )
    }

    // UX2-04: confirmación previa al borrado de categoría — era la única acción destructiva de la
    // app sin confirmación (mismo patrón que el borrado de movimientos en MovimientosScreen).
    val categoryToDelete = categories.find { it.id == categoryToDeleteId }
    if (categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { categoryToDeleteId = null },
            title = { Text("Confirmación de Eliminación", fontWeight = FontWeight.Bold) },
            text = { Text("¿Estás seguro de que deseas eliminar permanentemente la categoría '${categoryToDelete.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        categoryToDeleteId = null
                        viewModel.deleteCategory(categoryToDelete) { _, msg ->
                            scope.launch {
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = finance.negative, contentColor = Color.White) // UX2-01
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDeleteId = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Seed confirm dialogue
    if (showResetSemillaConfirm) {
        AlertDialog(
            onDismissRequest = { showResetSemillaConfirm = false },
            title = { Text("¿Restaurar datos semilla?", fontWeight = FontWeight.Bold) },
            text = { Text("Esto sobrescribirá tus movimientos actuales con los balances originales del Excel (Ingresos: CLP 1.090.094, Gastos: CLP 748.825, Bolsa).") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetSemillaConfirm = false
                        runSensitive {
                            viewModel.resetToSeedData()
                            scope.launch {
                                snackbarHostState.showSnackbar("Datos semilla cargados con éxito!")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) // UX2-01
                ) {
                    Text("Cargar Semilla", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetSemillaConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Wipe confirm dialogue
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("¿Confirmas la eliminación total?", fontWeight = FontWeight.Bold, color = finance.negative) }, // UX2-01
            text = { Text("Esta acción es irreversible. Se borrará permanentemente todo tu historial, presupuestos y portafolio de acciones local.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllConfirm = false
                        runSensitive {
                            viewModel.clearAllData()
                            scope.launch {
                                snackbarHostState.showSnackbar("Todos los datos locales han sido eliminados.")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = finance.negative, contentColor = Color.White) // UX2-01
                ) {
                    Text("Confirmar Borrado", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Reautenticación obligatoria para acciones sensibles (solo si hay PIN configurado).
    reauthAction?.let { action ->
        ConfirmPinDialog(
            title = "Confirmar identidad",
            message = "Ingresa tu PIN para continuar con esta acción sensible.",
            confirmText = "Confirmar",
            onDismiss = { reauthAction = null },
            onConfirm = { pin, onError ->
                // SEC: nueva firma de verifyPin — PIN como CharArray (no queda en pool de Strings) y
                // mensaje de error provisto por el ViewModel (incluye el aviso de lockout).
                securityViewModel.verifyPin(pin.toCharArray()) { ok, error ->
                    if (ok) {
                        reauthAction = null
                        action()
                    } else {
                        onError(error ?: "PIN incorrecto.")
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditCategoryFormDialog(
    category: CategoryEntity?,
    defaultType: String,
    onDismiss: () -> Unit,
    // ARQ2-05/ARQ2-06: onSave recibe un onError para que el ViewModel reporte colisiones de nombre
    // y el diálogo muestre el mensaje sin cerrarse.
    onSave: (name: String, type: String, colorHex: String, iconName: String, onError: (String) -> Unit) -> Unit
) {
    // UX2-09: el formulario sobrevive a rotación/muerte de proceso con rememberSaveable.
    var name by rememberSaveable { mutableStateOf(category?.name ?: "") }
    var type by rememberSaveable { mutableStateOf(category?.type ?: defaultType) }
    val finance = LocalFinanceColors.current // UX2-01

    // Paleta de colores asignables a categorías. Se evitan tonos demasiado oscuros (Slate #37474F,
    // Cobalt #1565C0) porque se usan como tint de icono/barra sobre superficies oscuras del
    // Dashboard y quedaban con bajo contraste; se reemplazan por variantes más claras y legibles. UX-09.
    val palette = listOf(
        "#2EA043", // Verde (más claro que #107C41)
        "#E04A4A", // Rojo (más claro que #C62828)
        "#4D8DFF", // Azul acento (AccentBlue)
        "#FB8C00", // Naranja
        "#9C5BD6", // Púrpura (más claro que #6A1B9A)
        "#26A6B3", // Teal (más claro que #00838F)
        "#78909C", // Slate claro (antes #37474F)
        "#FF7043", // Naranja intenso
        "#E05A8A", // Rosa (más claro que #C2185B)
        "#5B9BFF"  // Cobalto claro (antes #1565C0)
    )
    var selectedColor by rememberSaveable { mutableStateOf(category?.colorHex ?: palette.first()) } // UX2-09

    var selectedIcon by rememberSaveable { mutableStateOf(category?.iconName ?: "Category") } // UX2-09

    var errorMsg by rememberSaveable { mutableStateOf("") } // UX2-09

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (category == null) "Agregar Categoría Nueva" else "Editar Categoría", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMsg.isNotEmpty()) {
                    // UX2-08: liveRegion para que TalkBack anuncie el error al aparecer.
                    Text(
                        errorMsg,
                        color = finance.negative, // UX2-01
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }

                // Header info
                Text(
                    "Configura el comportamiento y el diseño visual de este rubro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Category entry name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de Categoría") },
                    placeholder = { Text("Ej: Suscripciones, Uber, Ventas, etc.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("category_name_field"),
                    shape = RoundedCornerShape(8.dp)
                )

                // Type Tab switcher
                TabRow(
                    selectedTabIndex = if (type == "INCOME") 0 else 1,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = type == "INCOME",
                        onClick = { type = "INCOME" },
                        text = { Text("Ingresos", color = if (type == "INCOME") finance.success else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) } // UX2-01
                    )
                    Tab(
                        selected = type == "EXPENSE",
                        onClick = { type = "EXPENSE" },
                        text = { Text("Gastos", color = if (type == "EXPENSE") finance.negative else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) } // UX2-01
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Color Selection Row
                Text("Elegir Color", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                FlowRow(
                    // UX2-05: grupo de selección única para que TalkBack lo anuncie como radio group.
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    palette.forEachIndexed { index, cHex ->
                        val col = Color(android.graphics.Color.parseColor(cHex))
                        val isSelected = selectedColor == cHex
                        // UX2-05: celda exterior de 48dp (target táctil accesible) con semántica de
                        // RadioButton y etiqueta por índice; el círculo visual sigue siendo de 36dp.
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { selectedColor = cHex }
                                )
                                .semantics { contentDescription = "Color ${index + 1} de ${palette.size}" },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Icon selection grid
                Text("Elegir Ícono", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                FlowRow(
                    // UX2-05: grupo de selección única para que TalkBack lo anuncie como radio group.
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconMapper.availableIcons.forEach { (iName, iValue) ->
                        val isSelected = selectedIcon == iName
                        // UX2-05: celda exterior de 48dp con semántica de RadioButton etiquetada con
                        // el nombre del ícono; el cuadro visual sigue siendo de 36dp.
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { selectedIcon = iName }
                                )
                                .semantics { contentDescription = iName },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                // La semántica vive en la celda exterior; el ícono queda sin etiqueta.
                                Icon(
                                    imageVector = iValue,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        errorMsg = "El nombre es obligatorio."
                        return@Button
                    }
                    // ARQ2-05/ARQ2-06: el error del ViewModel (p. ej. nombre duplicado) se muestra
                    // en el propio diálogo; el cierre lo decide el call site solo si no hubo error.
                    onSave(name.trim(), type, selectedColor, selectedIcon) { error ->
                        errorMsg = error
                    }
                },
                modifier = Modifier.testTag("save_category_btn")
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
