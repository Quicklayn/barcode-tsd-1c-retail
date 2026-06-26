package ru.local.barcodetsd

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class BarcodeLookupClientTest {

    private lateinit var server: HttpServer
    private lateinit var serviceUrl: String
    private val client = BarcodeLookupClient()

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
    fun resolveReturnsFoundProductName() {
        enqueueResponse(
            200,
            """{"status":"found","barcode":"4600000000011","matches":[{"name":"Товар 1"}]}"""
        )

        val result = client.resolve(serviceUrl, "", "", "4600000000011")

        assertTrue(result is LookupResult.Found)
        result as LookupResult.Found
        assertEquals("4600000000011", result.barcode)
        assertEquals("Товар 1", result.name)
    }

    @Test
    fun resolveReturnsNotFoundMessage() {
        enqueueResponse(
            200,
            """{"status":"not_found","barcode":"404","message":"Номенклатура не найдена"}"""
        )

        val result = client.resolve(serviceUrl, "", "", "404")

        assertTrue(result is LookupResult.NotFound)
        result as LookupResult.NotFound
        assertEquals("404", result.barcode)
        assertEquals("Номенклатура не найдена", result.message)
    }

    @Test
    fun resolveReturnsAmbiguousNames() {
        enqueueResponse(
            200,
            """{"status":"ambiguous","barcode":"22","matches":[{"name":"Товар A"},{"name":"Товар B"}]}"""
        )

        val result = client.resolve(serviceUrl, "", "", "22")

        assertTrue(result is LookupResult.Ambiguous)
        result as LookupResult.Ambiguous
        assertEquals(listOf("Товар A", "Товар B"), result.names)
    }

    @Test
    fun resolveMapsHttp400ToInvalidInput() {
        enqueueResponse(400, """{"message":"Пустой штрихкод"}""")

        val result = client.resolve(serviceUrl, "", "", "")

        assertTrue(result is LookupResult.InvalidInput)
        result as LookupResult.InvalidInput
        assertEquals("Пустой штрихкод", result.message)
    }

    @Test
    fun resolveMapsMalformedUrlToInvalidInput() {
        val result = client.resolve("not a url", "", "", "123")

        assertTrue(result is LookupResult.InvalidInput)
    }

    private fun enqueueResponse(code: Int, body: String) {
        server.createContext("/retail/hs/BarcodeTSD/v1/barcode/resolve") { exchange ->
            assertEquals("POST", exchange.requestMethod)
            val requestBody = exchange.requestBody.use { input ->
                String(input.readBytes(), StandardCharsets.UTF_8)
            }
            assertTrue(requestBody.contains("\"barcode\""))
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
}
