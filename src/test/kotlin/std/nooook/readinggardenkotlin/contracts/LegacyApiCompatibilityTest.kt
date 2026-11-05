package std.nooook.readinggardenkotlin.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import std.nooook.readinggardenkotlin.TestcontainersConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity
import std.nooook.readinggardenkotlin.modules.book.integration.BookCatalogClient
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenEntity
import std.nooook.readinggardenkotlin.modules.garden.entity.GardenMemberEntity
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenMemberRepository
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.repository.PushSettingsRepository
import java.time.LocalDateTime
import kotlin.test.assertNotNull

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, LegacyApiCompatibilityTest.TestConfig::class)
class LegacyApiCompatibilityTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val refreshTokenRepository: RefreshTokenRepository,
    @Autowired private val pushSettingsRepository: PushSettingsRepository,
    @Autowired private val gardenRepository: GardenRepository,
    @Autowired private val gardenMemberRepository: GardenMemberRepository,
    @Autowired private val bookRepository: BookRepository,
    @Autowired private val memoRepository: MemoRepository,
    @Autowired private val memoImageRepository: MemoImageRepository,
    @Autowired private val fixedBookCatalogClient: FixedBookCatalogClient,
) {
    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()
    private val exactEnvelopeValuePaths = setOf("$.resp_code", "$.resp_msg")

    @BeforeEach
    fun setUp() {
        memoImageRepository.deleteAll()
        memoRepository.deleteAll()
        bookRepository.deleteAll()
        gardenMemberRepository.deleteAll()
        gardenRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        pushSettingsRepository.deleteAll()
        userRepository.deleteAll()

        fixedBookCatalogClient.searchBooksResponse = mapOf(
            "query" to "클린 코드",
            "startIndex" to 1,
            "itemsPerPage" to 100,
            "item" to listOf(
                mapOf(
                    "title" to "클린 코드 결과",
                    "author" to "로버트 C. 마틴",
                    "isbn13" to "9780132350884",
                    "cover" to "https://example.com/cover.jpg",
                    "publisher" to "프래그마틱",
                ),
            ),
        )
        fixedBookCatalogClient.detailResponse = mapOf(
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

    @Test
    fun `post garden create should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardencontract@example.com")

        val response = mockMvc.perform(
            post("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"garden_title":"새 가든","garden_info":"소개","garden_color":"blue"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/create-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `get book search should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("bookcontract@example.com")

        val response = mockMvc.perform(
            get("/api/v1/book/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("query", "클린 코드"),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/book/search-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `get auth profile should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("authprofile@example.com")

        val response = mockMvc.perform(
            get("/api/v1/auth")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/auth/profile-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `get book detail should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("bookdetailcontract@example.com")

        val response = mockMvc.perform(
            get("/api/v1/book/detail-isbn")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("query", "9788937462788"),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/book/detail-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `get garden list should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardenlist@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardenlist@example.com"))
        val userNo = user.id
        removeSignupGarden(userNo)

        val mainGarden = gardenRepository.save(
            GardenEntity(
                title = "메인 가든",
                info = "첫 번째",
                color = "green",
                createdAt = LocalDateTime.of(2024, 1, 5, 8, 30, 0),
            ),
        )
        val secondaryGarden = gardenRepository.save(
            GardenEntity(
                title = "서브 가든",
                info = "두 번째",
                color = "blue",
                createdAt = LocalDateTime.of(2024, 1, 10, 9, 0, 0),
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = mainGarden,
                user = user,
                isLeader = true,
                isMain = true,
                joinDate = LocalDateTime.of(2024, 1, 6, 7, 0, 0),
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = secondaryGarden,
                user = user,
                isLeader = false,
                isMain = false,
                joinDate = LocalDateTime.of(2024, 1, 11, 10, 0, 0),
            ),
        )
        bookRepository.save(
            BookEntity(
                garden = mainGarden,
                title = "메인 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 100,
                isbn = "9781111111111",
                tree = "소설",
                imageUrl = "https://example.com/main.jpg",
                info = "메인 책 소개",
            ),
        )
        bookRepository.save(
            BookEntity(
                garden = secondaryGarden,
                title = "서브 책",
                author = "저자2",
                publisher = "출판사2",
                status = 0,
                user = user,
                page = 200,
                isbn = "9782222222222",
                tree = "에세이",
                imageUrl = "https://example.com/sub.jpg",
                info = "서브 책 소개",
            ),
        )

        val response = mockMvc.perform(
            get("/api/v1/garden/list")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/list-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `get garden detail should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardendetail@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardendetail@example.com"))
        val userNo = user.id
        removeSignupGarden(userNo)

        val garden = gardenRepository.save(
            GardenEntity(
                title = "상세 가든",
                info = "상세 설명",
                color = "orange",
                createdAt = LocalDateTime.of(2024, 2, 1, 12, 0, 0),
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = user,
                isLeader = true,
                isMain = true,
                joinDate = LocalDateTime.of(2024, 2, 1, 12, 5, 0),
            ),
        )
        val member = userRepository.save(
            UserEntity(
                email = "gardendetail-member@example.com",
                password = "pw1234",
                createdAt = LocalDateTime.of(2024, 2, 1, 12, 10, 0),
                nick = "멤버",
                image = "member.png",
                fcm = "fcm-member",
                socialId = "",
                socialType = "",
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = member,
                isLeader = false,
                isMain = false,
                joinDate = LocalDateTime.of(2024, 2, 2, 9, 0, 0),
            ),
        )
        bookRepository.save(
            BookEntity(
                garden = garden,
                title = "가든 책",
                author = "작가",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 400,
                isbn = "9783333333333",
                tree = "인문",
                imageUrl = "https://example.com/detail.jpg",
                info = "가든 책 소개",
            ),
        )
        bookRepository.save(
            BookEntity(
                garden = garden,
                title = "보조 책",
                author = "작가2",
                publisher = "출판사2",
                status = 0,
                user = user,
                page = 0,
                isbn = null,
                tree = null,
                imageUrl = null,
                info = "보조 책 소개",
            ),
        )

        val response = mockMvc.perform(
            get("/api/v1/garden/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", checkNotNull(garden.id).toString()),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/detail-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `get memo list should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("memocontract@example.com")
        val user = checkNotNull(userRepository.findByEmail("memocontract@example.com"))
        val userNo = user.id
        val gardenEntity = gardenRepository.save(
            GardenEntity(
                title = "메모 가든",
                info = "소개",
                color = "green",
            ),
        )
        val gardenNo = gardenEntity.id
        val bookEntity = bookRepository.save(
            BookEntity(
                garden = gardenEntity,
                title = "책 제목",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 240,
                imageUrl = "https://example.com/book.jpg",
                info = "책 소개",
            ),
        )
        val bookNo = bookEntity.id

        memoRepository.save(
            MemoEntity(
                book = bookEntity,
                content = "메모 내용",
                user = user,
                isLiked = true,
                createdAt = java.time.LocalDateTime.of(2026, 4, 6, 12, 0, 0),
            ),
        )

        val response = mockMvc.perform(
            get("/api/v1/memo/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/memo/list-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `get memo detail should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("memodetailcontract@example.com")
        val user = checkNotNull(userRepository.findByEmail("memodetailcontract@example.com"))
        val userNo = user.id
        val bookEntity = bookRepository.save(
            BookEntity(
                garden = null,
                title = "상세 메모 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 300,
                imageUrl = "https://example.com/memo-book.jpg",
                info = "메모 상세용 책 소개",
            ),
        )
        val bookNo = bookEntity.id

        val memoId = memoRepository.save(
            MemoEntity(
                book = bookEntity,
                content = "상세 메모 내용",
                user = user,
                isLiked = true,
                createdAt = LocalDateTime.of(2026, 4, 9, 16, 30, 0),
            ),
        ).id

        val response = mockMvc.perform(
            get("/api/v1/memo/detail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", memoId.toString()),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/memo/detail-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `assertSameShape should enforce exact equality for legacy envelope code and message`() {
        val fixture = objectMapper.readTree(
            """{"resp_code":200,"resp_msg":"성공","data":{"dynamic_value":"fixture"}}""",
        )
        val actual = objectMapper.readTree(
            """{"resp_code":201,"resp_msg":"다름","data":{"dynamic_value":"actual"}}""",
        )

        assertThrows<AssertionError> {
            assertSameShape(fixture = fixture, actual = actual)
        }
    }

    @Test
    fun `put garden update should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardenupdate@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardenupdate@example.com"))
        val userNo = user.id
        removeSignupGarden(userNo)

        val gardenNo = createGardenMembership(
            userId = userNo,
            title = "수정 전 가든",
            info = "수정 전 소개",
            color = "blue",
            leader = true,
            main = true,
        )

        val response = mockMvc.perform(
            put("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", gardenNo.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"garden_title":"수정 후 가든","garden_info":"수정 후 소개","garden_color":"green"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/update-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `delete garden should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardendelete@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardendelete@example.com"))
        val userNo = user.id

        val gardenNo = createGardenMembership(
            userId = userNo,
            title = "삭제 대상 가든",
            info = "삭제 소개",
            color = "red",
            leader = true,
            main = false,
        )

        val response = mockMvc.perform(
            delete("/api/v1/garden/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", gardenNo.toString()),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/delete-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `put garden move should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardenmove@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardenmove@example.com"))
        val userNo = user.id
        removeSignupGarden(userNo)

        val sourceGardenNo = createGardenMembership(
            userId = userNo,
            title = "이동 전 가든",
            info = "출발",
            color = "yellow",
            leader = true,
            main = true,
        )
        val destinationGardenNo = createGardenMembership(
            userId = userNo,
            title = "이동 후 가든",
            info = "도착",
            color = "purple",
            leader = false,
            main = false,
        )
        val sourceGarden = gardenRepository.findById(sourceGardenNo).orElseThrow()
        bookRepository.save(
            BookEntity(
                garden = sourceGarden,
                title = "이동할 책",
                author = "저자",
                publisher = "출판사",
                status = 1,
                user = user,
                page = 210,
                isbn = "9784444444444",
                tree = "소설",
                imageUrl = "https://example.com/move.jpg",
                info = "이동용 책",
            ),
        )

        val response = mockMvc.perform(
            put("/api/v1/garden/to")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", sourceGardenNo.toString())
                .queryParam("to_garden_no", destinationGardenNo.toString()),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/move-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `delete garden member leave should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardenleave@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardenleave@example.com"))
        val userNo = user.id

        val leader = userRepository.save(
            UserEntity(
                email = "garden-leave-leader@example.com",
                password = "pw1234",
                createdAt = LocalDateTime.of(2024, 3, 1, 9, 0, 0),
                nick = "리더",
                image = "leader.png",
                fcm = "leader-fcm",
                socialId = "",
                socialType = "",
            ),
        )
        val garden = gardenRepository.save(
            GardenEntity(
                title = "탈퇴 가든",
                info = "탈퇴 소개",
                color = "mint",
                createdAt = LocalDateTime.of(2024, 3, 1, 8, 30, 0),
            ),
        )
        val gardenNo = checkNotNull(garden.id)
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = leader,
                isLeader = true,
                isMain = true,
                joinDate = LocalDateTime.of(2024, 3, 1, 8, 40, 0),
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = user,
                isLeader = false,
                isMain = false,
                joinDate = LocalDateTime.of(2024, 3, 1, 8, 50, 0),
            ),
        )

        val response = mockMvc.perform(
            delete("/api/v1/garden/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", gardenNo.toString()),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/leave-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `put garden leader transfer should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardenleader@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardenleader@example.com"))
        val userNo = user.id
        removeSignupGarden(userNo)

        val member = userRepository.save(
            UserEntity(
                email = "garden-leader-member@example.com",
                password = "pw1234",
                createdAt = LocalDateTime.of(2024, 4, 1, 9, 30, 0),
                nick = "새 리더",
                image = "member.png",
                fcm = "member-fcm",
                socialId = "",
                socialType = "",
            ),
        )
        val garden = gardenRepository.save(
            GardenEntity(
                title = "리더 변경 가든",
                info = "리더 소개",
                color = "navy",
                createdAt = LocalDateTime.of(2024, 4, 1, 9, 0, 0),
            ),
        )
        val gardenNo = checkNotNull(garden.id)
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = user,
                isLeader = true,
                isMain = true,
                joinDate = LocalDateTime.of(2024, 4, 1, 9, 10, 0),
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = member,
                isLeader = false,
                isMain = false,
                joinDate = LocalDateTime.of(2024, 4, 1, 9, 20, 0),
            ),
        )

        val response = mockMvc.perform(
            put("/api/v1/garden/member")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", gardenNo.toString())
                .queryParam("user_no", checkNotNull(member.id).toString()),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/leader-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `put garden main should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardenmain@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardenmain@example.com"))
        val userNo = user.id

        val targetGardenNo = createGardenMembership(
            userId = userNo,
            title = "메인 변경 대상",
            info = "메인 소개",
            color = "white",
            leader = true,
            main = false,
        )

        val response = mockMvc.perform(
            put("/api/v1/garden/main")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", targetGardenNo.toString()),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/main-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `post garden invite should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("gardeninvite@example.com")
        val user = checkNotNull(userRepository.findByEmail("gardeninvite@example.com"))
        val userNo = user.id

        val leader = userRepository.save(
            UserEntity(
                email = "garden-invite-leader@example.com",
                password = "pw1234",
                createdAt = LocalDateTime.of(2024, 5, 1, 9, 0, 0),
                nick = "초대 리더",
                image = "leader.png",
                fcm = "invite-leader-fcm",
                socialId = "",
                socialType = "",
            ),
        )
        val garden = gardenRepository.save(
            GardenEntity(
                title = "초대 가든",
                info = "초대 소개",
                color = "pink",
                createdAt = LocalDateTime.of(2024, 5, 1, 8, 0, 0),
            ),
        )
        val gardenNo = checkNotNull(garden.id)
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = leader,
                isLeader = true,
                isMain = true,
                joinDate = LocalDateTime.of(2024, 5, 1, 8, 10, 0),
            ),
        )

        val response = mockMvc.perform(
            post("/api/v1/garden/invite")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("garden_no", gardenNo.toString()),
        )
            .andExpect(status().isCreated)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/garden/invite-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `get push should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("pushget@example.com")

        val response = mockMvc.perform(
            get("/api/v1/push/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/push/get-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
    }

    @Test
    fun `put push should match legacy contract shape`() {
        val accessToken = signupAndGetAccessToken("pushupdate@example.com")

        val response = mockMvc.perform(
            put("/api/v1/push/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"push_book_ok":true,"push_time":"2026-04-06T12:30:00"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()

        assertSameShape(
            fixture = readFixture("contracts/legacy/push/update-success.json"),
            actual = objectMapper.readTree(response.response.contentAsString),
        )
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

    private fun createGardenMembership(
        userId: Long,
        title: String,
        info: String,
        color: String,
        leader: Boolean,
        main: Boolean,
    ): Long {
        val user = checkNotNull(userRepository.findById(userId).orElse(null))
        val garden = gardenRepository.save(
            GardenEntity(
                title = title,
                info = info,
                color = color,
                createdAt = LocalDateTime.of(2024, 1, 1, 9, 0, 0),
            ),
        )
        gardenMemberRepository.save(
            GardenMemberEntity(
                garden = garden,
                user = user,
                isLeader = leader,
                isMain = main,
                joinDate = LocalDateTime.of(2024, 1, 1, 9, 5, 0),
            ),
        )
        return garden.id
    }

    private fun removeSignupGarden(userNo: Long) {
        gardenMemberRepository.findAllByUserId(userNo).forEach { membership ->
            gardenMemberRepository.delete(membership)
            gardenRepository.deleteById(membership.garden.id)
        }
    }

    private fun readFixture(path: String): JsonNode =
        ClassPathResource(path).inputStream.use(objectMapper::readTree)

    private fun assertSameShape(
        fixture: JsonNode,
        actual: JsonNode,
        path: String = "$",
    ) {
        when {
            fixture.isObject -> {
                assertThat(actual.isObject).withFailMessage("$path should be an object").isTrue()
                val expectedFields = fixture.fieldNames().asSequence().toSet()
                val actualFields = actual.fieldNames().asSequence().toSet()
                assertThat(actualFields).withFailMessage("$path field keys differ").isEqualTo(expectedFields)
                expectedFields.forEach { field ->
                    assertThat(actual.has(field)).withFailMessage("$path.$field is missing").isTrue()
                    assertSameShape(fixture.get(field), actual.get(field), "$path.$field")
                }
            }

            fixture.isArray -> {
                assertThat(actual.isArray).withFailMessage("$path should be an array").isTrue()
                if (fixture.size() > 0) {
                    assertThat(actual.size())
                        .withFailMessage("$path should contain at least ${fixture.size()} elements")
                        .isGreaterThanOrEqualTo(fixture.size())
                    for (index in 0 until fixture.size()) {
                        assertSameShape(fixture[index], actual[index], "$path[$index]")
                    }
                }
            }

            fixture.isTextual -> {
                assertThat(actual.isTextual).withFailMessage("$path should be a string").isTrue()
                if (path in exactEnvelopeValuePaths) {
                    assertThat(actual.asText())
                        .withFailMessage("$path should match the fixture value")
                        .isEqualTo(fixture.asText())
                }
            }

            fixture.isNumber -> {
                assertThat(actual.isNumber).withFailMessage("$path should be a number").isTrue()
                if (path in exactEnvelopeValuePaths) {
                    assertThat(actual.decimalValue())
                        .withFailMessage("$path should match the fixture value")
                        .isEqualTo(fixture.decimalValue())
                }
            }

            fixture.isBoolean -> {
                assertThat(actual.isBoolean).withFailMessage("$path should be a boolean").isTrue()
            }

            fixture.isNull -> {
                assertThat(actual.isNull).withFailMessage("$path should be null").isTrue()
            }

            else -> {
                assertNotNull(actual, "$path should exist")
            }
        }
    }

    class FixedBookCatalogClient : BookCatalogClient {
        var searchBooksResponse: Map<String, Any?> = emptyMap()
        var detailResponse: Map<String, Any?> = emptyMap()

        override fun searchBooks(
            query: String,
            start: Int,
            maxResults: Int,
        ): Map<String, Any?> = searchBooksResponse

        override fun searchBookByIsbn(query: String): Map<String, Any?> = emptyMap()

        override fun getBookDetailByIsbn(query: String): Map<String, Any?> = detailResponse
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        @Primary
        fun bookCatalogClient(): FixedBookCatalogClient = FixedBookCatalogClient()
    }
}
