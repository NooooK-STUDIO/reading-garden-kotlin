package std.nooook.readinggardenkotlin.modules.book.integration

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.net.URI

internal fun validateBookSearch(query: String, start: Int, maxResults: Int) {
    if (query.isBlank() || start < 1 || maxResults !in 1..100) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어와 페이지 번호, 조회 개수(1~100)를 확인해 주세요.")
    }
}

internal fun requireBookIsbn(value: String): String =
    normalizedBookIsbn(value) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 ISBN을 입력해 주세요.")

internal fun normalizedBookIsbn(value: String): String? {
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

internal fun bookCoverUrl(value: String): String? = runCatching {
    val trimmed = value.trim()
    val uri = URI(trimmed)
    trimmed.takeIf { uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null }
}.getOrNull()
