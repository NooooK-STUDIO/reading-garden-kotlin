package std.nooook.readinggardenkotlin.modules.book.integration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

@Component
class NationalLibraryRestClient internal constructor(
    private val apiKey: String,
    private val restClient: RestClient,
) : BookCatalogClient {
    @Autowired
    constructor(@Value("\${app.integrations.nl-api-key:}") apiKey: String) : this(apiKey, createRestClient())

    private val objectMapper = JsonMapper.builder().build()

    override fun searchBooks(query: String, start: Int, maxResults: Int): Map<String, Any?> {
        if (query.isBlank() || start < 1 || maxResults !in 1..100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어와 페이지 번호, 조회 개수(1~100)를 확인해 주세요.")
        }
        val response = fetch("title", query.trim(), start, maxResults)
        return payload(response, query, start, maxResults, response["docs"].filter(::isSelectable))
    }

    override fun searchBookByIsbn(query: String): Map<String, Any?> {
        val isbn = normalizedIsbn(query)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 ISBN을 입력해 주세요.")
        val response = fetch("isbn", isbn, 1, 100)
        // ISBN search is prefix-based; never substitute a different edition or a set ISBN.
        val matches = response["docs"].filter { isSelectable(it) && normalizedIsbn(it.path("EA_ISBN").asText()) == isbn }
        return payload(response, isbn, 1, 100, matches).toMutableMap().apply {
            this["totalResults"] = matches.size
            val kdc = matches.firstOrNull()?.path("KDC")?.asText().orEmpty()
            this["searchCategoryId"] = kdc.ifBlank { null } ?: 0
            this["searchCategoryName"] = categoryName(kdc)
        }
    }

    override fun getBookDetailByIsbn(query: String): Map<String, Any?> = searchBookByIsbn(query)

    private fun isSelectable(book: JsonNode): Boolean =
        book.path("TITLE").asText("").isNotBlank() && normalizedIsbn(book.path("EA_ISBN").asText()) != null

    private fun fetch(field: String, query: String, page: Int, size: Int): JsonNode {
        if (apiKey.isBlank()) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "도서 조회 서비스 설정이 준비되지 않았습니다.")
        }
        try {
            val uri = UriComponentsBuilder.fromUriString(API_URL)
                .queryParam("cert_key", "{key}")
                .queryParam("result_style", "json")
                .queryParam("page_no", page)
                .queryParam("page_size", size)
                .queryParam(field, "{query}")
                .encode()
                .buildAndExpand(mapOf("key" to apiKey, "query" to query))
                .toUri()
            val body = restClient.get().uri(uri).accept(MediaType.APPLICATION_JSON).retrieve().body(String::class.java)
            val root = objectMapper.readTree(body ?: "")
            if (root == null || root.path("RESULT").asText().equals("ERROR", ignoreCase = true) ||
                !root.path("ERR_CODE").asText().isNullOrBlank()
            ) {
                throw IllegalStateException("Provider returned an error")
            }
            val total = root.path("TOTAL_COUNT").asText().toIntOrNull()
            if (total == null || total < 0 || !root.path("docs").isArray ||
                root["docs"].any { !it.isObject } || (total == 0 && !root["docs"].isEmpty)
            ) {
                throw IllegalStateException("Invalid provider response")
            }
            return root
        } catch (ex: Exception) {
            // The request URI and provider body may contain the credential; do not log either or the cause.
            logger.warn("National Library book request failed exception={}", ex.javaClass.simpleName)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "국립중앙도서관 도서 정보를 불러오지 못했습니다.")
        }
    }

    private fun payload(
        response: JsonNode,
        query: String,
        page: Int,
        size: Int,
        books: List<JsonNode>,
    ): Map<String, Any?> = linkedMapOf(
        "query" to query,
        "totalResults" to response["TOTAL_COUNT"].asText().toInt(),
        "startIndex" to page,
        "itemsPerPage" to size,
        "item" to books.map { book ->
            linkedMapOf(
                "title" to book.path("TITLE").asText(""),
                "author" to book.path("AUTHOR").asText(""),
                "description" to book.path("BOOK_INTRODUCTION").asText(""),
                "isbn13" to (normalizedIsbn(book.path("EA_ISBN").asText()) ?: ""),
                "cover" to coverUrl(book.path("TITLE_URL").asText()),
                "publisher" to book.path("PUBLISHER").asText(""),
                "subInfo" to mapOf("itemPage" to pageCount(book.path("PAGE").asText())),
            )
        },
    )

    private fun coverUrl(value: String): String? = runCatching {
        val uri = URI(value.trim())
        value.trim().takeIf { uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null }
    }.getOrNull()

    private fun pageCount(value: String): Int {
        // Ambiguous multi-volume or preliminary-page counts must not become fabricated reading targets.
        val digits = Regex("^(\\d+)(?:\\s*(?:p\\.?|쪽|페이지))?$", RegexOption.IGNORE_CASE)
            .matchEntire(value.trim())?.groupValues?.get(1)
        return digits?.toIntOrNull()?.takeIf { it > 0 } ?: 0
    }

    private fun normalizedIsbn(value: String): String? {
        val isbn = value.filterNot { it.isWhitespace() || it == '-' }.uppercase()
        if (isbn.matches(Regex("\\d{9}[\\dX]"))) {
            val checksum = isbn.mapIndexed { index, c -> (10 - index) * (if (c == 'X') 10 else c.digitToInt()) }.sum()
            if (checksum % 11 != 0) return null
            val prefix = "978" + isbn.take(9)
            return prefix + isbn13CheckDigit(prefix)
        }
        return isbn.takeIf {
            it.matches(Regex("97[89]\\d{10}")) && it.last().digitToInt() == isbn13CheckDigit(it.take(12))
        }
    }

    private fun isbn13CheckDigit(prefix: String): Int =
        (10 - prefix.mapIndexed { index, c -> c.digitToInt() * if (index % 2 == 0) 1 else 3 }.sum() % 10) % 10

    private fun categoryName(kdc: String): String =
        if (kdc.matches(Regex("\\d{3}(?:\\.\\d+)?"))) {
            listOf("총류", "철학", "종교", "사회과학", "자연과학", "기술과학", "예술", "언어", "문학", "역사")[kdc.first().digitToInt()]
        } else {
            ""
        }

    companion object {
        private const val API_URL = "https://www.nl.go.kr/seoji/SearchApi.do"
        private val logger = LoggerFactory.getLogger(NationalLibraryRestClient::class.java)

        private fun createRestClient(): RestClient = RestClient.builder()
            .requestFactory(
                JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                ).apply { setReadTimeout(Duration.ofSeconds(15)) },
            )
            .build()
    }
}
