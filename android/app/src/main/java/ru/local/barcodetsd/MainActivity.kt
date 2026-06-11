package ru.local.barcodetsd

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Base64
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = BarcodeLookupClient()

    private lateinit var prefs: SharedPreferences
    private lateinit var serviceUrlInput: EditText
    private lateinit var userInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var barcodeInput: EditText
    private lateinit var lookupButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusView: TextView
    private lateinit var productNameView: TextView
    private lateinit var detailView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("connection", MODE_PRIVATE)
        setContentView(createContentView())
        loadSettings()
        bindActions()
        showIdle()
        barcodeInput.requestFocus()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun createContentView(): View {
        serviceUrlInput = EditText(this).apply {
            hint = "http://server/infobase/hs/BarcodeTSD"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        userInput = EditText(this).apply {
            hint = "Пользователь 1С (необязательно)"
            isSingleLine = true
        }
        passwordInput = EditText(this).apply {
            hint = "Пароль (необязательно)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        barcodeInput = EditText(this).apply {
            hint = "Штрихкод"
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
            setSelectAllOnFocus(true)
        }
        lookupButton = Button(this).apply {
            text = "Найти"
        }
        progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        statusView = TextView(this).apply {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        }
        productNameView = TextView(this).apply {
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
        }
        detailView = TextView(this).apply {
            textSize = 16f
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        content.addView(title("Barcode TSD"), matchWrap(dp(16)))
        addLabeledField(content, "URL сервиса 1С", serviceUrlInput)
        addLabeledField(content, "Авторизация", userInput)
        content.addView(passwordInput, matchWrap(dp(12)))
        addLabeledField(content, "Штрихкод", barcodeInput)
        content.addView(lookupButton, matchWrap(dp(12)))
        content.addView(progressBar, wrapWrap(dp(12)))
        content.addView(statusView, matchWrap(dp(8)))
        content.addView(productNameView, matchWrap(dp(8)))
        content.addView(detailView, matchWrap(0))

        return ScrollView(this).apply {
            addView(content)
        }
    }

    private fun bindActions() {
        lookupButton.setOnClickListener { startLookup() }
        barcodeInput.setOnEditorActionListener { _, actionId, event ->
            val imeSubmit = actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEND
            val enterUp = event != null &&
                event.action == KeyEvent.ACTION_UP &&
                event.keyCode == KeyEvent.KEYCODE_ENTER
            if (imeSubmit || enterUp) {
                startLookup()
                true
            } else {
                false
            }
        }
    }

    private fun startLookup() {
        val serviceUrl = serviceUrlInput.text.toString().trim()
        val barcode = barcodeInput.text.toString().trim()

        if (serviceUrl.isEmpty()) {
            showResult(LookupResult.InvalidInput("Укажите URL сервиса 1С."))
            return
        }
        if (barcode.isEmpty()) {
            showResult(LookupResult.InvalidInput("Введите штрихкод."))
            return
        }
        if (barcode.length > 200) {
            showResult(LookupResult.InvalidInput("Штрихкод длиннее 200 символов."))
            return
        }

        saveSettings()
        setLoading(true)
        statusView.text = "Запрос к 1С"
        productNameView.text = ""
        detailView.text = ""

        val user = userInput.text.toString()
        val password = passwordInput.text.toString()
        executor.execute {
            val result = client.resolve(serviceUrl, user, password, barcode)
            mainHandler.post {
                setLoading(false)
                showResult(result)
                barcodeInput.selectAll()
            }
        }
    }

    private fun showIdle() {
        statusView.text = "Готово"
        productNameView.text = ""
        detailView.text = "Введите штрихкод вручную или отсканируйте его с Enter."
    }

    private fun showResult(result: LookupResult) {
        when (result) {
            is LookupResult.Found -> {
                statusView.text = "Найдено"
                productNameView.text = result.name
                detailView.text = "Штрихкод: ${result.barcode}"
            }
            is LookupResult.NotFound -> {
                statusView.text = "Не найдено"
                productNameView.text = ""
                detailView.text = result.message ?: "Товар с таким штрихкодом не найден."
            }
            is LookupResult.Ambiguous -> {
                statusView.text = "Неоднозначно"
                productNameView.text = ""
                detailView.text = if (result.names.isEmpty()) {
                    "1С вернула несколько совпадений."
                } else {
                    result.names.joinToString(separator = "\n")
                }
            }
            is LookupResult.InvalidInput -> {
                statusView.text = "Некорректный ввод"
                productNameView.text = ""
                detailView.text = result.message
            }
            is LookupResult.AuthError -> {
                statusView.text = "Ошибка авторизации"
                productNameView.text = ""
                detailView.text = result.message
            }
            is LookupResult.ServerError -> {
                statusView.text = "Ошибка сервера"
                productNameView.text = ""
                detailView.text = result.message
            }
            is LookupResult.ConnectionError -> {
                statusView.text = "Ошибка подключения"
                productNameView.text = ""
                detailView.text = result.message
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        lookupButton.isEnabled = !isLoading
    }

    private fun loadSettings() {
        serviceUrlInput.setText(
            prefs.getString("serviceUrl", getString(R.string.default_service_url))
        )
        userInput.setText(prefs.getString("user", ""))
    }

    private fun saveSettings() {
        prefs.edit()
            .putString("serviceUrl", serviceUrlInput.text.toString().trim())
            .putString("user", userInput.text.toString())
            .apply()
    }

    private fun addLabeledField(container: LinearLayout, label: String, field: EditText) {
        container.addView(label(label), matchWrap(dp(4)))
        container.addView(field, matchWrap(dp(12)))
    }

    private fun title(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        }

    private fun label(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 14f
        }

    private fun matchWrap(bottomMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            this.bottomMargin = bottomMargin
        }

    private fun wrapWrap(bottomMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            this.bottomMargin = bottomMargin
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

private sealed class LookupResult {
    data class Found(val barcode: String, val name: String) : LookupResult()
    data class NotFound(val barcode: String, val message: String?) : LookupResult()
    data class Ambiguous(val barcode: String, val names: List<String>) : LookupResult()
    data class InvalidInput(val message: String) : LookupResult()
    data class AuthError(val message: String) : LookupResult()
    data class ServerError(val message: String) : LookupResult()
    data class ConnectionError(val message: String) : LookupResult()
}

private class BarcodeLookupClient {

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
