package ru.local.barcodetsd

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.EditText
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
        OneShotHttpServer(
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

        OneShotHttpServer(
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

    private class OneShotHttpServer(
        statusCode: Int,
        responseBody: String
    ) : AutoCloseable {

        private val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()

        val serviceUrl: String =
            "http://127.0.0.1:${socket.localPort}/retail/hs/BarcodeTSD"

        init {
            executor.execute {
                try {
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
        private const val ITEM_REF = "14f2c4da-8238-4a9f-bf56-3ec3a2f4d86f"
        private const val CACHED_BARCODE = "4600000000035"
        private const val CACHED_ITEM_REF = "99241fb2-b926-494c-b4e2-a0da243a2cc0"
        private const val CACHED_NAME = "Кешированный товар"
        private const val ONLINE_BARCODE = "4600000000042"
        private const val ONLINE_ITEM_REF = "70b6bb07-9505-432d-a403-6226fdbc211e"
        private const val ONLINE_NAME = "Онлайн-товар"
    }
}
