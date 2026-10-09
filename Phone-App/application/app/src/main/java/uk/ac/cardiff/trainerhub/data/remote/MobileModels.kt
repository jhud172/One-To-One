package uk.ac.cardiff.trainerhub.data.remote

data class MobileUser(
    val id: Long,
    val email: String,
    val username: String,
    val fullName: String,
    val role: MobileRole,
    val trainerVerified: Boolean,
)

enum class MobileRole {
    CLIENT,
    TRAINER,
    GYM_ADMIN,
    UNKNOWN,
}

data class AuthState(
    val loading: Boolean = true,
    val user: MobileUser? = null,
    val error: String? = null,
    val demoMode: Boolean = false,
)

data class HomeSummary(
    val headline: String,
    val todayCount: Int,
    val todayCompleted: Int,
    val actions: List<String>,
    val notifications: List<MobileNotification>,
    val relationshipCount: Int? = null,
)

data class DayItem(
    val id: String,
    val title: String,
    val notes: String,
    val type: String,
    val completed: Boolean,
)

data class CalendarDay(
    val date: String,
    val total: Int,
    val completed: Int,
)

data class TrainingLog(
    val id: String,
    val date: String,
    val comments: String,
    val durationMinutes: Int?,
)

data class MobileNotification(
    val id: String,
    val title: String,
    val message: String,
    val read: Boolean,
)

data class ChatMessage(
    val role: String,
    val content: String,
)

data class RoleItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val status: String,
)

data class MobileClientDetail(
    val name: String,
    val email: String,
    val logs: List<TrainingLog>,
)
