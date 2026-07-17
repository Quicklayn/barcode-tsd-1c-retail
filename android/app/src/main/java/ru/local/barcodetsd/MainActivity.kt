package ru.local.barcodetsd

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
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
    private val lookupClient = BarcodeLookupClient()
    private val collectionClient = BarcodeCollectionClient()
    private val scanSubmissionGate = ScanSubmissionGate()
    private var isOperationInProgress = false
    private var pendingAutoLookup = false
    private var currentSession: CollectionSession? = null

    private lateinit var prefs: SharedPreferences
    private lateinit var repository: CollectionRepository
    private lateinit var serviceUrlInput: EditText
    private lateinit var userInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var barcodeInput: EditText
    private lateinit var lookupButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusView: TextView
    private lateinit var productNameView: TextView
    private lateinit var detailView: TextView
    private lateinit var sessionIdView: TextView
    private lateinit var sessionStateView: TextView
    private lateinit var linesContainer: LinearLayout
    private lateinit var completeButton: Button
    private lateinit var sendButton: Button
    private lateinit var newDraftButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("connection", MODE_PRIVATE)
        repository = CollectionRepository(
            BarcodeDatabase.getInstance(applicationContext).collectionSessionDao()
        )
        setContentView(createContentView())
        loadSettings()
        bindActions()
        applyLaunchExtras(intent)
        loadSession()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLaunchExtras(intent)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
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
        sessionIdView = TextView(this).apply {
            id = R.id.session_id_view
            textSize = 13f
        }
        sessionStateView = TextView(this).apply {
            id = R.id.session_state_view
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
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
            text = "Найти и добавить"
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
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        }
        detailView = TextView(this).apply {
            id = R.id.detail_view
            textSize = 16f
        }
        linesContainer = LinearLayout(this).apply {
            id = R.id.collection_lines
            orientation = LinearLayout.VERTICAL
        }
        completeButton = Button(this).apply {
            id = R.id.complete_button
            text = "Завершить сбор"
        }
        sendButton = Button(this).apply {
            id = R.id.send_button
            text = "Отправить / повторить"
        }
        newDraftButton = Button(this).apply {
            id = R.id.new_draft_button
            text = "Новый сбор"
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        content.addView(title("Barcode TSD"), matchWrap(dp(16)))
        addLabeledField(content, "URL сервиса 1С", serviceUrlInput)
        addLabeledField(content, "Авторизация", userInput)
        content.addView(passwordInput, matchWrap(dp(16)))
        content.addView(label("Сессия сбора"), matchWrap(dp(4)))
        content.addView(sessionStateView, matchWrap(dp(2)))
        content.addView(sessionIdView, matchWrap(dp(12)))
        addLabeledField(content, "Штрихкод", barcodeInput)
        content.addView(lookupButton, matchWrap(dp(8)))
        content.addView(progressBar, wrapWrap(dp(8)))
        content.addView(statusView, matchWrap(dp(6)))
        content.addView(productNameView, matchWrap(dp(6)))
        content.addView(detailView, matchWrap(dp(16)))
        content.addView(label("Строки"), matchWrap(dp(4)))
        content.addView(linesContainer, matchWrap(dp(12)))
        content.addView(completeButton, matchWrap(dp(8)))
        content.addView(sendButton, matchWrap(dp(8)))
        content.addView(newDraftButton, matchWrap(0))

        return ScrollView(this).apply {
            addView(content)
        }
    }

    private fun bindActions() {
        lookupButton.setOnClickListener { startLookup() }
        completeButton.setOnClickListener { completeSession() }
        sendButton.setOnClickListener { submitSession() }
        newDraftButton.setOnClickListener { startNewDraft() }
        barcodeInput.setOnEditorActionListener { _, actionId, event ->
            val hardwareSubmit = event != null &&
                event.action == KeyEvent.ACTION_DOWN &&
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                scanSubmissionGate.acceptHardwareEnter(event.eventTime, event.repeatCount)
            val imeSubmit = event == null && (
                actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_ACTION_SEND
                )
            if (hardwareSubmit || imeSubmit) {
                startLookup()
                true
            } else {
                false
            }
        }
    }

    private fun loadSession() {
        setOperationInProgress(true)
        statusView.text = "Загрузка сессии"
        productNameView.text = ""
        detailView.text = ""
        executor.execute {
            val result = try {
                Result.success(repository.loadOrCreate())
            } catch (e: RuntimeException) {
                Result.failure(e)
            }
            postToMain {
                result.fold(
                    onSuccess = { session ->
                        currentSession = session
                        setOperationInProgress(false)
                        showRestoredSession(session)
                        if (pendingAutoLookup) {
                            pendingAutoLookup = false
                            startLookup()
                        } else {
                            restoreScanFocus()
                        }
                    },
                    onFailure = { error ->
                        setOperationInProgress(false)
                        showLocalError(error)
                    }
                )
            }
        }
    }

    private fun applyLaunchExtras(intent: Intent?) {
        if (intent == null) {
            return
        }

        val serviceUrl = intent.getStringExtra(EXTRA_SERVICE_URL)
        if (!serviceUrl.isNullOrBlank()) {
            serviceUrlInput.setText(serviceUrl)
        }

        val barcode = intent.getStringExtra(EXTRA_BARCODE)
        if (!barcode.isNullOrBlank()) {
            barcodeInput.setText(barcode)
            barcodeInput.selectAll()
        }

        if (intent.getBooleanExtra(EXTRA_AUTO_LOOKUP, false)) {
            if (currentSession == null) {
                pendingAutoLookup = true
            } else {
                mainHandler.post { startLookup() }
            }
        }
    }

    private fun startLookup() {
        if (isOperationInProgress) {
            return
        }

        val session = currentSession
        if (session == null) {
            showValidationError("Сессия ещё не загружена.")
            return
        }
        if (session.state != CollectionState.DRAFT) {
            showValidationError("Сканирование доступно только в черновике.")
            return
        }

        val serviceUrl = serviceUrlInput.text.toString().trim()
        val barcode = normalizeScanInput(barcodeInput.text.toString())
        if (serviceUrl.isEmpty()) {
            showValidationError("Укажите URL сервиса 1С.")
            return
        }
        if (barcode.isEmpty()) {
            showValidationError("Введите штрихкод.")
            return
        }
        if (barcode.length > MAX_BARCODE_LENGTH) {
            showValidationError("Штрихкод длиннее $MAX_BARCODE_LENGTH символов.")
            return
        }

        saveSettings()
        setOperationInProgress(true)
        statusView.text = "Запрос к 1С"
        productNameView.text = ""
        detailView.text = ""

        val user = userInput.text.toString()
        val password = passwordInput.text.toString()
        executor.execute {
            var updatedSession: CollectionSession? = null
            val result = try {
                val lookupResult = lookupClient.resolve(
                    serviceUrl,
                    user,
                    password,
                    barcode
                )
                try {
                    if (lookupResult is LookupResult.Found) {
                        updatedSession = repository.addResolvedProduct(
                            session.sessionId,
                            lookupResult
                        )
                    }
                    lookupResult
                } catch (e: CollectionValidationException) {
                    LookupResult.InvalidInput(e.message ?: "Товар нельзя добавить в сессию.")
                } catch (e: RuntimeException) {
                    LookupResult.ServerError(
                        e.localizedMessage ?: "Не удалось сохранить строку локально."
                    )
                }
            } catch (e: RuntimeException) {
                LookupResult.ConnectionError(
                    e.localizedMessage ?: "Не удалось выполнить запрос к 1С."
                )
            }
            postToMain {
                if (updatedSession != null) {
                    currentSession = updatedSession
                }
                setOperationInProgress(false)
                showLookupResult(result)
                restoreScanFocus()
            }
        }
    }

    private fun changeQuantity(itemRef: String, value: String) {
        val session = currentSession ?: return
        persistSession(
            successMessage = "Количество сохранено.",
            restoreFocus = true
        ) {
            repository.changeQuantity(session.sessionId, itemRef, value)
        }
    }

    private fun confirmDelete(itemRef: String, itemName: String) {
        val session = currentSession ?: return
        if (session.state != CollectionState.DRAFT || isOperationInProgress) {
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Удалить строку?")
            .setMessage(itemName)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ ->
                persistSession(
                    successMessage = "Строка удалена.",
                    restoreFocus = true
                ) {
                    repository.deleteLine(session.sessionId, itemRef)
                }
            }
            .show()
    }

    private fun completeSession() {
        if (isOperationInProgress) {
            return
        }
        val session = currentSession ?: return
        persistSession(
            successMessage = "Сессия завершена. Отправьте её в 1С."
        ) {
            repository.complete(session.sessionId)
        }
    }

    private fun submitSession() {
        if (isOperationInProgress) {
            return
        }
        val session = currentSession ?: return
        if (session.state != CollectionState.COMPLETED) {
            showValidationError("Отправить можно только завершённую сессию.")
            return
        }
        val serviceUrl = serviceUrlInput.text.toString().trim()
        if (serviceUrl.isEmpty()) {
            showValidationError("Укажите URL сервиса 1С.")
            return
        }

        saveSettings()
        setOperationInProgress(true)
        statusView.text = "Отправка в 1С"
        productNameView.text = ""
        detailView.text = "Сессия сохранена локально до подтверждения 1С."
        val user = userInput.text.toString()
        val password = passwordInput.text.toString()
        executor.execute {
            var sentSession: CollectionSession? = null
            val result = when (
                val submissionResult = collectionClient.submit(
                    serviceUrl,
                    user,
                    password,
                    session
                )
            ) {
                is SubmissionResult.Accepted -> {
                    try {
                        sentSession = repository.markSent(
                            session.sessionId,
                            submissionResult.documentRef
                        )
                        submissionResult
                    } catch (_: RuntimeException) {
                        SubmissionResult.ServerError(
                            "1С приняла сессию, но локальный статус не сохранён. Повторите отправку."
                        )
                    }
                }
                else -> submissionResult
            }
            postToMain {
                if (sentSession != null) {
                    currentSession = sentSession
                }
                setOperationInProgress(false)
                showSubmissionResult(result)
            }
        }
    }

    private fun startNewDraft() {
        if (isOperationInProgress) {
            return
        }
        val sentSession = currentSession ?: return
        if (sentSession.state != CollectionState.SENT) {
            showValidationError("Новый сбор доступен после успешной отправки.")
            return
        }

        setOperationInProgress(true)
        executor.execute {
            val result = try {
                Result.success(repository.startNewDraft(sentSession.sessionId))
            } catch (e: RuntimeException) {
                Result.failure(e)
            }
            postToMain {
                result.fold(
                    onSuccess = { draft ->
                        currentSession = draft
                        barcodeInput.text.clear()
                        setOperationInProgress(false)
                        showRestoredSession(draft)
                        restoreScanFocus()
                    },
                    onFailure = { error ->
                        setOperationInProgress(false)
                        showLocalError(error)
                    }
                )
            }
        }
    }

    private fun persistSession(
        successMessage: String,
        restoreFocus: Boolean = false,
        operation: () -> CollectionSession
    ) {
        if (isOperationInProgress) {
            return
        }
        setOperationInProgress(true)
        executor.execute {
            val result = try {
                Result.success(operation())
            } catch (e: RuntimeException) {
                Result.failure(e)
            }
            postToMain {
                result.fold(
                    onSuccess = { updatedSession ->
                        currentSession = updatedSession
                        setOperationInProgress(false)
                        statusView.text = "Готово"
                        productNameView.text = ""
                        detailView.text = successMessage
                        if (restoreFocus) {
                            restoreScanFocus()
                        }
                    },
                    onFailure = { error ->
                        setOperationInProgress(false)
                        if (error is CollectionValidationException) {
                            showValidationError(error.message ?: "Некорректное действие.")
                        } else {
                            showLocalError(error)
                        }
                    }
                )
            }
        }
    }

    private fun showRestoredSession(session: CollectionSession) {
        productNameView.text = ""
        when (session.state) {
            CollectionState.DRAFT -> {
                statusView.text = "Готово"
                detailView.text = "Введите штрихкод вручную или отсканируйте его с Enter."
            }
            CollectionState.COMPLETED -> {
                statusView.text = "Ожидает отправки"
                detailView.text = "Строки зафиксированы. Отправьте сессию в 1С."
            }
            CollectionState.SENT -> {
                statusView.text = "Отправлено"
                detailView.text = "Документ 1С: ${session.documentRef}"
            }
        }
    }

    private fun showLookupResult(result: LookupResult) {
        when (result) {
            is LookupResult.Found -> {
                statusView.text = "Добавлено"
                productNameView.text = result.name
                detailView.text = "Товар добавлен или его количество увеличено."
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

    private fun showSubmissionResult(result: SubmissionResult) {
        productNameView.text = ""
        when (result) {
            is SubmissionResult.Accepted -> {
                statusView.text = "Отправлено"
                detailView.text = "Документ 1С: ${result.documentRef}"
            }
            is SubmissionResult.InvalidRequest -> {
                statusView.text = "Сессия отклонена"
                detailView.text = result.message
            }
            is SubmissionResult.AuthError -> {
                statusView.text = "Ошибка авторизации"
                detailView.text = result.message
            }
            is SubmissionResult.Conflict -> {
                statusView.text = "Конфликт"
                detailView.text = "${result.message}\nКод: ${result.error}"
            }
            is SubmissionResult.ServerError -> {
                statusView.text = "Ошибка сервера"
                detailView.text = result.message
            }
            is SubmissionResult.ConnectionError -> {
                statusView.text = "Ошибка подключения"
                detailView.text = result.message
            }
        }
    }

    private fun showValidationError(message: String) {
        statusView.text = "Некорректное действие"
        productNameView.text = ""
        detailView.text = message
        restoreScanFocus()
    }

    private fun showLocalError(error: Throwable) {
        statusView.text = "Ошибка локального хранения"
        productNameView.text = ""
        detailView.text = error.localizedMessage ?: "Не удалось сохранить сессию на устройстве."
        restoreScanFocus()
    }

    private fun setOperationInProgress(inProgress: Boolean) {
        isOperationInProgress = inProgress
        progressBar.visibility = if (inProgress) View.VISIBLE else View.GONE
        serviceUrlInput.isEnabled = !inProgress
        userInput.isEnabled = !inProgress
        passwordInput.isEnabled = !inProgress
        renderSession()
    }

    private fun renderSession() {
        val session = currentSession
        if (session == null) {
            sessionStateView.text = "Загрузка"
            sessionIdView.text = ""
            linesContainer.removeAllViews()
            barcodeInput.isEnabled = false
            lookupButton.isEnabled = false
            completeButton.visibility = View.GONE
            sendButton.visibility = View.GONE
            newDraftButton.visibility = View.GONE
            return
        }

        sessionStateView.text = when (session.state) {
            CollectionState.DRAFT -> "Черновик"
            CollectionState.COMPLETED -> "Завершена"
            CollectionState.SENT -> "Отправлена"
        }
        sessionIdView.text = session.sessionId
        val draftEditable = session.state == CollectionState.DRAFT && !isOperationInProgress
        barcodeInput.isEnabled = draftEditable
        lookupButton.isEnabled = draftEditable

        completeButton.visibility = if (session.state == CollectionState.DRAFT) {
            View.VISIBLE
        } else {
            View.GONE
        }
        completeButton.isEnabled = draftEditable
        sendButton.visibility = if (session.state == CollectionState.COMPLETED) {
            View.VISIBLE
        } else {
            View.GONE
        }
        sendButton.isEnabled = session.state == CollectionState.COMPLETED && !isOperationInProgress
        newDraftButton.visibility = if (session.state == CollectionState.SENT) {
            View.VISIBLE
        } else {
            View.GONE
        }
        newDraftButton.isEnabled = session.state == CollectionState.SENT && !isOperationInProgress
        renderLines(session, draftEditable)
    }

    private fun renderLines(session: CollectionSession, editable: Boolean) {
        linesContainer.removeAllViews()
        if (session.lines.isEmpty()) {
            linesContainer.addView(
                TextView(this).apply {
                    text = "Строк пока нет."
                    textSize = 15f
                },
                matchWrap(0)
            )
            return
        }

        session.lines.forEachIndexed { index, line ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            row.addView(
                TextView(this).apply {
                    text = "${index + 1}. ${line.name}"
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                },
                matchWrap(dp(2))
            )
            row.addView(
                TextView(this).apply {
                    text = "Штрихкод: ${line.barcode}"
                    textSize = 14f
                },
                matchWrap(dp(4))
            )
            if (editable) {
                val quantityInput = EditText(this).apply {
                    setText(line.quantity.toDisplayString())
                    hint = "Количество"
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    isSingleLine = true
                }
                row.addView(quantityInput, matchWrap(dp(4)))
                val actions = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                actions.addView(
                    Button(this).apply {
                        text = "Сохранить"
                        setOnClickListener {
                            changeQuantity(line.itemRef, quantityInput.text.toString())
                        }
                    },
                    weightedWrap(dp(4))
                )
                actions.addView(
                    Button(this).apply {
                        text = "Удалить"
                        setOnClickListener { confirmDelete(line.itemRef, line.name) }
                    },
                    weightedWrap(0)
                )
                row.addView(actions, matchWrap(0))
            } else {
                row.addView(
                    TextView(this).apply {
                        text = "Количество: ${line.quantity.toDisplayString()}"
                        textSize = 16f
                    },
                    matchWrap(0)
                )
            }
            linesContainer.addView(row, matchWrap(dp(4)))
        }
    }

    private fun restoreScanFocus() {
        val session = currentSession
        if (
            session != null &&
            session.state == CollectionState.DRAFT &&
            !isOperationInProgress
        ) {
            barcodeInput.requestFocus()
            barcodeInput.selectAll()
        }
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

    private fun postToMain(action: () -> Unit) {
        mainHandler.post {
            if (!isDestroyed) {
                action()
            }
        }
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

    private fun weightedWrap(endMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginEnd = endMargin
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        private const val EXTRA_SERVICE_URL = "ru.local.barcodetsd.extra.SERVICE_URL"
        private const val EXTRA_BARCODE = "ru.local.barcodetsd.extra.BARCODE"
        private const val EXTRA_AUTO_LOOKUP = "ru.local.barcodetsd.extra.AUTO_LOOKUP"
        private const val MAX_BARCODE_LENGTH = 200
    }
}
