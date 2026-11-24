package std.nooook.readinggardenkotlin.modules.book.integration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.UriComponentsBuilder
import java.net.http.HttpClient
import java.time.Duration

internal data class KakaoBookPage(
    val totalResults: Int,
    val isEnd: Boolean,
    val items: List<Map<String, Any?>?>,
)

@Component
internal class KakaoBookRestClient internal constructor(
    private val apiKey: String,
    private val restClient: RestClient,
) {
    @Autowired
    constructor(@Value("\${app.integrations.kakao-rest-api-key:}") apiKey: String) : this(apiKey, createRestClient())

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    private val objectMapper = JsonMapper.builder().build()

    fun search(query: String, page: Int, size: Int, target: String? = null): KakaoBookPage {
        if (!isConfigured) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "도서 조회 서비스 설정이 준비되지 않았습니다.")
        }
        try {
            val uri = UriComponentsBuilder.fromUriString(API_URL)
                .queryParam("query", "{query}")
                .queryParam("page", page)
                .queryParam("size", size)
                .apply { if (target != null) queryParam("target", target) }
                .encode()
                .buildAndExpand(query)
                .toUri()
            val body = restClient.get().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "KakaoAK $apiKey")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(String::class.java)
            val root = objectMapper.readTree(body ?: "")
            val meta = root?.path("meta")
            val total = meta?.path("total_count")
            val isEnd = meta?.path("is_end")
            val documents = root?.path("documents")
            if (meta == null || !meta.isObject || total == null || !total.isIntegralNumber ||
                !total.canConvertToInt() || total.intValue() < 0 || isEnd == null || !isEnd.isBoolean ||
                documents == null || !documents.isArray || documents.size() > size || documents.any { !it.isObject } ||
                (total.intValue() == 0 && !documents.isEmpty)
            ) {
                throw IllegalStateException("Invalid provider response")
            }
            return KakaoBookPage(total.intValue(), isEnd.booleanValue(), documents.map(::book))
        } catch (ex: Exception) {
            logger.warn("Kakao book request failed exception={}", ex.javaClass.simpleName)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "카카오 도서 정보를 불러오지 못했습니다.")
        }
    }

    private fun book(document: JsonNode): Map<String, Any?>? {
        val title = text(document, "title")
        if (title.isBlank()) return null
        val tokens = text(document, "isbn").split(Regex("\\s+"))
        // Some records list a second identifier that does not describe the ISBN-13 edition.
        val isbn = tokens.firstNotNullOfOrNull { token ->
            token.takeIf { it.filterNot { char -> char == '-' }.length == 13 }?.let(::normalizedBookIsbn)
        } ?: tokens.firstNotNullOfOrNull(::normalizedBookIsbn) ?: return null
        return linkedMapOf(
            "title" to title,
            "author" to document.path("authors").takeIf { it.isArray }
                ?.filter { it.isTextual }.orEmpty().joinToString(", ") { it.asText() },
            "description" to text(document, "contents"),
            "publisher" to text(document, "publisher"),
            "cover" to bookCoverUrl(text(document, "thumbnail")),
            "isbn13" to isbn,
            "subInfo" to mapOf("itemPage" to 0),
        )
    }

    private fun text(document: JsonNode, field: String): String =
        document.path(field).takeIf { it.isTextual }?.asText().orEmpty()

    private companion object {
        private const val API_URL = "https://dapi.kakao.com/v3/search/book"
        private val logger = LoggerFactory.getLogger(KakaoBookRestClient::class.java)

        private fun createRestClient(): RestClient = RestClient.builder()
            .requestFactory(
                JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                ).apply { setReadTimeout(Duration.ofSeconds(5)) },
            )
            .build()
    }
}
