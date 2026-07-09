package std.nooook.readinggardenkotlin.modules.book

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.servlet.autoconfigure.MultipartProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import std.nooook.readinggardenkotlin.TestcontainersConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity
import std.nooook.readinggardenkotlin.modules.book.entity.BookImageEntity
import std.nooook.readinggardenkotlin.modules.book.entity.BookReadEntity
import std.nooook.readinggardenkotlin.modules.book.integration.AladinClient
import std.nooook.readinggardenkotlin.modules.book.repository.BookImageRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenEntity
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenMemberRepository
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoImageEntity
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.repository.PushSettingsRepository
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.time.LocalDateTime
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, BookControllerIntegrationTest.TestConfig::class)
class BookControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val multipartProperties: MultipartProperties,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val refreshTokenRepository: RefreshTokenRepository,
    @Autowired private val pushSettingsRepository: PushSettingsRepository,
    @Autowired private val bookRepository: BookRepository,
    @Autowired private val bookReadRepository: BookReadRepository,
    @Autowired private val bookImageRepository: BookImageRepository,
    @Autowired private val memoRepository: MemoRepository,
    @Autowired private val memoImageRepository: MemoImageRepository,
    @Autowired private val gardenRepository: GardenRepository,
    @Autowired private val gardenMemberRepository: GardenMemberRepository,
    @Autowired private val recordingAladinClient: RecordingAladinClient,
) {
    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    companion object {
        private val imagesRoot: Path = Files.createTempDirectory("reading-garden-images")

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
        bookImageRepository.deleteAll()
        bookReadRepository.deleteAll()
        bookRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        pushSettingsRepository.deleteAll()
        gardenMemberRepository.deleteAll()
        gardenRepository.deleteAll()
        userRepository.deleteAll()
        recordingAladinClient.lastQuery = null
        recordingAladinClient.lastStart = null
        recordingAladinClient.lastMaxResults = null
        recordingAladinClient.searchIsbnResponse = mapOf(
            "item" to listOf(mapOf("isbn13" to "9788937462788")),
        )
        recordingAladinClient.detailResponse = mapOf(
            "searchCategoryId" to 1,
            "searchCategoryName" to "소설",
            "item" to listOf(
                mapOf(
                    "title" to "상세 책",
                    "author" to "저자",
                    "description" to "소개",
                    "isbn13" to "9788937462788",
                    "cover" to "https://example.com/book.jpg",
                    "publisher" to "출판사",
                    "subInfo" to mapOf("itemPage" to 321),
                ),
            ),
        )
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
    fun `search should require authentication`() {
        mockMvc.perform(
            get("/api/v1/book/search")
                .queryParam("query", "클린 코드"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resp_code").value(401))
    }

    @Test
    fun `multipart temp location should use external images root`() {
        assertEquals(imagesRoot.resolve("multipart-temp").toString(), multipartProperties.location)
        assertEquals(10L * 1024L * 1024L, multipartProperties.maxFileSize.toBytes())
        assertEquals(10L * 1024L * 1024L, multipartProperties.maxRequestSize.toBytes())
    }

    @Test
    fun `search should use default paging values and return adapter payload`() {
        val accessToken = signupAndGetAccessToken("booksearch@example.com")

        mockMvc.perform(
            get("/api/v1/book/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("query", "클린 코드"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 검색 성공"))
            .andExpect(jsonPath("$.data.query").value("클린 코드"))
            .andExpect(jsonPath("$.data.startIndex").value(1))
            .andExpect(jsonPath("$.data.itemsPerPage").value(100))
            .andExpect(jsonPath("$.data.item[0].title").value("클린 코드 결과"))

        assertEquals("클린 코드", recordingAladinClient.lastQuery)
        assertEquals(1, recordingAladinClient.lastStart)
        assertEquals(100, recordingAladinClient.lastMaxResults)
    }

    @Test
    fun `search isbn should return adapter payload`() {
        val accessToken = signupAndGetAccessToken("bookisbn@example.com")

        mockMvc.perform(
            get("/api/v1/book/search-isbn")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("query", "9788937462788"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 검색(ISBN) 성공"))
            .andExpect(jsonPath("$.data.item[0].isbn13").value("9788937462788"))

        assertEquals("9788937462788", recordingAladinClient.lastIsbnQuery)
    }

    @Test
    fun `detail isbn should shape response like legacy`() {
        val accessToken = signupAndGetAccessToken("bookdetail@example.com")

        mockMvc.perform(
            get("/api/v1/book/detail-isbn")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("query", "9788937462788"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 상세 조회 성공"))
            .andExpect(jsonPath("$.data.searchCategoryId").value(1))
            .andExpect(jsonPath("$.data.searchCategoryName").value("소설"))
            .andExpect(jsonPath("$.data.title").value("상세 책"))
            .andExpect(jsonPath("$.data.author").value("저자"))
            .andExpect(jsonPath("$.data.description").value("소개"))
            .andExpect(jsonPath("$.data.isbn13").value("9788937462788"))
            .andExpect(jsonPath("$.data.cover").value("https://example.com/book.jpg"))
            .andExpect(jsonPath("$.data.publisher").value("출판사"))
            .andExpect(jsonPath("$.data.itemPage").value(321))
            .andExpect(jsonPath("$.data.record").isMap)
            .andExpect(jsonPath("$.data.memo").isMap)

        assertEquals("9788937462788", recordingAladinClient.lastDetailQuery)
    }

    @Test
    fun `detail isbn should return empty fields when required fields are missing`() {
        val accessToken = signupAndGetAccessToken("bookdetailfail@example.com")
        recordingAladinClient.detailResponse = mapOf(
            "searchCategoryId" to 1,
            "searchCategoryName" to "소설",
            "item" to listOf(emptyMap<String, Any>()),
        )

        mockMvc.perform(
            get("/api/v1/book/detail-isbn")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("query", "9788937462788"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.data.title").value(""))
            .andExpect(jsonPath("$.data.itemPage").value(0))
    }

    @Test
    fun `duplication should return forbidden for existing isbn`() {
        val accessToken = signupAndGetAccessToken("dup@example.com")
        val user = checkNotNull(userRepository.findByEmail("dup@example.com"))
        val userNo = user.id
        bookRepository.save(
            BookEntity(
                garden = null,
                title = "기존 책",
                author = "저자",
                publisher = "출판사",
                status = 2,
                user = user,
                page = 120,
                isbn = "9788937462788",
                info = "소개",
            ),
        )

        mockMvc.perform(
            get("/api/v1/book/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("isbn", "9788937462788"),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resp_code").value(403))
            .andExpect(jsonPath("$.resp_msg").value("책 중복"))
    }

    @Test
    fun `create should persist book and return created payload`() {
        val accessToken = signupAndGetAccessToken("create@example.com")
        val gardenEntity = gardenRepository.save(
            GardenEntity(
                title = "책 가든",
                info = "소개",
                color = "blue",
            ),
        )
        val gardenNo = gardenEntity.id

        val response = mockMvc.perform(
            post("/api/v1/book/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"book_isbn":"9788937462788","garden_no":$gardenNo,"book_title":"새 책","book_info":"소개","book_author":"저자","book_publisher":"출판사","book_tree":"seed","book_image_url":"https://example.com/book.jpg","book_status":2,"book_page":300}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("책 등록 성공"))
            .andReturn()

        val bookNo = objectMapper.readTree(response.response.contentAsString)
            .path("data")
            .path("book_no")
            .asLong()
        val savedBook = checkNotNull(bookRepository.findById(bookNo).orElse(null))

        assertEquals(gardenNo, savedBook.garden?.id)
        assertEquals("새 책", savedBook.title)
        assertEquals("저자", savedBook.author)
        assertEquals("출판사", savedBook.publisher)
        assertEquals("seed", savedBook.tree)
        assertEquals("https://example.com/book.jpg", savedBook.imageUrl)
        assertEquals(2, savedBook.status)
        assertEquals(300, savedBook.page)
        assertEquals("9788937462788", savedBook.isbn)
    }

    @Test
    fun `create should persist long title author and publisher values`() {
        val accessToken = signupAndGetAccessToken("createlong@example.com")
        val longTitle = "제목".repeat(180)
        val longAuthor = "저자".repeat(80)
        val longPublisher = "출판사".repeat(60)

        val response = mockMvc.perform(
            post("/api/v1/book/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "book_isbn" to "9788937462790",
                            "book_title" to longTitle,
                            "book_info" to "긴 메타데이터 저장 회귀 검증",
                            "book_author" to longAuthor,
                            "book_publisher" to longPublisher,
                            "book_tree" to "seed",
                            "book_image_url" to "https://example.com/book-long.jpg",
                            "book_status" to 2,
                            "book_page" to 300,
                        ),
                    ),
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("책 등록 성공"))
            .andReturn()

        val bookNo = objectMapper.readTree(response.response.contentAsString)
            .path("data")
            .path("book_no")
            .asLong()
        val savedBook = checkNotNull(bookRepository.findById(bookNo).orElse(null))

        assertEquals(longTitle, savedBook.title)
        assertEquals(longAuthor, savedBook.author)
        assertEquals(longPublisher, savedBook.publisher)
    }

    @Test
    fun `create should allow unlimited books when garden_no is null like legacy`() {
        val accessToken = signupAndGetAccessToken("createlimit@example.com")
        val user = checkNotNull(userRepository.findByEmail("createlimit@example.com"))
        val userNo = user.id
        repeat(30) { index ->
            bookRepository.save(
                BookEntity(
                    garden = null,
                    title = "기존 책 $index",
                    author = "저자",
                    publisher = "출판사",
                    status = 2,
                    user = user,
                    page = 120,
                    info = "소개",
                ),
            )
        }

        mockMvc.perform(
            post("/api/v1/book/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"book_isbn":"9788937462789","book_title":"새 책","book_info":"소개","book_author":"저자","book_publisher":"출판사","book_tree":"seed","book_image_url":"https://example.com/book.jpg","book_status":2,"book_page":300}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("책 등록 성공"))
    }

    @Test
    fun `update should mutate the targeted book and return legacy message`() {
        val accessToken = signupAndGetAccessToken("update@example.com")
        val user = checkNotNull(userRepository.findByEmail("update@example.com"))
        val userNo = user.id
        val sourceGardenEntity = gardenRepository.save(
            GardenEntity(
                title = "원본 가든",
                info = "소개",
                color = "green",
            ),
        )
        val sourceGardenNo = sourceGardenEntity.id
        val targetGardenEntity = gardenRepository.save(
            GardenEntity(
                title = "목표 가든",
                info = "소개",
                color = "yellow",
            ),
        )
        val targetGardenNo = targetGardenEntity.id
        val savedBookEntity = bookRepository.save(
            BookEntity(
                garden = sourceGardenEntity,
                title = "수정 전",
                author = "저자",
                publisher = "출판사",
                status = 2,
                user = user,
                page = 200,
                info = "소개",
            ),
        )
        val savedBookNo = savedBookEntity.id

        mockMvc.perform(
            put("/api/v1/book/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("book_no", savedBookNo.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"garden_no":$targetGardenNo,"book_tree":"새싹","book_status":1}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 수정 성공"))

        val savedBook = checkNotNull(bookRepository.findById(savedBookNo).orElse(null))
        assertEquals(targetGardenNo, savedBook.garden?.id)
        assertEquals("새싹", savedBook.tree)
        assertEquals(1, savedBook.status)
    }

    @Test
    fun `book rating should be saved and returned on read detail and completed bookshelf`() {
        val accessToken = signupAndGetAccessToken("rating@example.com")
        val user = checkNotNull(userRepository.findByEmail("rating@example.com"))
        val garden = gardenRepository.save(
            GardenEntity(
                title = "별점 가든",
                info = "소개",
                color = "green",
            ),
        )
        val book = bookRepository.save(
            BookEntity(
                garden = garden,
                user = user,
                title = "별점 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 1,
                page = 100,
            ),
        )

        mockMvc.perform(
            put("/api/v1/book/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("book_no", book.id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"book_rating":4}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 수정 성공"))

        assertEquals(4, checkNotNull(bookRepository.findById(book.id).orElse(null)).rating)

        mockMvc.perform(
            get("/api/v1/book/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("book_no", book.id.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.book_rating").value(4))

        mockMvc.perform(
            get("/api/v1/book/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("status", "1")
                .queryParam("page", "1")
                .queryParam("page_size", "10"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.list[0].book_rating").value(4))
    }

    @Test
    fun `book rating should be allowed only for completed books`() {
        val accessToken = signupAndGetAccessToken("rating-invalid@example.com")
        val user = checkNotNull(userRepository.findByEmail("rating-invalid@example.com"))
        val readingBook = bookRepository.save(
            BookEntity(
                user = user,
                title = "읽는 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 100,
            ),
        )
        val completedBook = bookRepository.save(
            BookEntity(
                user = user,
                title = "완독 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 1,
                page = 100,
                rating = 5,
            ),
        )

        mockMvc.perform(
            put("/api/v1/book/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("book_no", readingBook.id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"book_rating":4}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_msg").value("다 읽은 책에만 별점을 입력할 수 있습니다."))

        mockMvc.perform(
            put("/api/v1/book/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("book_no", completedBook.id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"book_status":0}"""),
        )
            .andExpect(status().isOk)

        val savedBook = checkNotNull(bookRepository.findById(completedBook.id).orElse(null))
        assertEquals(0, savedBook.status)
        assertEquals(null, savedBook.rating)
    }

    @Test
    fun `status should include reading and read books when status is three`() {
        val accessToken = signupAndGetAccessToken("status@example.com")
        val user = checkNotNull(userRepository.findByEmail("status@example.com"))
        val userNo = user.id
        val now = LocalDateTime.of(2026, 4, 6, 12, 0, 0)
        val readingBookEntity = bookRepository.save(
            BookEntity(
                user = user,
                title = "읽는중",
                author = "a",
                publisher = "p",
                info = "i",
                status = 0,
                page = 100,
            ),
        )
        val readingBookNo = readingBookEntity.id
        val readBookEntity = bookRepository.save(
            BookEntity(
                user = user,
                title = "읽음",
                author = "a",
                publisher = "p",
                info = "i",
                status = 1,
                page = 100,
            ),
        )
        val readBookNo = readBookEntity.id
        bookRepository.save(
            BookEntity(
                user = user,
                title = "읽고싶음",
                author = "a",
                publisher = "p",
                info = "i",
                status = 2,
                page = 100,
            ),
        )
        bookReadRepository.save(
            BookReadEntity(
                book = readingBookEntity,
                currentPage = 20,
                createdAt = now.minusHours(2),
                
            ),
        )
        bookReadRepository.save(
            BookReadEntity(
                book = readingBookEntity,
                currentPage = 40,
                createdAt = now.minusHours(1),
                
            ),
        )
        bookReadRepository.save(
            BookReadEntity(
                book = readBookEntity,
                currentPage = 100,
                createdAt = now,
                
            ),
        )

        val response = mockMvc.perform(
            get("/api/v1/book/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("status", "3")
                .queryParam("page", "1")
                .queryParam("page_size", "10"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 상태 조회 성공"))
            .andExpect(jsonPath("$.data.current_page").value(1))
            .andExpect(jsonPath("$.data.max_page").value(1))
            .andExpect(jsonPath("$.data.total_items").value(2))
            .andExpect(jsonPath("$.data.page_size").value(10))
            .andExpect(jsonPath("$.data.list.length()").value(2))
            .andReturn()

        val list = objectMapper.readTree(response.response.contentAsString)
            .path("data")
            .path("list")
        val titles = list.map { it.path("book_title").asText() }.toSet()
        val percentByTitle = list.associate { it.path("book_title").asText() to it.path("percent").asDouble() }

        assertEquals(setOf("읽는중", "읽음"), titles)
        assertEquals(40.0, percentByTitle.getValue("읽는중"))
        assertEquals(100.0, percentByTitle.getValue("읽음"))
        assertFalse(titles.contains("읽고싶음"))

        val firstPageResponse = mockMvc.perform(
            get("/api/v1/book/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("status", "3")
                .queryParam("page", "1")
                .queryParam("page_size", "1"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.data.list.length()").value(1))
            .andExpect(jsonPath("$.data.list[0].book_title").value("읽는중"))
            .andReturn()

        assertEquals(
            "읽는중",
            objectMapper.readTree(firstPageResponse.response.contentAsString)
                .path("data")
                .path("list")
                .get(0)
                .path("book_title")
                .asText(),
        )
    }

    @Test
    fun `status should return zero percent when book page is zero`() {
        val accessToken = signupAndGetAccessToken("statuszeropage@example.com")
        val user = checkNotNull(userRepository.findByEmail("statuszeropage@example.com"))
        val userNo = user.id
        val bookEntity = bookRepository.save(
            BookEntity(
                user = user,
                title = "제로 페이지 상태 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 0,
            ),
        )
        val bookNo = bookEntity.id
        bookReadRepository.save(
            BookReadEntity(
                book = bookEntity,
                currentPage = 1,
                
            ),
        )

        mockMvc.perform(
            get("/api/v1/book/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("status", "0"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 상태 조회 성공"))
            .andExpect(jsonPath("$.data.list[0].book_no").value(bookNo))
            .andExpect(jsonPath("$.data.list[0].percent").value(0.0))
            .andExpect(jsonPath("$.data.list[0].book_page").value(0))
    }

    @Test
    fun `status should keep legacy internal error for zero page size`() {
        val accessToken = signupAndGetAccessToken("statuszeropagesize@example.com")
        val user = checkNotNull(userRepository.findByEmail("statuszeropagesize@example.com"))
        val userNo = user.id
        bookRepository.save(
            BookEntity(
                user = user,
                title = "페이지사이즈 제로 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 100,
            ),
        )

        mockMvc.perform(
            get("/api/v1/book/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("status", "0")
                .queryParam("page_size", "0"),
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.resp_code").value(500))
            .andExpect(jsonPath("$.resp_msg").value("An unexpected error occurred."))
    }

    @Test
    fun `read detail should keep legacy shape and ordering`() {
        val accessToken = signupAndGetAccessToken("readshape@example.com")
        val user = checkNotNull(userRepository.findByEmail("readshape@example.com"))
        val userNo = user.id
        val gardenEntity = gardenRepository.save(
            GardenEntity(
                title = "읽기 가든",
                info = "소개",
                color = "blue",
            ),
        )
        val gardenNo = gardenEntity.id
        val bookEntity = bookRepository.save(
            BookEntity(
                garden = gardenEntity,
                user = user,
                title = "읽기 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 100,
            ),
        )
        val bookNo = bookEntity.id
        val now = LocalDateTime.of(2026, 4, 6, 12, 0, 0)

        memoRepository.save(
            MemoEntity(
                book = bookEntity,
                content = "늦은 메모",
                createdAt = now.minusMinutes(10),
                user = user,
                isLiked = true,
            ),
        )
        memoRepository.save(
            MemoEntity(
                book = bookEntity,
                content = "빠른 메모",
                createdAt = now.minusHours(1),
                user = user,
                isLiked = true,
            ),
        )
        memoRepository.save(
            MemoEntity(
                book = bookEntity,
                content = "싫어요 메모",
                createdAt = now,
                user = user,
                isLiked = false,
            ),
        )

        mockMvc.perform(
            get("/api/v1/book/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("book_no", bookNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("독서 기록 조회 성공"))
            .andExpect(jsonPath("$.data.book_no").value(bookNo))
            .andExpect(jsonPath("$.data.user_no").value(userNo))
            .andExpect(jsonPath("$.data.garden_title").value("읽기 가든"))
            .andExpect(jsonPath("$.data.garden_color").value("blue"))
            .andExpect(jsonPath("$.data.book_current_page").value(0))
            .andExpect(jsonPath("$.data.percent").value(0.0))
            .andExpect(jsonPath("$.data.book_read_list.length()").value(0))

        val firstRead = bookReadRepository.save(
            BookReadEntity(
                book = bookEntity,
                currentPage = 20,
                startDate = LocalDateTime.of(2026, 4, 6, 10, 0, 0),
                endDate = LocalDateTime.of(2026, 4, 6, 10, 30, 0),
                createdAt = LocalDateTime.of(2026, 4, 6, 10, 0, 0),
                
            ),
        )
        bookReadRepository.save(
            BookReadEntity(
                book = bookEntity,
                currentPage = 40,
                startDate = LocalDateTime.of(2026, 4, 6, 11, 0, 0),
                endDate = LocalDateTime.of(2026, 4, 6, 11, 30, 0),
                createdAt = LocalDateTime.of(2026, 4, 6, 11, 0, 0),
                
            ),
        )

        val response = mockMvc.perform(
            get("/api/v1/book/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("book_no", bookNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("독서 기록 조회 성공"))
            .andExpect(jsonPath("$.data.book_no").value(bookNo))
            .andExpect(jsonPath("$.data.user_no").value(userNo))
            .andExpect(jsonPath("$.data.garden_title").value("읽기 가든"))
            .andExpect(jsonPath("$.data.garden_color").value("blue"))
            .andExpect(jsonPath("$.data.book_current_page").value(40))
            .andExpect(jsonPath("$.data.percent").value(40.0))
            .andExpect(jsonPath("$.data.book_read_list.length()").value(2))
            .andExpect(jsonPath("$.data.book_read_list[0].book_created_at").value("2026-04-06T11:00:00"))
            .andExpect(jsonPath("$.data.book_read_list[0].book_start_date").value("2026-04-06T11:00:00"))
            .andExpect(jsonPath("$.data.book_read_list[0].book_end_date").value("2026-04-06T11:30:00"))
            .andExpect(jsonPath("$.data.book_read_list[0].created_ad").doesNotExist())
            .andExpect(jsonPath("$.data.book_read_list[0].created_at").doesNotExist())
            .andExpect(jsonPath("$.data.book_read_list[0].book_read_no").doesNotExist())
            .andExpect(jsonPath("$.data.book_read_list[0].book_current_page").value(40))
            .andExpect(jsonPath("$.data.book_read_list[1].book_current_page").value(20))
            .andExpect(jsonPath("$.data.memo_list[0].memo_content").value("늦은 메모"))
            .andExpect(jsonPath("$.data.memo_list[0].memo_created_at").value("2026-04-06T11:50:00"))
            .andExpect(jsonPath("$.data.memo_list[0].image_url").doesNotExist())
            .andExpect(jsonPath("$.data.memo_list[0].book_no").doesNotExist())
            .andExpect(jsonPath("$.data.memo_list[0].book_title").doesNotExist())
            .andExpect(jsonPath("$.data.memo_list[1].memo_content").value("빠른 메모"))
            .andExpect(jsonPath("$.data.memo_list[1].memo_created_at").value("2026-04-06T11:00:00"))
            .andExpect(jsonPath("$.data.memo_list[2].memo_content").value("싫어요 메모"))
            .andExpect(jsonPath("$.data.memo_list[2].memo_created_at").value("2026-04-06T12:00:00"))
            .andReturn()

        val body = objectMapper.readTree(response.response.contentAsString).path("data")
        assertEquals(40.0, body.path("percent").asDouble())
        assertEquals("늦은 메모", body.path("memo_list").get(0).path("memo_content").asText())
        assertEquals("2026-04-06T11:50:00", body.path("memo_list").get(0).path("memo_created_at").asText())
        assertEquals("빠른 메모", body.path("memo_list").get(1).path("memo_content").asText())
        assertEquals("2026-04-06T11:00:00", body.path("memo_list").get(1).path("memo_created_at").asText())
        assertEquals("싫어요 메모", body.path("memo_list").get(2).path("memo_content").asText())
        assertEquals("2026-04-06T12:00:00", body.path("memo_list").get(2).path("memo_created_at").asText())
        assertNotNull(firstRead.id)
    }

    @Test
    fun `read detail should return bad request when garden is missing`() {
        val accessToken = signupAndGetAccessToken("readnogarden1@example.com")
        val user = checkNotNull(userRepository.findByEmail("readnogarden1@example.com"))
        val userNo = user.id
        val bookEntity = bookRepository.save(
            BookEntity(
                garden = null,
                user = user,
                title = "가든 없는 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 100,
            ),
        )
        val bookNo = bookEntity.id

        mockMvc.perform(
            get("/api/v1/book/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("book_no", bookNo.toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 책 정보가 없습니다."))
    }

    @Test
    fun `read detail should return bad request when linked garden is deleted`() {
        val accessToken = signupAndGetAccessToken("readmissing1@example.com")
        val user = checkNotNull(userRepository.findByEmail("readmissing1@example.com"))
        val userNo = user.id
        val gardenEntity = gardenRepository.save(
            GardenEntity(
                title = "임시 가든",
                info = "소개",
                color = "green",
            ),
        )
        val gardenNo = gardenEntity.id
        val bookEntity = bookRepository.save(
            BookEntity(
                garden = gardenEntity,
                user = user,
                title = "가든 삭제 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 100,
            ),
        )
        val bookNo = bookEntity.id
        gardenRepository.deleteById(gardenNo)

        mockMvc.perform(
            get("/api/v1/book/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("book_no", bookNo.toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 책 정보가 없습니다."))
    }

    @Test
    fun `read detail should return zero percent when book page is zero`() {
        val accessToken = signupAndGetAccessToken("readzeropage@example.com")
        val user = checkNotNull(userRepository.findByEmail("readzeropage@example.com"))
        val userNo = user.id
        val gardenEntity = gardenRepository.save(
            GardenEntity(
                title = "제로 페이지 가든",
                info = "소개",
                color = "yellow",
            ),
        )
        val gardenNo = gardenEntity.id
        val bookEntity = bookRepository.save(
            BookEntity(
                garden = gardenEntity,
                user = user,
                title = "제로 페이지 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 0,
            ),
        )
        val bookNo = bookEntity.id
        bookReadRepository.save(
            BookReadEntity(
                book = bookEntity,
                currentPage = 1,
                
            ),
        )

        mockMvc.perform(
            get("/api/v1/book/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("book_no", bookNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("독서 기록 조회 성공"))
            .andExpect(jsonPath("$.data.book_no").value(bookNo))
            .andExpect(jsonPath("$.data.book_current_page").value(1))
            .andExpect(jsonPath("$.data.percent").value(0.0))
            .andExpect(jsonPath("$.data.book_page").value(0))
    }

    @Test
    fun `create read should transition book status on first and final page`() {
        val accessToken = signupAndGetAccessToken("readstatus@example.com")
        val user = checkNotNull(userRepository.findByEmail("readstatus@example.com"))
        val userNo = user.id
        val bookEntity = bookRepository.save(
            BookEntity(
                user = user,
                title = "상태 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 2,
                page = 100,
            ),
        )
        val bookNo = bookEntity.id

        mockMvc.perform(
            post("/api/v1/book/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"book_no":$bookNo,"book_current_page":20}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("책 기록 성공"))
            .andExpect(jsonPath("$.data.book_current_page").value(20))
            .andExpect(jsonPath("$.data.percent").value(20.0))
            .andExpect(jsonPath("$.data.id").doesNotExist())
            .andExpect(jsonPath("$.data.book_read_no").doesNotExist())

        val firstRead = checkNotNull(bookReadRepository.findAllByBookIdOrderByCreatedAtDesc(bookNo).firstOrNull())
        assertNotNull(firstRead.startDate)
        assertEquals(0, checkNotNull(bookRepository.findById(bookNo).orElse(null)).status)

        mockMvc.perform(
            post("/api/v1/book/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"book_no":$bookNo,"book_current_page":30}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("책 기록 성공"))
            .andExpect(jsonPath("$.data.book_current_page").value(30))
            .andExpect(jsonPath("$.data.percent").value(30.0))
            .andExpect(jsonPath("$.data.id").doesNotExist())
            .andExpect(jsonPath("$.data.book_read_no").doesNotExist())

        val secondRead = checkNotNull(bookReadRepository.findAllByBookIdOrderByCreatedAtDesc(bookNo).firstOrNull())
        assertEquals(30, secondRead.currentPage)
        assertEquals(null, secondRead.startDate)
        assertEquals(0, checkNotNull(bookRepository.findById(bookNo).orElse(null)).status)

        mockMvc.perform(
            post("/api/v1/book/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"book_no":$bookNo,"book_current_page":100}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("책 기록 성공"))
            .andExpect(jsonPath("$.data.book_current_page").value(100))
            .andExpect(jsonPath("$.data.percent").value(100.0))
            .andExpect(jsonPath("$.data.id").doesNotExist())
            .andExpect(jsonPath("$.data.book_read_no").doesNotExist())

        val reads = bookReadRepository.findAllByBookIdOrderByCreatedAtDesc(bookNo)
        val latestRead = checkNotNull(reads.firstOrNull())
        assertEquals(null, latestRead.startDate)
        assertNotNull(latestRead.endDate)
        assertEquals(1, checkNotNull(bookRepository.findById(bookNo).orElse(null)).status)

        mockMvc.perform(
            post("/api/v1/book/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"book_no":$bookNo,"book_current_page":40}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("책 기록 성공"))
            .andExpect(jsonPath("$.data.book_current_page").value(40))
            .andExpect(jsonPath("$.data.percent").value(40.0))
            .andExpect(jsonPath("$.data.id").doesNotExist())
            .andExpect(jsonPath("$.data.book_read_no").doesNotExist())

        val latestAfterRegression = checkNotNull(bookReadRepository.findAllByBookIdOrderByCreatedAtDesc(bookNo).firstOrNull())
        assertEquals(40, latestAfterRegression.currentPage)
        assertEquals(null, latestAfterRegression.startDate)
        assertEquals(1, checkNotNull(bookRepository.findById(bookNo).orElse(null)).status)
    }

    @Test
    fun `create read should accept Dart DateTime format with space separator`() {
        val accessToken = signupAndGetAccessToken("dartdate@example.com")
        val user = checkNotNull(userRepository.findByEmail("dartdate@example.com"))
        val userNo = user.id
        val bookEntity = bookRepository.save(
            BookEntity(
                user = user,
                title = "Dart 날짜 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 2,
                page = 200,
            ),
        )
        val bookNo = bookEntity.id

        mockMvc.perform(
            post("/api/v1/book/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"book_no":$bookNo,"book_start_date":"2026-04-14 15:30:45.123456","book_current_page":0}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("책 기록 성공"))

        val read = checkNotNull(bookReadRepository.findAllByBookIdOrderByCreatedAtDesc(bookNo).firstOrNull())
        assertNotNull(read.startDate)
    }

    @Test
    fun `update and delete read should use id without ownership guard`() {
        val accessToken = signupAndGetAccessToken("readmutate@example.com")
        val user = checkNotNull(userRepository.findByEmail("readmutate@example.com"))
        val userNo = user.id
        val bookEntity = bookRepository.save(
            BookEntity(
                user = user,
                title = "수정 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 100,
            ),
        )
        val bookNo = bookEntity.id
        val readId = bookReadRepository.save(
            BookReadEntity(
                book = bookEntity,
                currentPage = 10,
                
            ),
        ).id

        mockMvc.perform(
            put("/api/v1/book/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", readId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"book_start_date":"2026-04-06T11:00:00","book_end_date":"2026-04-06T12:00:00"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("독서 기록 수정 성공"))

        val updatedRead = checkNotNull(bookReadRepository.findById(readId).orElse(null))
        assertEquals(LocalDateTime.of(2026, 4, 6, 11, 0, 0), updatedRead.startDate)
        assertEquals(LocalDateTime.of(2026, 4, 6, 12, 0, 0), updatedRead.endDate)

        mockMvc.perform(
            delete("/api/v1/book/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", readId.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 기록 삭제 성공"))

        assertFalse(bookReadRepository.findById(readId).isPresent)
    }

    @Test
    fun `update read should return legacy bad request when id is missing`() {
        val accessToken = signupAndGetAccessToken("readupmissing@example.com")

        mockMvc.perform(
            put("/api/v1/book/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", "999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"book_start_date":"2026-04-06T11:00:00"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 책 기록이 없습니다."))
    }

    @Test
    fun `delete read should return legacy bad request when id is missing`() {
        val accessToken = signupAndGetAccessToken("readdelmissing@example.com")

        mockMvc.perform(
            delete("/api/v1/book/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", "999999"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 책 기록이 없습니다."))
    }

    @Test
    fun `upload image should persist file and serve it publicly`() {
        val accessToken = signupAndGetAccessToken("bookimageupload@example.com")
        val user = checkNotNull(userRepository.findByEmail("bookimageupload@example.com"))
        val userNo = user.id
        val bookEntity = bookRepository.save(
            BookEntity(
                user = user,
                title = "이미지 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 100,
            ),
        )
        val bookNo = bookEntity.id
        val file = MockMultipartFile(
            "file",
            "cover.png",
            MediaType.IMAGE_PNG_VALUE,
            "image-bytes".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/v1/book/image")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("book_no", bookNo.toString()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("이미지 업로드 성공"))

        val image = checkNotNull(bookImageRepository.findByBookId(bookNo))
        val storedPath = imagesRoot.resolve(image.url)
        assertTrue(Files.exists(storedPath))
        assertTrue(image.url.startsWith("book/"))

        val imageResponse = mockMvc.perform(
            get("/images/${image.url}"),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertContentEquals(
            "image-bytes".toByteArray(),
            imageResponse.response.contentAsByteArray,
        )

        val legacyImageResponse = mockMvc.perform(
            get("/api/images/${image.url}"),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertContentEquals(
            "image-bytes".toByteArray(),
            legacyImageResponse.response.contentAsByteArray,
        )
    }

    @Test
    fun `upload image should replace existing file and keep one row`() {
        val accessToken = signupAndGetAccessToken("bookimagereplace@example.com")
        val user = checkNotNull(userRepository.findByEmail("bookimagereplace@example.com"))
        val userNo = user.id
        val bookEntity = bookRepository.save(
            BookEntity(
                user = user,
                title = "교체 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 100,
            ),
        )
        val bookNo = bookEntity.id
        val firstFile = MockMultipartFile(
            "file",
            "first.png",
            MediaType.IMAGE_PNG_VALUE,
            "first-image".toByteArray(),
        )
        val secondFile = MockMultipartFile(
            "file",
            "second.png",
            MediaType.IMAGE_PNG_VALUE,
            "second-image".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/v1/book/image")
                .file(firstFile)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("book_no", bookNo.toString()),
        )
            .andExpect(status().isCreated)

        val firstImage = checkNotNull(bookImageRepository.findByBookId(bookNo))
        val firstStoredPath = imagesRoot.resolve(firstImage.url)
        assertTrue(Files.exists(firstStoredPath))

        mockMvc.perform(
            multipart("/api/v1/book/image")
                .file(secondFile)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("book_no", bookNo.toString()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_msg").value("이미지 업로드 성공"))

        val replacedImage = checkNotNull(bookImageRepository.findByBookId(bookNo))
        val replacedStoredPath = imagesRoot.resolve(replacedImage.url)
        assertFalse(Files.exists(firstStoredPath))
        assertTrue(Files.exists(replacedStoredPath))
        assertEquals(1, bookImageRepository.count())
        assertFalse(firstImage.url == replacedImage.url)
    }

    @Test
    fun `upload image should reject files over five megabytes`() {
        val accessToken = signupAndGetAccessToken("bookimageoversize@example.com")
        val user = checkNotNull(userRepository.findByEmail("bookimageoversize@example.com"))
        val userNo = user.id
        val bookEntity = bookRepository.save(
            BookEntity(
                user = user,
                title = "큰 이미지 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 100,
            ),
        )
        val bookNo = bookEntity.id
        val oversizedBytes = ByteArray(5 * 1024 * 1024 + 1)
        val file = MockMultipartFile(
            "file",
            "too-big.png",
            MediaType.IMAGE_PNG_VALUE,
            oversizedBytes,
        )

        mockMvc.perform(
            multipart("/api/v1/book/image")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("book_no", bookNo.toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("이미지 용량은 5MB를 초과할 수 없습니다."))

        assertTrue(bookImageRepository.findByBookId(bookNo) == null)
    }

    @Test
    fun `delete image should remove file and record`() {
        val accessToken = signupAndGetAccessToken("bookimagedelete@example.com")
        val user = checkNotNull(userRepository.findByEmail("bookimagedelete@example.com"))
        val userNo = user.id
        val bookEntity = bookRepository.save(
            BookEntity(
                user = user,
                title = "삭제 이미지 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 100,
            ),
        )
        val bookNo = bookEntity.id
        val file = MockMultipartFile(
            "file",
            "delete.png",
            MediaType.IMAGE_PNG_VALUE,
            "delete-image".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/v1/book/image")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("book_no", bookNo.toString()),
        )
            .andExpect(status().isCreated)

        val storedImage = checkNotNull(bookImageRepository.findByBookId(bookNo))
        val storedPath = imagesRoot.resolve(storedImage.url)
        assertTrue(Files.exists(storedPath))

        mockMvc.perform(
            delete("/api/v1/book/image")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("book_no", bookNo.toString()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("이미지 삭제 성공"))

        assertTrue(bookImageRepository.findByBookId(bookNo) == null)
        assertFalse(Files.exists(storedPath))
    }

    @Test
    fun `delete image should return bad request when missing`() {
        val accessToken = signupAndGetAccessToken("bookimagemissing@example.com")
        val user = checkNotNull(userRepository.findByEmail("bookimagemissing@example.com"))
        val userNo = user.id
        val bookEntity = bookRepository.save(
            BookEntity(
                user = user,
                title = "이미지 없는 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 100,
            ),
        )
        val bookNo = bookEntity.id

        mockMvc.perform(
            delete("/api/v1/book/image")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("book_no", bookNo.toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 이미지가 없습니다."))
    }

    @Test
    fun `delete book should cascade delete related rows and files`() {
        val accessToken = signupAndGetAccessToken("bookdeletecascade@example.com")
        val user = checkNotNull(userRepository.findByEmail("bookdeletecascade@example.com"))
        val userNo = user.id
        val bookEntity = bookRepository.save(
            BookEntity(
                user = user,
                title = "삭제 대상 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 100,
            ),
        )
        val bookNo = bookEntity.id
        bookReadRepository.save(
            BookReadEntity(
                book = bookEntity,
                currentPage = 20,
                
            ),
        )
        val bookImageRelativePath = "book/delete-book-image.png"
        val bookImagePath = imagesRoot.resolve(bookImageRelativePath)
        Files.createDirectories(bookImagePath.parent)
        Files.writeString(bookImagePath, "book-image")
        bookImageRepository.save(
            BookImageEntity(
                book = bookEntity,
                name = "delete-book-image.png",
                url = bookImageRelativePath,
            ),
        )
        val memo = memoRepository.save(
            MemoEntity(
                book = bookEntity,
                content = "메모",
                createdAt = LocalDateTime.of(2026, 4, 6, 13, 0, 0),
                user = user,
                isLiked = true,
            ),
        )
        val memoId = checkNotNull(memo.id)
        val memoImageRelativePath = "memo/delete-memo-image.png"
        val memoImagePath = imagesRoot.resolve(memoImageRelativePath)
        Files.createDirectories(memoImagePath.parent)
        Files.writeString(memoImagePath, "memo-image")
        memoImageRepository.save(
            MemoImageEntity(
                memo = memo,
                name = "delete-memo-image.png",
                url = memoImageRelativePath,
            ),
        )

        mockMvc.perform(
            delete("/api/v1/book/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("book_no", bookNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 삭제 성공"))

        assertFalse(bookRepository.findById(bookNo).isPresent)
        assertTrue(bookReadRepository.findAllByBookIdOrderByCreatedAtDesc(bookNo).isEmpty())
        assertTrue(bookImageRepository.findAllByBookId(bookNo).isEmpty())
        assertTrue(memoRepository.findAllByBookId(bookNo).isEmpty())
        assertTrue(memoImageRepository.findAllByMemoIdIn(listOf(memoId)).isEmpty())
        assertFalse(Files.exists(bookImagePath))
        assertFalse(Files.exists(memoImagePath))
    }

    @Test
    fun `delete book should ignore missing related image files`() {
        val accessToken = signupAndGetAccessToken("bookdeletefilemissing@example.com")
        val user = checkNotNull(userRepository.findByEmail("bookdeletefilemissing@example.com"))
        val userNo = user.id
        val bookEntity = bookRepository.save(
            BookEntity(
                user = user,
                title = "누락 파일 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 100,
            ),
        )
        val bookNo = bookEntity.id
        bookImageRepository.save(
            BookImageEntity(
                book = bookEntity,
                name = "missing-book.png",
                url = "book/missing-book.png",
            ),
        )
        val memo = memoRepository.save(
            MemoEntity(
                book = bookEntity,
                content = "메모",
                createdAt = LocalDateTime.of(2026, 4, 6, 14, 0, 0),
                user = user,
                isLiked = false,
            ),
        )
        memoImageRepository.save(
            MemoImageEntity(
                memo = memo,
                name = "missing-memo.png",
                url = "memo/missing-memo.png",
            ),
        )

        mockMvc.perform(
            delete("/api/v1/book/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("book_no", bookNo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("책 삭제 성공"))

        assertFalse(bookRepository.findById(bookNo).isPresent)
        assertTrue(bookImageRepository.findAllByBookId(bookNo).isEmpty())
        assertTrue(memoRepository.findAllByBookId(bookNo).isEmpty())
    }

    @Test
    fun `delete book should reject another users book and keep related data`() {
        signupAndGetAccessToken("bookdeleteowner@example.com")
        val owner = checkNotNull(userRepository.findByEmail("bookdeleteowner@example.com"))
        val ownerNo = owner.id
        val attackerAccessToken = signupAndGetAccessToken("bookdeleteattacker@example.com")
        val bookEntity = bookRepository.save(
            BookEntity(
                user = owner,
                title = "타인 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 100,
            ),
        )
        val bookNo = bookEntity.id
        val bookImageRelativePath = "book/owner-book.png"
        val bookImagePath = imagesRoot.resolve(bookImageRelativePath)
        Files.createDirectories(bookImagePath.parent)
        Files.writeString(bookImagePath, "owner-book-image")
        bookImageRepository.save(
            BookImageEntity(
                book = bookEntity,
                name = "owner-book.png",
                url = bookImageRelativePath,
            ),
        )
        val readId = checkNotNull(
            bookReadRepository.save(
                BookReadEntity(
                    book = bookEntity,
                    currentPage = 12,
                    
                ),
            ).id,
        )
        val memo = memoRepository.save(
            MemoEntity(
                book = bookEntity,
                content = "비소유자 삭제 방지",
                createdAt = LocalDateTime.of(2026, 4, 6, 16, 0, 0),
                user = owner,
                isLiked = true,
            ),
        )
        val memoId = checkNotNull(memo.id)
        val memoImageRelativePath = "memo/owner-memo.png"
        val memoImagePath = imagesRoot.resolve(memoImageRelativePath)
        Files.createDirectories(memoImagePath.parent)
        Files.writeString(memoImagePath, "owner-memo-image")
        memoImageRepository.save(
            MemoImageEntity(
                memo = memo,
                name = "owner-memo.png",
                url = memoImageRelativePath,
            ),
        )

        mockMvc.perform(
            delete("/api/v1/book/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $attackerAccessToken")
                .queryParam("book_no", bookNo.toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 책 정보가 없습니다."))

        assertTrue(bookRepository.findById(bookNo).isPresent)
        assertTrue(bookReadRepository.findById(readId).isPresent)
        assertFalse(bookImageRepository.findAllByBookId(bookNo).isEmpty())
        assertTrue(memoRepository.findById(memoId).isPresent)
        assertFalse(memoImageRepository.findAllByMemoIdIn(listOf(memoId)).isEmpty())
        assertTrue(Files.exists(bookImagePath))
        assertTrue(Files.exists(memoImagePath))
    }

    @Test
    fun `delete book should return bad request when book is missing`() {
        val accessToken = signupAndGetAccessToken("bookdeletemissing@example.com")

        mockMvc.perform(
            delete("/api/v1/book/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("book_no", "999999"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 책 정보가 없습니다."))
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

    class RecordingAladinClient : AladinClient {
        var lastQuery: String? = null
        var lastStart: Int? = null
        var lastMaxResults: Int? = null
        var lastIsbnQuery: String? = null
        var lastDetailQuery: String? = null
        var searchIsbnResponse: Map<String, Any?> = emptyMap()
        var detailResponse: Map<String, Any?> = emptyMap()

        override fun searchBooks(
            query: String,
            start: Int,
            maxResults: Int,
        ): Map<String, Any?> {
            lastQuery = query
            lastStart = start
            lastMaxResults = maxResults
            return mapOf(
                "query" to query,
                "startIndex" to start,
                "itemsPerPage" to maxResults,
                "item" to listOf(mapOf("title" to "${query} 결과")),
            )
        }

        override fun searchBookByIsbn(query: String): Map<String, Any?> {
            lastIsbnQuery = query
            return searchIsbnResponse
        }

        override fun getBookDetailByIsbn(query: String): Map<String, Any?> {
            lastDetailQuery = query
            return detailResponse
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        @Primary
        fun aladinClient(): RecordingAladinClient = RecordingAladinClient()
    }
}
