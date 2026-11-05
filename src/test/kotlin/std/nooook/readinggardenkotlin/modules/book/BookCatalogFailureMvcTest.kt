package std.nooook.readinggardenkotlin.modules.book

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.TestcontainersConfiguration
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.book.integration.BookCatalogClient

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class BookCatalogFailureMvcTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var bookCatalogClient: BookCatalogClient

    @Test
    fun `missing catalog key returns service unavailable`() {
        given(bookCatalogClient.searchBooks("해리포터", 2, 10))
            .willThrow(ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "catalog key missing"))

        mockMvc.perform(searchRequest())
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.resp_code").value(503))
            .andExpect(jsonPath("$.resp_msg").value("catalog key missing"))
    }

    @Test
    fun `catalog upstream error returns bad gateway`() {
        given(bookCatalogClient.searchBooks("해리포터", 2, 10))
            .willThrow(ResponseStatusException(HttpStatus.BAD_GATEWAY, "catalog upstream error"))

        mockMvc.perform(searchRequest())
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.resp_code").value(502))
            .andExpect(jsonPath("$.resp_msg").value("catalog upstream error"))
    }

    @Test
    fun `empty catalog search remains successful`() {
        given(bookCatalogClient.searchBooks("해리포터", 2, 10))
            .willReturn(mapOf("item" to emptyList<Any>()))

        mockMvc.perform(searchRequest())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.data.item").isArray)
            .andExpect(jsonPath("$.data.item").isEmpty)
    }

    @Test
    fun `missing isbn detail returns not found`() {
        given(bookCatalogClient.getBookDetailByIsbn("9788937462788"))
            .willReturn(mapOf("item" to emptyList<Any>()))

        mockMvc.perform(
            get("/api/v1/book/detail-isbn")
                .with(bookAuth())
                .queryParam("query", "9788937462788"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resp_code").value(404))
    }

    private fun searchRequest() =
        get("/api/v1/book/search")
            .with(bookAuth())
            .queryParam("query", "해리포터")
            .queryParam("start", "2")
            .queryParam("maxResults", "10")

    private fun bookAuth() =
        authentication(
            UsernamePasswordAuthenticationToken(
                LegacyAuthenticationPrincipal(1L, "테스터"),
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            ),
        )
}
