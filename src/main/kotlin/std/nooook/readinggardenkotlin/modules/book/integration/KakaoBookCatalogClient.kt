package std.nooook.readinggardenkotlin.modules.book.integration

import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
internal class KakaoBookCatalogClient(
    private val kakao: KakaoBookRestClient,
    private val nationalLibrary: NationalLibraryRestClient,
) : BookCatalogClient {
    override fun searchBooks(query: String, start: Int, maxResults: Int): Map<String, Any?> {
        if (!kakao.isConfigured) return nationalLibrary.searchBooks(query, start, maxResults)
        validateBookSearch(query, start, maxResults)
        val searchTerm = query.trim()

        // Keep the legacy offset when a requested page spans more than Kakao's 50 rows.
        val upstreamSize = maxResults.coerceAtMost(KAKAO_MAX_PAGE_SIZE)
        val offset = (start - 1L) * maxResults
        val firstPage = offset / upstreamSize + 1
        if (firstPage > KAKAO_MAX_PAGE) {
            val total = kakao.search(searchTerm, 1, 1).totalResults
            return payload(query, start, maxResults, total, emptyList())
        }
        val lastPage = minOf((offset + maxResults - 1) / upstreamSize + 1, KAKAO_MAX_PAGE)
        val books = mutableListOf<Map<String, Any?>?>()
        var total = 0
        for (page in firstPage..lastPage) {
            val response = kakao.search(searchTerm, page.toInt(), upstreamSize)
            if (page == firstPage) total = response.totalResults
            books += response.items
            if (response.isEnd) break
        }
        val items = books.drop((offset % upstreamSize).toInt()).take(maxResults).filterNotNull()
        return payload(query, start, maxResults, total, items)
    }

    override fun searchBookByIsbn(query: String): Map<String, Any?> {
        if (!kakao.isConfigured) return nationalLibrary.searchBookByIsbn(query)
        val isbn = requireBookIsbn(query)
        val books = kakao.search(isbn, 1, KAKAO_MAX_PAGE_SIZE, target = "isbn").items
            .filterNotNull()
            .filter { it["isbn13"] == isbn }
        if (books.isEmpty()) return nationalLibrary.searchBookByIsbn(isbn)

        val library = try {
            nationalLibrary.searchBookByIsbn(isbn)
        } catch (_: ResponseStatusException) {
            // Kakao already returned the book, so a page lookup failure must not block registration.
            emptyMap()
        }
        val libraryBooks = (library["item"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        val itemPage = libraryBooks.firstNotNullOfOrNull { book ->
            ((book["subInfo"] as? Map<*, *>)?.get("itemPage") as? Int)?.takeIf { it > 0 }
        } ?: 0
        val libraryCover = libraryBooks.firstNotNullOfOrNull { it["cover"] as? String }
        val items = books.map { book ->
            book + mapOf(
                "cover" to (book["cover"] ?: libraryCover),
                "subInfo" to mapOf("itemPage" to itemPage),
            )
        }
        return payload(isbn, 1, 100, items.size, items) + mapOf(
            "searchCategoryId" to (library["searchCategoryId"] ?: 0),
            "searchCategoryName" to (library["searchCategoryName"] ?: ""),
        )
    }

    override fun getBookDetailByIsbn(query: String): Map<String, Any?> = searchBookByIsbn(query)

    private fun payload(
        query: String,
        start: Int,
        size: Int,
        total: Int,
        items: List<Map<String, Any?>>,
    ): Map<String, Any?> = linkedMapOf(
        "query" to query,
        "totalResults" to total,
        "startIndex" to start,
        "itemsPerPage" to size,
        "item" to items,
    )

    private companion object {
        const val KAKAO_MAX_PAGE = 50L
        const val KAKAO_MAX_PAGE_SIZE = 50
    }
}
