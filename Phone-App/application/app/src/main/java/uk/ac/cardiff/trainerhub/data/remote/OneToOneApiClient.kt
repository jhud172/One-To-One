package uk.ac.cardiff.trainerhub.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class OneToOneApiClient(
    private val sessionStore: SecureSessionStore,
) {
    suspend fun get(path: String): JSONObject = request("GET", path, null)

    suspend fun post(path: String, body: JSONObject = JSONObject()): JSONObject = request("POST", path, body)

    private suspend fun request(method: String, path: String, body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        require(path.startsWith('/') && !path.startsWith("//")) { "Use a path on the configured server." }
        val connection = URL(sessionStore.baseUrl() + path).openConnection() as HttpURLConnection
        try {
            // A moved endpoint must not silently forward credentials to a different server.
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")

            val token = sessionStore.token()
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }

            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val text = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrNull()
            if (statusCode !in 200..299) {
                throw MobileApiClientException(if (statusCode in 300..399) "The server endpoint has moved. Check the configured server before signing in again."
                    else json?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: "Request failed. Please try again.", statusCode)
            }
            json ?: throw MobileApiClientException("The server returned an invalid response. Please try again.", statusCode)
        } catch (_: IOException) {
            throw MobileApiClientException("Cannot connect to One To One. Check your connection. If you were saving a change, check its outcome before trying again.", 0)
        } finally {
            connection.disconnect()
        }
    }
}

class MobileApiClientException(
    message: String,
    val statusCode: Int,
) : Exception(message)
