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
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Base64

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
            """{"status":"found","barcode":"4600000000011","matches":[{"itemRef":"item-1","name":"Товар 1"}]}"""
        )

        val result = client.resolve(serviceUrl, "", "", "4600000000011")

        assertTrue(result is LookupResult.Found)
        result as LookupResult.Found
        assertEquals("4600000000011", result.barcode)
        assertEquals("item-1", result.itemRef)
        assertEquals("Товар 1", result.name)
        assertEquals(LookupSource.ONLINE, result.source)
    }

    @Test
    fun foundResultDistinguishesOnlineAndCachedSources() {
        val online = LookupResult.Found("1", "item-1", "Товар", LookupSource.ONLINE)
        val cached = LookupResult.Found("1", "item-1", "Товар", LookupSource.CACHED)

        assertEquals(LookupSource.ONLINE, online.source)
        assertEquals(LookupSource.CACHED, cached.source)
    }

    @Test
    fun onlyConnectionErrorAllowsCachedFallback() {
        val resultsWithoutFallback = listOf(
            LookupResult.Found("1", "item-1", "Товар"),
            LookupResult.NotFound("1", null),
            LookupResult.Ambiguous(
                "1",
                listOf(
                    ProductCandidate("item-a", "Товар A"),
                    ProductCandidate("item-b", "Товар B")
                )
            ),
            LookupResult.InvalidInput("Некорректный запрос"),
            LookupResult.AuthError("Нет доступа"),
            LookupResult.ServerError("Ошибка ответа сервера")
        )

        resultsWithoutFallback.forEach { result ->
            assertFalse(result.allowsCachedFallback())
        }
        assertTrue(LookupResult.ConnectionError("Нет соединения").allowsCachedFallback())
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
    fun resolveRetainsAmbiguousCandidatesInResponseOrder() {
        enqueueResponse(
            200,
            """{"status":"ambiguous","barcode":"22","matches":[{"itemRef":"item-a","name":"Товар A"},{"itemRef":"item-b","name":"Товар B"}]}"""
        )

        val result = client.resolve(serviceUrl, "", "", "22")

        assertTrue(result is LookupResult.Ambiguous)
        result as LookupResult.Ambiguous
        assertEquals("22", result.barcode)
        assertEquals(
            listOf(
                ProductCandidate("item-a", "Товар A"),
                ProductCandidate("item-b", "Товар B")
            ),
            result.candidates
        )
    }

    @Test
    fun resolveMapsAmbiguousWithFewerThanTwoCandidatesToServerError() {
        enqueueResponse(
            200,
            """{"status":"ambiguous","barcode":"22","matches":[{"itemRef":"item-a","name":"Товар A"}]}"""
        )

        val result = client.resolve(serviceUrl, "", "", "22")

        assertTrue(result is LookupResult.ServerError)
    }

    @Test
    fun resolveMapsAmbiguousCandidateWithoutItemRefToServerError() {
        enqueueResponse(
            200,
            """{"status":"ambiguous","barcode":"22","matches":[{"itemRef":"item-a","name":"Товар A"},{"name":"Товар B"}]}"""
        )

        val result = client.resolve(serviceUrl, "", "", "22")

        assertTrue(result is LookupResult.ServerError)
    }

    @Test
    fun resolveMapsAmbiguousCandidateWithBlankNameToServerError() {
        enqueueResponse(
            200,
            """{"status":"ambiguous","barcode":"22","matches":[{"itemRef":"item-a","name":"Товар A"},{"itemRef":"item-b","name":"  "}]}"""
        )

        val result = client.resolve(serviceUrl, "", "", "22")

        assertTrue(result is LookupResult.ServerError)
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
    fun resolveSendsBasicAuthHeader() {
        val expectedAuth = "Basic " + Base64.getEncoder()
            .encodeToString("user:secret".toByteArray(StandardCharsets.UTF_8))
        enqueueResponse(
            200,
            """{"status":"found","barcode":"1","matches":[{"itemRef":"item-1","name":"Товар"}]}""",
            expectedAuth
        )

        val result = client.resolve(serviceUrl, "user", "secret", "1")

        assertTrue(result is LookupResult.Found)
    }

    @Test
    fun resolveMapsHttp401ToAuthError() {
        enqueueResponse(401, """{"message":"Нет доступа"}""")

        val result = client.resolve(serviceUrl, "", "", "1")

        assertTrue(result is LookupResult.AuthError)
    }

    @Test
    fun resolveMapsHttp403ToAuthError() {
        enqueueResponse(403, """{"message":"Доступ запрещен"}""")

        val result = client.resolve(serviceUrl, "", "", "1")

        assertTrue(result is LookupResult.AuthError)
        result as LookupResult.AuthError
        assertEquals("Доступ запрещен", result.message)
    }

    @Test
    fun resolveMapsHttp500ToServerError() {
        enqueueResponse(500, """{"message":"Ошибка 1С"}""")

        val result = client.resolve(serviceUrl, "", "", "1")

        assertTrue(result is LookupResult.ServerError)
        result as LookupResult.ServerError
        assertEquals("Ошибка 1С", result.message)
    }

    @Test
    fun resolveMapsMalformedJsonToServerError() {
        enqueueResponse(200, """{""")

        val result = client.resolve(serviceUrl, "", "", "1")

        assertTrue(result is LookupResult.ServerError)
    }

    @Test
    fun resolveMapsFoundWithoutNameToServerError() {
        enqueueResponse(
            200,
            """{"status":"found","barcode":"1","matches":[{"itemRef":"item-1"}]}"""
        )

        val result = client.resolve(serviceUrl, "", "", "1")

        assertTrue(result is LookupResult.ServerError)
        result as LookupResult.ServerError
        assertEquals("1С вернула found без ссылки или наименования товара.", result.message)
    }

    @Test
    fun resolveMapsFoundWithoutItemRefToServerError() {
        enqueueResponse(
            200,
            """{"status":"found","barcode":"1","matches":[{"name":"Товар"}]}"""
        )

        val result = client.resolve(serviceUrl, "", "", "1")

        assertTrue(result is LookupResult.ServerError)
        result as LookupResult.ServerError
        assertEquals("1С вернула found без ссылки или наименования товара.", result.message)
    }

    @Test
    fun resolveMapsUnknownStatusToServerError() {
        enqueueResponse(
            200,
            """{"status":"unexpected","barcode":"1","matches":[]}"""
        )

        val result = client.resolve(serviceUrl, "", "", "1")

        assertTrue(result is LookupResult.ServerError)
        result as LookupResult.ServerError
        assertEquals("1С вернула неизвестный статус: unexpected.", result.message)
    }

    @Test
    fun resolveSendsOnlyBarcodeInRequestBody() {
        enqueueResponse(
            200,
            """{"status":"found","barcode":"4600000000011","matches":[{"itemRef":"item-1","name":"Товар"}]}""",
            expectedBarcode = "4600000000011"
        )

        val result = client.resolve(serviceUrl, "user", "secret", "4600000000011")

        assertTrue(result is LookupResult.Found)
    }

    @Test
    fun resolveMapsMalformedUrlToInvalidInput() {
        val result = client.resolve("not a url", "", "", "123")

        assertTrue(result is LookupResult.InvalidInput)
    }

    @Test
    fun resolveMapsUnsupportedUrlSchemeToInvalidInput() {
        val result = client.resolve("file:///tmp/service", "", "", "123")

        assertTrue(result is LookupResult.InvalidInput)
    }

    @Test
    fun resolveAcceptsFullEndpointUrl() {
        enqueueResponse(
            200,
            """{"status":"found","barcode":"1","matches":[{"itemRef":"item-1","name":"Товар"}]}"""
        )

        val result = client.resolve("$serviceUrl/v1/barcode/resolve", "", "", "1")

        assertTrue(result is LookupResult.Found)
    }

    @Test
    fun resolveAcceptsProductLookupBaseUrlWithoutDuplicatingSuffix() {
        enqueueResponse(
            200,
            """{"status":"found","barcode":"1","matches":[{"itemRef":"item-1","name":"Товар"}]}""",
            path = "/retail/hs/BarcodeTSD/ProductLookup/v1/barcode/resolve"
        )

        val result = client.resolve("$serviceUrl/ProductLookup", "", "", "1")

        assertTrue(result is LookupResult.Found)
    }

    private fun enqueueResponse(
        code: Int,
        body: String,
        expectedAuth: String? = null,
        expectedBarcode: String? = null,
        path: String = "/retail/hs/BarcodeTSD/v1/barcode/resolve"
    ) {
        server.createContext(path) { exchange ->
            assertEquals("POST", exchange.requestMethod)
            if (expectedAuth != null) {
                assertEquals(expectedAuth, exchange.requestHeaders.getFirst("Authorization"))
            }
            val requestBody = exchange.requestBody.use { input ->
                String(input.readBytes(), StandardCharsets.UTF_8)
            }
            assertTrue(requestBody.contains("\"barcode\""))
            if (expectedBarcode != null) {
                val requestJson = JSONObject(requestBody)
                assertEquals(1, requestJson.length())
                assertEquals(expectedBarcode, requestJson.getString("barcode"))
            }
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
