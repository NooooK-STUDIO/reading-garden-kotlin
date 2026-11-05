package std.nooook.readinggardenkotlin.modules.book.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.modules.book.controller.BookDetailResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookLookupResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookSearchResponse
import std.nooook.readinggardenkotlin.modules.book.integration.BookCatalogClient

@Service
class BookService(
    private val bookCatalogClient: BookCatalogClient,
) {
    fun searchBooks(
        query: String,
        start: Int,
        maxResults: Int,
    ): BookSearchResponse = bookCatalogClient.searchBooks(query, start, maxResults)

    fun searchBookByIsbn(query: String): BookLookupResponse = bookCatalogClient.searchBookByIsbn(query)

    fun getBookDetailByIsbn(query: String): BookDetailResponse {
        val response = bookCatalogClient.getBookDetailByIsbn(query)

        val item = (response["item"] as? List<*>)?.firstOrNull() as? Map<*, *>
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "일치하는 도서 정보가 없습니다.")
        val subInfo = item["subInfo"] as? Map<*, *>

        return BookDetailResponse(
            searchCategoryId = response["searchCategoryId"] ?: 0,
            searchCategoryName = (response["searchCategoryName"] as? String).orEmpty(),
            title = (item["title"] as? String).orEmpty(),
            author = (item["author"] as? String).orEmpty(),
            description = (item["description"] as? String).orEmpty(),
            isbn13 = (item["isbn13"] as? String).orEmpty(),
            cover = (item["cover"] as? String)?.takeIf { it.isNotBlank() },
            publisher = (item["publisher"] as? String).orEmpty(),
            itemPage = (subInfo?.get("itemPage") as? Number)?.toInt() ?: 0,
            record = HashMap(),
            memo = HashMap(),
        )
    }
}
