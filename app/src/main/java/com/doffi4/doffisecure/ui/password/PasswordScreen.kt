@file:OptIn(ExperimentalFoundationApi::class)

package com.doffi4.doffisecure.ui.password

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role

import androidx.compose.ui.graphics.Color
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.model.SiteGroup
import com.doffi4.doffisecure.domain.model.groupBySite
import android.os.SystemClock
import android.app.Activity
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.doffi4.doffisecure.R
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
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.lock_warmup_progress, warmupProgress),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordScreen(
    modifier: Modifier = Modifier,
    viewModel: PasswordViewModel = koinViewModel(),
    onNavigateToSecurityCenter: () -> Unit = {},
    onNavigateToDetail: (Long) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val totalPasswordsCount by viewModel.totalPasswordsCount.collectAsState()
    val loadFavicons by viewModel.loadFavicons.collectAsState()

    // Report the first interactive (fully-drawn) frame so startup-latency tooling
    // and baseline-profile generation can measure the real time to content
    // instead of just the Activity launch. Idempotent per composition.
    var reportedFullyDrawn by remember { mutableStateOf(value = false) }
    val fullyDrawnActivity = LocalContext.current.findActivity()
    LaunchedEffect(uiState) {
        if (!reportedFullyDrawn && (uiState is PasswordUiState.Success)) {
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
    val allPasskeys by viewModel.allPasskeys.collectAsState()
    var devTaps by remember { mutableIntStateOf(0) }
    var lastDevTapTime by remember { mutableLongStateOf(0L) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is PasswordUiEvent.ShowToast -> {
                    snackbarHostState.showSnackbar(event.message.asString(context))
                }
            }
        }
    }

    // --- Developer-mode unlock dialog (6 taps on the "Decryptum" title) ---
    var showDevPasswordDialog by remember { mutableStateOf(value = false) }
    var devPasswordInput by remember { mutableStateOf("") }
    var devPasswordWrong by remember { mutableStateOf(value = false) }
    var devPasswordVisible by remember { mutableStateOf(value = false) }

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
                Text(stringResource(R.string.dev_dialog_title), fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.dev_dialog_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = devPasswordInput,
                        onValueChange = {
                            devPasswordInput = it
                            if (devPasswordWrong) devPasswordWrong = false
                        },
                        label = { Text(stringResource(R.string.dev_dialog_field_label)) },
                        singleLine = true,
                        isError = devPasswordWrong,
                        supportingText = if (devPasswordWrong) {
                            { Text(stringResource(R.string.dev_dialog_wrong_password)) }
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
                                    contentDescription = if (devPasswordVisible) stringResource(R.string.action_hide) else stringResource(R.string.action_show)
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
                TextButton(onClick = submitDevPassword) { Text(stringResource(R.string.action_activate)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDevPasswordDialog = false
                        devPasswordWrong = false
                        devPasswordInput = ""
                    }
                ) { Text(stringResource(R.string.action_cancel)) }
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
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.clickable {
                            // 6 quick taps unlock developer mode (password dialog)
                            val now = SystemClock.uptimeMillis()
                            if ((now - lastDevTapTime) > DEV_TAP_WINDOW_MS) devTaps = 0
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
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSecurityCenter) {
                        Icon(Icons.Default.Security, stringResource(R.string.security_center_title))
                    }
                    // Small bug icon: visible reminder that developer mode is active
                    if (devModeEnabled) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = stringResource(R.string.section_developer),
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
                    viewModel.onShowAddDialog(show = true)
                },
                // Keep the FAB clear of the floating bottom-navigation capsule:
                // it floats just above it instead of being hidden underneath.
                modifier = Modifier.padding(bottom = 90.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.action_add))
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
                placeholder = { Text(stringResource(R.string.search_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { 
                                searchQuery = ""
                                viewModel.searchPassword("") 
                            },
                        ) {
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
                            text = stringResource(R.string.dev_pill_passwords_count, totalPasswordsCount),
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
                        state.message.asString(), 
                        Modifier.align(Alignment.Center), 
                        color = MaterialTheme.colorScheme.error
                    )
                    is PasswordUiState.Success -> {
                        if (state.passwords.isEmpty()) {
                            Text(
                                stringResource(if (searchQuery.isBlank()) R.string.vault_empty_guidance else R.string.empty_search_results),
                                Modifier.align(Alignment.Center).padding(horizontal = 24.dp, vertical = 16.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val groups = remember(state.passwords) { state.passwords.groupBySite() }
                            // Which sites are expanded (accounts shown). Kept at screen
                            // level so the account entries can be rebuilt on toggle.
                            var expandedDomains by remember { mutableStateOf(emptySet<String>()) }

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
                                                loadFavicons = loadFavicons,
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
                                                (index == 0) || (entries[index - 1] !is PasswordListEntry.Account)
                                            val hasPasskey = remember(allPasskeys, entry.pwd.id) {
                                                allPasskeys.any {
                                                    (it.linkedPasswordId == entry.pwd.id) ||
                                                        (it.userName.equals(entry.pwd.username, ignoreCase = true) && it.rpId.contains(entry.pwd.service, ignoreCase = true))
                                                }
                                            }
                                            AccountRow(
                                                pwd = entry.pwd,
                                                hasPasskey = hasPasskey,
                                                isFirst = isFirst,
                                                isLast = entry.isLastInGroup,
                                                onCopyUsername = handleCopyUsername,
                                                onCopyPassword = handleCopyPassword,
                                                onClick = { onNavigateToDetail(entry.pwd.id) },
                                                modifier = Modifier.animateItem(),
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
            PasswordEntrySheet(
                showStrength = showPasswordStrength,
                onDismiss = viewModel::onDismissAddDialog,
                onSave = viewModel::addPassword,
                onGenerate = viewModel::generatePasswordForEntry,
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
    loadFavicons: Boolean = true,
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
            SiteAvatar(
                displayName = group.displayName,
                faviconUrl = group.faviconUrl,
                isLocalNetwork = group.parsedDomain.isLocalNetwork,
                apexDomain = group.parsedDomain.apexDomain,
                enabled = loadFavicons
            )
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
    hasPasskey: Boolean = false,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pwd.username,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (hasPasskey) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = stringResource(R.string.passkey_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = if (pwd.password.isEmpty() && hasPasskey) stringResource(R.string.passkey_login_only) else "••••••••",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Copy username: person icon + mini copy badge (bottom-right corner)
                QuickCopyButton(
                    icon = Icons.Default.Person,
                    contentDescription = stringResource(R.string.cd_copy_username),
                ) { onCopyUsername(pwd.username) }
                Spacer(modifier = Modifier.width(4.dp))
                // Copy password (only if password exists)
                if (pwd.password.isNotEmpty()) {
                    QuickCopyButton(
                        icon = Icons.Default.Key,
                        contentDescription = stringResource(R.string.cd_copy_password),
                    ) { onCopyPassword(pwd.id) }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.view_details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
        }
    }
    }
}

/**
 * Quick-copy action button: a main icon (person/key) with a small "copy" badge
 * pinned cleanly in the lower-right corner of a rounded squircle button.
 */
@Composable
private fun QuickCopyButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .clickable(role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .offset(x = (-1.5).dp, y = (-1.5).dp)
        )
        // Mini copy badge in the lower-right corner (never clipped by squircle shape)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 1.5.dp, bottom = 1.5.dp)
                .size(14.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(8.dp)
            )
        }
    }
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
        title = { Text(stringResource(R.string.edit_password_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = service,
                    onValueChange = { service = it },
                    label = { Text(stringResource(R.string.field_service)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text(stringResource(R.string.field_username)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text(stringResource(R.string.field_password)) },
                    visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { passVisible = !passVisible }) {
                            Icon(
                                imageVector = if (passVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(if (passVisible) R.string.action_hide else R.string.action_show),
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
                enabled = service.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
            ) { Text(stringResource(R.string.action_update)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
