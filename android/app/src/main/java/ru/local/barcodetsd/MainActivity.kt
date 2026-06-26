package ru.local.barcodetsd

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = BarcodeLookupClient()
    private var isLookupInProgress = false

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
            id = R.id.service_url_input
            hint = "http://server/infobase/hs/BarcodeTSD"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        userInput = EditText(this).apply {
            id = R.id.user_input
            hint = "Пользователь 1С (необязательно)"
            isSingleLine = true
        }
        passwordInput = EditText(this).apply {
            id = R.id.password_input
            hint = "Пароль (необязательно)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        barcodeInput = EditText(this).apply {
            id = R.id.barcode_input
            hint = "Штрихкод"
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
            setSelectAllOnFocus(true)
        }
        lookupButton = Button(this).apply {
            id = R.id.lookup_button
            text = "Найти"
        }
        progressBar = ProgressBar(this).apply {
            id = R.id.lookup_progress
            isIndeterminate = true
            visibility = View.GONE
        }
        statusView = TextView(this).apply {
            id = R.id.status_view
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        }
        productNameView = TextView(this).apply {
            id = R.id.product_name_view
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
        }
        detailView = TextView(this).apply {
            id = R.id.detail_view
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
            val enterDown = event != null &&
                event.action == KeyEvent.ACTION_DOWN &&
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.repeatCount == 0
            if (imeSubmit || enterDown) {
                startLookup()
                true
            } else {
                false
            }
        }
    }

    private fun startLookup() {
        if (isLookupInProgress) {
            return
        }

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
            val result = try {
                client.resolve(serviceUrl, user, password, barcode)
            } catch (e: RuntimeException) {
                LookupResult.ConnectionError(e.localizedMessage ?: "Не удалось выполнить запрос к 1С.")
            }
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
                detailView.text = ""
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
        isLookupInProgress = isLoading
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
