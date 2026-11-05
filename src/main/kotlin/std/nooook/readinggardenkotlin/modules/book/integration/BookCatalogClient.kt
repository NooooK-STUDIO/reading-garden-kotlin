package std.nooook.readinggardenkotlin.modules.book.integration

interface BookCatalogClient {
    fun searchBooks(
        query: String,
        start: Int,
        maxResults: Int,
    ): Map<String, Any?>

    fun searchBookByIsbn(query: String): Map<String, Any?>

    fun getBookDetailByIsbn(query: String): Map<String, Any?>
}
