package std.nooook.readinggardenkotlin.modules.memo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
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
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoImageEntity
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.memo.service.MemoImageService
import std.nooook.readinggardenkotlin.modules.push.repository.PushSettingsRepository
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class MemoImageIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val refreshTokenRepository: RefreshTokenRepository,
    @Autowired private val pushSettingsRepository: PushSettingsRepository,
    @Autowired private val bookRepository: BookRepository,
    @Autowired private val memoRepository: MemoRepository,
    @Autowired private val memoImageRepository: MemoImageRepository,
    @Autowired private val memoImageService: MemoImageService,
    @Autowired private val transactionManager: PlatformTransactionManager,
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

    @Test
    fun `upload memo image should persist file and row`() {
        val accessToken = signupAndGetAccessToken("memoimageupload@example.com")
        val user = checkNotNull(userRepository.findByEmail("memoimageupload@example.com"))
        val userNo = user.id
        val memoId = createMemo(userNo, "이미지 업로드 메모")
        val file = MockMultipartFile(
            "file",
            "cover.png",
            MediaType.IMAGE_PNG_VALUE,
            "image-bytes".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/v1/memo/image")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("id", memoId.toString()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("이미지 업로드 성공"))

        val image = memoImagesFor(memoId).single()
        assertTrue(image.url.startsWith("memo/"))
        assertTrue(Files.exists(imagesRoot.resolve(image.url)))
    }

    @Test
    fun `upload memo image should replace existing file and keep one row`() {
        val accessToken = signupAndGetAccessToken("memoimagereplace@example.com")
        val user = checkNotNull(userRepository.findByEmail("memoimagereplace@example.com"))
        val userNo = user.id
        val memoId = createMemo(userNo, "이미지 교체 메모")
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
            multipart("/api/v1/memo/image")
                .file(firstFile)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("id", memoId.toString()),
        )
            .andExpect(status().isCreated)

        val firstImage = memoImagesFor(memoId).single()
        val firstStoredPath = imagesRoot.resolve(firstImage.url)
        assertTrue(Files.exists(firstStoredPath))

        mockMvc.perform(
            multipart("/api/v1/memo/image")
                .file(secondFile)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("id", memoId.toString()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_msg").value("이미지 업로드 성공"))

        val replacedImage = memoImagesFor(memoId).single()
        assertFalse(Files.exists(firstStoredPath))
        assertTrue(Files.exists(imagesRoot.resolve(replacedImage.url)))
        assertEquals(1, memoImageRepository.count())
        assertFalse(firstImage.url == replacedImage.url)
    }

    @Test
    fun `upload memo image should clean up saved file when outer transaction rolls back`() {
        val accessToken = signupAndGetAccessToken("memoimageuploadrollback@example.com")
        val user = checkNotNull(userRepository.findByEmail("memoimageuploadrollback@example.com"))
        val userNo = user.id
        val memoId = createMemo(userNo, "롤백 메모")
        val originalFile = MockMultipartFile(
            "file",
            "original.png",
            MediaType.IMAGE_PNG_VALUE,
            "original-image".toByteArray(),
        )
        val replacementFile = MockMultipartFile(
            "file",
            "replacement.png",
            MediaType.IMAGE_PNG_VALUE,
            "replacement-image".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/v1/memo/image")
                .file(originalFile)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("id", memoId.toString()),
        )
            .andExpect(status().isCreated)

        val beforeImage = memoImagesFor(memoId).single()
        val beforePath = imagesRoot.resolve(beforeImage.url)
        assertTrue(Files.exists(beforePath))

        assertThrows(IllegalStateException::class.java) {
            TransactionTemplate(transactionManager).executeWithoutResult {
                memoImageService.uploadMemoImage(userNo, memoId, replacementFile)
                error("rollback")
            }
        }

        val afterImage = memoImagesFor(memoId).single()
        assertEquals(beforeImage.url, afterImage.url)
        assertTrue(Files.exists(beforePath))
        assertEquals(setOf(beforeImage.url), listRegularFiles())
    }

    @Test
    fun `upload memo image should clean duplicate rows and files before storing one image`() {
        val accessToken = signupAndGetAccessToken("memoimageduplicateupload@example.com")
        val user = checkNotNull(userRepository.findByEmail("memoimageduplicateupload@example.com"))
        val userNo = user.id
        val memoId = createMemo(userNo, "중복 업로드 메모")
        seedMemoImage(memoId, "memo/duplicate-1.png", "duplicate-one")
        seedMemoImage(memoId, "memo/duplicate-2.png", "duplicate-two")
        val replacementFile = MockMultipartFile(
            "file",
            "replacement.png",
            MediaType.IMAGE_PNG_VALUE,
            "replacement-image".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/v1/memo/image")
                .file(replacementFile)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("id", memoId.toString()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_msg").value("이미지 업로드 성공"))

        val images = memoImagesFor(memoId)
        assertEquals(1, images.size)
        assertTrue(images.single().url.startsWith("memo/"))
        assertFalse(Files.exists(imagesRoot.resolve("memo/duplicate-1.png")))
        assertFalse(Files.exists(imagesRoot.resolve("memo/duplicate-2.png")))
        assertTrue(Files.exists(imagesRoot.resolve(images.single().url)))
    }

    @Test
    fun `delete memo image should remove duplicate rows and files`() {
        val accessToken = signupAndGetAccessToken("memoimageduplicatedelete@example.com")
        val user = checkNotNull(userRepository.findByEmail("memoimageduplicatedelete@example.com"))
        val userNo = user.id
        val memoId = createMemo(userNo, "중복 삭제 메모")
        seedMemoImage(memoId, "memo/delete-duplicate-1.png", "delete-duplicate-one")
        seedMemoImage(memoId, "memo/delete-duplicate-2.png", "delete-duplicate-two")

        mockMvc.perform(
            delete("/api/v1/memo/image")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", memoId.toString()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("이미지 삭제 성공"))

        assertTrue(memoImagesFor(memoId).isEmpty())
        assertFalse(Files.exists(imagesRoot.resolve("memo/delete-duplicate-1.png")))
        assertFalse(Files.exists(imagesRoot.resolve("memo/delete-duplicate-2.png")))
    }

    @Test
    fun `upload memo image should reject files over five megabytes`() {
        val accessToken = signupAndGetAccessToken("memoimageoversize@example.com")
        val user = checkNotNull(userRepository.findByEmail("memoimageoversize@example.com"))
        val userNo = user.id
        val memoId = createMemo(userNo, "큰 이미지 메모")
        val oversizedBytes = ByteArray(5 * 1024 * 1024 + 1)
        val file = MockMultipartFile(
            "file",
            "too-big.png",
            MediaType.IMAGE_PNG_VALUE,
            oversizedBytes,
        )

        mockMvc.perform(
            multipart("/api/v1/memo/image")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("id", memoId.toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("이미지 용량은 5MB를 초과할 수 없습니다."))

        assertTrue(memoImagesFor(memoId).isEmpty())
    }

    @Test
    fun `upload memo image should return bad request when memo is missing`() {
        val accessToken = signupAndGetAccessToken("memoimagemissingupload@example.com")
        val file = MockMultipartFile(
            "file",
            "missing.png",
            MediaType.IMAGE_PNG_VALUE,
            "image-bytes".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/v1/memo/image")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("id", "999999"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))
    }

    @Test
    fun `upload memo image should return bad request when memo belongs to another user`() {
        signupAndGetAccessToken("memoimageuploadowner@example.com")
        val visitorAccessToken = signupAndGetAccessToken("memoimageuploadvisitor@example.com")
        val ownerUser = checkNotNull(userRepository.findByEmail("memoimageuploadowner@example.com"))
        val memoId = createMemo(ownerUser.id, "타인 이미지 업로드 메모")
        val file = MockMultipartFile(
            "file",
            "other-user.png",
            MediaType.IMAGE_PNG_VALUE,
            "image-bytes".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/v1/memo/image")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $visitorAccessToken")
                .param("id", memoId.toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))

        assertTrue(memoImagesFor(memoId).isEmpty())
        assertTrue(listRegularFiles().isEmpty())
    }

    @Test
    fun `delete memo image should remove file and record`() {
        val accessToken = signupAndGetAccessToken("memoimagedelete@example.com")
        val user = checkNotNull(userRepository.findByEmail("memoimagedelete@example.com"))
        val userNo = user.id
        val memoId = createMemo(userNo, "이미지 삭제 메모")
        val file = MockMultipartFile(
            "file",
            "delete.png",
            MediaType.IMAGE_PNG_VALUE,
            "delete-image".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/v1/memo/image")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .param("id", memoId.toString()),
        )
            .andExpect(status().isCreated)

        val storedImage = memoImagesFor(memoId).single()
        val storedPath = imagesRoot.resolve(storedImage.url)
        assertTrue(Files.exists(storedPath))

        mockMvc.perform(
            delete("/api/v1/memo/image")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", memoId.toString()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("이미지 삭제 성공"))

        assertTrue(memoImagesFor(memoId).isEmpty())
        assertFalse(Files.exists(storedPath))
    }

    @Test
    fun `delete memo image should return bad request when image is missing`() {
        val accessToken = signupAndGetAccessToken("memoimagemissing@example.com")
        val user = checkNotNull(userRepository.findByEmail("memoimagemissing@example.com"))
        val userNo = user.id
        val memoId = createMemo(userNo, "이미지 없는 메모")

        mockMvc.perform(
            delete("/api/v1/memo/image")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", memoId.toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 이미지가 없습니다."))
    }

    @Test
    fun `delete memo image should return bad request when memo is missing`() {
        val accessToken = signupAndGetAccessToken("memoimagemissingmemo@example.com")

        mockMvc.perform(
            delete("/api/v1/memo/image")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .queryParam("id", "999999"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))
    }

    @Test
    fun `delete memo image should return bad request when memo belongs to another user`() {
        signupAndGetAccessToken("memoimagedeleteowner@example.com")
        val visitorAccessToken = signupAndGetAccessToken("memoimagedeletevisitor@example.com")
        val ownerUser = checkNotNull(userRepository.findByEmail("memoimagedeleteowner@example.com"))
        val memoId = createMemo(ownerUser.id, "타인 이미지 삭제 메모")
        val imageRelativePath = "memo/other-user-delete.png"
        seedMemoImage(memoId, imageRelativePath, "delete-image")
        val storedPath = imagesRoot.resolve(imageRelativePath)

        mockMvc.perform(
            delete("/api/v1/memo/image")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $visitorAccessToken")
                .queryParam("id", memoId.toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("일치하는 메모가 없습니다."))

        assertEquals(1, memoImagesFor(memoId).size)
        assertTrue(Files.exists(storedPath))
    }

    private fun createMemo(
        userNo: Long,
        memoContent: String,
    ): Long {
        val userEntity = userRepository.findById(userNo).orElseThrow()
        val bookEntity = bookRepository.save(
            BookEntity(
                user = userEntity,
                title = "$memoContent 책",
                author = "저자",
                publisher = "출판사",
                info = "소개",
                status = 0,
                page = 100,
            ),
        )

        return memoRepository.save(
            MemoEntity(
                book = bookEntity,
                content = memoContent,
                user = userEntity,
                isLiked = false,
            ),
        ).id
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

    private fun memoImagesFor(memoId: Long): List<MemoImageEntity> {
        return memoImageRepository.findAllByMemoIdIn(listOf(memoId))
    }

    private fun seedMemoImage(
        memoId: Long,
        relativePath: String,
        content: String,
    ) {
        val targetPath = imagesRoot.resolve(relativePath)
        Files.createDirectories(targetPath.parent)
        Files.write(targetPath, content.toByteArray())
        val memoEntity = memoRepository.findById(memoId).orElseThrow()
        memoImageRepository.save(
            MemoImageEntity(
                memo = memoEntity,
                name = relativePath.substringAfterLast('/'),
                url = relativePath,
            ),
        )
    }

    private fun listRegularFiles(): Set<String> {
        if (!Files.exists(imagesRoot)) {
            return emptySet()
        }

        Files.walk(imagesRoot).use { paths ->
            return paths
                .filter { Files.isRegularFile(it) }
                .map { imagesRoot.relativize(it).toString().replace('\\', '/') }
                .toList()
                .toSet()
        }
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
