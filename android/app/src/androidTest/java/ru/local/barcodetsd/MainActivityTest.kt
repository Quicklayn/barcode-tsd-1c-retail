package ru.local.barcodetsd

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private lateinit var database: BarcodeDatabase

    @Before
    fun prepareDraft() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = BarcodeDatabase.getInstance(context)
        database.openHelper.writableDatabase.execSQL("DELETE FROM cached_products")
        database.collectionSessionDao().replaceSession(CollectionSession.draft(SESSION_ID))
        context.getSharedPreferences("connection", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun restoreDraft() {
        database.openHelper.writableDatabase.execSQL("DELETE FROM cached_products")
        database.collectionSessionDao().replaceSession(CollectionSession.draft(SESSION_ID))
    }

    @Test
    fun emptyDraftDisplaysZeroSummary() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario) { activity ->
                activity.findViewById<TextView>(R.id.session_state_view).text.toString() == "Черновик"
            }

            scenario.onActivity { activity ->
                assertEquals(
                    "Позиций: 0 · Количество: 0",
                    activity.findViewById<TextView>(R.id.collection_summary_view).text.toString()
                )
            }
        }
    }

    @Test
    fun fractionalSummaryUpdatesAfterEditDeleteAndCompletedRestore() {
        val session = CollectionSession.restore(
            SESSION_ID,
            CollectionState.DRAFT,
            listOf(
                CollectionLine("item-1", "Товар 1", "barcode-1", CollectionQuantity.parse("2")!!),
                CollectionLine("item-2", "Товар 2", "barcode-2", CollectionQuantity.parse("1.250")!!)
            ),
            null
        )
        database.collectionSessionDao().replaceSession(session)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario) { activity ->
                activity.findViewById<TextView>(R.id.collection_summary_view).text.toString() ==
                    "Позиций: 2 · Количество: 3.25"
            }

            scenario.onActivity { activity ->
                val lines = activity.findViewById<LinearLayout>(R.id.collection_lines)
                val firstRow = lines.getChildAt(0) as LinearLayout
                (firstRow.getChildAt(2) as EditText).setText("2.500")
                (firstRow.getChildAt(3) as LinearLayout).getChildAt(0).performClick()
            }
            waitUntil(scenario) { activity ->
                activity.findViewById<TextView>(R.id.collection_summary_view).text.toString() ==
                    "Позиций: 2 · Количество: 3.75"
            }

            scenario.close()
            database.collectionSessionDao().replaceSession(
                session.changeQuantity("item-1", "2.500").deleteLine("item-2")
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario) { activity ->
                activity.findViewById<TextView>(R.id.collection_summary_view).text.toString() ==
                    "Позиций: 1 · Количество: 2.5"
            }
        }

        database.collectionSessionDao().replaceSession(session.complete())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario) { activity ->
                activity.findViewById<TextView>(R.id.session_state_view).text.toString() == "Завершена"
            }
            scenario.onActivity { activity ->
                assertEquals(
                    "Позиций: 2 · Количество: 3.25",
                    activity.findViewById<TextView>(R.id.collection_summary_view).text.toString()
                )
            }
        }

        database.collectionSessionDao().replaceSession(session.complete().markSent(SENT_DOCUMENT_REF))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario) { activity ->
                activity.findViewById<TextView>(R.id.session_state_view).text.toString() == "Отправлена"
            }
            scenario.onActivity { activity ->
                assertEquals(
                    "Позиций: 2 · Количество: 3.25",
                    activity.findViewById<TextView>(R.id.collection_summary_view).text.toString()
                )
            }
        }
    }

    @Test
    fun emptyManualSubmissionRestoresScannerFocus() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario) { activity ->
                activity.findViewById<Button>(R.id.lookup_button).isEnabled
            }

            scenario.onActivity { activity ->
                val barcodeInput = activity.findViewById<EditText>(R.id.barcode_input)
                barcodeInput.clearFocus()
                barcodeInput.setText("  ")

                activity.findViewById<Button>(R.id.lookup_button).performClick()

                assertTrue(barcodeInput.hasFocus())
                assertEquals(
                    "Некорректное действие",
                    activity.findViewById<TextView>(R.id.status_view).text.toString()
                )
            }
        }
    }

    @Test
    fun completedSessionDisablesMutationAndShowsManualSend() {
        val completed = CollectionSession.draft(SESSION_ID)
            .aggregate(
                LookupResult.Found(
                    barcode = "4600000000011",
                    itemRef = ITEM_REF,
                    name = "Товар"
                )
            )
            .complete()
        database.collectionSessionDao().replaceSession(completed)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario) { activity ->
                activity.findViewById<TextView>(R.id.session_state_view).text.toString() ==
                    "Завершена"
            }

            scenario.onActivity { activity ->
                assertFalse(activity.findViewById<EditText>(R.id.barcode_input).isEnabled)
                assertEquals(View.GONE, activity.findViewById<Button>(R.id.complete_button).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<Button>(R.id.send_button).visibility)
                assertTrue(activity.findViewById<Button>(R.id.send_button).isEnabled)
                assertEquals(View.GONE, activity.findViewById<Button>(R.id.new_draft_button).visibility)
            }
        }
    }

    @Test
    fun completingActiveDraftImmediatelyRendersManualSendAction() {
        val draft = CollectionSession.draft(SESSION_ID)
            .aggregate(
                LookupResult.Found(
                    barcode = "4600000000011",
                    itemRef = ITEM_REF,
                    name = "Товар"
                )
            )
        database.collectionSessionDao().replaceSession(draft)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario) { activity ->
                activity.findViewById<Button>(R.id.complete_button).isEnabled
            }

            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.complete_button).performClick()
            }

            waitUntil(scenario) { activity ->
                activity.findViewById<TextView>(R.id.session_state_view).text.toString() ==
                    "Завершена"
            }

            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<Button>(R.id.complete_button).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<Button>(R.id.send_button).visibility)
                assertTrue(activity.findViewById<Button>(R.id.send_button).isEnabled)
            }
        }
    }

    @Test
    fun ambiguousDialogShowsResponseOrderAndSelectedCandidateAggregatesExactly() {
        HttpTestServer(
            200,
            ambiguousResponseBody(),
            requestCount = 2
        ).use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitUntil(scenario) { activity ->
                    activity.findViewById<Button>(R.id.lookup_button).isEnabled
                }

                scenario.onActivity { activity ->
                    activity.findViewById<EditText>(R.id.service_url_input)
                        .setText(server.serviceUrl)
                    activity.findViewById<EditText>(R.id.barcode_input)
                        .setText(AMBIGUOUS_REQUEST_BARCODE)
                    activity.findViewById<Button>(R.id.lookup_button).performClick()
                }

                waitUntil(scenario) { activity ->
                    activity.activeCandidateDialog?.isShowing == true
                }
                scenario.onActivity { activity ->
                    val dialog = requireNotNull(activity.activeCandidateDialog)
                    assertEquals(2, dialog.listView.adapter.count)
                    assertEquals("1. $FIRST_CANDIDATE_NAME", dialog.listView.adapter.getItem(0))
                    assertEquals("2. $SECOND_CANDIDATE_NAME", dialog.listView.adapter.getItem(1))
                    clickDialogItem(dialog, 1)
                }

                waitUntil(scenario) { activity ->
                    activity.activeCandidateDialog == null &&
                        activity.findViewById<TextView>(R.id.status_view).text.toString() ==
                        "Добавлено"
                }

                val repository = CollectionRepository(database.collectionSessionDao())
                val selected = repository.loadOrCreate()
                assertEquals(SECOND_CANDIDATE_ITEM_REF, selected.lines.single().itemRef)
                assertEquals(SECOND_CANDIDATE_NAME, selected.lines.single().name)
                assertEquals(AMBIGUOUS_RESPONSE_BARCODE, selected.lines.single().barcode)
                assertEquals(1_000L, selected.lines.single().quantity.milliUnits)

                scenario.onActivity { activity ->
                    assertEquals(
                        "Позиций: 1 · Количество: 1",
                        activity.findViewById<TextView>(R.id.collection_summary_view).text.toString()
                    )
                    assertEquals(
                        SECOND_CANDIDATE_NAME,
                        activity.findViewById<TextView>(R.id.product_name_view).text.toString()
                    )
                    assertTrue(activity.findViewById<EditText>(R.id.barcode_input).hasFocus())
                    activity.findViewById<Button>(R.id.lookup_button).performClick()
                }
                waitUntil(scenario) { activity ->
                    activity.activeCandidateDialog?.isShowing == true
                }
                scenario.onActivity { activity ->
                    clickDialogItem(requireNotNull(activity.activeCandidateDialog), 1)
                }
                waitUntil(scenario) { activity ->
                    activity.activeCandidateDialog == null &&
                        activity.findViewById<TextView>(R.id.status_view).text.toString() ==
                        "Добавлено" &&
                        activity.findViewById<TextView>(R.id.collection_summary_view).text.toString() ==
                        "Позиций: 1 · Количество: 2"
                }

                val repeated = repository.loadOrCreate()
                assertEquals(1, repeated.lines.size)
                assertEquals(2_000L, repeated.lines.single().quantity.milliUnits)
            }
        }
    }

    @Test
    fun cancellingAmbiguousDialogDoesNotMutateDraftAndRestoresFocus() {
        HttpTestServer(200, ambiguousResponseBody()).use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitUntil(scenario) { activity ->
                    activity.findViewById<Button>(R.id.lookup_button).isEnabled
                }
                scenario.onActivity { activity ->
                    activity.findViewById<EditText>(R.id.service_url_input)
                        .setText(server.serviceUrl)
                    activity.findViewById<EditText>(R.id.barcode_input)
                        .setText(AMBIGUOUS_REQUEST_BARCODE)
                    activity.findViewById<Button>(R.id.lookup_button).performClick()
                }
                waitUntil(scenario) { activity ->
                    activity.activeCandidateDialog?.isShowing == true
                }

                scenario.onActivity { activity ->
                    requireNotNull(activity.activeCandidateDialog)
                        .getButton(AlertDialog.BUTTON_NEGATIVE)
                        .performClick()
                }

                waitUntil(scenario) { activity ->
                    activity.activeCandidateDialog == null &&
                        activity.findViewById<TextView>(R.id.status_view).text.toString() ==
                        "Не добавлено" &&
                        activity.findViewById<EditText>(R.id.barcode_input).hasFocus()
                }
            }
        }

        assertEquals(
            0,
            CollectionRepository(database.collectionSessionDao()).loadOrCreate().lines.size
        )
    }

    @Test
    fun backFromAmbiguousDialogDoesNotMutateDraftAndRestoresFocus() {
        HttpTestServer(200, ambiguousResponseBody()).use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitUntil(scenario) { activity ->
                    activity.findViewById<Button>(R.id.lookup_button).isEnabled
                }
                scenario.onActivity { activity ->
                    activity.findViewById<EditText>(R.id.service_url_input)
                        .setText(server.serviceUrl)
                    activity.findViewById<EditText>(R.id.barcode_input)
                        .setText(AMBIGUOUS_REQUEST_BARCODE)
                    activity.findViewById<Button>(R.id.lookup_button).performClick()
                }
                waitUntil(scenario) { activity ->
                    activity.activeCandidateDialog?.isShowing == true
                }

                scenario.onActivity { activity ->
                    pressDialogBack(requireNotNull(activity.activeCandidateDialog))
                }

                waitUntil(scenario) { activity ->
                    activity.activeCandidateDialog == null &&
                        activity.findViewById<TextView>(R.id.status_view).text.toString() ==
                        "Не добавлено" &&
                        activity.findViewById<EditText>(R.id.barcode_input).hasFocus()
                }
            }
        }

        assertEquals(
            0,
            CollectionRepository(database.collectionSessionDao()).loadOrCreate().lines.size
        )
    }

    @Test
    fun connectionFailureUsesCacheAndMarksVisibleResult() {
        val repository = CollectionRepository(database.collectionSessionDao()) { SESSION_ID }
        repository.addResolvedProduct(
            SESSION_ID,
            LookupResult.Found(CACHED_BARCODE, CACHED_ITEM_REF, CACHED_NAME)
        )
        database.collectionSessionDao().replaceSession(CollectionSession.draft(SESSION_ID))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario) { activity ->
                activity.findViewById<Button>(R.id.lookup_button).isEnabled
            }

            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.service_url_input)
                    .setText("http://127.0.0.1:1/retail/hs/BarcodeTSD")
                activity.findViewById<EditText>(R.id.barcode_input).setText(CACHED_BARCODE)
                activity.findViewById<Button>(R.id.lookup_button).performClick()
            }

            waitUntil(scenario) { activity ->
                activity.findViewById<TextView>(R.id.status_view).text.toString() ==
                    "Добавлено из кеша"
            }

            scenario.onActivity { activity ->
                assertEquals(
                    CACHED_NAME,
                    activity.findViewById<TextView>(R.id.product_name_view).text.toString()
                )
                assertTrue(
                    activity.findViewById<TextView>(R.id.detail_view)
                        .text
                        .toString()
                        .contains("локального кеша")
                )
            }
        }

        val restored = repository.loadOrCreate()
        assertEquals(CACHED_ITEM_REF, restored.lines.single().itemRef)
        assertEquals(1_000L, restored.lines.single().quantity.milliUnits)
    }

    @Test
    fun onlineLookupKeepsExistingPresentationAndCollectionFlow() {
        HttpTestServer(
            200,
            """{"status":"found","barcode":"$ONLINE_BARCODE","matches":[{"itemRef":"$ONLINE_ITEM_REF","name":"$ONLINE_NAME"}]}"""
        ).use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitUntil(scenario) { activity ->
                    activity.findViewById<Button>(R.id.lookup_button).isEnabled
                }

                scenario.onActivity { activity ->
                    activity.findViewById<EditText>(R.id.service_url_input)
                        .setText(server.serviceUrl)
                    activity.findViewById<EditText>(R.id.barcode_input).setText(ONLINE_BARCODE)
                    activity.findViewById<Button>(R.id.lookup_button).performClick()
                }

                waitUntil(scenario) { activity ->
                    activity.findViewById<TextView>(R.id.status_view).text.toString() == "Добавлено"
                }

                scenario.onActivity { activity ->
                    assertEquals(
                        ONLINE_NAME,
                        activity.findViewById<TextView>(R.id.product_name_view).text.toString()
                    )
                    assertEquals(
                        "Товар добавлен или его количество увеличено.",
                        activity.findViewById<TextView>(R.id.detail_view).text.toString()
                    )
                }
            }
        }

        val restored = CollectionRepository(database.collectionSessionDao()).loadOrCreate()
        assertEquals(ONLINE_ITEM_REF, restored.lines.single().itemRef)
    }

    @Test
    fun receivedNotFoundResponseDoesNotUseCachedProduct() {
        val repository = CollectionRepository(database.collectionSessionDao()) { SESSION_ID }
        repository.addResolvedProduct(
            SESSION_ID,
            LookupResult.Found(CACHED_BARCODE, CACHED_ITEM_REF, CACHED_NAME)
        )
        database.collectionSessionDao().replaceSession(CollectionSession.draft(SESSION_ID))

        HttpTestServer(
            200,
            """{"status":"not_found","barcode":"$CACHED_BARCODE","matches":[],"message":"Не найдено в 1С"}"""
        ).use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitUntil(scenario) { activity ->
                    activity.findViewById<Button>(R.id.lookup_button).isEnabled
                }

                scenario.onActivity { activity ->
                    activity.findViewById<EditText>(R.id.service_url_input)
                        .setText(server.serviceUrl)
                    activity.findViewById<EditText>(R.id.barcode_input).setText(CACHED_BARCODE)
                    activity.findViewById<Button>(R.id.lookup_button).performClick()
                }

                waitUntil(scenario) { activity ->
                    activity.findViewById<TextView>(R.id.status_view).text.toString() == "Не найдено"
                }

                scenario.onActivity { activity ->
                    assertEquals(
                        "Не найдено в 1С",
                        activity.findViewById<TextView>(R.id.detail_view).text.toString()
                    )
                    assertEquals(
                        "",
                        activity.findViewById<TextView>(R.id.product_name_view).text.toString()
                    )
                }
            }
        }

        assertEquals(0, repository.loadOrCreate().lines.size)
    }

    private fun waitUntil(
        scenario: ActivityScenario<MainActivity>,
        condition: (MainActivity) -> Boolean
    ) {
        repeat(250) {
            var satisfied = false
            scenario.onActivity { activity ->
                satisfied = condition(activity)
            }
            if (satisfied) {
                return
            }
            Thread.sleep(20)
        }
        fail("Activity did not reach the expected state.")
    }

    private fun clickDialogItem(dialog: AlertDialog, index: Int) {
        val list = dialog.listView
        val itemView = list.getChildAt(index) ?: list.adapter.getView(index, null, list)
        assertTrue(list.performItemClick(itemView, index, list.adapter.getItemId(index)))
    }

    @Suppress("DEPRECATION")
    private fun pressDialogBack(dialog: AlertDialog) {
        dialog.onBackPressed()
    }

    private fun ambiguousResponseBody(): String =
        """{"status":"ambiguous","barcode":"$AMBIGUOUS_RESPONSE_BARCODE","matches":[{"itemRef":"$FIRST_CANDIDATE_ITEM_REF","name":"$FIRST_CANDIDATE_NAME"},{"itemRef":"$SECOND_CANDIDATE_ITEM_REF","name":"$SECOND_CANDIDATE_NAME"}]}"""

    private class HttpTestServer(
        statusCode: Int,
        responseBody: String,
        requestCount: Int = 1
    ) : AutoCloseable {

        private val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()

        val serviceUrl: String =
            "http://127.0.0.1:${socket.localPort}/retail/hs/BarcodeTSD"

        init {
            executor.execute {
                try {
                    repeat(requestCount) {
                        socket.accept().use { client ->
                            client.soTimeout = 5_000
                            val input = BufferedInputStream(client.getInputStream())
                            readRequest(input)
                            val body = responseBody.toByteArray(StandardCharsets.UTF_8)
                            val headers = buildString {
                                append("HTTP/1.1 $statusCode Test\r\n")
                                append("Content-Type: application/json; charset=utf-8\r\n")
                                append("Content-Length: ${body.size}\r\n")
                                append("Connection: close\r\n\r\n")
                            }.toByteArray(StandardCharsets.US_ASCII)
                            client.getOutputStream().use { output ->
                                output.write(headers)
                                output.write(body)
                                output.flush()
                            }
                        }
                    }
                } catch (_: IOException) {
                    // Closing the test server interrupts accept/read when a scenario fails early.
                }
            }
        }

        override fun close() {
            socket.close()
            executor.shutdownNow()
        }

        private fun readRequest(input: InputStream) {
            readAsciiLine(input)
            var contentLength = 0
            while (true) {
                val line = readAsciiLine(input)
                if (line.isEmpty()) {
                    break
                }
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(':').trim().toInt()
                }
            }
            repeat(contentLength) {
                if (input.read() < 0) {
                    return
                }
            }
        }

        private fun readAsciiLine(input: InputStream): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val value = input.read()
                if (value < 0 || value == '\n'.code) {
                    break
                }
                if (value != '\r'.code) {
                    bytes.write(value)
                }
            }
            return bytes.toString(StandardCharsets.US_ASCII.name())
        }
    }

    private companion object {
        private const val SESSION_ID = "52af8363-48d3-4e7b-82b4-239760470f41"
        private const val SENT_DOCUMENT_REF = "4552555d-b9b8-4b0f-9c5c-874971511bc3"
        private const val ITEM_REF = "14f2c4da-8238-4a9f-bf56-3ec3a2f4d86f"
        private const val CACHED_BARCODE = "4600000000035"
        private const val CACHED_ITEM_REF = "99241fb2-b926-494c-b4e2-a0da243a2cc0"
        private const val CACHED_NAME = "Кешированный товар"
        private const val ONLINE_BARCODE = "4600000000042"
        private const val ONLINE_ITEM_REF = "70b6bb07-9505-432d-a403-6226fdbc211e"
        private const val ONLINE_NAME = "Онлайн-товар"
        private const val AMBIGUOUS_REQUEST_BARCODE = "ambiguous-request"
        private const val AMBIGUOUS_RESPONSE_BARCODE = "ambiguous-response"
        private const val FIRST_CANDIDATE_ITEM_REF = "f2674553-ec1c-43c1-846c-0552c799f1af"
        private const val FIRST_CANDIDATE_NAME = "Первый кандидат"
        private const val SECOND_CANDIDATE_ITEM_REF = "aa82e57b-dcce-4fc1-b22d-a7ce60db1804"
        private const val SECOND_CANDIDATE_NAME = "Второй кандидат"
    }
}
