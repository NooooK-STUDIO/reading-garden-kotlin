package std.nooook.readinggardenkotlin.modules.memo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import std.nooook.readinggardenkotlin.TestcontainersConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoImageEntity
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.repository.PushSettingsRepository
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class MemoControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val refreshTokenRepository: RefreshTokenRepository,
    @Autowired private val pushSettingsRepository: PushSettingsRepository,
    @Autowired private val bookRepository: BookRepository,
    @Autowired private val memoRepository: MemoRepository,
    @Autowired private val memoImageRepository: MemoImageRepository,
) {
    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    companion object {
        private val imagesRoot: Path = Files.createTempDirectory("reading-garden-memo-images")

        @JvmStatic
        @DynamicPropertySource
        fun registerStorageProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.storage.images-root") { imagesRoot.toString() }
            registry.add("spring.servlet.multipart.location") { imagesRoot.resolve("multipart-temp").toString() }
        }
    }

    @BeforeEach
    fun setUp() {
        cleanImagesRoot()
        memoImageRepository.deleteAll()
        memoRepository.deleteAll()
        bookRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        pushSettingsRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun cleanImagesRoot() {
        if (!Files.exists(imagesRoot)) {
            Files.createDirectories(imagesRoot)
            return
        }

        Files.walk(imagesRoot).use { paths ->
            paths
                .sorted(Comparator.reverseOrder())
                .filter { it != imagesRoot }
                .forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `get memo should require authentication`() {
        mockMvc.perform(
            get("/api/v1/memo/"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resp_code").value(401))
            .andExpect(jsonPath("$.resp_msg").value("Unauthorized"))
    }

    @Test
    fun `get memo detail should require authentication`() {
        mockMvc.perform(
            get("/api/v1/memo/detail")
                .queryParam("id", "1"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resp_code").value(401))
            .andExpect(jsonPath("$.resp_msg").value("Unauthorized"))
    }

    @Test
    fun `get memo should return current user's memos with legacy paging and ordering`() {
        val accessToken = signupAndGetAccessToken("memolist@example.com")
        val user = checkNotNull(userRepository.findByEmail("memolist@example.com"))
        val userNo = user.id
        val now = java.time.LocalDateTime.of(2026, 4, 6, 12, 0, 0)

        val book1 = bookRepository.save(
            BookEntity(
                title = "첫 번째 책",
                author = "저자 A",
                publisher = "출판사 A",
                status = 1,
                user = user,
                page = 111,
                imageUrl = "https://example.com/book-a.jpg",
                info = "소개 A",
            ),
        )
        val book2 = bookRepository.save(
            BookEntity(
                title = "두 번째 책",
                author = "저자 B",
                publisher = "출판사 B",
                status = 1,
                user = user,
                page = 222,
                imageUrl = "https://example.com/book-b.jpg",
                info = "소개 B",
            ),
        )
        val book3 = bookRepository.save(
            BookEntity(
                title = "세 번째 책",
                author = "저자 C",
                publisher = "출판사 C",
                status = 1,
                user = user,
                page = 333,
                imageUrl = "https://example.com/book-c.jpg",
                info = "소개 C",
            ),
        )

        val memo1 = memoRepository.save(
            MemoEntity(
                book = book1,
                content = "메모 A",
                createdAt = now.minusHours(2),
                user = user,
                isLiked = true,
            ),
        )
        memoImageRepository.save(
            MemoImageEntity(
                name = "memo-a.png",
                url = "https://example.com/memo-a.png",
                createdAt = now.minusHours(2),
                memo = memo1,
            ),
        )

        memoRepository.save(
            MemoEntity(
                book = book2,
                content = "메모 B",
                createdAt = now.minusHours(1),
                user = user,
                isLiked = true,
            ),
        )

        memoRepository.save(
            MemoEntity(
                book = book3,
                content = "메모 C",
                createdAt = now,
                user = user,
                isLiked = false,
            ),
        )

        mockMvc.perform(
            get("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 리스트 조회 성공"))
            .andExpect(jsonPath("$.data.current_page").value(1))
            .andExpect(jsonPath("$.data.max_page").value(1))
            .andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.page_size").value(10))
            .andExpect(jsonPath("$.data.list.length()").value(3))
            .andExpect(jsonPath("$.data.list[0].memo_content").value("메모 B"))
            .andExpect(jsonPath("$.data.list[0].memo_like").value(true))
            .andExpect(jsonPath("$.data.list[0].image_url").doesNotExist())
            .andExpect(jsonPath("$.data.list[0].book_title").value("두 번째 책"))
            .andExpect(jsonPath("$.data.list[1].memo_content").value("메모 A"))
            .andExpect(jsonPath("$.data.list[1].memo_like").value(true))
            .andExpect(jsonPath("$.data.list[1].image_url").value("https://example.com/memo-a.png"))
            .andExpect(jsonPath("$.data.list[1].book_title").value("첫 번째 책"))
            .andExpect(jsonPath("$.data.list[2].memo_content").value("메모 C"))
            .andExpect(jsonPath("$.data.list[2].memo_like").value(false))
            .andExpect(jsonPath("$.data.list[2].book_title").value("세 번째 책"))
            .andExpect(jsonPath("$.data.list[0].memo_created_at").exists())
            .andExpect(jsonPath("$.data.list[1].memo_created_at").exists())
            .andExpect(jsonPath("$.data.list[2].memo_created_at").exists())
    }

    @Test
    fun `get memo should ignore orphan memos in result and total`() {
        val accessToken = signupAndGetAccessToken("memoorphan@example.com")
        val user = checkNotNull(userRepository.findByEmail("memoorphan@example.com"))
        val userNo = user.id

        val book = bookRepository.save(
            BookEntity(
                title = "정상 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 100,
                imageUrl = "https://example.com/book.jpg",
                info = "소개",
            ),
        )

        memoRepository.save(
            MemoEntity(
                book = book,
                content = "정상 메모",
                user = user,
                isLiked = true,
            ),
        )
        val orphanBook = bookRepository.save(
            BookEntity(
                title = "삭제될 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 100,
                info = "삭제될 책 소개",
            ),
        )
        memoRepository.save(
            MemoEntity(
                book = orphanBook,
                content = "고아 메모",
                user = user,
                isLiked = true,
            ),
        )
        bookRepository.delete(orphanBook)

        mockMvc.perform(
            get("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 리스트 조회 성공"))
            .andExpect(jsonPath("$.data.current_page").value(1))
            .andExpect(jsonPath("$.data.max_page").value(1))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.page_size").value(10))
            .andExpect(jsonPath("$.data.list.length()").value(1))
            .andExpect(jsonPath("$.data.list[0].memo_content").value("정상 메모"))
            .andExpect(jsonPath("$.data.list[0].book_title").value("정상 책"))
    }

    @Test
    fun `get memo detail should return latest image and legacy payload`() {
        val accessToken = signupAndGetAccessToken("memodetail@example.com")
        val user = checkNotNull(userRepository.findByEmail("memodetail@example.com"))
        val userNo = user.id
        val now = java.time.LocalDateTime.of(2026, 4, 6, 12, 0, 0)

        val book = bookRepository.save(
            BookEntity(
                title = "상세 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 123,
                imageUrl = "https://example.com/book-detail.jpg",
                info = "책 소개",
            ),
        )
        val otherBook = bookRepository.save(
            BookEntity(
                title = "다른 책",
                author = "다른 저자",
                publisher = "다른 출판사",
                status = 1,
                user = user,
                page = 456,
                imageUrl = "https://example.com/book-other.jpg",
                info = "다른 책 소개",
            ),
        )
        memoRepository.save(
            MemoEntity(
                book = otherBook,
                content = "첫 메모",
                createdAt = now.plusHours(1),
                user = user,
                isLiked = true,
            ),
        )
        val memo = memoRepository.save(
            MemoEntity(
                book = book,
                content = "상세 메모",
                createdAt = now.minusHours(1),
                user = user,
                isLiked = false,
            ),
        )
        val memoNo = checkNotNull(memo.id)

        memoImageRepository.save(
            MemoImageEntity(
                name = "memo-old.png",
                url = "https://example.com/memo-old.png",
                createdAt = now.minusHours(1),
                memo = memo,
            ),
        )
        memoImageRepository.save(
            MemoImageEntity(
                name = "memo-new.png",
                url = "https://example.com/memo-new.png",
                createdAt = now,
                memo = memo,
            ),
        )

        mockMvc.perform(
            get("/api/v1/memo/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", memoNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 상세 조회 성공"))
            .andExpect(jsonPath("$.data.id").value(memoNo))
            .andExpect(jsonPath("$.data.book_no").value(checkNotNull(book.id)))
            .andExpect(jsonPath("$.data.book_title").value("상세 책"))
            .andExpect(jsonPath("$.data.book_author").value("저자"))
            .andExpect(jsonPath("$.data.book_publisher").value("출판사"))
            .andExpect(jsonPath("$.data.book_info").value("책 소개"))
            .andExpect(jsonPath("$.data.memo_content").value("상세 메모"))
            .andExpect(jsonPath("$.data.image_url").value("https://example.com/memo-new.png"))
            .andExpect(jsonPath("$.data.memo_created_at").value("2026-04-06T11:00:00"))
    }

    @Test
    fun `get memo detail should return bad request when memo belongs to another user`() {
        signupAndGetAccessToken("memoowner@example.com")
        val visitorAccessToken = signupAndGetAccessToken("memovisitor@example.com")
        val ownerUser = checkNotNull(userRepository.findByEmail("memoowner@example.com"))
        val ownerUserNo = ownerUser.id
        val now = java.time.LocalDateTime.of(2026, 4, 6, 12, 0, 0)

        val book = bookRepository.save(
            BookEntity(
                title = "타인 메모 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = ownerUser,
                page = 123,
                imageUrl = "https://example.com/book-owner.jpg",
                info = "책 소개",
            ),
        )
        val memo = memoRepository.save(
            MemoEntity(
                book = book,
                content = "타인 메모",
                createdAt = now,
                user = ownerUser,
                isLiked = false,
            ),
        )

        mockMvc.perform(
            get("/api/v1/memo/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $visitorAccessToken")
                .queryParam("id", checkNotNull(memo.id).toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))
    }

    @Test
    fun `get memo detail should return bad request when memo does not exist`() {
        val accessToken = signupAndGetAccessToken("memomissing@example.com")

        mockMvc.perform(
            get("/api/v1/memo/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", "999999"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))
    }

    @Test
    fun `create memo should create memo for owned book`() {
        val accessToken = signupAndGetAccessToken("memocreate@example.com")
        val user = checkNotNull(userRepository.findByEmail("memocreate@example.com"))
        val userNo = user.id
        val book = bookRepository.save(
            BookEntity(
                title = "생성용 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 101,
                imageUrl = "https://example.com/create-book.jpg",
                info = "책 소개",
            ),
        )

        val response = mockMvc.perform(
            post("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"book_no":${checkNotNull(book.id)},"memo_content":"새 메모"}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("메모 추가 성공"))
            .andExpect(jsonPath("$.data.id").isNumber)
            .andReturn()

        val memoId = objectMapper.readTree(response.response.contentAsString)
            .path("data")
            .path("id")
            .asLong()

        val saved = checkNotNull(memoRepository.findById(memoId).orElse(null))
        assertEquals(userNo, saved.user.id)
        assertEquals(book.id, saved.book.id)
        assertEquals("새 메모", saved.content)
        assertFalse(saved.isLiked)
    }

    @Test
    fun `create memo should return bad request when book belongs to another user`() {
        signupAndGetAccessToken("memo_owner_book@example.com")
        val accessToken = signupAndGetAccessToken("memo_create_visitor@example.com")
        val ownerUser = checkNotNull(userRepository.findByEmail("memo_owner_book@example.com"))
        val ownerUserNo = ownerUser.id
        val ownerBook = bookRepository.save(
            BookEntity(
                title = "타인 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = ownerUser,
                page = 202,
                imageUrl = "https://example.com/owner-book.jpg",
                info = "책 소개",
            ),
        )

        mockMvc.perform(
            post("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"book_no":${checkNotNull(ownerBook.id)},"memo_content":"실패 메모"}""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 책 정보가 없습니다."))
    }

    @Test
    fun `update memo should update memo when memo exists and book is owned`() {
        val accessToken = signupAndGetAccessToken("memoupdate@example.com")
        val user = checkNotNull(userRepository.findByEmail("memoupdate@example.com"))
        val userNo = user.id
        val sourceBook = bookRepository.save(
            BookEntity(
                title = "원본 책",
                author = "저자 A",
                publisher = "출판사 A",
                status = 1,
                user = user,
                page = 111,
                imageUrl = "https://example.com/update-book-a.jpg",
                info = "소개 A",
            ),
        )
        val targetBook = bookRepository.save(
            BookEntity(
                title = "수정 대상 책",
                author = "저자 B",
                publisher = "출판사 B",
                status = 1,
                user = user,
                page = 222,
                imageUrl = "https://example.com/update-book-b.jpg",
                info = "소개 B",
            ),
        )
        val memo = memoRepository.save(
            MemoEntity(
                book = sourceBook,
                content = "기존 메모",
                user = user,
                isLiked = false,
            ),
        )

        mockMvc.perform(
            put("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", checkNotNull(memo.id).toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"book_no":${checkNotNull(targetBook.id)},"memo_content":"수정된 메모"}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 수정 성공"))

        val updated = checkNotNull(memoRepository.findById(checkNotNull(memo.id)).orElse(null))
        assertEquals(targetBook.id, updated.book.id)
        assertEquals("수정된 메모", updated.content)
    }

    @Test
    fun `update memo should return bad request when target book belongs to another user`() {
        val accessToken = signupAndGetAccessToken("memoupdate_book_owner@example.com")
        val user = checkNotNull(userRepository.findByEmail("memoupdate_book_owner@example.com"))
        val userNo = user.id
        signupAndGetAccessToken("memoupdate_book_other@example.com")
        val otherUser = checkNotNull(userRepository.findByEmail("memoupdate_book_other@example.com"))
        val otherUserNo = otherUser.id

        val ownedBook = bookRepository.save(
            BookEntity(
                title = "내 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 123,
                imageUrl = "https://example.com/owned-book.jpg",
                info = "내 책 소개",
            ),
        )
        val otherBook = bookRepository.save(
            BookEntity(
                title = "타인 책",
                author = "타인 저자",
                publisher = "타인 출판사",
                status = 1,
                user = otherUser,
                page = 456,
                imageUrl = "https://example.com/other-book.jpg",
                info = "타인 책 소개",
            ),
        )
        val memo = memoRepository.save(
            MemoEntity(
                book = ownedBook,
                content = "업데이트 전",
                user = user,
                isLiked = false,
            ),
        )

        mockMvc.perform(
            put("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", checkNotNull(memo.id).toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"book_no":${checkNotNull(otherBook.id)},"memo_content":"실패"}""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 책 정보가 없습니다."))
    }

    @Test
    fun `update memo should return bad request when memo does not exist`() {
        val accessToken = signupAndGetAccessToken("memoupdate_missing@example.com")
        val user = checkNotNull(userRepository.findByEmail("memoupdate_missing@example.com"))
        val userNo = user.id
        val book = bookRepository.save(
            BookEntity(
                title = "업데이트 대상 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 777,
                imageUrl = "https://example.com/update-missing-book.jpg",
                info = "책 소개",
            ),
        )

        mockMvc.perform(
            put("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", "999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"book_no":${checkNotNull(book.id)},"memo_content":"없는 메모 수정"}""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))
    }

    @Test
    fun `update memo should return bad request when memo belongs to another user`() {
        signupAndGetAccessToken("memo_update_owner@example.com")
        val visitorAccessToken = signupAndGetAccessToken("memo_update_visitor@example.com")
        val ownerUser = checkNotNull(userRepository.findByEmail("memo_update_owner@example.com"))
        val ownerUserNo = ownerUser.id
        val visitorUser = checkNotNull(userRepository.findByEmail("memo_update_visitor@example.com"))

        val ownerBook = bookRepository.save(
            BookEntity(
                title = "소유자 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = ownerUser,
                page = 123,
                imageUrl = "https://example.com/owner-update-book.jpg",
                info = "소유자 책 소개",
            ),
        )
        val visitorBook = bookRepository.save(
            BookEntity(
                title = "방문자 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = visitorUser,
                page = 321,
                imageUrl = "https://example.com/visitor-update-book.jpg",
                info = "방문자 책 소개",
            ),
        )
        val ownerMemo = memoRepository.save(
            MemoEntity(
                book = ownerBook,
                content = "소유자 메모",
                user = ownerUser,
                isLiked = false,
            ),
        )

        mockMvc.perform(
            put("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $visitorAccessToken")
                .queryParam("id", checkNotNull(ownerMemo.id).toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"book_no":${checkNotNull(visitorBook.id)},"memo_content":"타인 메모 수정"}""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))

        val updated = checkNotNull(memoRepository.findById(checkNotNull(ownerMemo.id)).orElse(null))
        assertEquals(ownerUserNo, updated.user.id)
        assertEquals(ownerBook.id, updated.book.id)
        assertEquals("소유자 메모", updated.content)
    }

    @Test
    fun `delete memo should delete memo images rows and files`() {
        val accessToken = signupAndGetAccessToken("memodelete@example.com")
        val user = checkNotNull(userRepository.findByEmail("memodelete@example.com"))
        val userNo = user.id
        val book = bookRepository.save(
            BookEntity(
                title = "삭제용 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 303,
                imageUrl = "https://example.com/delete-book.jpg",
                info = "책 소개",
            ),
        )
        val memo = memoRepository.save(
            MemoEntity(
                book = book,
                content = "삭제 메모",
                user = user,
                isLiked = false,
            ),
        )
        val memoNo = checkNotNull(memo.id)
        val imageRelativePath = "memo/delete/memo-delete.png"
        val storedPath = imagesRoot.resolve(imageRelativePath)
        Files.createDirectories(storedPath.parent)
        Files.writeString(storedPath, "memo image")
        memoImageRepository.save(
            MemoImageEntity(
                name = "memo-delete.png",
                url = imageRelativePath,
                memo = memo,
            ),
        )

        mockMvc.perform(
            delete("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", memoNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 삭제 성공"))

        assertTrue(memoRepository.findById(memoNo).isEmpty)
        assertTrue(memoImageRepository.findAllByMemoIdIn(listOf(memoNo)).isEmpty())
        assertFalse(Files.exists(storedPath))
    }

    @Test
    fun `delete memo should return bad request when memo belongs to another user`() {
        signupAndGetAccessToken("memo_delete_owner@example.com")
        val visitorAccessToken = signupAndGetAccessToken("memo_delete_visitor@example.com")
        val ownerUser = checkNotNull(userRepository.findByEmail("memo_delete_owner@example.com"))

        val ownerBook = bookRepository.save(
            BookEntity(
                title = "삭제 소유자 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = ownerUser,
                page = 404,
                imageUrl = "https://example.com/delete-owner-book.jpg",
                info = "책 소개",
            ),
        )
        val ownerMemo = memoRepository.save(
            MemoEntity(
                book = ownerBook,
                content = "삭제 대상 메모",
                user = ownerUser,
                isLiked = false,
            ),
        )

        mockMvc.perform(
            delete("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $visitorAccessToken")
                .queryParam("id", checkNotNull(ownerMemo.id).toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))

        assertTrue(memoRepository.findById(checkNotNull(ownerMemo.id)).isPresent)
    }

    @Test
    fun `delete memo should return bad request when memo does not exist`() {
        val accessToken = signupAndGetAccessToken("memodelete_missing@example.com")

        mockMvc.perform(
            delete("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", "999999"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))
    }

    @Test
    fun `like memo should toggle memo like both directions`() {
        val accessToken = signupAndGetAccessToken("memolike@example.com")
        val user = checkNotNull(userRepository.findByEmail("memolike@example.com"))
        val userNo = user.id
        val book = bookRepository.save(
            BookEntity(
                title = "좋아요 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 505,
                imageUrl = "https://example.com/like-book.jpg",
                info = "책 소개",
            ),
        )
        val memo = memoRepository.save(
            MemoEntity(
                book = book,
                content = "좋아요 메모",
                user = user,
                isLiked = false,
            ),
        )
        val memoNo = checkNotNull(memo.id)

        mockMvc.perform(
            put("/api/v1/memo/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", memoNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 즐겨찾기 추가/해제"))

        assertTrue(checkNotNull(memoRepository.findById(memoNo).orElse(null)).isLiked)

        mockMvc.perform(
            put("/api/v1/memo/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", memoNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메모 즐겨찾기 추가/해제"))

        assertFalse(checkNotNull(memoRepository.findById(memoNo).orElse(null)).isLiked)
    }

    @Test
    fun `like memo should return bad request when memo belongs to another user`() {
        signupAndGetAccessToken("memo_like_owner@example.com")
        val visitorAccessToken = signupAndGetAccessToken("memo_like_visitor@example.com")
        val ownerUser = checkNotNull(userRepository.findByEmail("memo_like_owner@example.com"))

        val ownerBook = bookRepository.save(
            BookEntity(
                title = "좋아요 소유자 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = ownerUser,
                page = 606,
                imageUrl = "https://example.com/like-owner-book.jpg",
                info = "책 소개",
            ),
        )
        val ownerMemo = memoRepository.save(
            MemoEntity(
                book = ownerBook,
                content = "타인 좋아요 메모",
                user = ownerUser,
                isLiked = false,
            ),
        )

        mockMvc.perform(
            put("/api/v1/memo/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $visitorAccessToken")
                .queryParam("id", checkNotNull(ownerMemo.id).toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))

        assertFalse(checkNotNull(memoRepository.findById(checkNotNull(ownerMemo.id)).orElse(null)).isLiked)
    }

    @Test
    fun `like memo should return bad request when memo does not exist`() {
        val accessToken = signupAndGetAccessToken("memolike_missing@example.com")

        mockMvc.perform(
            put("/api/v1/memo/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", "999999"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))
    }

    private fun signupAndGetAccessToken(email: String): String {
        val signupResponse = mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"$email","user_password":"pw1234","user_fcm":"fcm-token","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readTree(signupResponse.response.contentAsString)
            .path("data")
            .path("access_token")
            .asText()
    }
}
