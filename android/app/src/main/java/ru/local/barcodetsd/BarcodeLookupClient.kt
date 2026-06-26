package ru.local.barcodetsd

import android.util.Base64
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

internal sealed class LookupResult {
    data class Found(val barcode: String, val name: String) : LookupResult()
    data class NotFound(val barcode: String, val message: String?) : LookupResult()
    data class Ambiguous(val barcode: String, val names: List<String>) : LookupResult()
    data class InvalidInput(val message: String) : LookupResult()
    data class AuthError(val message: String) : LookupResult()
    data class ServerError(val message: String) : LookupResult()
    data class ConnectionError(val message: String) : LookupResult()
}

internal class BarcodeLookupClient {

    fun resolve(
        serviceUrl: String,
        user: String,
        password: String,
        barcode: String
    ): LookupResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(resolveEndpoint(serviceUrl)).openConnection() as HttpURLConnection)
            val requestBody = JSONObject()
                .put("barcode", barcode)
                .toString()
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
            mapHttpResponse(code, response, barcode)
        } catch (e: MalformedURLException) {
            LookupResult.InvalidInput("Некорректный URL сервиса 1С.")
        } catch (e: IOException) {
            LookupResult.ConnectionError(e.localizedMessage ?: "Публикация 1С недоступна.")
        } catch (e: JSONException) {
            LookupResult.ServerError("Некорректный JSON от сервера 1С.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun mapHttpResponse(code: Int, response: String, requestBarcode: String): LookupResult =
        when (code) {
            200 -> parseResolveResponse(response, requestBarcode)
            400 -> LookupResult.InvalidInput(
                parseErrorMessage(response) ?: "1С отклонила пустой или некорректный штрихкод."
            )
            401, 403 -> LookupResult.AuthError(
                parseErrorMessage(response) ?: "Проверьте пользователя и пароль 1С."
            )
            in 500..599 -> LookupResult.ServerError(
                parseErrorMessage(response) ?: "Сервер 1С вернул HTTP $code."
            )
            else -> LookupResult.ServerError(
                parseErrorMessage(response) ?: "Сервер 1С вернул HTTP $code."
            )
        }

    private fun parseResolveResponse(response: String, requestBarcode: String): LookupResult {
        val json = JSONObject(response)
        val status = json.getString("status")
        val barcode = json.optString("barcode").ifBlank { requestBarcode }
        val matches = json.optJSONArray("matches") ?: JSONArray()

        return when (status) {
            "found" -> {
                val name = matches.optJSONObject(0)?.optString("name").orEmpty()
                if (name.isBlank()) {
                    LookupResult.ServerError("1С вернула found без наименования товара.")
                } else {
                    LookupResult.Found(barcode, name)
                }
            }
            "not_found" -> LookupResult.NotFound(
                barcode,
                json.optString("message").ifBlank { null }
            )
            "ambiguous" -> LookupResult.Ambiguous(barcode, readNames(matches))
            else -> LookupResult.ServerError("1С вернула неизвестный статус: $status.")
        }
    }

    private fun readNames(matches: JSONArray): List<String> {
        val names = mutableListOf<String>()
        for (index in 0 until matches.length()) {
            val name = matches.optJSONObject(index)?.optString("name").orEmpty()
            if (name.isNotBlank()) {
                names.add(name)
            }
        }
        return names
    }

    private fun parseErrorMessage(response: String): String? =
        try {
            val json = JSONObject(response)
            json.optString("message").ifBlank {
                json.optString("error").ifBlank { null }
            }
        } catch (_: JSONException) {
            null
        }

    private fun basicAuth(user: String, password: String): String {
        val credentials = "$user:$password".toByteArray(StandardCharsets.UTF_8)
        return "Basic ${Base64.encodeToString(credentials, Base64.NO_WRAP)}"
    }

    private fun resolveEndpoint(serviceUrl: String): String {
        val normalized = serviceUrl.trim().trimEnd('/')
        return if (normalized.endsWith(RESOLVE_PATH)) {
            normalized
        } else {
            normalized + RESOLVE_PATH
        }
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
        private const val RESOLVE_PATH = "/v1/barcode/resolve"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }
}
