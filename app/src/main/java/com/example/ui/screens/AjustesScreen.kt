package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.theme.ExcelDarkBlue
import com.example.ui.theme.ExcelGreen
import com.example.ui.theme.ExcelRed
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

    var tabIndex by remember { mutableStateOf(0) } // 0: Gastos, 1: Ingresos

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var selectedCategoryToEdit by remember { mutableStateOf<CategoryEntity?>(null) }
    var showResetSemillaConfirm by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    // Reautenticación para acciones sensibles: si hay PIN, exige confirmarlo antes de ejecutar.
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
                    selectedCategoryToEdit = null
                    showAddCategoryDialog = true
                },
                containerColor = ExcelDarkBlue,
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
                            text = { Text("Gastos (${expensesCats.size})", fontWeight = FontWeight.Bold, color = if (tabIndex == 0) ExcelRed else MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                        Tab(
                            selected = tabIndex == 1,
                            onClick = { tabIndex = 1 },
                            text = { Text("Ingresos (${incomeCats.size})", fontWeight = FontWeight.Bold, color = if (tabIndex == 1) ExcelGreen else MaterialTheme.colorScheme.onSurfaceVariant) }
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
                                      selectedCategoryToEdit = cat
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

                                    IconButton(
                                        onClick = {
                                            viewModel.deleteCategory(cat) { success, msg ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(msg)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp).testTag("delete_category_${cat.name}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar Categoría",
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
                                    .background(ExcelDarkBlue.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = ExcelDarkBlue)
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
                                    .background(ExcelRed.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = ExcelRed)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Borrar todos los datos",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = ExcelRed)
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
                selectedCategoryToEdit = null
            },
            onSave = { name, type, colorHex, iconName ->
                if (editing != null) {
                    viewModel.updateCategory(editing.copy(name = name, type = type, colorHex = colorHex, iconName = iconName))
                } else {
                    viewModel.addCategory(name, type, colorHex, iconName)
                }
                showAddCategoryDialog = false
                selectedCategoryToEdit = null
            }
        )
    }

    // Seed confirm dialogue
    if (showResetSemillaConfirm) {
        AlertDialog(
            onDismissRequest = { showResetSemillaConfirm = false },
            title = { Text("⚡ ¿Restaurar datos semilla?", fontWeight = FontWeight.Bold) },
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
                    colors = ButtonDefaults.buttonColors(containerColor = ExcelDarkBlue)
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
            title = { Text("🚨 ¿Confirmas la eliminación total?", fontWeight = FontWeight.Bold, color = ExcelRed) },
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
                    colors = ButtonDefaults.buttonColors(containerColor = ExcelRed)
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
                securityViewModel.verifyPin(pin) { ok ->
                    if (ok) {
                        reauthAction = null
                        action()
                    } else {
                        onError("PIN incorrecto.")
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
    onSave: (name: String, type: String, colorHex: String, iconName: String) -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var type by remember { mutableStateOf(category?.type ?: defaultType) }

    // Preselected beautiful material colors for client selections
    val palette = listOf(
        "#107C41", // Excel Green
        "#C62828", // Excel Red
        "#0078D4", // Excel Blue
        "#EF6C00", // Orange
        "#6A1B9A", // Purple
        "#00838F", // Teal
        "#37474F", // Slate
        "#D84315", // Deep Orange
        "#C2185B", // Deep Pink
        "#1565C0"  // Cobalt Blue
    )
    var selectedColor by remember { mutableStateOf(category?.colorHex ?: palette.first()) }

    var selectedIcon by remember { mutableStateOf(category?.iconName ?: "Category") }

    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (category == null) "➕ Agregar Categoría Nueva" else "📝 Editar Categoría", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = ExcelRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
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
                        text = { Text("Ingresos", color = if (type == "INCOME") ExcelGreen else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = type == "EXPENSE",
                        onClick = { type = "EXPENSE" },
                        text = { Text("Gastos", color = if (type == "EXPENSE") ExcelRed else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Color Selection Row
                Text("Elegir Color", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    palette.forEach { cHex ->
                        val col = Color(android.graphics.Color.parseColor(cHex))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    width = if (selectedColor == cHex) 3.dp else 0.dp,
                                    color = if (selectedColor == cHex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = cHex }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Icon selection grid
                Text("Elegir Ícono", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconMapper.availableIcons.forEach { (iName, iValue) ->
                        val isSelected = selectedIcon == iName
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
                                )
                                .clickable { selectedIcon = iName },
                            contentAlignment = Alignment.Center
                        ) {
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
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        errorMsg = "El nombre es obligatorio."
                        return@Button
                    }
                    onSave(name.trim(), type, selectedColor, selectedIcon)
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
