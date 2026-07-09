package std.nooook.readinggardenkotlin.modules.memo

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.doReturn
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import std.nooook.readinggardenkotlin.TestcontainersConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.memo.controller.CreateMemoRequest
import std.nooook.readinggardenkotlin.modules.memo.controller.CreateMemoResponse
import std.nooook.readinggardenkotlin.modules.memo.controller.MemoDetailResponse
import std.nooook.readinggardenkotlin.modules.memo.controller.MemoListItemResponse
import std.nooook.readinggardenkotlin.modules.memo.controller.MemoListResponse
import std.nooook.readinggardenkotlin.modules.memo.controller.UpdateMemoRequest
import std.nooook.readinggardenkotlin.modules.memo.service.MemoCommandService
import std.nooook.readinggardenkotlin.modules.memo.service.MemoImageService
import std.nooook.readinggardenkotlin.modules.memo.service.MemoQueryService
import std.nooook.readinggardenkotlin.modules.memo.service.MemoService

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class MemoControllerMvcTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var memoService: MemoService

    @MockitoBean
    private lateinit var memoQueryService: MemoQueryService

    @MockitoBean
    private lateinit var memoCommandService: MemoCommandService

    @MockitoBean
    private lateinit var memoImageService: MemoImageService

    @Test
    fun `get memo should return legacy success envelope`() {
        given(memoService.getMemoList(userId = 1, page = 2, pageSize = 10))
            .willReturn(
                MemoListResponse(
                    current_page = 2,
                    max_page = 3,
                    total = 21,
                    page_size = 10,
                    list = listOf(
                        MemoListItemResponse(
                            id = 9,
                            book_no = 17,
                            book_title = "책 제목",
                            book_author = "저자",
                            book_image_url = "https://example.com/book.jpg",
                            memo_content = "메모 내용",
                            memo_like = true,
                            image_url = "",
                            memo_created_at = "2026-04-06T12:00:00",
                        ),
                    ),
                ),
            )

        mockMvc.perform(
            get("/api/v1/memo/")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1L, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
                .queryParam("page", "2")
                .queryParam("page_size", "10"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 리스트 조회 성공"))
            .andExpect(jsonPath("$.data.current_page").value(2))
            .andExpect(jsonPath("$.data.max_page").value(3))
            .andExpect(jsonPath("$.data.total").value(21))
            .andExpect(jsonPath("$.data.page_size").value(10))
            .andExpect(jsonPath("$.data.list[0].id").value(9))
            .andExpect(jsonPath("$.data.list[0].book_no").value(17))
            .andExpect(jsonPath("$.data.list[0].image_url").value(""))
    }

    @Test
    fun `get memo detail should return legacy success envelope`() {
        given(memoQueryService.getMemoDetail(userId = 1, id = 9))
            .willReturn(
                MemoDetailResponse(
                    id = 9,
                    book_no = 17,
                    book_title = "책 제목",
                    book_author = "저자",
                    book_publisher = "출판사",
                    book_info = "책 소개",
                    memo_content = "메모 내용",
                    image_url = "https://example.com/memo.jpg",
                    memo_created_at = "2026-04-06T12:00:00",
                ),
            )

        mockMvc.perform(
            get("/api/v1/memo/detail")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1L, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
                .queryParam("id", "9"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 상세 조회 성공"))
            .andExpect(jsonPath("$.data.id").value(9))
            .andExpect(jsonPath("$.data.book_no").value(17))
            .andExpect(jsonPath("$.data.image_url").value("https://example.com/memo.jpg"))
    }

    @Test
    fun `create memo should return legacy created envelope`() {
        given(
            memoCommandService.createMemo(
                userId = 1,
                request = CreateMemoRequest(
                    book_no = 17,
                    memo_content = "메모 내용",
                ),
            ),
        ).willReturn(CreateMemoResponse(id = 9))

        mockMvc.perform(
            post("/api/v1/memo/")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1L, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"book_no":17,"memo_content":"메모 내용"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("메모 추가 성공"))
            .andExpect(jsonPath("$.data.id").value(9))
    }

    @Test
    fun `update memo should return legacy success envelope`() {
        given(
            memoCommandService.updateMemo(
                userId = 1,
                id = 9,
                request = UpdateMemoRequest(
                    book_no = 17,
                    memo_content = "수정된 메모",
                ),
            ),
        ).willReturn("메모 수정 성공")

        mockMvc.perform(
            put("/api/v1/memo/")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1L, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
                .queryParam("id", "9")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"book_no":17,"memo_content":"수정된 메모"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 수정 성공"))
    }

    @Test
    fun `update memo should return bad request when id query parameter is missing`() {
        mockMvc.perform(
            put("/api/v1/memo/")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1L, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"book_no":17,"memo_content":"수정된 메모"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("Required parameter 'id' is not present."))
            .andExpect(jsonPath("$.errors[0].parameter").value("id"))
            .andExpect(jsonPath("$.errors[0].expectedType").value("long"))
    }

    @Test
    fun `delete memo should return legacy success envelope`() {
        given(memoCommandService.deleteMemo(userId = 1, id = 9))
            .willReturn("메모 삭제 성공")

        mockMvc.perform(
            delete("/api/v1/memo/")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1L, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
                .queryParam("id", "9"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 삭제 성공"))
    }

    @Test
    fun `delete memo should return bad request when id query parameter is missing`() {
        mockMvc.perform(
            delete("/api/v1/memo/")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1L, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("Required parameter 'id' is not present."))
            .andExpect(jsonPath("$.errors[0].parameter").value("id"))
            .andExpect(jsonPath("$.errors[0].expectedType").value("long"))
    }

    @Test
    fun `upload memo image should return legacy created envelope`() {
        val file = MockMultipartFile(
            "file",
            "memo.png",
            MediaType.IMAGE_PNG_VALUE,
            "image-bytes".toByteArray(),
        )

        doReturn("이미지 업로드 성공")
            .`when`(memoImageService)
            .uploadMemoImage(1, 9, file)

        mockMvc.perform(
            multipart("/api/v1/memo/image")
                .file(file)
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1L, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
                .param("id", "9"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("이미지 업로드 성공"))
    }

    @Test
    fun `upload memo image should require authentication`() {
        val file = MockMultipartFile(
            "file",
            "memo.png",
            MediaType.IMAGE_PNG_VALUE,
            "image-bytes".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/v1/memo/image")
                .file(file)
                .param("id", "9"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resp_code").value(401))
            .andExpect(jsonPath("$.resp_msg").value("Unauthorized"))
    }

    @Test
    fun `upload memo image should return bad request when id query parameter is missing`() {
        val file = MockMultipartFile(
            "file",
            "memo.png",
            MediaType.IMAGE_PNG_VALUE,
            "image-bytes".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/v1/memo/image")
                .file(file)
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1L, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("Required parameter 'id' is not present."))
            .andExpect(jsonPath("$.errors[0].parameter").value("id"))
            .andExpect(jsonPath("$.errors[0].expectedType").value("long"))
    }

    @Test
    fun `delete memo image should return legacy created envelope`() {
        doReturn("이미지 삭제 성공")
            .`when`(memoImageService)
            .deleteMemoImage(1, 9)

        mockMvc.perform(
            delete("/api/v1/memo/image")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1L, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
                .queryParam("id", "9"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("이미지 삭제 성공"))
    }

    @Test
    fun `delete memo image should require authentication`() {
        mockMvc.perform(
            delete("/api/v1/memo/image")
                .queryParam("id", "9"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resp_code").value(401))
            .andExpect(jsonPath("$.resp_msg").value("Unauthorized"))
    }

    @Test
    fun `delete memo image should return bad request when id query parameter is missing`() {
        mockMvc.perform(
            delete("/api/v1/memo/image")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1L, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("Required parameter 'id' is not present."))
            .andExpect(jsonPath("$.errors[0].parameter").value("id"))
            .andExpect(jsonPath("$.errors[0].expectedType").value("long"))
    }

    @Test
    fun `like memo should return legacy success envelope`() {
        given(memoCommandService.toggleMemoLike(userId = 1, id = 9))
            .willReturn("메모 즐겨찾기 추가/해제")

        mockMvc.perform(
            put("/api/v1/memo/like")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1L, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                )
                .queryParam("id", "9"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 즐겨찾기 추가/해제"))
    }

    @Test
    fun `like memo should return bad request when id query parameter is missing`() {
        mockMvc.perform(
            put("/api/v1/memo/like")
                .with(
                    authentication(
                        UsernamePasswordAuthenticationToken(
                            LegacyAuthenticationPrincipal(1L, "테스터"),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        ),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("Required parameter 'id' is not present."))
            .andExpect(jsonPath("$.errors[0].parameter").value("id"))
            .andExpect(jsonPath("$.errors[0].expectedType").value("long"))
    }
}
