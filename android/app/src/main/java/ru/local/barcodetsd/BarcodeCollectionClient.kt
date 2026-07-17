package ru.local.barcodetsd

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

internal sealed class SubmissionResult {
    data class Accepted(val sessionId: String, val documentRef: String) : SubmissionResult()
    data class InvalidRequest(val message: String) : SubmissionResult()
    data class AuthError(val message: String) : SubmissionResult()
    data class Conflict(val error: String, val message: String) : SubmissionResult()
    data class ServerError(val message: String) : SubmissionResult()
    data class ConnectionError(val message: String) : SubmissionResult()
}

internal class BarcodeCollectionClient {

    fun submit(
        serviceUrl: String,
        user: String,
        password: String,
        session: CollectionSession
    ): SubmissionResult {
        if (session.state != CollectionState.COMPLETED || session.lines.isEmpty()) {
            return SubmissionResult.InvalidRequest("Отправить можно только непустую завершённую сессию.")
        }

        var connection: HttpURLConnection? = null
        return try {
            connection = resolveConnection(serviceUrl)
            val requestBody = createRequestBody(session)
                .toByteArray(StandardCharsets.UTF_8)

            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(requestBody.size)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            if (user.isNotBlank() || password.isNotBlank()) {
                connection.setRequestProperty("Authorization", basicAuth(user, password))
            }

            connection.outputStream.use { output ->
                output.write(requestBody)
            }

            val code = connection.responseCode
            val response = readStream(
                if (code in 200..399) connection.inputStream else connection.errorStream
            )
            mapHttpResponse(code, response, session.sessionId)
        } catch (_: MalformedURLException) {
            SubmissionResult.InvalidRequest("Некорректный URL сервиса 1С.")
        } catch (e: IOException) {
            SubmissionResult.ConnectionError(e.localizedMessage ?: "Публикация 1С недоступна.")
        } catch (_: JSONException) {
            SubmissionResult.ServerError("Некорректный JSON от сервера 1С.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun createRequestBody(session: CollectionSession): String {
        val lines = JSONArray()
        session.lines.forEach { line ->
            lines.put(
                JSONObject()
                    .put("itemRef", line.itemRef)
                    .put("barcode", line.barcode)
                    .put("quantity", line.quantity.toBigDecimal())
            )
        }
        return JSONObject()
            .put("sessionId", session.sessionId)
            .put("lines", lines)
            .toString()
    }

    private fun mapHttpResponse(
        code: Int,
        response: String,
        requestSessionId: String
    ): SubmissionResult = when (code) {
        200 -> parseAcceptedResponse(response, requestSessionId)
        400 -> SubmissionResult.InvalidRequest(
            parseError(response).second ?: "1С отклонила данные сессии."
        )
        401, 403 -> SubmissionResult.AuthError(
            parseError(response).second ?: "Проверьте пользователя, пароль и права в 1С."
        )
        409 -> {
            val (error, message) = parseError(response)
            SubmissionResult.Conflict(
                error = error ?: "conflict",
                message = message ?: "1С отклонила сессию из-за конфликта."
            )
        }
        in 500..599 -> SubmissionResult.ServerError(
            parseError(response).second ?: "Сервер 1С вернул HTTP $code."
        )
        else -> SubmissionResult.ServerError(
            parseError(response).second ?: "Сервер 1С вернул HTTP $code."
        )
    }

    private fun parseAcceptedResponse(
        response: String,
        requestSessionId: String
    ): SubmissionResult {
        val json = JSONObject(response)
        val status = json.optString("status")
        val sessionId = json.optString("sessionId")
        val documentRef = json.optString("documentRef")
        if (status != "accepted" || sessionId != requestSessionId || documentRef.isBlank()) {
            return SubmissionResult.ServerError("1С вернула некорректный результат приёма сессии.")
        }
        return SubmissionResult.Accepted(sessionId, documentRef)
    }

    private fun parseError(response: String): Pair<String?, String?> =
        try {
            val json = JSONObject(response)
            val error = json.optString("error").ifBlank { null }
            val message = json.optString("message").ifBlank { error }
            error to message
        } catch (_: JSONException) {
            null to null
        }

    private fun basicAuth(user: String, password: String): String {
        val credentials = "$user:$password".toByteArray(StandardCharsets.UTF_8)
        return "Basic ${Base64.getEncoder().encodeToString(credentials)}"
    }

    private fun resolveConnection(serviceUrl: String): HttpURLConnection {
        val normalized = serviceUrl.trim().trimEnd('/')
        val endpoint = when {
            normalized.endsWith(SUBMIT_PATH) -> normalized
            normalized.endsWith(RESOLVE_PATH) -> normalized.removeSuffix(RESOLVE_PATH) + SUBMIT_PATH
            else -> normalized + SUBMIT_PATH
        }
        val url = URL(endpoint)
        if (url.protocol != "http" && url.protocol != "https") {
            throw MalformedURLException()
        }
        return url.openConnection() as HttpURLConnection
    }

    private fun readStream(stream: InputStream?): String {
        if (stream == null) {
            return ""
        }
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }

    private companion object {
        private const val SUBMIT_PATH = "/v1/barcode-collection-sessions"
        private const val RESOLVE_PATH = "/v1/barcode/resolve"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }
}
