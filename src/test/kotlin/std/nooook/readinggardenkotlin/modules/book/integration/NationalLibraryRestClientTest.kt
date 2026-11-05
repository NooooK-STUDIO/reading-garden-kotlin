package std.nooook.readinggardenkotlin.modules.book.integration

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class NationalLibraryRestClientTest {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = NationalLibraryRestClient(KEY, builder.build())
    private val logger = LoggerFactory.getLogger(NationalLibraryRestClient::class.java) as Logger
    private val logs = ListAppender<ILoggingEvent>().apply { start(); logger.addAppender(this) }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(logs)
        logs.stop()
    }

    @Test
    fun `title query is encoded and results retain legacy fields and page`() {
        server.expect { request ->
            assertEquals(HttpMethod.GET, request.method)
            val raw = request.uri.rawQuery
            assertTrue(raw.contains("title=%ED%95%9C%EA%B8%80%20%26%20%2B"), raw)
            val params = raw.split('&').associate {
                val parts = it.split('=', limit = 2)
                parts[0] to URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
            }
            assertEquals("한글 & +", params["title"])
            assertEquals("3", params["page_no"])
            assertEquals("20", params["page_size"])
            assertEquals(KEY, params["cert_key"])
        }.andRespond(withSuccess(SYNTHETIC_BOOKS, MediaType.APPLICATION_JSON))

        val result = client.searchBooks("한글 & +", 3, 20)
        assertEquals(42, result["totalResults"])
        assertTrue(result["totalResults"] is Int)
        assertEquals(3, result["startIndex"])
        assertEquals(20, result["itemsPerPage"])
        assertEquals("한글 & +", result["query"])
        assertEquals(
            mapOf(
                "title" to "합성 제목", "author" to "합성 저자", "description" to "합성 소개",
                "isbn13" to "9780306406157", "cover" to "https://example.test/cover.jpg",
                "publisher" to "합성 출판사", "subInfo" to mapOf("itemPage" to 320),
            ),
            items(result).first(),
        )
        server.verify()
    }

    @Test
    fun `page counts covers and missing metadata use conservative defaults`() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(SYNTHETIC_BOOKS, MediaType.APPLICATION_JSON))
        val books = items(client.searchBooks("합성", 1, 10))
        assertEquals(listOf(320, 320, 0, 0, 0), books.map { (it["subInfo"] as Map<*, *>)["itemPage"] })
        assertNull(books[1]["cover"])
        assertNull(books[2]["cover"])
        assertNull(books[3]["cover"])
        assertEquals("정보 누락", books[4]["title"])
        assertEquals("", books[4]["author"])
        assertEquals("", books[4]["description"])
        assertEquals("9780306406157", books[4]["isbn13"])
        assertEquals("", books[4]["publisher"])
        assertNull(books[4]["cover"])
        server.verify()
    }

    @Test
    fun `search excludes unusable books but preserves provider page and count`() {
        server.expect { request ->
            assertTrue(request.uri.rawQuery.contains("page_no=2"))
        }.andRespond(withSuccess("""{
          "TOTAL_COUNT":"60","docs":[
            {"TITLE":"ISBN 없음"},
            {"TITLE":"잘못된 체크섬","EA_ISBN":"9780306406158"},
            {"TITLE":"세트 ISBN","EA_ISBN":"9780306406157 (세트)"},
            {"TITLE":" ","EA_ISBN":"9780306406157"}
          ]
        }""", MediaType.APPLICATION_JSON))
        val result = client.searchBooks("합성", 2, 10)
        assertEquals(emptyList<Any>(), result["item"])
        assertEquals(60, result["totalResults"])
        assertEquals(2, result["startIndex"])
        server.verify()
    }

    @Test
    fun `isbn10 converts to isbn13 and excludes prefix and set results`() {
        server.expect { request ->
            assertTrue(request.uri.rawQuery.contains("isbn=9780306406157"))
        }.andRespond(withSuccess(SYNTHETIC_ISBN, MediaType.APPLICATION_JSON))
        val result = client.searchBookByIsbn("0-306-40615-2")
        assertEquals("9780306406157", result["query"])
        assertEquals(1, result["totalResults"])
        assertEquals(listOf("정확한 판본"), items(result).map { it["title"] })
        assertEquals("9780306406157", items(result).single()["isbn13"])
        server.verify()
    }

    @Test
    fun `empty genuine docs returns no items`() {
        server.expect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"TOTAL_COUNT":"0","docs":[]}""", MediaType.APPLICATION_JSON))
        val result = client.searchBooks("없음", 1, 10)
        assertEquals(0, result["totalResults"])
        assertEquals(emptyList<Any>(), result["item"])
        server.verify()
    }

    @Test
    fun `missing key returns 503 without making a request`() {
        val noKeyClient = NationalLibraryRestClient(" ", builder.build())
        val error = assertThrows(ResponseStatusException::class.java) { noKeyClient.searchBooks("책", 1, 10) }
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.statusCode)
        server.verify()
    }

    @Test
    fun `provider errors malformed responses and transport failures are sanitized 502`() {
        val responses = listOf(
            withSuccess("""{"RESULT":"ERROR","ERR_CODE":"011","message":"$PROVIDER_ERROR"}""", MediaType.APPLICATION_JSON),
            withSuccess("""{"docs":[$PROVIDER_ERROR""", MediaType.APPLICATION_JSON),
            withSuccess("""{"TOTAL_COUNT":"1","docs":{},"message":"$PROVIDER_ERROR"}""", MediaType.APPLICATION_JSON),
            withSuccess("""{"TOTAL_COUNT":"0","docs":[{}],"message":"$PROVIDER_ERROR"}""", MediaType.APPLICATION_JSON),
            withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(PROVIDER_ERROR).contentType(MediaType.TEXT_PLAIN),
            withException(IOException("$KEY $PROVIDER_ERROR")),
        )
        responses.forEach { response ->
            server.expect(method(HttpMethod.GET)).andRespond(response)
            val error = assertThrows(ResponseStatusException::class.java) { client.searchBooks("책", 1, 10) }
            assertEquals(HttpStatus.BAD_GATEWAY, error.statusCode)
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            assertFalse(trace.contains(KEY), trace)
            assertFalse(trace.contains(PROVIDER_ERROR), trace)
            server.verify()
            server.reset()
        }
        assertEquals(responses.size, logs.list.size)
        logs.list.forEach { event ->
            val recorded = event.formattedMessage + (event.throwableProxy?.message ?: "")
            assertFalse(recorded.contains(KEY), recorded)
            assertFalse(recorded.contains(PROVIDER_ERROR), recorded)
            assertNull(event.throwableProxy)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun items(result: Map<String, Any?>): List<Map<String, Any?>> = result["item"] as List<Map<String, Any?>>

    companion object {
        private const val KEY = "synthetic-test-key"
        private const val PROVIDER_ERROR = "synthetic-provider-error"

        // Synthetic metadata using the success envelope verified against the live API.
        private const val SYNTHETIC_BOOKS = """{
          "TOTAL_COUNT":"42","docs":[
            {"TITLE":"합성 제목","AUTHOR":"합성 저자","BOOK_INTRODUCTION":"합성 소개","EA_ISBN":"0-306-40615-2","TITLE_URL":"https://example.test/cover.jpg","PUBLISHER":"합성 출판사","PAGE":"320"},
            {"TITLE":"페이지 접미사","EA_ISBN":"9780306406157","PAGE":"320 p.","TITLE_URL":""},
            {"TITLE":"페이지 미상","EA_ISBN":"9780306406157","PAGE":"unknown","TITLE_URL":"not a url"},
            {"TITLE":"복합 페이지","EA_ISBN":"9780306406157","PAGE":"320 p. ; 2 volumes","TITLE_URL":"https://user@example.test/cover"},
            {"TITLE":"정보 누락","EA_ISBN":"9780306406157"}
          ]
        }"""
        private const val SYNTHETIC_ISBN = """{
          "TOTAL_COUNT":"4","docs":[
            {"TITLE":"정확한 판본","EA_ISBN":"0-306-40615-2"},
            {"TITLE":"다른 판본","EA_ISBN":"9780131103627"},
            {"TITLE":"접두어","EA_ISBN":"97803064061570"},
            {"TITLE":"세트","EA_ISBN":"9780306406157 (세트)"}
          ]
        }"""
    }
}
