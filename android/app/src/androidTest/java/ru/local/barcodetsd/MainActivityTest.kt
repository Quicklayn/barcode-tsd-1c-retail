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

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private lateinit var database: BarcodeDatabase

    @Before
    fun prepareDraft() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = BarcodeDatabase.getInstance(context)
        database.collectionSessionDao().replaceSession(CollectionSession.draft(SESSION_ID))
    }

    @After
    fun restoreDraft() {
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

    private fun waitUntil(
        scenario: ActivityScenario<MainActivity>,
        condition: (MainActivity) -> Boolean
    ) {
        repeat(100) {
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

    private companion object {
        private const val SESSION_ID = "52af8363-48d3-4e7b-82b4-239760470f41"
        private const val ITEM_REF = "14f2c4da-8238-4a9f-bf56-3ec3a2f4d86f"
    }
}
