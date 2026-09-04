@file:OptIn(ExperimentalFoundationApi::class)

package com.doffi4.doffisecure.ui.password

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

import androidx.compose.ui.graphics.Color
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import com.doffi4.doffisecure.domain.model.DomainUtils
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.model.SiteGroup
import com.doffi4.doffisecure.domain.model.groupBySite
import android.os.SystemClock
import android.app.Activity
import android.os.Build
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material.icons.filled.Build
import org.koin.androidx.compose.koinViewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.doffi4.doffisecure.ui.components.PasswordStrengthBadge

/**
 * Dev-mode pill that shows the live vault warm-up percentage. Extracted as its
 * own composable so frequent progress updates only recompose this tiny subtree.
 */
@Composable
private fun DevWarmupPill(
    show: Boolean,
    viewModel: PasswordViewModel,
) {
    if (!show) return
    val warmupProgress by viewModel.warmupProgress.collectAsState()

    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (warmupProgress >= 100) {
                    "Warm-up: 100% (ready)"
                } else {
                    "Warm-up: $warmupProgress%"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
/**
 * Walks up the Context wrapper chain to find the host [Activity]. Compose's
 * LocalContext is usually a ContextThemeWrapper, so a direct cast fails;
 * LocalActivity is not available in this activity-compose version.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Number of taps on the app title required to enable developer mode. */
private const val DEV_TAPS_REQUIRED = 6
/** Max gap (ms) between taps; a slower sequence restarts the counter. */
private const val DEV_TAP_WINDOW_MS = 1500L
/** Favicons enqueued immediately on list load (visible area + margin). */
private const val FAVICON_QUICK_WARM = 16
/** Remaining favicons are warmed in batches to avoid a network storm. */
private const val FAVICON_BATCH_SIZE = 8
/** Pause between warm-up batches (ms) so the main thread stays responsive. */
private const val FAVICON_BATCH_DELAY_MS = 90L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordScreen(
    modifier: Modifier = Modifier,
    viewModel: PasswordViewModel = koinViewModel(),
    onNavigateToDetail: (Long) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val totalPasswordsCount by viewModel.totalPasswordsCount.collectAsState()

    // Report the first interactive (fully-drawn) frame so startup-latency tooling
    // and baseline-profile generation can measure the real time to content
    // instead of just the Activity launch. Idempotent per composition.
    var reportedFullyDrawn by remember { mutableStateOf(false) }
    val fullyDrawnActivity = LocalContext.current.findActivity()
    LaunchedEffect(uiState) {
        if (!reportedFullyDrawn && uiState is PasswordUiState.Success) {
            reportedFullyDrawn = true
            fullyDrawnActivity?.reportFullyDrawn()
        }
    }

    // Exposed through the ViewModel, which delegates to the shared
    // DevModeManager singleton, so Settings changes stay in sync.
    val devModeEnabled by viewModel.devModeEnabled.collectAsState()
    val showDevPasswordCount by viewModel.showDevPasswordCount.collectAsState()
    val showDevWarmupProgress by viewModel.showDevWarmupProgress.collectAsState()
    val showPasswordStrength by viewModel.showPasswordStrength.collectAsState()
    val devPrefetchCount by viewModel.devPrefetchCount.collectAsState()
    var devTaps by remember { mutableIntStateOf(0) }
    var lastDevTapTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is PasswordUiEvent.ShowToast -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    // --- Developer-mode unlock dialog (6 taps on the "Decryptum" title) ---
    var showDevPasswordDialog by remember { mutableStateOf(false) }
    var devPasswordInput by remember { mutableStateOf("") }
    var devPasswordWrong by remember { mutableStateOf(false) }
    var devPasswordVisible by remember { mutableStateOf(false) }

    val submitDevPassword: () -> Unit = {
        if (viewModel.enableDeveloperMode(devPasswordInput)) {
            showDevPasswordDialog = false
            devPasswordWrong = false
            devPasswordInput = ""
        } else {
            devPasswordWrong = true
        }
    }

    if (showDevPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showDevPasswordDialog = false
                devPasswordWrong = false
                devPasswordInput = ""
            },
            title = {
                Text("Режим разработчика", fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Text(
                        text = "Введите дев-пароль, чтобы активировать режим разработчика.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = devPasswordInput,
                        onValueChange = {
                            devPasswordInput = it
                            if (devPasswordWrong) devPasswordWrong = false
                        },
                        label = { Text("Дев-пароль") },
                        singleLine = true,
                        isError = devPasswordWrong,
                        supportingText = if (devPasswordWrong) {
                            { Text("Неверный пароль, попробуйте ещё раз") }
                        } else {
                            null
                        },
                        visualTransformation = if (devPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { devPasswordVisible = !devPasswordVisible }) {
                                Icon(
                                    imageVector = if (devPasswordVisible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = if (devPasswordVisible) "Скрыть" else "Показать"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitDevPassword() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = submitDevPassword) { Text("Активировать") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDevPasswordDialog = false
                        devPasswordWrong = false
                        devPasswordInput = ""
                    }
                ) { Text("Отмена") }
            }
        )
    }

    val handleCopyUsername = remember {
        { username: String -> viewModel.copyUsername(username) }
    }
    val handleCopyPassword = remember {
        { passwordId: Long -> viewModel.copyPasswordById(passwordId) }
    }

    Scaffold(
        containerColor = Color.Transparent, // <-- Додай цей рядок!
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.clickable {
                            // 6 quick taps unlock developer mode (password dialog)
                            val now = SystemClock.uptimeMillis()
                            if (now - lastDevTapTime > DEV_TAP_WINDOW_MS) devTaps = 0
                            lastDevTapTime = now
                            devTaps++
                            if (devTaps >= DEV_TAPS_REQUIRED) {
                                devTaps = 0
                                // Already active -> toast; otherwise open the
                                // password dialog to activate developer mode.
                                if (viewModel.onDevTitleTapped()) {
                                    showDevPasswordDialog = true
                                }
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Decryptum", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    // Small bug icon: visible reminder that developer mode is active
                    if (devModeEnabled) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Developer mode",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.onShowAddDialog(true)
                },
                // Keep the FAB clear of the floating bottom-navigation capsule:
                // it floats just above it instead of being hidden underneath.
                modifier = Modifier.padding(bottom = 90.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, "Add")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    viewModel.searchPassword(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { 
                            searchQuery = ""
                            viewModel.searchPassword("") 
                        }) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            // Developer pill: live password count (enabled in Settings > Developer)
            AnimatedVisibility(visible = devModeEnabled && showDevPasswordCount) {
                Surface(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DEV · Passwords: $totalPasswordsCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Developer pill: live warm-up progress. Reads its own state so
            // the main screen (and the LazyColumn) are NOT recomposed on
            // every progress tick while the warm-up runs.
            DevWarmupPill(
                show = devModeEnabled && showDevWarmupProgress,
                viewModel = viewModel
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is PasswordUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is PasswordUiState.Error -> Text(
                        state.message, 
                        Modifier.align(Alignment.Center), 
                        color = MaterialTheme.colorScheme.error
                    )
                    is PasswordUiState.Success -> {
                        if (state.passwords.isEmpty()) {
                            Text("No passwords found", Modifier.align(Alignment.Center))
                        } else {
                            val groups = remember(state.passwords) { state.passwords.groupBySite() }
                            // Which sites are expanded (accounts shown). Kept at screen
                            // level so the account entries can be rebuilt on toggle.
                            var expandedDomains by remember { mutableStateOf(emptySet<String>()) }

                            // Warm the favicon caches in batches: the first groups are enqueued
                            // immediately, the rest in small chunks with short pauses.
                            // This way the very first scroll pass is largely cache hits
                            // (60 fps) instead of triggering loads while scrolling.
                            val context = LocalContext.current
                            LaunchedEffect(groups) {
                                val loader = context.imageLoader
                                val urls = groups.mapNotNull { it.faviconUrl.takeIf(String::isNotBlank) }
                                urls.take(FAVICON_QUICK_WARM).forEach { url ->
                                    loader.enqueue(
                                        ImageRequest.Builder(context).data(url).size(160).build()
                                    )
                                }
                                urls.drop(FAVICON_QUICK_WARM)
                                    .chunked(FAVICON_BATCH_SIZE)
                                    .forEach { chunk ->
                                        chunk.forEach { url ->
                                            loader.enqueue(
                                                ImageRequest.Builder(context).data(url).size(160).build()
                                            )
                                        }
                                        kotlinx.coroutines.delay(FAVICON_BATCH_DELAY_MS)
                                    }
                            }
                            // Flatten groups into top-level lazy entries: every account
                            // becomes its own LazyColumn item, so a site with 80+ rows
                            // is virtualized and stays smooth even when fully expanded.
                            val entries = remember(groups, expandedDomains) {
                                buildList {
                                    groups.forEach { group ->
                                        add(PasswordListEntry.Header(group))
                                        if (group.domain in expandedDomains) {
                                            group.accounts.forEachIndexed { accountIndex, pwd ->
                                                add(
                                                    PasswordListEntry.Account(
                                                        pwd = pwd,
                                                        isLastInGroup = accountIndex == group.accounts.lastIndex
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // LazyColumn: the prefetch strategy is a developer-tunable knob
                            // (Settings > Developer > "List prefetch"). Composing items
                            // ahead of the viewport removes the first-composition spike
                            // on fast flings (the top source of scroll micro-jank).
                            val listState = rememberLazyListState(
                                prefetchStrategy = remember(devPrefetchCount) {
                                    LazyListPrefetchStrategy(devPrefetchCount)
                                }
                            )

                            // ТАК МАЄ БУТИ:
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = 100.dp // Піднімає останні картки паролів вище капсули і прибирає нижню смужку!
                                ),
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                itemsIndexed(
                                    items = entries,
                                    key = { _, entry -> entry.key },
                                    contentType = { _, entry -> entry.contentType }
                                ) { index, entry ->
                                    when (entry) {
                                        is PasswordListEntry.Header -> {
                                            val group = entry.group
                                            val expanded = group.domain in expandedDomains
                                            SiteGroupHeader(
                                                group = group,
                                                expanded = expanded,
                                                onToggle = {
                                                    expandedDomains = if (expanded) {
                                                        expandedDomains - group.domain
                                                    } else {
                                                        expandedDomains + group.domain
                                                    }
                                                },
                                                modifier = Modifier.animateItem()
                                            )
                                        }
                                        is PasswordListEntry.Account -> {
                                            val isFirst =
                                                index == 0 || entries[index - 1] !is PasswordListEntry.Account
                                            AccountRow(
                                                pwd = entry.pwd,
                                                isFirst = isFirst,
                                                isLast = entry.isLastInGroup,
                                                onCopyUsername = handleCopyUsername,
                                                onCopyPassword = handleCopyPassword,
                                                onClick = { onNavigateToDetail(entry.pwd.id) },
                                                modifier = Modifier.animateItem()
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

        if (showAddDialog) {
            AddPasswordDialog(
                showStrength = showPasswordStrength,
                onDismiss = viewModel::onDismissAddDialog,
                onConfirm = { s, u, p ->
                    viewModel.addPassword(s, u, p)
                }
            )
        }
    }
}

@Composable
fun SiteGroupHeader(
    group: SiteGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLow
    // The header is the top edge of the group card: while expanded the bottom
    // corners stay square so the account rows visually continue the same card.
    val shape = if (expanded) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    } else {
        RoundedCornerShape(12.dp)
    }
    // Smooth arrow rotation while the account rows animate in/out below.
    val arrowAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "groupArrow",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(top = 8.dp)
            .clip(shape)
            .background(cardColor),
    ) {
        // Site header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SiteAvatar(displayName = group.displayName, faviconUrl = group.faviconUrl)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (group.accountCount == 1) "1 account" else "${group.accountCount} accounts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(24.dp)
                    .rotate(arrowAngle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Top-level entries of the password list. Every account is its own item so the
 * LazyColumn virtualizes it (only visible rows compose) instead of materializing
 * all accounts of an expanded site at once.
 */
private sealed interface PasswordListEntry {
    val key: String
    val contentType: String

    data class Header(val group: SiteGroup) : PasswordListEntry {
        override val key: String get() = "h:${group.domain}"
        override val contentType: String get() = "header"
    }

    data class Account(
        val pwd: Password,
        /** True when this is the last account of its group (bottom card rounding). */
        val isLastInGroup: Boolean = false,
    ) : PasswordListEntry {
        override val key: String get() = "a:${pwd.id}"
        override val contentType: String get() = "account"
    }
}

@Composable
private fun AccountRow(
    pwd: Password,
    isFirst: Boolean,
    isLast: Boolean,
    onCopyUsername: (String) -> Unit,
    onCopyPassword: (Long) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLow
    val cardShape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .then(
                if (isLast) {
                    // Bottom edge of the group card: rounded corners + the gap
                    // that visually separates this group from the next one.
                    Modifier
                        .clip(cardShape)
                        .background(cardColor)
                        .padding(bottom = 8.dp)
                } else {
                    Modifier.background(cardColor)
                }
            )
    ) {
        // Accounts below the first one get a hairline divider, indented to line
        // up with the group's text column (not the screen edge).
        if (!isFirst) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 52.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pwd.username,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "••••••••",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Copy username: person icon + mini copy badge (bottom-right corner)
            QuickCopyButton(
                icon = Icons.Default.Person,
                contentDescription = "Copy username",
                onClick = { onCopyUsername(pwd.username) }
            )
            Spacer(modifier = Modifier.width(4.dp))
            // Copy password: key icon + mini copy badge (bottom-right corner)
            QuickCopyButton(
                icon = Icons.Default.Key,
                contentDescription = "Copy password",
                onClick = { onCopyPassword(pwd.id) }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
    }
}

/**
 * Round quick-copy action: a main icon (person/key) with a small "copy" badge
 * pinned to the lower-right corner of the button.
 */
@Composable
private fun QuickCopyButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        // Mini copy badge in the lower-right corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(13.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(8.dp)
            )
        }
    }
}

@Composable
fun PasswordListItem(
    pwd: Password,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SiteAvatar(
                        displayName = pwd.service,
                        faviconUrl = DomainUtils.faviconUrl(
                            DomainUtils.extract(pwd.url ?: pwd.service)
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = pwd.service,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = pwd.username,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        )
    }
}

@Composable
fun AddPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
    showStrength: Boolean = false,
) {
    var service by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = service,
                    onValueChange = { service = it },
                    label = { Text("Service") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password") },
                    visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { passVisible = !passVisible }) {
                            Icon(
                                imageVector = if (passVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                )
                if (showStrength) {
                    Spacer(modifier = Modifier.height(4.dp))
                    PasswordStrengthBadge(
                        password = pass,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (service.isNotBlank() && pass.isNotBlank()) {
                        onConfirm(service, user, pass)
                    }
                    onDismiss()
                },
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun EditPasswordDialog(
    passwordToEdit: Password,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, String, String) -> Unit,
    showStrength: Boolean = false,
) {
    var service by remember { mutableStateOf(passwordToEdit.service) }
    var user by remember { mutableStateOf(passwordToEdit.username) }
    var pass by remember { mutableStateOf(passwordToEdit.password) }
    var passVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = service,
                    onValueChange = { service = it },
                    label = { Text("Service") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password") },
                    visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { passVisible = !passVisible }) {
                            Icon(
                                imageVector = if (passVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                )
                if (showStrength) {
                    Spacer(modifier = Modifier.height(4.dp))
                    PasswordStrengthBadge(
                        password = pass,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        passwordToEdit.id,
                        service,
                        user,
                        pass,
                    )
                    onDismiss()
                },
            ) { Text("Update") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
