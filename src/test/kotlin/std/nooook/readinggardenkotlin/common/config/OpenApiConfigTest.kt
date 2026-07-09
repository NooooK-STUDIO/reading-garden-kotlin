package std.nooook.readinggardenkotlin.common.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import std.nooook.readinggardenkotlin.TestcontainersConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiConfigTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()
    private lateinit var apiDocs: JsonNode

    @BeforeEach
    fun setUp() {
        val response = mockMvc.perform(
            get("/v3/api-docs").accept(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andReturn()

        apiDocs = objectMapper.readTree(response.response.contentAsString)
    }

    @Test
    fun `open api should expose legacy metadata and bearer auth`() {
        assertThat(apiDocs.at("/info/title").asText()).isEqualTo("Reading Garden Legacy API")
        assertThat(apiDocs.at("/info/description").asText()).contains("Legacy").contains("Flutter")
        assertThat(apiDocs.at("/components/securitySchemes/bearerAuth/type").asText()).isEqualTo("http")
        assertThat(apiDocs.at("/tags").toString()).contains("Auth", "Book", "Garden", "Memo", "Push")
    }

    @Test
    fun `auth endpoints should describe signup and protected profile contract`() {
        val signup = requireOperation("post", "/api/v1/auth", "/api/v1/auth/")
        val profile = requireOperation("get", "/api/v1/auth", "/api/v1/auth/")

        assertThat(signup.at("/summary").asText()).contains("회원가입")
        assertThat(signup.at("/description").asText()).contains("user_email").contains("user_password")
        assertThat(signup.at("/requestBody/content/application~1json/schema/\$ref").asText()).contains("CreateUserRequest")
        assertThat(signup.at("/responses/201").isMissingNode).isFalse
        assertThat(signup.at("/responses/400").isMissingNode).isFalse

        assertThat(profile.at("/summary").asText()).contains("프로필")
        assertThat(profile.at("/security").isEmpty).isFalse
        assertThat(profile.at("/responses/200").isMissingNode).isFalse
        assertThat(profile.at("/responses/401").isMissingNode).isFalse
    }

    @Test
    fun `app and auth endpoints should expose concrete response examples`() {
        val appVersion = requireOperation("get", "/api/v1/app/version")
        val signup = requireOperation("post", "/api/v1/auth", "/api/v1/auth/")
        val login = requireOperation("post", "/api/v1/auth/login")
        val logout = requireOperation("post", "/api/v1/auth/logout")
        val refresh = requireOperation("post", "/api/v1/auth/refresh")
        val findPassword = requireOperation("post", "/api/v1/auth/find-password")
        val findPasswordCheck = requireOperation("post", "/api/v1/auth/find-password/check")
        val updatePasswordWithoutToken = requireOperation("put", "/api/v1/auth/find-password/update-password")
        val profile = requireOperation("get", "/api/v1/auth", "/api/v1/auth/")
        val profileUpdate = requireOperation("put", "/api/v1/auth", "/api/v1/auth/")

        assertResponseSchemaRef(signup, "201", "application/json", "SignupLegacyDataResponse")
        assertResponseSchemaRef(login, "200", "application/json", "LoginLegacyDataResponse")
        assertResponseSchemaRef(logout, "200", "application/json", "EmptyLegacyDataResponse")
        assertResponseSchemaRef(refresh, "200", "application/json", "TokenRefreshLegacyDataResponse")
        assertResponseSchemaRef(appVersion, "200", "application/json", "AppVersionLegacyDataResponse")
        assertResponseSchemaRef(profile, "200", "application/json", "UserProfileLegacyDataResponse")
        assertResponseSchemaRef(profileUpdate, "200", "application/json", "UserSummaryLegacyDataResponse")
        assertResponseSchemaRef(refresh, "401", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(findPassword, "400", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(findPasswordCheck, "400", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(updatePasswordWithoutToken, "200", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(updatePasswordWithoutToken, "400", "application/json", "LegacyHttpResponse")

        assertResponseExamplesContain(appVersion, "400", "platform must be one of: ios, android")
        assertResponseExamplesContain(appVersion, "404", "App version not found for platform: ios")

        assertResponseExamplesContain(login, "200", "로그인 성공")
        assertResponseExamplesContain(login, "400", "등록되지 않은 이메일 주소입니다.")
        assertResponseExamplesContain(logout, "200", "로그아웃 성공")
        assertResponseExamplesContain(refresh, "200", "토큰 발급 성공")
        assertResponseExamplesContain(refresh, "401", "Unauthorized")
        assertResponseExamplesContain(findPassword, "200", "메일이 발송되었습니다. 확인해주세요.")
        assertResponseExamplesContain(findPassword, "400", "등록되지 않은 이메일 주소입니다.")
        assertResponseExamplesContain(findPasswordCheck, "400", "인증번호 불일치")
        assertResponseExamplesContain(updatePasswordWithoutToken, "400", "등록되지 않은 이메일 주소입니다.")

        assertThat(refresh.at("/responses/401").isMissingNode).isFalse
        assertThat(refresh.at("/responses/400").isMissingNode).isTrue

        assertSchemaExample("AppVersionResponse", "latest_version", "1.2.0")
        assertSchemaExample("UserProfileResponse", "user_nick", "임의닉네임")
        assertSchemaExample("UserSummaryResponse", "user_fcm", "fcm-token-value")
    }

    @Test
    fun `book endpoints should expose concrete examples for map and http response payloads`() {
        val duplication = requireOperation("get", "/api/v1/book", "/api/v1/book/")
        val search = requireOperation("get", "/api/v1/book/search")
        val searchIsbn = requireOperation("get", "/api/v1/book/search-isbn")
        val detailIsbn = requireOperation("get", "/api/v1/book/detail-isbn")
        val bookStatus = requireOperation("get", "/api/v1/book/status")
        val bookRead = requireOperation("get", "/api/v1/book/read")
        val updateBook = requireOperation("put", "/api/v1/book", "/api/v1/book/")
        val updateRead = requireOperation("put", "/api/v1/book/read")
        val deleteBook = requireOperation("delete", "/api/v1/book", "/api/v1/book/")
        val upload = requireOperation("post", "/api/v1/book/image")
        val deleteImage = requireOperation("delete", "/api/v1/book/image")

        assertResponseSchemaRef(duplication, "200", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(duplication, "403", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(search, "200", "application/json", "BookSearchLegacyDataResponse")
        assertResponseSchemaRef(searchIsbn, "200", "application/json", "BookLookupLegacyDataResponse")
        assertResponseSchemaRef(detailIsbn, "200", "application/json", "BookDetailLegacyDataResponse")
        assertResponseSchemaRef(bookStatus, "200", "application/json", "BookStatusLegacyDataResponse")
        assertResponseSchemaRef(bookRead, "200", "application/json", "BookReadDetailLegacyDataResponse")
        assertResponseSchemaRef(updateRead, "200", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(upload, "201", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(deleteBook, "200", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(deleteImage, "201", "application/json", "LegacyHttpResponse")

        assertResponseExamplesContain(duplication, "200", "책 등록 가능")
        assertResponseExamplesContain(duplication, "403", "책 중복")
        assertResponseExamplesContain(search, "200", "책 검색 성공")
        assertResponseExamplesContain(searchIsbn, "200", "책 검색(ISBN) 성공")
        assertResponseExamplesContain(detailIsbn, "200", "책 상세 조회 성공")
        assertResponseExamplesContain(bookStatus, "200", "book_rating")
        assertResponseExamplesContain(bookRead, "200", "book_rating")
        assertResponseExamplesContain(updateRead, "200", "독서 기록 수정 성공")
        assertResponseExamplesContain(upload, "201", "이미지 업로드 성공")
        assertResponseExamplesContain(deleteBook, "200", "책 삭제 성공")
        assertResponseExamplesContain(deleteImage, "201", "이미지 삭제 성공")

        assertThat(search.at("/parameters").toString()).contains("query", "start", "maxResults")
        assertThat(updateBook.at("/requestBody/content/application~1json/schema/\$ref").asText()).contains("UpdateBookRequest")
        assertThat(updateBook.at("/requestBody/content/application~1json/examples").toString()).contains("book_rating")
        assertThat(upload.at("/requestBody/content/multipart~1form-data").isMissingNode).isFalse
        assertThat(upload.at("/security").isEmpty).isFalse
        assertThat(upload.at("/responses/400").isMissingNode).isFalse
        assertThat(upload.at("/responses/401").isMissingNode).isFalse

        assertSchemaExample("BookStatusResponse", "current_page", "1")
        assertSchemaExample("BookStatusItemResponse", "book_rating", "5")
        assertSchemaExample("BookReadDetailResponse", "book_rating", "5")
        assertSchemaExample("UpdateBookRequest", "book_rating", "5")
        assertSchemaExample("BookReadHistoryItemResponse", "book_current_page", "150")
        assertSchemaExample("BookDetailResponse", "itemPage", "321")
    }

    @Test
    fun `garden memo and push endpoints should expose concrete examples and dto schema examples`() {
        val gardenList = requireOperation("get", "/api/v1/garden/list")
        val gardenUpdate = requireOperation("put", "/api/v1/garden", "/api/v1/garden/")
        val memoList = requireOperation("get", "/api/v1/memo", "/api/v1/memo/")
        val memoImage = requireOperation("post", "/api/v1/memo/image")
        val memoLike = requireOperation("put", "/api/v1/memo/like")
        val memoImageDelete = requireOperation("delete", "/api/v1/memo/image")
        val pushGet = requireOperation("get", "/api/v1/push", "/api/v1/push/")
        val pushUpdate = requireOperation("put", "/api/v1/push", "/api/v1/push/")
        val pushNotice = requireOperation("post", "/api/v1/push/notice")

        assertResponseSchemaRef(gardenList, "200", "application/json", "GardenListLegacyDataResponse")
        assertResponseSchemaRef(gardenUpdate, "200", "application/json", "GardenEmptyLegacyDataResponse")
        assertResponseSchemaRef(memoList, "200", "application/json", "MemoListLegacyDataResponse")
        assertResponseSchemaRef(memoImage, "201", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(memoLike, "200", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(memoImageDelete, "201", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(pushGet, "200", "application/json", "PushLegacyDataResponse")
        assertResponseSchemaRef(pushUpdate, "200", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(pushNotice, "200", "application/json", "PushSendLegacyDataResponse")

        assertResponseExamplesContain(gardenList, "200", "가든 리스트 조회 성공")
        assertResponseExamplesContain(gardenUpdate, "200", "가든 수정 성공")
        assertResponseExamplesContain(memoList, "200", "메모 리스트 조회 성공")
        assertResponseExamplesContain(memoImage, "201", "이미지 업로드 성공")
        assertResponseExamplesContain(memoLike, "200", "메모 즐겨찾기 추가/해제")
        assertResponseExamplesContain(memoImageDelete, "201", "이미지 삭제 성공")
        assertResponseExamplesContain(pushGet, "200", "푸시 알림 조회 성공")
        assertResponseExamplesContain(pushUpdate, "200", "푸시 알림 수정 성공")
        assertResponseExamplesContain(pushNotice, "200", "공지사항 푸시 전송 성공")

        assertThat(memoImage.at("/requestBody/content/multipart~1form-data").isMissingNode).isFalse
        assertThat(memoImage.at("/security").isEmpty).isFalse

        assertSchemaExample("GardenDetailResponse", "garden_title", "메인 가든")
        assertSchemaExample("MemoListResponse", "current_page", "1")
        assertSchemaExample("PushResponse", "push_time", "2026-04-10T21:00:00")
    }

    @Test
    fun `open api should expose shared bad request unauthorized and not found examples`() {
        val appVersion = requireOperation("get", "/api/v1/app/version")
        val profile = requireOperation("get", "/api/v1/auth", "/api/v1/auth/")
        val memoImage = requireOperation("post", "/api/v1/memo/image")
        val bookImage = requireOperation("post", "/api/v1/book/image")

        assertResponseSchemaRef(appVersion, "400", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(appVersion, "404", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(profile, "401", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(memoImage, "400", "application/json", "LegacyHttpResponse")
        assertResponseSchemaRef(bookImage, "401", "application/json", "LegacyHttpResponse")

        assertResponseExamplesContain(appVersion, "400", "platform must be one of: ios, android")
        assertResponseExamplesContain(appVersion, "404", "App version not found for platform: ios")
        assertResponseExamplesContain(profile, "401", "Unauthorized")
        assertResponseExamplesContain(memoImage, "400", "Request body validation failed.")
        assertResponseExamplesContain(bookImage, "401", "Unauthorized")
    }

    @Test
    fun `legacy response schema should document envelope fields`() {
        val httpResponse = requireSchema("LegacyHttpResponse")
        val dataResponse = requireSchema("AppVersionLegacyDataResponse")

        assertThat(httpResponse.at("/description").asText()).contains("레거시")
        assertThat(httpResponse.at("/properties").toString()).contains("resp_code", "resp_msg", "errors")
        assertThat(dataResponse.at("/description").asText()).contains("레거시")
        assertThat(dataResponse.at("/properties").toString()).contains("resp_code", "resp_msg", "data")
    }

    @Test
    fun `open api should use same origin server instead of hard coded localhost`() {
        assertThat(apiDocs.at("/servers/0/url").asText()).isEqualTo("/")
        assertThat(apiDocs.at("/servers/0/url").asText()).doesNotContain("localhost:8080")
    }

    private fun requireSchema(name: String): JsonNode {
        val schema = apiDocs.at("/components/schemas/$name")
        require(!schema.isMissingNode) { "Schema not found: $name" }
        return schema
    }

    private fun assertResponseExamplesContain(operation: JsonNode, responseCode: String, expectedText: String) {
        val content = operation.at("/responses/$responseCode/content")
        assertThat(content.isMissingNode).isFalse

        val exampleValues = mutableListOf<JsonNode>()
        val mediaTypeNames = content.fieldNames()
        while (mediaTypeNames.hasNext()) {
            val mediaTypeNode = content.get(mediaTypeNames.next()) ?: continue
            mediaTypeNode.get("example")?.takeUnless { it.isMissingNode }?.let(exampleValues::add)

            val examplesNode = mediaTypeNode.get("examples")
            val exampleNames = examplesNode?.fieldNames()
            while (exampleNames != null && exampleNames.hasNext()) {
                val exampleNode = examplesNode.get(exampleNames.next())?.get("value")?.takeUnless { it.isMissingNode }
                if (exampleNode != null) {
                    exampleValues.add(exampleNode)
                }
            }
        }

        assertThat(exampleValues).isNotEmpty
        assertThat(exampleValues).anySatisfy { example ->
            assertThat(example.renderExampleText()).contains(expectedText)
        }
    }

    private fun assertSchemaExample(schemaName: String, propertyName: String, expectedExample: String) {
        val example = requireSchema(schemaName).at("/properties/$propertyName/example")
        assertThat(example.isMissingNode).isFalse
        assertThat(example.asText()).isEqualTo(expectedExample)
    }

    private fun assertResponseSchemaRef(
        operation: JsonNode,
        responseCode: String,
        mediaType: String,
        expectedSchemaName: String,
    ) {
        val escapedMediaType = mediaType.replace("~", "~0").replace("/", "~1")
        val schemaRef = operation.at("/responses/$responseCode/content/$escapedMediaType/schema/\$ref")
        assertThat(schemaRef.isMissingNode).isFalse
        assertThat(schemaRef.asText()).contains(expectedSchemaName)
    }

    private fun JsonNode.renderExampleText(): String = when {
        isTextual || isNumber || isBoolean || isNull -> asText()
        else -> toString()
    }

    private fun requireOperation(method: String, vararg candidates: String): JsonNode {
        val operation = candidates.firstNotNullOfOrNull { path ->
            val escapedPath = path.replace("~", "~0").replace("/", "~1")
            apiDocs.at("/paths/$escapedPath/$method").takeUnless { it.isMissingNode }
        }
        requireNotNull(operation) { "Operation not found for paths=${candidates.toList()} method=$method" }
        return operation
    }
}
