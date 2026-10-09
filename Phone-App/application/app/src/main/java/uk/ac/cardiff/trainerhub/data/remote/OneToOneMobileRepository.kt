package uk.ac.cardiff.trainerhub.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

class OneToOneMobileRepository(
    private val apiClient: OneToOneApiClient,
    private val sessionStore: SecureSessionStore,
) {
    private val _authState = MutableStateFlow(AuthState(loading = true, user = sessionStore.cachedUser()))
    val authState: StateFlow<AuthState> = _authState

    suspend fun restoreSession() {
        val token = sessionStore.token()
        if (token == null) {
            _authState.value = AuthState(loading = false)
            return
        }
        try {
            val user = parseUser(apiClient.get("/api/mobile/me").getJSONObject("user"))
            sessionStore.saveSession(token, user)
            _authState.value = AuthState(loading = false, user = user)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (exception is MobileApiClientException && exception.statusCode in listOf(401, 403)) {
                sessionStore.clearSession()
                _authState.value = AuthState(loading = false, error = "Your session has ended. Sign in again to continue.")
            } else {
                _authState.value = AuthState(loading = false, user = sessionStore.cachedUser(), error = exception.message)
            }
        }
    }

    suspend fun login(usernameOrEmail: String, password: String) {
        authenticate(
            "/api/mobile/auth/login",
            JSONObject()
                .put("usernameOrEmail", usernameOrEmail)
                .put("password", password)
                .put("deviceName", "Android"),
        )
    }

    suspend fun signup(role: MobileRole, email: String, username: String, password: String, firstName: String, lastName: String) {
        val path = when (role) {
            MobileRole.TRAINER -> "/api/mobile/auth/signup/trainer"
            MobileRole.GYM_ADMIN -> "/api/mobile/auth/signup/gym"
            else -> "/api/mobile/auth/signup/client"
        }
        authenticate(
            path,
            JSONObject()
                .put("email", email)
                .put("username", username)
                .put("password", password)
                .put("firstName", firstName)
                .put("lastName", lastName)
                .put("gymName", "$firstName $lastName")
                .put("deviceName", "Android"),
        )
    }

    suspend fun logout() {
        try {
            apiClient.post("/api/mobile/auth/logout")
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
        } finally {
            sessionStore.clearSession()
            _authState.value = AuthState(loading = false)
        }
    }

    fun useDemoMode() {
        _authState.value = AuthState(
            loading = false,
            user = MobileUser(0, "demo@onetone.local", "demo", "Demo User", MobileRole.CLIENT, false),
            demoMode = true,
        )
    }

    fun gymApplicationUrl(): String = sessionStore.baseUrl() + "/signup/gym"

    fun websiteUrl(path: String): String {
        require(path in setOf("/profile", "/preferences/edit", "/policies/privacy", "/policies/terms", "/support", "/trainer/gyms", "/trainer/verification", "/gym/admin/trainers/affiliations") || path.matches(Regex("/trainer/clients/[1-9][0-9]*")))
        return sessionStore.baseUrl() + path
    }

    fun clearAuthError() {
        _authState.value = _authState.value.copy(error = null)
    }

    suspend fun clientDetail(clientId: String): MobileClientDetail {
        require(clientId.toLongOrNull()?.let { it > 0 } == true) { "Choose a valid client record." }
        val json = apiClient.get("/api/mobile/trainer/clients/$clientId")
        val client = json.getJSONObject("client")
        return MobileClientDetail(
            name = "${client.optString("first_name")} ${client.optString("last_name")}".trim(),
            email = client.optString("email"),
            logs = parseLogs(json.optJSONArray("logs")),
        )
    }

    suspend fun home(): HomeSummary {
        val json = apiClient.get("/api/mobile/home")
        return HomeSummary(
            headline = json.optString("headline"),
            todayCount = json.optInt("todayCount"),
            todayCompleted = json.optInt("todayCompleted"),
            actions = json.optJSONArray("actions").strings(),
            notifications = parseNotifications(json.optJSONArray("notifications")),
            relationshipCount = when (_authState.value.user?.role) {
                MobileRole.TRAINER -> json.optJSONObject("stats")?.takeIf { it.has("clients") }?.optInt("clients")
                MobileRole.GYM_ADMIN -> json.optJSONObject("stats")?.takeIf { it.has("trainers") }?.optInt("trainers")
                else -> null
            },
        )
    }

    suspend fun day(date: LocalDate = LocalDate.now()): List<DayItem> {
        val items = apiClient.get("/api/mobile/calendar/day?date=$date").optJSONArray("items")
        val dayItems = mutableListOf<DayItem>()
        forEachJson(items) { row ->
            dayItems.add(
                DayItem(
                    id = row.optString("id"),
                    title = row.optString("title"),
                    notes = row.optString("notes"),
                    type = row.optString("type"),
                    completed = row.optBoolean("completed"),
                )
            )
        }
        return dayItems
    }

    suspend fun month(month: YearMonth = YearMonth.now()): List<CalendarDay> {
        val days = apiClient.get("/api/mobile/calendar/month?month=$month").optJSONArray("days")
        val calendarDays = mutableListOf<CalendarDay>()
        forEachJson(days) { row ->
            calendarDays.add(CalendarDay(row.optString("date"), row.optInt("total"), row.optInt("completed")))
        }
        return calendarDays
    }

    suspend fun complete(itemId: String) {
        apiClient.post("/api/mobile/day/tasks/$itemId/complete")
    }

    suspend fun training(): List<TrainingLog> {
        val logs = apiClient.get("/api/mobile/training").optJSONArray("logs")
        return parseLogs(logs)
    }

    suspend fun addTrainingLog(comments: String, durationMinutes: Int) {
        apiClient.post(
            "/api/mobile/training/logs",
            JSONObject()
                .put("date", LocalDate.now().toString())
                .put("comments", comments)
                .put("durationMinutes", durationMinutes)
                .put("moodBefore", 3)
                .put("moodAfter", 4)
                .put("confidence", 4),
        )
    }

    suspend fun chatHistory(): List<ChatMessage> {
        val messages = apiClient.get("/api/mobile/chat/history").optJSONArray("messages")
        val chatMessages = mutableListOf<ChatMessage>()
        forEachJson(messages) { row ->
            chatMessages.add(ChatMessage(row.optString("role"), row.optString("content")))
        }
        return chatMessages
    }

    suspend fun sendChat(message: String): String {
        return apiClient.post("/api/mobile/chat/message", JSONObject().put("message", message)).optString("reply")
    }

    suspend fun roleItems(route: String = "trainers"): List<RoleItem> {
        val user = _authState.value.user ?: return emptyList()
        val json = when (user.role) {
            MobileRole.TRAINER -> apiClient.get("/api/mobile/trainer/clients").optJSONArray("clients")
            MobileRole.GYM_ADMIN -> if (route == "requests") {
                apiClient.get("/api/mobile/gym/requests").optJSONArray("requests")
            } else {
                apiClient.get("/api/mobile/gym/trainers").optJSONArray("trainers")
            }
            else -> apiClient.get("/api/mobile/client/trainer").optJSONArray("trainers")
        }
        val items = mutableListOf<RoleItem>()
        forEachJson(json) { row ->
            val firstName = row.optString("first_name")
            val lastName = row.optString("last_name")
            val name = "$firstName $lastName".trim()
            val title = if (name.isBlank()) row.optString("gym_name", "One To One item") else name
            val status = if (row.optBoolean("trainer_verified")) "VERIFIED" else "PENDING"
            items.add(
                RoleItem(
                    id = row.optString("id"),
                    title = title,
                    subtitle = row.optString("email", row.optString("admin_email", "")),
                    status = row.optString("status", status),
                ),
            )
        }
        return items
    }

    suspend fun notifications(): List<MobileNotification> {
        return parseNotifications(apiClient.get("/api/mobile/notifications").optJSONArray("notifications"))
    }

    private suspend fun authenticate(path: String, body: JSONObject) {
        _authState.value = _authState.value.copy(loading = true, error = null)
        try {
            val json = apiClient.post(path, body)
            val token = json.getString("token")
            val user = parseUser(json.getJSONObject("user"))
            sessionStore.saveSession(token, user)
            _authState.value = AuthState(loading = false, user = user)
        } catch (exception: CancellationException) {
            _authState.value = _authState.value.copy(loading = false)
            throw exception
        } catch (exception: Exception) {
            _authState.value = AuthState(loading = false, error = exception.message)
        }
    }

    private fun parseUser(json: JSONObject): MobileUser {
        return MobileUser(
            id = json.optLong("id"),
            email = json.optString("email"),
            username = json.optString("username"),
            fullName = json.optString("fullName"),
            role = parseRole(json.optString("role")),
            trainerVerified = json.optBoolean("trainerVerified"),
        )
    }

    private fun parseNotifications(array: JSONArray?): List<MobileNotification> {
        val notifications = mutableListOf<MobileNotification>()
        forEachJson(array) { row ->
            notifications.add(
                MobileNotification(
                    id = row.optString("id"),
                    title = row.optString("title", "Notification"),
                    message = row.optString("message"),
                    read = !row.optString("read_at").isNullOrBlank() && row.optString("read_at") != "null",
                )
            )
        }
        return notifications
    }

    private fun parseLogs(array: JSONArray?): List<TrainingLog> {
        val logs = mutableListOf<TrainingLog>()
        forEachJson(array) { row ->
            logs.add(
                TrainingLog(
                    id = row.optString("id"),
                    date = row.optString("date"),
                    comments = row.optString("comments"),
                    durationMinutes = if (row.isNull("duration_minutes")) null else row.optInt("duration_minutes"),
                )
            )
        }
        return logs
    }

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        val values = mutableListOf<String>()
        for (index in 0 until length()) {
            values.add(optString(index))
        }
        return values
    }

    private fun forEachJson(array: JSONArray?, block: (JSONObject) -> Unit) {
        if (array == null) return
        for (index in 0 until array.length()) {
            block(array.optJSONObject(index) ?: JSONObject())
        }
    }

    private fun parseRole(roleName: String): MobileRole {
        return try {
            MobileRole.valueOf(roleName)
        } catch (_: Exception) {
            MobileRole.UNKNOWN
        }
    }
}
