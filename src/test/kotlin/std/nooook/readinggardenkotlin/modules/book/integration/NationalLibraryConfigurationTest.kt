package std.nooook.readinggardenkotlin.modules.book.integration

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NationalLibraryConfigurationTest {
    @Test
    fun `missing keys permit startup but refuse book lookup`() {
        ApplicationContextRunner()
            .withUserConfiguration(
                KakaoBookCatalogClient::class.java,
                KakaoBookRestClient::class.java,
                NationalLibraryRestClient::class.java,
            )
            .withPropertyValues("app.integrations.nl-api-key=", "app.integrations.kakao-rest-api-key=")
            .run { context ->
                assertNull(context.startupFailure)
                val client = context.getBean(BookCatalogClient::class.java)
                val error = assertFailsWith<ResponseStatusException> { client.searchBooks("책", 1, 30) }
                assertEquals(503, error.statusCode.value())
            }
    }
}
