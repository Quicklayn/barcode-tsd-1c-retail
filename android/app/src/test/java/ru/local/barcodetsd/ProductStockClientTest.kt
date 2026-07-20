package ru.local.barcodetsd

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Base64

class ProductStockClientTest {

    private lateinit var server: HttpServer
    private lateinit var serviceUrl: String
    private lateinit var recordedRequest: RecordedRequest
    private val client = ProductStockClient()

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
    fun resolveSendsExactContractShapeAndBasicAuth() {
        enqueueResponse(200, foundResponse("12.500"))

        val result = client.resolve(serviceUrl, "user", "secret", ITEM_REF)

        assertEquals(ProductStockResult.Found(ITEM_REF, BigDecimal("12.500")), result)
        assertEquals("POST", recordedRequest.method)
        assertEquals("/retail/hs/BarcodeTSD/v1/product-stock/resolve", recordedRequest.path)
        val expectedAuth = "Basic " + Base64.getEncoder()
            .encodeToString("user:secret".toByteArray(StandardCharsets.UTF_8))
        assertEquals(expectedAuth, recordedRequest.authorization)

        val request = JSONObject(recordedRequest.body)
        assertEquals(setOf("itemRef"), request.keys().asSequence().toSet())
        assertEquals(ITEM_REF, request.getString("itemRef"))
    }

    @Test
    fun resolveAcceptsZeroAndNegativeQuantities() {
        enqueueResponse(200, foundResponse("-0.125"))

        val result = client.resolve(serviceUrl, "", "", ITEM_REF)

        assertEquals(ProductStockResult.Found(ITEM_REF, BigDecimal("-0.125")), result)
    }

    @Test
    fun resolveMapsHttp400ToInvalidInput() {
        enqueueResponse(400, """{"error":"invalid_request","message":"Некорректная ссылка"}""")

        val result = client.resolve(serviceUrl, "", "", ITEM_REF)

        assertEquals(ProductStockResult.InvalidInput("Некорректная ссылка"), result)
    }

    @Test
    fun resolveMapsHttp401ToAuthError() {
        enqueueResponse(401, "")

        val result = client.resolve(serviceUrl, "", "", ITEM_REF)

        assertTrue(result is ProductStockResult.AuthError)
    }

    @Test
    fun resolveMapsHttp409AndRetainsErrorCode() {
        enqueueResponse(
            409,
            """{"error":"warehouse_not_configured","message":"Склад не настроен"}"""
        )

        val result = client.resolve(serviceUrl, "", "", ITEM_REF)

        assertEquals(
            ProductStockResult.Conflict("warehouse_not_configured", "Склад не настроен"),
            result
        )
    }

    @Test
    fun resolveRejectsMismatchedItemRef() {
        enqueueResponse(200, foundResponse("1", OTHER_ITEM_REF))

        val result = client.resolve(serviceUrl, "", "", ITEM_REF)

        assertTrue(result is ProductStockResult.ServerError)
    }

    @Test
    fun resolveRejectsQuantityWithExcessPrecision() {
        enqueueResponse(200, foundResponse("1.0001"))

        val result = client.resolve(serviceUrl, "", "", ITEM_REF)

        assertTrue(result is ProductStockResult.ServerError)
    }

    @Test
    fun resolveAcceptsFullEndpointWithoutDuplicatingPath() {
        enqueueResponse(200, foundResponse("0"))

        val result = client.resolve("$serviceUrl/v1/product-stock/resolve", "", "", ITEM_REF)

        assertEquals(ProductStockResult.Found(ITEM_REF, BigDecimal.ZERO), result)
        assertEquals("/retail/hs/BarcodeTSD/v1/product-stock/resolve", recordedRequest.path)
    }

    @Test
    fun compactDecimalRemovesOnlyUnneededTrailingZeros() {
        assertEquals("12.5", BigDecimal("12.500").toCompactDecimal())
        assertEquals("0", BigDecimal.ZERO.toCompactDecimal())
        assertEquals("-0.125", BigDecimal("-0.125").toCompactDecimal())
    }

    private fun enqueueResponse(code: Int, body: String) {
        server.createContext("/retail/hs/BarcodeTSD/v1/product-stock/resolve") { exchange ->
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

    private fun foundResponse(quantity: String, itemRef: String = ITEM_REF): String =
        """{"status":"found","itemRef":"$itemRef","quantity":$quantity}"""

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
        private const val ITEM_REF = "14f2c4da-8238-4a9f-bf56-3ec3a2f4d86f"
        private const val OTHER_ITEM_REF = "a3fe213b-334b-48ed-ae7a-f55cc3eac9f5"
    }
}
