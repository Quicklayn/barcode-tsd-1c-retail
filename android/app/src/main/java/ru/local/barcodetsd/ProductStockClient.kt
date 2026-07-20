package ru.local.barcodetsd

import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

internal sealed class ProductStockResult {
    data class Found(val itemRef: String, val quantity: BigDecimal) : ProductStockResult()
    data class InvalidInput(val message: String) : ProductStockResult()
    data class AuthError(val message: String) : ProductStockResult()
    data class Conflict(val error: String, val message: String) : ProductStockResult()
    data class ServerError(val message: String) : ProductStockResult()
    data class ConnectionError(val message: String) : ProductStockResult()
}

internal class ProductStockClient {

    fun resolve(
        serviceUrl: String,
        user: String,
        password: String,
        itemRef: String
    ): ProductStockResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = resolveConnection(serviceUrl)
            val requestBody = JSONObject()
                .put("itemRef", itemRef)
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
            mapHttpResponse(code, response, itemRef)
        } catch (e: MalformedURLException) {
            ProductStockResult.InvalidInput("Некорректный URL сервиса 1С.")
        } catch (e: IOException) {
            ProductStockResult.ConnectionError(e.localizedMessage ?: "Публикация 1С недоступна.")
        } catch (e: JSONException) {
            ProductStockResult.ServerError("Некорректный JSON от сервера 1С.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun mapHttpResponse(
        code: Int,
        response: String,
        requestItemRef: String
    ): ProductStockResult = when (code) {
        200 -> parseResolveResponse(response, requestItemRef)
        400 -> ProductStockResult.InvalidInput(
            parseErrorMessage(response) ?: "1С отклонила некорректную ссылку на товар."
        )
        401, 403 -> ProductStockResult.AuthError(
            parseErrorMessage(response) ?: "Проверьте пользователя и пароль 1С."
        )
        409 -> ProductStockResult.Conflict(
            parseErrorCode(response) ?: "conflict",
            parseErrorMessage(response) ?: "1С вернула конфликт настройки склада."
        )
        in 500..599 -> ProductStockResult.ServerError(
            parseErrorMessage(response) ?: "Сервер 1С вернул HTTP $code."
        )
        else -> ProductStockResult.ServerError(
            parseErrorMessage(response) ?: "Сервер 1С вернул HTTP $code."
        )
    }

    private fun parseResolveResponse(response: String, requestItemRef: String): ProductStockResult {
        val json = JSONObject(response)
        if (json.getString("status") != "found") {
            return ProductStockResult.ServerError("1С вернула неизвестный статус остатка.")
        }
        val itemRef = json.getString("itemRef")
        if (itemRef != requestItemRef) {
            return ProductStockResult.ServerError("1С вернула остаток для другого товара.")
        }
        val quantityValue = json.get("quantity")
        if (quantityValue !is Number) {
            return ProductStockResult.ServerError("1С вернула некорректное количество остатка.")
        }
        val quantity = try {
            BigDecimal(quantityValue.toString())
        } catch (_: NumberFormatException) {
            return ProductStockResult.ServerError("1С вернула некорректное количество остатка.")
        }
        if (quantity.scale() > MAX_FRACTION_DIGITS) {
            return ProductStockResult.ServerError("1С вернула остаток с недопустимой точностью.")
        }
        return ProductStockResult.Found(itemRef, quantity)
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

    private fun parseErrorCode(response: String): String? =
        try {
            JSONObject(response).optString("error").ifBlank { null }
        } catch (_: JSONException) {
            null
        }

    private fun basicAuth(user: String, password: String): String {
        val credentials = "$user:$password".toByteArray(StandardCharsets.UTF_8)
        return "Basic ${Base64.getEncoder().encodeToString(credentials)}"
    }

    private fun resolveConnection(serviceUrl: String): HttpURLConnection {
        val normalized = serviceUrl.trim().trimEnd('/')
        val endpoint = if (normalized.endsWith(RESOLVE_PATH)) {
            normalized
        } else {
            normalized + RESOLVE_PATH
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
        private const val RESOLVE_PATH = "/v1/product-stock/resolve"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val MAX_FRACTION_DIGITS = 3
    }
}

internal fun BigDecimal.toCompactDecimal(): String =
    stripTrailingZeros().toPlainString()
