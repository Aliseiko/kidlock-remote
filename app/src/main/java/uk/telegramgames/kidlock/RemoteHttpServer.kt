package uk.telegramgames.kidlock

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.security.SecureRandom

/**
 * Встроенный веб-сервер родительской панели (этап 2).
 * Слушает 0.0.0.0:8080 в процессе accessibility-сервиса; доступ из локальной сети
 * (и через туннель — этап 3). Все /api-эндпоинты, кроме /api/login, требуют Bearer-токен.
 * PIN и ограничение попыток — общие с экраном ТВ (DataRepository).
 *
 * Все ответы пишутся с UTF-8 (charset в Content-Type), иначе кириллица ломается.
 */
class RemoteHttpServer(
    context: Context,
    private val onLog: (String) -> Unit = {}
) : NanoHTTPD(DataRepository.HTTP_PORT) {

    private val appContext = context.applicationContext
    private val dataRepository = DataRepository.getInstance(appContext)
    private val usageStatsHelper = UsageStatsHelper(appContext)

    // token -> expiresAt (epoch millis)
    private val sessions = HashMap<String, Long>()
    private val random = SecureRandom()

    companion object {
        private const val SESSION_TTL_MS = 8 * 60 * 60 * 1000L // 8 hours
        private val TOKEN_CHARS = "abcdef0123456789".toCharArray()

        @Volatile
        private var runningInstance: RemoteHttpServer? = null

        /** Запуск, если включён в настройках и ещё не запущен. Безопасно вызывать многократно. */
        fun startIfEnabled(context: Context, onLog: (String) -> Unit = {}) {
            synchronized(this) {
                if (runningInstance != null) return
                val repo = DataRepository.getInstance(context.applicationContext)
                if (!repo.isHttpEnabled()) return
                val server = RemoteHttpServer(context, onLog)
                if (server.startSafe()) runningInstance = server
            }
        }

        fun stopIfRunning() {
            synchronized(this) {
                runningInstance?.stop()
                runningInstance = null
            }
        }
    }

    fun startSafe(): Boolean {
        return try {
            start(SOCKET_READ_TIMEOUT, false)
            onLog("RemoteHttpServer started on port ${DataRepository.HTTP_PORT}")
            true
        } catch (e: Exception) {
            onLog("RemoteHttpServer start failed: ${e.message}")
            false
        }
    }

    // --- Entry point ---

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.trimEnd('/')
        val method = session.method

        // Static panel
        if (method == Method.GET && (uri.isEmpty() || uri == "/" || uri == "/index.html")) {
            return serveAsset("www/index.html", "text/html")
        }
        if (method == Method.GET && uri == "/app.js") {
            return serveAsset("www/app.js", "application/javascript")
        }
        if (method == Method.GET && uri == "/style.css") {
            return serveAsset("www/style.css", "text/css")
        }

        if (!uri.startsWith("/api/")) {
            return jsonError(Response.Status.NOT_FOUND, "not_found")
        }

        // Login is the only unauthenticated endpoint
        if (uri == "/api/login" && method == Method.POST) {
            return handleLogin(session)
        }

        val token = bearerToken(session)
        if (token == null || !isTokenValid(token)) {
            return jsonError(Response.Status.UNAUTHORIZED, "unauthorized")
        }
        // Sliding expiry
        sessions[token] = System.currentTimeMillis() + SESSION_TTL_MS

        return try {
            when (uri.substringAfter("/api/")) {
                "status" -> apiStatus()
                "apps" -> apiApps()
                "limits" -> if (method == Method.POST) apiSetLimit(session) else apiLimits()
                "app-limits" -> when (method) {
                    Method.POST -> apiSetAppLimit(session)
                    Method.DELETE -> apiRemoveAppLimit(session)
                    else -> apiAppLimits()
                }
                "schedule" -> if (method == Method.POST) apiSetSchedule(session) else apiSchedule()
                "unlock" -> apiUnlock(session)
                "reset-today" -> apiResetToday()
                else -> jsonError(Response.Status.NOT_FOUND, "not_found")
            }
        } catch (e: Exception) {
            jsonError(Response.Status.BAD_REQUEST, "bad_request: ${e.message}")
        }
    }

    // --- Handlers ---

    private fun handleLogin(session: IHTTPSession): Response {
        if (dataRepository.isPinLockedOut()) {
            return jsonError(Response.Status.TOO_MANY_REQUESTS, "pin_lockout")
        }
        val body = readBody(session)
        val pin = body.optString("pin")
        if (dataRepository.verifyPin(pin)) {
            dataRepository.resetPinAttempts()
            val token = generateToken()
            sessions[token] = System.currentTimeMillis() + SESSION_TTL_MS
            return jsonOk(JSONObject().put("token", token))
        }
        dataRepository.incrementPinAttempts()
        if (dataRepository.getPinAttempts() >= 5) {
            dataRepository.setPinLockoutUntil(System.currentTimeMillis() + 60_000L)
            return jsonError(Response.Status.TOO_MANY_REQUESTS, "pin_lockout")
        }
        return jsonError(Response.Status.UNAUTHORIZED, "bad_pin")
    }

    private fun apiStatus(): Response {
        val remaining = usageStatsHelper.getRemainingTimeBreakdown(
            dataRepository.getDailyTimeLimitMinutes(),
            dataRepository.getAddedTimeMinutes()
        )
        val perApp = JSONArray()
        dataRepository.getAppLimits().forEach { (pkg, limit) ->
            val used = usageStatsHelper.getTodayUsageMinutesForPackage(pkg) ?: 0
            perApp.put(
                JSONObject()
                    .put("package", pkg)
                    .put("appName", resolveAppName(pkg))
                    .put("limitMinutes", limit)
                    .put("usedMinutes", used)
            )
        }
        val windows = JSONArray()
        dataRepository.getTimeWindows().forEach { windows.put(it.toJson()) }

        val body = JSONObject()
            .put("version", BuildConfig.VERSION_NAME)
            .put("mode", when {
                dataRepository.isScheduleEnabled() -> "schedule"
                dataRepository.isTimerEnabled() -> "timer"
                else -> "off"
            })
            .put("blockingEnabled", dataRepository.isBlockingEnabled())
            .put("dailyLimitMinutes", dataRepository.getDailyTimeLimitMinutes())
            .put("remaining", JSONObject()
                .put("totalMinutes", remaining.totalMinutes)
                .put("dailyRemaining", remaining.dailyRemaining)
                .put("bonusRemaining", remaining.bonusRemaining))
            .put("unlockedUntilTomorrow", dataRepository.isUnlockedUntilTomorrow())
            .put("scheduleEnabled", dataRepository.isScheduleEnabled())
            .put("scheduleBypassed", ScheduleManager.isScheduleBypassed(dataRepository))
            .put("timeWindows", windows)
            .put("appLimits", perApp)
            .put("addedTimeMinutes", dataRepository.getAddedTimeMinutes())
        return jsonOk(body)
    }

    private fun apiApps(): Response {
        val pm: PackageManager = appContext.packageManager
        val apps = JSONArray()
        pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { appInfo ->
                JSONObject()
                    .put("package", appInfo.packageName)
                    .put("appName", pm.getApplicationLabel(appInfo).toString())
            }
            .sortedBy { it.optString("appName").lowercase() }
            .forEach { apps.put(it) }
        return jsonOk(JSONObject().put("apps", apps))
    }

    private fun apiLimits(): Response {
        return jsonOk(
            JSONObject()
                .put("minutes", dataRepository.getDailyTimeLimitMinutes())
                .put("timerEnabled", dataRepository.isTimerEnabled())
        )
    }

    private fun apiSetLimit(session: IHTTPSession): Response {
        val body = readBody(session)
        val minutes = body.optInt("minutes", -1)
        if (minutes < 0) return jsonError(Response.Status.BAD_REQUEST, "invalid_minutes")
        dataRepository.setDailyTimeLimitMinutes(minutes)
        return jsonOk(JSONObject().put("minutes", minutes))
    }

    private fun apiAppLimits(): Response {
        val perApp = JSONArray()
        dataRepository.getAppLimits().forEach { (pkg, limit) ->
            val used = usageStatsHelper.getTodayUsageMinutesForPackage(pkg) ?: 0
            perApp.put(
                JSONObject()
                    .put("package", pkg)
                    .put("appName", resolveAppName(pkg))
                    .put("limitMinutes", limit)
                    .put("usedMinutes", used)
            )
        }
        return jsonOk(JSONObject().put("appLimits", perApp))
    }

    private fun apiSetAppLimit(session: IHTTPSession): Response {
        val body = readBody(session)
        val pkg = body.optString("package")
        val minutes = body.optInt("minutes", -1)
        if (pkg.isEmpty() || minutes <= 0) return jsonError(Response.Status.BAD_REQUEST, "invalid_params")
        dataRepository.setAppLimit(pkg, minutes)
        return apiAppLimits()
    }

    private fun apiRemoveAppLimit(session: IHTTPSession): Response {
        val pkg = session.parms["package"] ?: ""
        if (pkg.isEmpty()) return jsonError(Response.Status.BAD_REQUEST, "invalid_params")
        dataRepository.setAppLimit(pkg, null)
        return apiAppLimits()
    }

    private fun apiSchedule(): Response {
        val windows = JSONArray()
        dataRepository.getTimeWindows().forEach { windows.put(it.toJson()) }
        return jsonOk(
            JSONObject()
                .put("enabled", dataRepository.isScheduleEnabled())
                .put("timeWindows", windows)
        )
    }

    private fun apiSetSchedule(session: IHTTPSession): Response {
        val body = readBody(session)
        val enabled = body.optBoolean("enabled", dataRepository.isScheduleEnabled())
        if (enabled) {
            if (body.has("timeWindows")) {
                val arr = body.optJSONArray("timeWindows") ?: return jsonError(
                    Response.Status.BAD_REQUEST, "invalid_windows"
                )
                val windows = mutableListOf<TimeWindow>()
                for (i in 0 until arr.length()) {
                    val w = TimeWindow.fromJson(arr.getJSONObject(i)) ?: return jsonError(
                        Response.Status.BAD_REQUEST, "invalid_window"
                    )
                    if (!dataRepository.canAddTimeWindow(w, excludeId = w.id)) {
                        return jsonError(Response.Status.BAD_REQUEST, "overlap")
                    }
                    windows.add(w)
                }
                dataRepository.saveTimeWindows(windows)
            }
            if (dataRepository.getTimeWindows().isEmpty()) {
                return jsonError(Response.Status.BAD_REQUEST, "requires_window")
            }
        }
        dataRepository.setScheduleEnabled(enabled)
        return apiSchedule()
    }

    private fun apiUnlock(session: IHTTPSession): Response {
        val body = readBody(session)
        if (body.optBoolean("untilTomorrow", false)) {
            val minutesUntilMidnight = TimeManager.getMinutesUntilMidnight()
            dataRepository.setAddedTimeMinutes(minutesUntilMidnight)
            dataRepository.setUnlockedUntilTomorrow(true)
            return jsonOk(JSONObject().put("unlockedUntilTomorrow", true))
        }
        val minutes = body.optInt("minutes", 0)
        if (minutes <= 0) return jsonError(Response.Status.BAD_REQUEST, "invalid_minutes")
        dataRepository.addTimeMinutes(minutes)
        return jsonOk(JSONObject().put("addedMinutes", minutes))
    }

    private fun apiResetToday(): Response {
        dataRepository.resetDailyData()
        return jsonOk(JSONObject().put("reset", true))
    }

    // --- Helpers ---

    private fun resolveAppName(pkg: String): String {
        return try {
            val pm = appContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    private fun generateToken(): String {
        val sb = StringBuilder(32)
        repeat(32) { sb.append(TOKEN_CHARS[random.nextInt(TOKEN_CHARS.size)]) }
        return sb.toString()
    }

    private fun bearerToken(session: IHTTPSession): String? {
        val header = session.headers["authorization"] ?: return null
        return if (header.startsWith("Bearer ")) header.removePrefix("Bearer ").trim() else null
    }

    private fun isTokenValid(token: String): Boolean {
        val expiresAt = sessions[token] ?: return false
        if (System.currentTimeMillis() > expiresAt) {
            sessions.remove(token)
            return false
        }
        return true
    }

    private fun readBody(session: IHTTPSession): JSONObject {
        val map = HashMap<String, String>()
        session.parseBody(map)
        val raw = map["postData"] ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun serveAsset(path: String, mime: String): Response {
        return try {
            val bytes = appContext.assets.open(path).use { it.readBytes() }
            newChunkedResponse(Response.Status.OK, "$mime; charset=utf-8", ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            jsonError(Response.Status.NOT_FOUND, "asset_missing")
        }
    }

    private fun jsonOk(body: JSONObject): Response =
        newFixedLengthResponse(
            Response.Status.OK,
            "application/json; charset=utf-8",
            body.toString() + "\n"
        )

    private fun jsonError(status: Response.Status, code: String): Response =
        newFixedLengthResponse(
            status,
            "application/json; charset=utf-8",
            JSONObject().put("error", code).toString() + "\n"
        )
}
