package uk.ac.cardiff.trainerhub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.util.Patterns
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import uk.ac.cardiff.trainerhub.R
import uk.ac.cardiff.trainerhub.data.remote.AuthState
import uk.ac.cardiff.trainerhub.data.remote.CalendarDay
import uk.ac.cardiff.trainerhub.data.remote.ChatMessage
import uk.ac.cardiff.trainerhub.data.remote.DayItem
import uk.ac.cardiff.trainerhub.data.remote.HomeSummary
import uk.ac.cardiff.trainerhub.data.remote.MobileRole
import uk.ac.cardiff.trainerhub.data.remote.MobileClientDetail
import uk.ac.cardiff.trainerhub.data.remote.MobileUser
import uk.ac.cardiff.trainerhub.data.remote.OneToOneMobileRepository
import uk.ac.cardiff.trainerhub.data.remote.RoleItem
import uk.ac.cardiff.trainerhub.data.remote.TrainingLog
import uk.ac.cardiff.trainerhub.data.reminders.ReminderScheduler
import uk.ac.cardiff.trainerhub.ui.components.AppBackground
import uk.ac.cardiff.trainerhub.ui.components.EmptyStateCard
import uk.ac.cardiff.trainerhub.ui.components.PremiumButton
import uk.ac.cardiff.trainerhub.ui.components.PremiumCard
import uk.ac.cardiff.trainerhub.ui.components.SectionTitle
import uk.ac.cardiff.trainerhub.ui.components.StatCard
import uk.ac.cardiff.trainerhub.ui.components.StatusChip
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class NavItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun TrainerHubApp(
    mobileRepository: OneToOneMobileRepository,
    reminderScheduler: ReminderScheduler,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var restoringSession by remember { mutableStateOf(true) }
    val authState by mobileRepository.authState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        try {
            mobileRepository.restoreSession()
        } finally {
            restoringSession = false
        }
    }

    LaunchedEffect(Unit) {
        // The current shell uses remote records. Legacy local sample reminders are unrelated.
        reminderScheduler.update(false)
    }

    AppBackground {
        when {
            restoringSession -> LoadingScreen()
            authState.user == null -> PublicShell(mobileRepository, authState, snackbarHostState)
            else -> SignedInShell(mobileRepository, authState, snackbarHostState)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            BrandMark()
            Text("Opening your coaching space", style = MaterialTheme.typography.titleMedium)
            CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublicShell(
    mobileRepository: OneToOneMobileRepository,
    authState: AuthState,
    snackbarHostState: SnackbarHostState,
) {
    var route by rememberSaveable { mutableStateOf("welcome") }
    BackHandler(enabled = route != "welcome") { if (!authState.loading) route = "welcome" }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { BrandTitle() },
            )
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    NavItem("welcome", "Home", Icons.Outlined.Home),
                    NavItem("explore", "Explore", Icons.Outlined.FitnessCenter),
                    NavItem("login", "Login", Icons.AutoMirrored.Outlined.Login),
                    NavItem("signup", "Sign up", Icons.Outlined.PersonAdd),
                ).forEach { item ->
                    NavigationBarItem(
                        selected = route == item.key,
                        enabled = !authState.loading,
                        onClick = { mobileRepository.clearAuthError(); route = item.key },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (route) {
            "explore" -> ExploreScreen(padding)
            "login" -> LoginScreen(mobileRepository, authState, padding)
            "signup" -> SignupScreen(mobileRepository, authState, padding)
            else -> WelcomeScreen(mobileRepository, padding, sessionMessage = authState.error, onLogin = { mobileRepository.clearAuthError(); route = "login" }, onSignup = { mobileRepository.clearAuthError(); route = "signup" })
        }
    }
}

@Composable
private fun WelcomeScreen(
    mobileRepository: OneToOneMobileRepository,
    padding: PaddingValues,
    sessionMessage: String?,
    onLogin: () -> Unit,
    onSignup: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(padding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        sessionMessage?.let { message -> item { PremiumCard(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { Text(message) } } }
        item {
            PremiumCard(tonal = true) {
                Text("YOUR COACHING SPACE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Your next session. A clearer direction.", style = MaterialTheme.typography.headlineMedium)
                Text("Clients train with a verified coach. Trainers manage real clients. Gyms oversee their coaching team.")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                PremiumButton(onClick = onLogin, modifier = Modifier.weight(1f)) { Text("Login") }
                PremiumButton(onClick = onSignup, modifier = Modifier.weight(1f)) { Text("Sign up") }
            }
        }
        item {
            PremiumCard {
                Text("See how it works", fontWeight = FontWeight.SemiBold)
                Text("Explore a sample coaching workspace without creating an account.")
                TextButton(onClick = { scope.launch { mobileRepository.useDemoMode() } }) {
                    Text("Explore sample workspace")
                }
            }
        }
    }
}

@Composable
private fun ExploreScreen(padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(padding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SectionTitle("Explore One To One", "A mobile companion for clients, trainers and gyms") }
        items(
            listOf(
                "Verified trainers and one active trainer-client relationship.",
                "Day view, calendar, training logs and coach chat.",
                "Role-aware tools for clients, trainers and gym accounts.",
                "Payments and sensitive account work stay inside the hosted platform.",
            ),
        ) { text ->
            PremiumCard { Text(text) }
        }
    }
}

@Composable
private fun LoginScreen(
    mobileRepository: OneToOneMobileRepository,
    authState: AuthState,
    padding: PaddingValues,
) {
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val usernameError = if (submitted && username.isBlank()) "Username or email is required." else null
    val passwordError = if (submitted && password.isBlank()) "Password is required." else null
    val formErrors = listOfNotNull(usernameError, passwordError)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(padding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SectionTitle("Welcome back", "Return to your coaching space") }
        item {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Username or email") },
                enabled = !authState.loading,
                singleLine = true,
                isError = usernameError != null,
                supportingText = { usernameError?.let { Text(it) } },
            )
        }
        item {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                enabled = !authState.loading,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = passwordError != null,
                supportingText = { passwordError?.let { Text(it) } },
            )
        }
        if (formErrors.isNotEmpty()) {
            item { ValidationErrorCard("Complete login", formErrors) }
        }
        item {
            PremiumButton(
                onClick = {
                    submitted = true
                    if (username.isNotBlank() && password.isNotBlank()) {
                        scope.launch { mobileRepository.login(username.trim(), password) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.loading,
            ) {
                Text(if (authState.loading) "Signing in…" else "Log in")
            }
        }
        authState.error?.let { item { EmptyStateCard("Login issue", it) } }
    }
}

@Composable
private fun SignupScreen(
    mobileRepository: OneToOneMobileRepository,
    authState: AuthState,
    padding: PaddingValues,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var role by remember { mutableStateOf(MobileRole.CLIENT) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val firstNameError = if (submitted && firstName.isBlank()) "First name is required." else null
    val lastNameError = if (submitted && lastName.isBlank()) "Last name is required." else null
    val emailError = when {
        !submitted -> null
        email.isBlank() -> "Email is required."
        !isValidEmail(email.trim()) -> "Use a valid email address."
        else -> null
    }
    val usernameError = when {
        !submitted -> null
        username.isBlank() -> "Username is required."
        username.trim().length < 3 -> "Username must be at least 3 characters."
        else -> null
    }
    val passwordError = when {
        !submitted -> null
        password.isBlank() -> "Password is required."
        password.length < 8 -> "Password must be at least 8 characters."
        else -> null
    }
    val formErrors = listOfNotNull(firstNameError, lastNameError, emailError, usernameError, passwordError)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(padding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SectionTitle("Create account", "Choose the role that should drive the app experience") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RoleButton("Client", role == MobileRole.CLIENT, Modifier.weight(1f)) { role = MobileRole.CLIENT }
                RoleButton("Trainer", role == MobileRole.TRAINER, Modifier.weight(1f)) { role = MobileRole.TRAINER }
                RoleButton("Gym", role == MobileRole.GYM_ADMIN, Modifier.weight(1f)) { role = MobileRole.GYM_ADMIN }
            }
        }
        if (role == MobileRole.GYM_ADMIN) {
            item {
                PremiumCard {
                    Text("Apply for a gym account", style = MaterialTheme.typography.titleMedium)
                    Text("Gym accounts are created after your application has been reviewed. Complete the application on the One To One website.")
                    PremiumButton(onClick = { uriHandler.openUri(mobileRepository.gymApplicationUrl()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open gym application")
                    }
                }
            }
        } else {
            item {
                OutlinedTextField(
                    firstName,
                    { firstName = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("First name") },
                    singleLine = true,
                    isError = firstNameError != null,
                    supportingText = { firstNameError?.let { Text(it) } },
                )
            }
            item {
                OutlinedTextField(
                    lastName,
                    { lastName = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Last name") },
                    singleLine = true,
                    isError = lastNameError != null,
                    supportingText = { lastNameError?.let { Text(it) } },
                )
            }
            item {
                OutlinedTextField(
                    email,
                    { email = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = emailError != null,
                    supportingText = { emailError?.let { Text(it) } },
                )
            }
            item {
                OutlinedTextField(
                    username,
                    { username = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Username") },
                    singleLine = true,
                    isError = usernameError != null,
                    supportingText = { usernameError?.let { Text(it) } },
                )
            }
            item {
                OutlinedTextField(
                    password,
                    { password = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = passwordError != null,
                    supportingText = { passwordError?.let { Text(it) } },
                )
            }
            if (formErrors.isNotEmpty()) {
                item { ValidationErrorCard("Complete signup", formErrors) }
            }
            item {
                PremiumButton(
                    onClick = {
                        submitted = true
                        if (firstName.isNotBlank() &&
                            lastName.isNotBlank() &&
                            isValidEmail(email.trim()) &&
                            username.trim().length >= 3 &&
                            password.length >= 8
                        ) {
                            scope.launch {
                                mobileRepository.signup(
                                    role = role,
                                    email = email.trim(),
                                    username = username.trim(),
                                    password = password,
                                    firstName = firstName.trim(),
                                    lastName = lastName.trim(),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !authState.loading,
                ) {
                    Text("Create ${roleLabel(role)} account")
                }
            }
        }
        authState.error?.let { item { EmptyStateCard("Signup issue", it) } }
    }
}

@Composable
private fun RoleButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PremiumCard(
        modifier = modifier.clickable(onClick = onClick),
        tonal = selected,
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignedInShell(
    mobileRepository: OneToOneMobileRepository,
    authState: AuthState,
    snackbarHostState: SnackbarHostState,
) {
    val user = authState.user ?: return
    val destinations = destinationsFor(user.role)
    var route by rememberSaveable(user.id, user.role) { mutableStateOf(destinations.first().key) }
    var selectedDayValue by rememberSaveable(user.id) { mutableStateOf(LocalDate.now().toString()) }
    var selectedMonthValue by rememberSaveable(user.id) { mutableStateOf(YearMonth.now().toString()) }
    var selectedClientId by rememberSaveable(user.id) { mutableStateOf<String?>(null) }
    val selectedDay = LocalDate.parse(selectedDayValue)
    BackHandler(enabled = route != destinations.first().key) {
        route = when (route) { "day" -> "calendar"; "client" -> "clients"; else -> destinations.first().key }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BrandMark(modifier = Modifier.size(34.dp))
                        Column {
                        Text("One To One")
                        Text(user.fullName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                destinations.forEach { item ->
                    NavigationBarItem(
                        selected = route == item.key,
                        onClick = { route = item.key },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (route) {
            "day" -> DayScreen(mobileRepository, selectedDay, padding, authState.demoMode)
            "calendar" -> CalendarScreen(
                repository = mobileRepository,
                padding = padding,
                demoMode = authState.demoMode,
                month = YearMonth.parse(selectedMonthValue),
                onMonthChange = { selectedMonthValue = it.toString() },
                onOpenDay = { day ->
                    selectedDayValue = day.toString()
                    route = "day"
                },
            )
            "train" -> TrainingScreen(mobileRepository, padding, authState.demoMode)
            "chat" -> ChatScreen(mobileRepository, padding, authState.demoMode)
            "clients", "trainers", "requests" -> RoleListScreen(mobileRepository, user, route, padding, authState.demoMode,
                onOpenClient = { id -> selectedClientId = id; route = "client" })
            "client" -> selectedClientId?.let { id -> ClientRecordScreen(mobileRepository, id, padding, onBack = { route = "clients" }) }
            "more" -> MoreScreen(mobileRepository, user, padding)
            else -> HomeScreen(
                repository = mobileRepository,
                user = user,
                padding = padding,
                demoMode = authState.demoMode,
                onNavigate = { target ->
                    if (target == "day") {
                        selectedDayValue = LocalDate.now().toString()
                    }
                    route = target
                },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    repository: OneToOneMobileRepository,
    user: MobileUser,
    padding: PaddingValues,
    demoMode: Boolean,
    onNavigate: (String) -> Unit,
) {
    var home by remember { mutableStateOf<HomeSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(user, demoMode, retry) {
        error = null
        if (demoMode) {
            home = HomeSummary("Sample workspace: explore a training day and log. Changes stay in this preview.", 3, 1, listOf("Open day view", "Log training", "Open Charlie helper"), emptyList())
        } else {
            try {
                home = repository.home()
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                error = exception.message
            }
        }
    }
    ContentState(home, error, padding, onRetry = { retry++ }) { data ->
        item {
            PremiumCard(tonal = true) {
                Text("YOUR COACHING SPACE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(roleLabel(user.role) + " home", style = MaterialTheme.typography.headlineMedium)
                Text(data.headline)
                if (user.role == MobileRole.TRAINER) StatusChip(if (user.trainerVerified) "VERIFIED" else "NOT VERIFIED")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (data.relationshipCount != null) {
                    StatCard(if (user.role == MobileRole.TRAINER) "Client links" else "Trainers", data.relationshipCount.toString(), if (user.role == MobileRole.TRAINER) "All relationships" else "Linked to your gym", Modifier.weight(1f))
                    StatCard("Today", data.todayCount.toString(), "Your day plan", Modifier.weight(1f))
                } else {
                    StatCard("Today", data.todayCount.toString(), "Items planned", Modifier.weight(1f))
                    StatCard("Done", data.todayCompleted.toString(), "Completed", Modifier.weight(1f))
                }
            }
        }
        items(data.actions) { action ->
            ActionCard(action = action, onClick = { onNavigate(routeForAction(action, user.role)) })
        }
    }
}

@Composable
private fun DayScreen(repository: OneToOneMobileRepository, date: LocalDate, padding: PaddingValues, demoMode: Boolean) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<DayItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var completing by remember { mutableStateOf(false) }
    fun load() {
        scope.launch {
            error = null
            if (demoMode) {
                items = listOf(DayItem("demo", "Strength session", "Demo lower body work", "TASK", false))
            } else {
                try {
                    items = repository.day(date)
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    error = exception.message
                }
            }
        }
    }
    LaunchedEffect(demoMode, date) { load() }
    ContentState(items, error, padding, onRetry = { load() }) { data ->
        item { SectionTitle(if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.UK)), "Complete the work assigned for this day") }
        if (data.isEmpty()) item { EmptyStateCard("Clear day", "No tasks or sessions are scheduled.") }
        items(data) { item ->
            PremiumCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    StatusChip(if (item.completed) "COMPLETED" else item.type)
                }
                if (item.notes.isNotBlank()) Text(item.notes, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!item.completed && !demoMode) {
                    TextButton(enabled = !completing, onClick = {
                        completing = true
                        scope.launch {
                            try {
                                repository.complete(item.id)
                                error = null
                                load()
                            } catch (exception: Exception) {
                                if (exception is CancellationException) throw exception
                                error = exception.message ?: "Unable to complete this item. Please try again."
                            } finally {
                                completing = false
                            }
                        }
                    }) {
                        Text("Mark complete")
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarScreen(
    repository: OneToOneMobileRepository,
    padding: PaddingValues,
    demoMode: Boolean,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    var days by remember { mutableStateOf<List<CalendarDay>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(demoMode, month, retry) {
        error = null
        days = null
        if (demoMode) {
            days = (1..month.lengthOfMonth()).map { day -> CalendarDay(month.atDay(day).toString(), if (day == LocalDate.now().dayOfMonth) 2 else 0, 0) }
        } else {
            try {
                days = repository.month(month)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                error = exception.message
            }
        }
    }
    ContentState(days, error, padding, onRetry = { retry++ }) { data ->
        item {
            SectionTitle("Calendar", month.format(DateTimeFormatter.ofPattern("MMMM uuuu", Locale.UK)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Text("Previous") }
                TextButton(onClick = { onMonthChange(YearMonth.now()) }) { Text("This month") }
                TextButton(onClick = { onMonthChange(month.plusMonths(1)) }) { Text("Next") }
            }
        }
        if (data.none { it.total > 0 }) item { EmptyStateCard("A clear month", "No work is scheduled. Open any date to see its day plan.") }
        items(data, key = { it.date }) { day ->
            PremiumCard(
                modifier = Modifier.clickable { onOpenDay(LocalDate.parse(day.date)) },
                tonal = day.date == LocalDate.now().toString(),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.UK)), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(if (day.total == 0) "Clear day" else "${day.completed}/${day.total} complete", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TrainingScreen(repository: OneToOneMobileRepository, padding: PaddingValues, demoMode: Boolean) {
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<TrainingLog>?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var duration by rememberSaveable { mutableStateOf("45") }
    val durationMinutes = duration.toIntOrNull()
    val durationError = if (submitted && (durationMinutes == null || durationMinutes !in 1..1440)) "Enter a duration between 1 and 1440 minutes." else null
    val notesError = if (submitted && notes.isBlank()) "Add training notes before saving." else null
    fun load() {
        scope.launch {
            error = null
            if (demoMode) {
                logs = listOf(TrainingLog("demo", LocalDate.now().toString(), "Demo training log", 45))
            } else {
                try {
                    logs = repository.training()
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    error = exception.message
                }
            }
        }
    }
    LaunchedEffect(demoMode) { load() }
    ContentState(logs, error, padding, onRetry = { load() }) { data ->
        item { SectionTitle("Training logs", "Record your training and progress") }
        item {
            PremiumCard(tonal = true) {
                OutlinedTextField(
                    notes,
                    { notes = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Training notes") },
                    enabled = !saving,
                    isError = notesError != null,
                    supportingText = { notesError?.let { Text(it) } },
                )
                notesError?.let { ValidationErrorCard("Complete log", listOf(it)) }
                OutlinedTextField(
                    duration,
                    { duration = it.filter(Char::isDigit).take(4) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Duration (minutes)") },
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = durationError != null,
                    supportingText = { durationError?.let { Text(it) } },
                )
                PremiumButton(
                    onClick = {
                        submitted = true
                        if (notes.isNotBlank() && durationMinutes != null && durationMinutes in 1..1440 && !saving) {
                            saving = true
                            val pendingNotes = notes.trim()
                            scope.launch {
                                try {
                                    if (!demoMode) repository.addTrainingLog(pendingNotes, durationMinutes)
                                    notes = ""
                                    submitted = false
                                    error = null
                                    load()
                                } catch (exception: Exception) {
                                    if (exception is CancellationException) throw exception
                                    error = exception.message ?: "Unable to save your training. Please try again."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving && notesError == null && durationError == null,
                ) { Text("Save log") }
            }
        }
        items(data) { log ->
            PremiumCard {
                Text(log.date, fontWeight = FontWeight.SemiBold)
                Text(log.comments.ifBlank { "Training logged" })
                Text(log.durationMinutes?.let { "$it minutes" } ?: "Duration not recorded", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChatScreen(repository: OneToOneMobileRepository, padding: PaddingValues, demoMode: Boolean) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<ChatMessage>?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val draftError = if (submitted && draft.isBlank()) "Write a message before sending." else null
    fun load() {
        scope.launch {
            error = null
            if (demoMode) {
                messages = listOf(ChatMessage("ASSISTANT", "Demo coach ready."))
            } else {
                try {
                    messages = repository.chatHistory()
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    error = exception.message
                }
            }
        }
    }
    LaunchedEffect(demoMode) { load() }
    ContentState(messages, error, padding, onRetry = { load() }) { data ->
        item { SectionTitle("Charlie", "Your automated training assistant. Messages are separate from conversations with your trainer.") }
        items(data) { msg ->
            PremiumCard(tonal = msg.role.equals("ASSISTANT", true)) {
                Text(if (msg.role.equals("USER", true)) "You" else "Charlie", fontWeight = FontWeight.SemiBold)
                Text(msg.content)
            }
        }
        item {
            PremiumCard {
                OutlinedTextField(
                    draft,
                    { draft = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Message") },
                    enabled = !sending,
                    isError = draftError != null,
                    supportingText = { draftError?.let { Text(it) } },
                )
                draftError?.let { ValidationErrorCard("Complete message", listOf(it)) }
                PremiumButton(
                    onClick = {
                        submitted = true
                        if (draft.isNotBlank() && !sending) {
                            sending = true
                            val pendingMessage = draft.trim()
                            scope.launch {
                                try {
                                    if (!demoMode) repository.sendChat(pendingMessage)
                                    draft = ""
                                    submitted = false
                                    error = null
                                    load()
                                } catch (exception: Exception) {
                                    if (exception is CancellationException) throw exception
                                    error = exception.message ?: "Unable to send your message. Please try again."
                                } finally {
                                    sending = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sending && draftError == null,
                ) { Text("Send") }
            }
        }
    }
}

@Composable
private fun RoleListScreen(repository: OneToOneMobileRepository, user: MobileUser, route: String, padding: PaddingValues, demoMode: Boolean, onOpenClient: (String) -> Unit) {
    var items by remember { mutableStateOf<List<RoleItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    var search by rememberSaveable(route) { mutableStateOf("") }
    LaunchedEffect(user, route, demoMode, retry) {
        error = null
        items = null
        if (demoMode) {
            items = listOf(RoleItem("demo", "Demo relationship", "Sample coaching record", "ACTIVE"))
        } else {
            try {
                items = repository.roleItems(route)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                error = exception.message
            }
        }
    }
    ContentState(items, error, padding, onRetry = { retry++ }) { data ->
        item { SectionTitle(if (route == "requests") "Your gym application" else route.replaceFirstChar { it.uppercase() }, if (route == "requests") "Application status for this account. Reviews are managed by platform staff." else "Coaching relationships linked to your account") }
        item { OutlinedTextField(search, { search = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Search records") }, singleLine = true) }
        val visible = data.filter { search.isBlank() || listOf(it.title, it.subtitle, it.status).any { field -> field.contains(search.trim(), ignoreCase = true) } }
        if (data.isEmpty()) item { EmptyStateCard("Nothing here yet", if (route == "requests") "No application record is linked to this account." else "Your coaching relationships will appear here when they are linked to your account.") }
        else if (visible.isEmpty()) item { EmptyStateCard("No matching records", "Try another name or clear your search."); TextButton(onClick = { search = "" }) { Text("Clear search") } }
        items(visible, key = { it.id }) { row ->
            PremiumCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(row.title, fontWeight = FontWeight.SemiBold)
                        Text(row.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusChip(row.status.ifBlank { "ACTIVE" })
                }
                if (user.role == MobileRole.TRAINER && !demoMode && row.id.toLongOrNull()?.let { it > 0 } == true) {
                    TextButton(onClick = { onOpenClient(row.id) }) { Text("View client records") }
                }
            }
        }
    }
}

@Composable
private fun ClientRecordScreen(repository: OneToOneMobileRepository, clientId: String, padding: PaddingValues, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var detail by remember(clientId) { mutableStateOf<MobileClientDetail?>(null) }
    var error by remember(clientId) { mutableStateOf<String?>(null) }
    var retry by remember(clientId) { mutableStateOf(0) }
    LaunchedEffect(clientId, retry) {
        error = null
        try { detail = repository.clientDetail(clientId) }
        catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            error = exception.message
        }
    }
    ContentState(detail, error, padding, onRetry = { retry++ }) { data ->
        item { TextButton(onClick = onBack) { Text("Back to clients") } }
        item {
            PremiumCard(tonal = true) {
                Text(data.name, style = MaterialTheme.typography.headlineSmall)
                if (data.email.isNotBlank()) Text(data.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Coaching records", style = MaterialTheme.typography.titleMedium)
                Text("Open the full workspace to manage sessions, plans and client messages. Website sign-in is required.")
                PremiumButton(onClick = { uriHandler.openUri(repository.websiteUrl("/trainer/clients/$clientId")) }) { Text("Open full coaching workspace") }
            }
        }
        item { SectionTitle("Recent training", "Latest saved logs for this client") }
        if (data.logs.isEmpty()) item { EmptyStateCard("No training logs yet", "Saved training records will appear here.") }
        items(data.logs, key = { it.id }) { log ->
            PremiumCard {
                Text(log.date, fontWeight = FontWeight.SemiBold)
                Text(log.comments.ifBlank { "Training logged" })
                Text(log.durationMinutes?.let { "$it minutes" } ?: "Duration not recorded", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MoreScreen(repository: OneToOneMobileRepository, user: MobileUser, padding: PaddingValues) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var signingOut by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(padding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PremiumCard(tonal = true) {
                Text(user.fullName, style = MaterialTheme.typography.titleLarge)
                Text("${roleLabel(user.role)} account")
                Text(user.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            PremiumCard {
                Text("Account and support", fontWeight = FontWeight.SemiBold)
                Text("Manage your profile and preferences on the One To One website. Website sign-in is required.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { uriHandler.openUri(repository.websiteUrl("/profile")) }) { Text("Edit profile on website") }
                TextButton(onClick = { uriHandler.openUri(repository.websiteUrl("/preferences/edit")) }) { Text("Preferences on website") }
                if (user.role == MobileRole.TRAINER || user.role == MobileRole.GYM_ADMIN) {
                    val connectionPath = if (user.role == MobileRole.TRAINER) "/trainer/gyms" else "/gym/admin/trainers/affiliations"
                    TextButton(onClick = { uriHandler.openUri(repository.websiteUrl(connectionPath)) }) { Text("Gym connections on website") }
                }
                TextButton(onClick = { uriHandler.openUri(repository.websiteUrl("/support")) }) { Text("Contact support") }
            }
        }
        if (user.role == MobileRole.TRAINER) {
            item {
                PremiumCard {
                    Text("Professional review", fontWeight = FontWeight.SemiBold)
                    Text(
                        "View your latest review status, manage supporting documents and respond to reviewer questions on the website. Website sign-in is required.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { uriHandler.openUri(repository.websiteUrl("/trainer/verification")) }) {
                        Text("Open professional review on website")
                    }
                }
            }
        }
        item {
            PremiumCard {
                Text("Your privacy", fontWeight = FontWeight.SemiBold)
                Text("Sign out when you finish using a shared phone. Your training records stay with your account.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { uriHandler.openUri(repository.websiteUrl("/policies/privacy")) }) { Text("Read privacy policy") }
                TextButton(onClick = { uriHandler.openUri(repository.websiteUrl("/policies/terms")) }) { Text("Read terms") }
            }
        }
        item {
            PremiumButton(onClick = {
                signingOut = true
                scope.launch {
                    try { repository.logout() } finally { signingOut = false }
                }
            }, enabled = !signingOut, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                Text(if (signingOut) "Signing out…" else "Sign out")
            }
        }
    }
}

@Composable
private fun <T> ContentState(
    data: T?,
    error: String?,
    padding: PaddingValues,
    onRetry: (() -> Unit)? = null,
    content: LazyListScope.(T) -> Unit,
) {
    if (data == null && error == null) {
        LoadingScreen()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(padding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (error != null) {
            item {
                PremiumCard(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text("Unable to load", style = MaterialTheme.typography.titleMedium)
                    Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    onRetry?.let { retry -> TextButton(onClick = retry) { Text("Try again") } }
                }
            }
        }
        if (data != null) {
            content(data)
        }
    }
}

private fun screenPadding(padding: PaddingValues): PaddingValues =
    PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = padding.calculateTopPadding() + 16.dp,
        bottom = padding.calculateBottomPadding() + 24.dp,
    )

private fun destinationsFor(role: MobileRole): List<NavItem> = when (role) {
    MobileRole.UNKNOWN -> listOf(NavItem("more", "Account", Icons.Outlined.Settings))
    MobileRole.TRAINER -> listOf(
        NavItem("home", "Home", Icons.Outlined.Home),
        NavItem("clients", "Clients", Icons.Outlined.People),
        NavItem("calendar", "Calendar", Icons.Outlined.CalendarMonth),
        NavItem("chat", "Charlie", Icons.AutoMirrored.Outlined.Chat),
        NavItem("more", "Account", Icons.Outlined.Settings),
    )
    MobileRole.GYM_ADMIN -> listOf(
        NavItem("home", "Home", Icons.Outlined.Home),
        NavItem("trainers", "Trainers", Icons.Outlined.People),
        NavItem("requests", "Status", Icons.AutoMirrored.Outlined.ListAlt),
        NavItem("calendar", "Calendar", Icons.Outlined.CalendarMonth),
        NavItem("more", "Account", Icons.Outlined.Settings),
    )
    else -> listOf(
        NavItem("home", "Home", Icons.Outlined.Home),
        NavItem("calendar", "Calendar", Icons.Outlined.CalendarMonth),
        NavItem("train", "Train", Icons.Outlined.FitnessCenter),
        NavItem("chat", "Charlie", Icons.AutoMirrored.Outlined.Chat),
        NavItem("more", "Account", Icons.Outlined.Settings),
    )
}

private fun roleLabel(role: MobileRole): String = when (role) {
    MobileRole.TRAINER -> "Trainer"
    MobileRole.GYM_ADMIN -> "Gym"
    MobileRole.CLIENT -> "Client"
    MobileRole.UNKNOWN -> "One To One"
}

@Composable
private fun BrandTitle() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BrandMark(modifier = Modifier.size(34.dp))
        Text("One To One")
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier.height(48.dp)) {
    Image(
        painter = painterResource(id = R.drawable.one_to_one_logo),
        contentDescription = "One To One logo",
        modifier = modifier,
    )
}

@Composable
private fun ActionCard(action: String, onClick: () -> Unit) {
    PremiumCard(
        modifier = Modifier.clickable(onClick = onClick),
        tonal = false,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(action, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text("Open", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

internal fun routeForAction(action: String, role: MobileRole): String {
    val normalised = action.lowercase()
    return when {
        "day" in normalised -> "day"
        "trainer" in normalised && role == MobileRole.GYM_ADMIN -> "trainers"
        "client" in normalised && role == MobileRole.TRAINER -> "clients"
        ("request" in normalised || "application" in normalised) && role == MobileRole.GYM_ADMIN -> "requests"
        "account" in normalised -> "more"
        "log" in normalised || "train" in normalised -> "train"
        "charlie" in normalised || "coach" in normalised || "message" in normalised || "chat" in normalised -> "chat"
        "calendar" in normalised || "session" in normalised || "plan" in normalised -> "calendar"
        else -> "home"
    }
}

@Composable
private fun ValidationErrorCard(title: String, errors: List<String>) {
    PremiumCard(tonal = true) {
        Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
        errors.distinct().forEach { error ->
            Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun isValidEmail(email: String): Boolean {
    return Patterns.EMAIL_ADDRESS.matcher(email).matches()
}
