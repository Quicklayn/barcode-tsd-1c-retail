package ru.local.barcodetsd

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONObject
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Base64

class BarcodeCollectionClientTest {

    private lateinit var server: HttpServer
    private lateinit var serviceUrl: String
    private lateinit var recordedRequest: RecordedRequest
    private val client = BarcodeCollectionClient()

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        serviceUrl = "http://127.0.0.1:${server.address.port}/retail/hs/BarcodeTSD"
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun submitSendsExactContractShapeAndBasicAuth() {
        enqueueResponse(
            200,
            """{"status":"accepted","sessionId":"$SESSION_ID","documentRef":"$DOCUMENT_REF"}"""
        )

        val result = client.submit(serviceUrl, "user", "secret", completedSession())

        assertTrue(result is SubmissionResult.Accepted)
        assertEquals("POST", recordedRequest.method)
        assertEquals("/retail/hs/BarcodeTSD/v1/barcode-collection-sessions", recordedRequest.path)
        val expectedAuth = "Basic " + Base64.getEncoder()
            .encodeToString("user:secret".toByteArray(StandardCharsets.UTF_8))
        assertEquals(expectedAuth, recordedRequest.authorization)

        val request = JSONObject(recordedRequest.body)
        assertEquals(setOf("sessionId", "lines"), request.keys().asSequence().toSet())
        assertEquals(SESSION_ID, request.getString("sessionId"))
        val lines = request.getJSONArray("lines")
        assertEquals(2, lines.length())
        val first = lines.getJSONObject(0)
        assertEquals(setOf("itemRef", "barcode", "quantity"), first.keys().asSequence().toSet())
        assertFalse(first.has("name"))
        assertEquals(ITEM_REF, first.getString("itemRef"))
        assertEquals("4600000000011", first.getString("barcode"))
        assertEquals(BigDecimal("1.001"), BigDecimal(first.get("quantity").toString()))
    }

    @Test
    fun submitMapsAcceptedResponse() {
        enqueueResponse(
            200,
            """{"status":"accepted","sessionId":"$SESSION_ID","documentRef":"$DOCUMENT_REF"}"""
        )

        val result = client.submit(serviceUrl, "", "", completedSession())

        assertEquals(SubmissionResult.Accepted(SESSION_ID, DOCUMENT_REF), result)
    }

    @Test
    fun submitMapsHttp400ToInvalidRequest() {
        enqueueResponse(400, """{"error":"invalid_request","message":"Некорректные строки"}""")

        val result = client.submit(serviceUrl, "", "", completedSession())

        assertEquals(SubmissionResult.InvalidRequest("Некорректные строки"), result)
    }

    @Test
    fun submitMapsHttp401ToAuthError() {
        enqueueResponse(401, "")

        val result = client.submit(serviceUrl, "", "", completedSession())

        assertTrue(result is SubmissionResult.AuthError)
    }

    @Test
    fun submitMapsHttp403ToAuthError() {
        enqueueResponse(403, """{"message":"Доступ запрещен"}""")

        val result = client.submit(serviceUrl, "", "", completedSession())

        assertEquals(SubmissionResult.AuthError("Доступ запрещен"), result)
    }

    @Test
    fun submitMapsHttp409AndRetainsErrorCode() {
        enqueueResponse(
            409,
            """{"error":"idempotency_conflict","message":"Сессия уже содержит другие строки"}"""
        )

        val result = client.submit(serviceUrl, "", "", completedSession())

        assertEquals(
            SubmissionResult.Conflict(
                "idempotency_conflict",
                "Сессия уже содержит другие строки"
            ),
            result
        )
    }

    @Test
    fun submitMapsHttp5xxToServerError() {
        enqueueResponse(503, """{"error":"server_error","message":"1С недоступна"}""")

        val result = client.submit(serviceUrl, "", "", completedSession())

        assertEquals(SubmissionResult.ServerError("1С недоступна"), result)
    }

    @Test
    fun submitRejectsMismatchedAcceptedSessionId() {
        enqueueResponse(
            200,
            """{"status":"accepted","sessionId":"$OTHER_SESSION_ID","documentRef":"$DOCUMENT_REF"}"""
        )

        val result = client.submit(serviceUrl, "", "", completedSession())

        assertTrue(result is SubmissionResult.ServerError)
    }

    @Test
    fun submitAcceptsFullEndpointWithoutDuplicatingPath() {
        enqueueResponse(
            200,
            """{"status":"accepted","sessionId":"$SESSION_ID","documentRef":"$DOCUMENT_REF"}"""
        )

        val result = client.submit(
            "$serviceUrl/v1/barcode-collection-sessions",
            "",
            "",
            completedSession()
        )

        assertTrue(result is SubmissionResult.Accepted)
        assertEquals("/retail/hs/BarcodeTSD/v1/barcode-collection-sessions", recordedRequest.path)
    }

    private fun completedSession(): CollectionSession =
        CollectionSession.draft(SESSION_ID)
            .aggregate(
                LookupResult.Found(
                    barcode = "4600000000011",
                    itemRef = ITEM_REF,
                    name = "Товар 1"
                )
            )
            .changeQuantity(ITEM_REF, "1.001")
            .aggregate(
                LookupResult.Found(
                    barcode = "4600000000028",
                    itemRef = OTHER_ITEM_REF,
                    name = "Товар 2"
                )
            )
            .complete()

    private fun enqueueResponse(code: Int, body: String) {
        server.createContext("/retail/hs/BarcodeTSD/v1/barcode-collection-sessions") { exchange ->
            recordedRequest = RecordedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                body = exchange.requestBody.use { input ->
                    String(input.readBytes(), StandardCharsets.UTF_8)
                }
            )
            writeResponse(exchange, code, body)
        }
    }

    private fun writeResponse(exchange: HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { output ->
            output.write(bytes)
        }
    }

    private data class RecordedRequest(
        val method: String,
        val path: String,
        val authorization: String?,
        val body: String
    )

    private companion object {
        private const val SESSION_ID = "52af8363-48d3-4e7b-82b4-239760470f41"
        private const val OTHER_SESSION_ID = "2f7a5520-ac39-4c06-a069-89db6421a7fb"
        private const val ITEM_REF = "14f2c4da-8238-4a9f-bf56-3ec3a2f4d86f"
        private const val OTHER_ITEM_REF = "a3fe213b-334b-48ed-ae7a-f55cc3eac9f5"
        private const val DOCUMENT_REF = "8c85bdb8-5905-4869-b152-8b0fe2d5b413"
    }
}
