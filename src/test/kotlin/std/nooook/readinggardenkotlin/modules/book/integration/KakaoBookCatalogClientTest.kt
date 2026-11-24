package std.nooook.readinggardenkotlin.modules.book.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class KakaoBookCatalogClientTest {
    private val kakaoBuilder = RestClient.builder()
    private val libraryBuilder = RestClient.builder()
    private val kakaoServer = MockRestServiceServer.bindTo(kakaoBuilder).build()
    private val libraryServer = MockRestServiceServer.bindTo(libraryBuilder).build()
    private val kakao = KakaoBookRestClient("synthetic-kakao-key", kakaoBuilder.build())
    private val library = NationalLibraryRestClient("synthetic-library-key", libraryBuilder.build())
    private val client: BookCatalogClient = KakaoBookCatalogClient(kakao, library)

    @Test
    fun `default size reads two Kakao pages and preserves legacy title payload`() {
        expectKakao("title", "합성", 1, 50, kakaoPage(120, false, 1..50))
        expectKakao("title", "합성", 2, 50, kakaoPage(120, false, 51..100))

        val result = client.searchBooks("합성", 1, 100)

        assertEquals("합성", result["query"])
        assertEquals(120, result["totalResults"])
        assertEquals(1, result["startIndex"])
        assertEquals(100, result["itemsPerPage"])
        assertEquals(100, items(result).size)
        assertEquals(kakaoItem("제목 1", 0), items(result).first())
        assertEquals("제목 100", items(result).last()["title"])
        verifyServers()
    }

    @Test
    fun `offset is applied to raw Kakao rows before invalid records are filtered`() {
        expectKakao("title", "합성", 2, 50, kakaoPage(150, false, 51..100, invalid = 61))
        expectKakao("title", "합성", 3, 50, kakaoPage(150, true, 101..150))

        val result = client.searchBooks("합성", 2, 60)

        assertEquals(150, result["totalResults"])
        assertEquals(2, result["startIndex"])
        assertEquals(60, result["itemsPerPage"])
        assertEquals(59, items(result).size)
        assertEquals("제목 62", items(result).first()["title"])
        assertEquals("제목 120", items(result).last()["title"])
        verifyServers()
    }

    @Test
    fun `pages past the Kakao limit preserve the total without returning out of range books`() {
        expectKakao("title", "합성", 1, 1, kakaoPage(1600, false, 1..1))

        val result = client.searchBooks("합성", 51, 30)

        assertEquals(51, result["startIndex"])
        assertEquals(1600, result["totalResults"])
        assertEquals(emptyList<Map<String, Any?>>(), items(result))
        verifyServers()
    }

    @Test
    fun `Kakao upstream errors do not expose provider content or credentials`() {
        kakaoServer.expect { assertEquals(HttpMethod.GET, it.method) }
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("synthetic-kakao-key and provider error"))

        val error = assertThrows(ResponseStatusException::class.java) { client.searchBooks("합성", 1, 30) }

        assertEquals(HttpStatus.BAD_GATEWAY, error.statusCode)
        assertFalse(error.stackTraceToString().contains("synthetic-kakao-key"))
        assertFalse(error.stackTraceToString().contains("provider error"))
        verifyServers()
    }

    @Test
    fun `both ISBN routes merge same edition library pages with primary Kakao fields`() {
        for (detail in listOf(false, true)) {
            expectKakao("isbn", ISBN13, 1, 50, kakaoResponse(kakaoDocument("카카오 제목")))
            expectLibrary(ISBN13, LIBRARY_BOOK)

            val result = lookup(detail, "0-306-40615-2")

            assertEquals(ISBN13, result["query"])
            assertEquals(1, result["totalResults"])
            assertEquals(kakaoItem("카카오 제목", 320), items(result).single())
            verifyServers()
            kakaoServer.reset()
            libraryServer.reset()
        }
    }

    @Test
    fun `conflicting explicit ISBN13 is not matched through ISBN10 and uses legacy library payload`() {
        for (detail in listOf(false, true)) {
            expectKakao("isbn", ISBN13, 1, 50, kakaoResponse(kakaoDocument("다른 판본", "0306406152 9780131103627")))
            expectLibrary(ISBN13, LIBRARY_BOOK)

            val result = lookup(detail, "0-306-40615-2")

            assertEquals(ISBN13, result["query"])
            assertEquals(1, result["totalResults"])
            assertEquals(
                mapOf(
                    "title" to "도서관 제목", "author" to "도서관 저자", "description" to "도서관 소개",
                    "isbn13" to ISBN13, "cover" to "https://example.test/library.jpg",
                    "publisher" to "도서관 출판사", "subInfo" to mapOf("itemPage" to 320),
                ),
                items(result).single(),
            )
            assertEquals("800", result["searchCategoryId"])
            assertEquals("문학", result["searchCategoryName"])
            verifyServers()
            kakaoServer.reset()
            libraryServer.reset()
        }
    }

    @Test
    fun `unavailable library leaves exact Kakao book usable with zero pages`() {
        expectKakao("isbn", ISBN13, 1, 50, kakaoResponse(kakaoDocument("카카오 제목")))
        libraryServer.expect { assertEquals(HttpMethod.GET, it.method) }
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))

        val result = client.searchBookByIsbn(ISBN13)

        assertEquals(kakaoItem("카카오 제목", 0), items(result).single())
        verifyServers()
    }

    @Test
    fun `missing same edition page stays zero and missing Kakao cover uses same edition library cover`() {
        expectKakao("isbn", ISBN13, 1, 50, kakaoResponse(kakaoDocument("카카오 제목", thumbnail = "")))
        expectLibrary(ISBN13, """{"TOTAL_COUNT":"2","docs":[
            {"TITLE":"다른 판본","EA_ISBN":"9780131103627","PAGE":"999","TITLE_URL":"https://example.test/wrong.jpg"},
            {"TITLE":"같은 판본","EA_ISBN":"$ISBN13","TITLE_URL":"https://example.test/library.jpg"}
        ]}""")

        val item = items(client.getBookDetailByIsbn(ISBN13)).single()

        assertEquals(kakaoItem("카카오 제목", 0, "https://example.test/library.jpg"), item)
        verifyServers()
    }

    @Test
    fun `another library edition cannot supply page or cover`() {
        expectKakao("isbn", ISBN13, 1, 50, kakaoResponse(kakaoDocument("카카오 제목", thumbnail = "")))
        expectLibrary(ISBN13, """{"TOTAL_COUNT":"1","docs":[
            {"TITLE":"다른 판본","EA_ISBN":"9780131103627","PAGE":"999","TITLE_URL":"https://example.test/wrong.jpg"}
        ]}""")

        val item = items(client.searchBookByIsbn(ISBN13)).single()

        assertEquals(0, (item["subInfo"] as Map<*, *>)["itemPage"])
        assertNull(item["cover"])
        verifyServers()
    }

    @Test
    fun `invalid search inputs fail without provider requests`() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException::class.java) {
            client.searchBooks("합성", 0, 60)
        }.statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException::class.java) {
            client.searchBooks("합성", 1, 101)
        }.statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException::class.java) {
            client.searchBookByIsbn("9780306406158")
        }.statusCode)
        verifyServers()
    }

    @Test
    fun `no Kakao key delegates title and both ISBN routes to library`() {
        val noKakao: BookCatalogClient = KakaoBookCatalogClient(KakaoBookRestClient(" ", kakaoBuilder.build()), library)
        expectLibrary("합성", LIBRARY_BOOK, "title")
        val title = noKakao.searchBooks("합성", 1, 10)
        assertEquals("도서관 제목", items(title).single()["title"])
        verifyServers()
        libraryServer.reset()

        for (detail in listOf(false, true)) {
            expectLibrary(ISBN13, LIBRARY_BOOK)
            val result = if (detail) noKakao.getBookDetailByIsbn(ISBN13) else noKakao.searchBookByIsbn(ISBN13)
            assertEquals("도서관 제목", items(result).single()["title"])
            assertEquals(320, (items(result).single()["subInfo"] as Map<*, *>)["itemPage"])
            verifyServers()
            libraryServer.reset()
        }
    }

    private fun lookup(detail: Boolean, isbn: String): Map<String, Any?> =
        if (detail) client.getBookDetailByIsbn(isbn) else client.searchBookByIsbn(isbn)

    private fun expectKakao(target: String, query: String, page: Int, size: Int, body: String) {
        kakaoServer.expect { request ->
            assertEquals(HttpMethod.GET, request.method)
            assertEquals("KakaoAK synthetic-kakao-key", request.headers.getFirst(HttpHeaders.AUTHORIZATION))
            val params = params(request.uri.rawQuery)
            assertEquals(target, params["target"])
            assertEquals(query, params["query"])
            assertEquals(page.toString(), params["page"])
            assertEquals(size.toString(), params["size"])
        }.andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }

    private fun expectLibrary(query: String, body: String, field: String = "isbn") {
        libraryServer.expect { request ->
            assertEquals(HttpMethod.GET, request.method)
            assertEquals(query, params(request.uri.rawQuery)[field])
        }.andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }

    private fun params(raw: String): Map<String, String> = raw.split('&').associate {
        val parts = it.split('=', limit = 2)
        parts[0] to URLDecoder.decode(parts.getOrElse(1) { "" }, StandardCharsets.UTF_8)
    }

    private fun verifyServers() {
        kakaoServer.verify()
        libraryServer.verify()
    }

    @Suppress("UNCHECKED_CAST")
    private fun items(result: Map<String, Any?>): List<Map<String, Any?>> = result["item"] as List<Map<String, Any?>>

    private fun kakaoPage(total: Int, end: Boolean, range: IntRange, invalid: Int = -1): String =
        """{"meta":{"total_count":$total,"is_end":$end},"documents":[${range.joinToString(",") {
            if (it == invalid) kakaoDocument("", ISBN13) else kakaoDocument("제목 $it")
        }}]}"""

    private fun kakaoResponse(document: String): String =
        """{"meta":{"total_count":1,"is_end":true},"documents":[$document]}"""

    private fun kakaoDocument(title: String, isbn: String = "0306406152 $ISBN13", thumbnail: String = "https://example.test/kakao.jpg"): String =
        """{"title":"$title","isbn":"$isbn","authors":["저자 하나","저자 둘"],"contents":"카카오 소개","publisher":"카카오 출판사","thumbnail":"$thumbnail"}"""

    private fun kakaoItem(title: String, pages: Int, cover: String? = "https://example.test/kakao.jpg"): Map<String, Any?> =
        mapOf(
            "title" to title, "author" to "저자 하나, 저자 둘", "description" to "카카오 소개",
            "isbn13" to ISBN13, "cover" to cover, "publisher" to "카카오 출판사",
            "subInfo" to mapOf("itemPage" to pages),
        )

    companion object {
        private const val ISBN13 = "9780306406157"
        private const val LIBRARY_BOOK = """{"TOTAL_COUNT":"1","docs":[{
            "TITLE":"도서관 제목","AUTHOR":"도서관 저자","BOOK_INTRODUCTION":"도서관 소개",
            "EA_ISBN":"9780306406157","TITLE_URL":"https://example.test/library.jpg",
            "PUBLISHER":"도서관 출판사","PAGE":"320","KDC":"800"
        }]}"""
    }
}
