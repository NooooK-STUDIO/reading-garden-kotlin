package std.nooook.readinggardenkotlin.modules.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import std.nooook.readinggardenkotlin.TestcontainersConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import std.nooook.readinggardenkotlin.modules.auth.entity.RefreshTokenEntity
import std.nooook.readinggardenkotlin.modules.auth.integration.MailSender
import std.nooook.readinggardenkotlin.modules.auth.repository.RefreshTokenRepository
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.auth.service.AuthService
import std.nooook.readinggardenkotlin.modules.book.entity.BookEntity
import std.nooook.readinggardenkotlin.modules.book.entity.BookImageEntity
import std.nooook.readinggardenkotlin.modules.book.entity.BookReadEntity
import std.nooook.readinggardenkotlin.modules.book.repository.BookImageRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenMemberRepository
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoEntity
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoImageEntity
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository
import std.nooook.readinggardenkotlin.modules.push.repository.PushSettingsRepository
import std.nooook.readinggardenkotlin.modules.scheduler.repository.ScheduledJobRepository
import std.nooook.readinggardenkotlin.modules.scheduler.service.AuthPasswordResetExpiryJobService
import std.nooook.readinggardenkotlin.modules.scheduler.service.SchedulerJobExecutionPhase
import std.nooook.readinggardenkotlin.modules.scheduler.service.SchedulerJobExecutionRecord
import std.nooook.readinggardenkotlin.modules.scheduler.service.SchedulerJobExecutionRecorder
import std.nooook.readinggardenkotlin.modules.scheduler.service.SchedulerJobTriggerSource
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.TimeZone
import kotlin.test.assertEquals
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest(
    properties = [
        "app.auth.password-reset-auth-ttl=PT2S",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, AuthControllerIntegrationTest.TestConfig::class)
class AuthControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val refreshTokenRepository: RefreshTokenRepository,
    @Autowired private val bookRepository: BookRepository,
    @Autowired private val bookReadRepository: BookReadRepository,
    @Autowired private val bookImageRepository: BookImageRepository,
    @Autowired private val gardenRepository: GardenRepository,
    @Autowired private val gardenMemberRepository: GardenMemberRepository,
    @Autowired private val memoRepository: MemoRepository,
    @Autowired private val memoImageRepository: MemoImageRepository,
    @Autowired private val pushSettingsRepository: PushSettingsRepository,
    @Autowired private val scheduledJobRepository: ScheduledJobRepository,
    @Autowired private val passwordResetExpiryJobService: AuthPasswordResetExpiryJobService,
    @Autowired private val authService: AuthService,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired private val recordingMailSender: RecordingMailSender,
    @Autowired private val recordingTaskScheduler: RecordingTaskScheduler,
    @Autowired private val recordingSchedulerJobExecutionRecorder: RecordingSchedulerJobExecutionRecorder,
) {
    private val objectMapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    @BeforeEach
    fun setUp() {
        refreshTokenRepository.deleteAll()
        bookReadRepository.deleteAll()
        bookImageRepository.deleteAll()
        bookRepository.deleteAll()
        pushSettingsRepository.deleteAll()
        gardenMemberRepository.deleteAll()
        gardenRepository.deleteAll()
        memoImageRepository.deleteAll()
        memoRepository.deleteAll()
        scheduledJobRepository.deleteAll()
        userRepository.deleteAll()
        recordingMailSender.sentMessages.clear()
        recordingTaskScheduler.clear()
        recordingSchedulerJobExecutionRecorder.records.clear()
    }

    @Test
    fun `signup should create default garden push and refresh token`() {
        val response = mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"new@example.com","user_password":"pw1234","user_fcm":"first-fcm","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))
            .andExpect(jsonPath("$.resp_msg").value("회원가입 성공"))
            .andExpect(jsonPath("$.data.access_token").isString)
            .andExpect(jsonPath("$.data.refresh_token").isString)
            .andReturn()

        val body = objectMapper.readTree(response.response.contentAsString)
        val user = userRepository.findByEmail("new@example.com")
        checkNotNull(user)
        val userNo = checkNotNull(user.id)

        kotlin.test.assertNotEquals("pw1234", user.password)
        kotlin.test.assertEquals("first-fcm", user.fcm)
        kotlin.test.assertEquals(1L, gardenMemberRepository.countByUserId(userNo))
        kotlin.test.assertNotNull(pushSettingsRepository.findByUserId(userNo))
        kotlin.test.assertEquals(
            body.path("data").path("refresh_token").asText(),
            refreshTokenRepository.findByUserId(userNo)?.token,
        )
    }

    @Test
    fun `get push should recreate missing settings for legacy user`() {
        val signupBody = signup("missingpush@example.com", "pw1234", "fcm-missing-push")
        val accessToken = signupBody.path("data").path("access_token").asText()
        val user = userRepository.findByEmail("missingpush@example.com")
        checkNotNull(user)
        val userNo = user.id
        val existingPush = pushSettingsRepository.findByUserId(userNo)
        checkNotNull(existingPush)
        pushSettingsRepository.delete(existingPush)

        mockMvc.perform(
            get("/api/v1/push/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.data.user_no").value(userNo))
            .andExpect(jsonPath("$.data.push_app_ok").value(true))
            .andExpect(jsonPath("$.data.push_book_ok").value(false))

        val recreatedPush = pushSettingsRepository.findByUserId(userNo)
        kotlin.test.assertNotNull(recreatedPush)
        kotlin.test.assertEquals(true, recreatedPush.appOk)
        kotlin.test.assertEquals(false, recreatedPush.bookOk)
    }

    @Test
    fun `duplicate email signup should return legacy conflict envelope`() {
        signup("dup@example.com", "pw", "fcm-1")

        mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"dup@example.com","user_password":"pw","user_fcm":"fcm-2","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.resp_code").value(409))
            .andExpect(jsonPath("$.resp_msg").value("이메일 중복"))
    }

    @Test
    fun `signup should reject invalid email format with legacy validation envelope`() {
        mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"invalid-email","user_password":"pw","user_fcm":"fcm-2","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("Request body validation failed."))
            .andExpect(jsonPath("$.errors[0].field").value("user_email"))

        kotlin.test.assertNull(userRepository.findByEmail("invalid-email"))
    }

    @Test
    fun `login profile refresh and logout should preserve legacy auth flow`() {
        signup("flow@example.com", "pw1234", "fcm-1")

        val loginResponse = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"flow@example.com","user_password":"pw1234","user_fcm":"updated-fcm","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("로그인 성공"))
            .andExpect(jsonPath("$.data.access_token").isString)
            .andExpect(jsonPath("$.data.refresh_token").isString)
            .andReturn()

        val loginBody = objectMapper.readTree(loginResponse.response.contentAsString)
        val accessToken = loginBody.path("data").path("access_token").asText()
        val refreshToken = loginBody.path("data").path("refresh_token").asText()
        val user = userRepository.findByEmail("flow@example.com")
        checkNotNull(user)

        kotlin.test.assertEquals("updated-fcm", user.fcm)

        mockMvc.perform(
            get("/api/v1/auth")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("조회 성공"))
            .andExpect(jsonPath("$.data.user_email").value("flow@example.com"))
            .andExpect(jsonPath("$.data.garden_count").value(1))

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refresh_token":"$refreshToken"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("토큰 발급 성공"))
            .andExpect(jsonPath("$.data").isString)

        mockMvc.perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("로그아웃 성공"))

        kotlin.test.assertEquals("", userRepository.findByEmail("flow@example.com")?.fcm)
        kotlin.test.assertNull(refreshTokenRepository.findByUserId(checkNotNull(user.id)))

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refresh_token":"$refreshToken"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resp_code").value(401))
            .andExpect(jsonPath("$.resp_msg").value("Unauthorized"))
    }

    @Test
    fun `refresh should accept expiry stored in utc local time`() {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
        try {
            signup("tz@example.com", "pw1234", "fcm-tz")

            val loginResponse = mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"user_email":"tz@example.com","user_password":"pw1234","user_fcm":"fcm-tz-2","user_social_id":"","user_social_type":""}""",
                    ),
            )
                .andExpect(status().isOk)
                .andReturn()

            val loginBody = objectMapper.readTree(loginResponse.response.contentAsString)
            val refreshToken = loginBody.path("data").path("refresh_token").asText()
            val user = checkNotNull(userRepository.findByEmail("tz@example.com"))
        val userNo = user.id
            val storedToken = checkNotNull(refreshTokenRepository.findByUserId(userNo))
            storedToken.exp = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30)
            refreshTokenRepository.save(storedToken)

            mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refresh_token":"$refreshToken"}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.resp_code").value(200))
                .andExpect(jsonPath("$.resp_msg").value("토큰 발급 성공"))
                .andExpect(jsonPath("$.data").isString)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun `login should delete all previous refresh tokens before issuing a new one`() {
        signup("refreshcleanup@example.com", "pw1234", "fcm-a")
        val user = checkNotNull(userRepository.findByEmail("refreshcleanup@example.com"))
        val userNo = user.id

        refreshTokenRepository.save(
            RefreshTokenEntity(
                user = user,
                token = "stale-token-1",
                exp = LocalDateTime.now(ZoneOffset.UTC).plusDays(1),
            ),
        )
        refreshTokenRepository.save(
            RefreshTokenEntity(
                user = user,
                token = "stale-token-2",
                exp = LocalDateTime.now(ZoneOffset.UTC).plusDays(1),
            ),
        )

        val loginResponse = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"refreshcleanup@example.com","user_password":"pw1234","user_fcm":"fcm-b","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isOk)
            .andReturn()

        val loginBody = objectMapper.readTree(loginResponse.response.contentAsString)
        val newRefreshToken = loginBody.path("data").path("refresh_token").asText()
        val storedTokens = refreshTokenRepository.findAllByUserId(userNo)

        assertEquals(1, storedTokens.size)
        assertEquals(newRefreshToken, storedTokens.single().token)
    }

    @Test
    fun `find password flow should send mail verify auth code and update password`() {
        signup("reset@example.com", "before-password", "fcm-reset")

        mockMvc.perform(
            post("/api/v1/auth/find-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"reset@example.com"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("메일이 발송되었습니다. 확인해주세요."))

        kotlin.test.assertEquals(1, recordingMailSender.sentMessages.size)
        val sentMessage = recordingMailSender.sentMessages.single()
        kotlin.test.assertEquals("reset@example.com", sentMessage.email)
        kotlin.test.assertEquals("[독서가든] 인증번호 안내드립니다", sentMessage.title)

        val authNumber = userRepository.findByEmail("reset@example.com")?.authNumber
        checkNotNull(authNumber)
        kotlin.test.assertEquals(authNumber, sentMessage.content)

        mockMvc.perform(
            post("/api/v1/auth/find-password/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"reset@example.com","auth_number":"$authNumber"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("인증 성공"))

        mockMvc.perform(
            put("/api/v1/auth/find-password/update-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"reset@example.com","user_password":"after-password"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("비밀번호 변경 성공"))

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"reset@example.com","user_password":"after-password","user_fcm":"new-reset-fcm","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_msg").value("로그인 성공"))
    }

    @Test
    fun `find password should persist auth expiry job`() {
        signup("resetpersist@example.com", "before-password", "fcm-reset-persist")

        mockMvc.perform(
            post("/api/v1/auth/find-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"resetpersist@example.com"}"""),
        )
            .andExpect(status().isOk)

        val user = checkNotNull(userRepository.findByEmail("resetpersist@example.com"))
        val userNo = user.id
        val persistedJob = scheduledJobRepository.findById(AuthPasswordResetExpiryJobService.jobId(userNo))

        kotlin.test.assertTrue(persistedJob.isPresent)
        kotlin.test.assertNotNull(persistedJob.get().scheduledAt)
    }

    @Test
    fun `schedule password reset expiry should register task only after outer transaction commits`() {
        signup("resetaftercommit@example.com", "before-password", "fcm-reset-after-commit")

        transactionTemplate.executeWithoutResult {
            authService.sendPasswordResetMail("resetaftercommit@example.com")

            val user = checkNotNull(userRepository.findByEmail("resetaftercommit@example.com"))
        val userNo = user.id
            kotlin.test.assertTrue(
                scheduledJobRepository.findById(AuthPasswordResetExpiryJobService.jobId(userNo)).isPresent,
            )
            kotlin.test.assertTrue(recordingTaskScheduler.scheduledTasks.isEmpty())
        }

        kotlin.test.assertEquals(1, recordingTaskScheduler.scheduledTasks.size)
        val scheduledTask = recordingTaskScheduler.scheduledTasks.single()
        val user = checkNotNull(userRepository.findByEmail("resetaftercommit@example.com"))
        val userNo = user.id
        val persistedJob = checkNotNull(
            scheduledJobRepository.findById(AuthPasswordResetExpiryJobService.jobId(userNo)).orElse(null),
        )

        kotlin.test.assertEquals(
            persistedJob.scheduledAt.toInstant().toEpochMilli(),
            scheduledTask.runAt.toEpochMilli(),
        )
        kotlin.test.assertTrue(recordingSchedulerJobExecutionRecorder.records.isEmpty())
    }

    @Test
    fun `expired persisted auth expiry job should fail check and clear auth number`() {
        signup("resetexpire@example.com", "before-password", "fcm-reset-expire")

        mockMvc.perform(
            post("/api/v1/auth/find-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"resetexpire@example.com"}"""),
        )
            .andExpect(status().isOk)

        val user = checkNotNull(userRepository.findByEmail("resetexpire@example.com"))
        val authNumber = checkNotNull(user.authNumber)
        val jobId = AuthPasswordResetExpiryJobService.jobId(checkNotNull(user.id))
        val persistedJob = checkNotNull(scheduledJobRepository.findById(jobId).orElse(null))
        persistedJob.scheduledAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1)
        scheduledJobRepository.save(persistedJob)

        mockMvc.perform(
            post("/api/v1/auth/find-password/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"resetexpire@example.com","auth_number":"$authNumber"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("인증번호 불일치"))

        kotlin.test.assertNull(userRepository.findByEmail("resetexpire@example.com")?.authNumber)
        kotlin.test.assertFalse(scheduledJobRepository.findById(jobId).isPresent)
    }

    @Test
    fun `startup rehydration should clear overdue jobs and reschedule future jobs`() {
        signup("rehydrateoverdue@example.com", "before-password", "fcm-rehydrate-overdue")
        signup("rehydratefuture@example.com", "before-password", "fcm-rehydrate-future")

        mockMvc.perform(
            post("/api/v1/auth/find-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"rehydrateoverdue@example.com"}"""),
        )
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/auth/find-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"rehydratefuture@example.com"}"""),
        )
            .andExpect(status().isOk)

        val overdueUser = checkNotNull(userRepository.findByEmail("rehydrateoverdue@example.com"))
        val futureUser = checkNotNull(userRepository.findByEmail("rehydratefuture@example.com"))
        val overdueJobId = AuthPasswordResetExpiryJobService.jobId(checkNotNull(overdueUser.id))
        val futureJobId = AuthPasswordResetExpiryJobService.jobId(checkNotNull(futureUser.id))
        val overdueJob = checkNotNull(scheduledJobRepository.findById(overdueJobId).orElse(null))
        overdueJob.scheduledAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1)
        scheduledJobRepository.save(overdueJob)

        recordingTaskScheduler.clear()
        passwordResetExpiryJobService.rehydratePasswordResetExpiryJobs()

        kotlin.test.assertNull(userRepository.findByEmail("rehydrateoverdue@example.com")?.authNumber)
        kotlin.test.assertFalse(scheduledJobRepository.findById(overdueJobId).isPresent)
        kotlin.test.assertTrue(scheduledJobRepository.findById(futureJobId).isPresent)
        kotlin.test.assertEquals(1, recordingTaskScheduler.scheduledTasks.size)
        kotlin.test.assertEquals(
            listOf(
                SchedulerJobExecutionPhase.STARTED,
                SchedulerJobExecutionPhase.SUCCEEDED,
            ),
            recordingSchedulerJobExecutionRecorder.records.map { it.phase },
        )
        kotlin.test.assertEquals(
            listOf(
                SchedulerJobTriggerSource.REHYDRATED,
                SchedulerJobTriggerSource.REHYDRATED,
            ),
            recordingSchedulerJobExecutionRecorder.records.map { it.triggerSource },
        )
        kotlin.test.assertEquals(
            listOf(overdueJobId, overdueJobId),
            recordingSchedulerJobExecutionRecorder.records.map { it.jobId },
        )
        kotlin.test.assertEquals(
            "expire_overdue",
            recordingSchedulerJobExecutionRecorder.records.first().context["rehydrate_action"],
        )
        kotlin.test.assertTrue(
            recordingTaskScheduler.scheduledTasks.any { scheduledTask ->
                scheduledTask.runAt.toEpochMilli() ==
                    checkNotNull(scheduledJobRepository.findById(futureJobId).orElse(null)).scheduledAt.toInstant().toEpochMilli()
            },
        )

        Thread.sleep(2_500)

        kotlin.test.assertNull(userRepository.findByEmail("rehydratefuture@example.com")?.authNumber)
        kotlin.test.assertFalse(scheduledJobRepository.findById(futureJobId).isPresent)
    }

    @Test
    fun `reissuing password reset should keep latest auth number valid until latest ttl`() {
        signup("reissue@example.com", "before-password", "fcm-reissue")

        mockMvc.perform(
            post("/api/v1/auth/find-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"reissue@example.com"}"""),
        )
            .andExpect(status().isOk)

        val firstAuthNumber = checkNotNull(userRepository.findByEmail("reissue@example.com")?.authNumber)

        Thread.sleep(1_400)

        mockMvc.perform(
            post("/api/v1/auth/find-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"reissue@example.com"}"""),
        )
            .andExpect(status().isOk)

        val secondAuthNumber = checkNotNull(userRepository.findByEmail("reissue@example.com")?.authNumber)
        kotlin.test.assertNotEquals(firstAuthNumber, secondAuthNumber)

        Thread.sleep(900)

        mockMvc.perform(
            post("/api/v1/auth/find-password/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"reissue@example.com","auth_number":"$secondAuthNumber"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("인증 성공"))

        Thread.sleep(1_300)

        mockMvc.perform(
            post("/api/v1/auth/find-password/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_email":"reissue@example.com","auth_number":"$secondAuthNumber"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resp_code").value(400))
            .andExpect(jsonPath("$.resp_msg").value("인증번호 불일치"))
    }

    @Test
    fun `startup rehydration should delete malformed persisted jobs safely`() {
        scheduledJobRepository.save(
            std.nooook.readinggardenkotlin.modules.scheduler.entity.ScheduledJobEntity(
                id = "auth:password-reset-expiry:999",
                jobType = "AUTH_PASSWORD_RESET_EXPIRY",
                scheduledAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(120),
                payload = """{"malformed": true}""",
            ),
        )

        passwordResetExpiryJobService.rehydratePasswordResetExpiryJobs()

        kotlin.test.assertFalse(scheduledJobRepository.findById("auth:password-reset-expiry:999").isPresent)
        kotlin.test.assertTrue(recordingSchedulerJobExecutionRecorder.records.isEmpty())
    }

    @Test
    fun `update profile should change nickname for app client request`() {
        val signupBody = signup("profilenick@example.com", "pw1234", "fcm-profile")
        val accessToken = signupBody.path("data").path("access_token").asText()

        mockMvc.perform(
            put("/api/v1/auth/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_nick":"변경닉네임"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("프로필 변경 성공"))
            .andExpect(jsonPath("$.data.user_email").value("profilenick@example.com"))
            .andExpect(jsonPath("$.data.user_nick").value("변경닉네임"))
            .andExpect(jsonPath("$.data.user_image").value("데이지"))

        val savedUser = checkNotNull(userRepository.findByEmail("profilenick@example.com"))
        kotlin.test.assertEquals("변경닉네임", savedUser.nick)
        kotlin.test.assertEquals("데이지", savedUser.image)
    }

    @Test
    fun `update profile should change image for app client request`() {
        val signupBody = signup("profileimage@example.com", "pw1234", "fcm-profile-image")
        val accessToken = signupBody.path("data").path("access_token").asText()
        val beforeUser = checkNotNull(userRepository.findByEmail("profileimage@example.com"))
        val beforeNick = beforeUser.nick

        mockMvc.perform(
            put("/api/v1/auth/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_image":"튤립"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("프로필 변경 성공"))
            .andExpect(jsonPath("$.data.user_email").value("profileimage@example.com"))
            .andExpect(jsonPath("$.data.user_nick").value(beforeNick))
            .andExpect(jsonPath("$.data.user_image").value("튤립"))

        val savedUser = checkNotNull(userRepository.findByEmail("profileimage@example.com"))
        kotlin.test.assertEquals(beforeNick, savedUser.nick)
        kotlin.test.assertEquals("튤립", savedUser.image)
    }

    @Test
    fun `update profile should refresh fcm token for auto login client`() {
        val signupBody = signup("profilefcm@example.com", "pw1234", "fcm-initial")
        val accessToken = signupBody.path("data").path("access_token").asText()

        mockMvc.perform(
            put("/api/v1/auth/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"user_fcm":"fcm-rotated"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.data.user_fcm").value("fcm-rotated"))

        val savedUser = checkNotNull(userRepository.findByEmail("profilefcm@example.com"))
        kotlin.test.assertEquals("fcm-rotated", savedUser.fcm)
    }

    @Test
    fun `signup should accept apple social signup with empty email`() {
        mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"","user_password":"","user_fcm":"fcm-apple","user_social_id":"apple-uid-1","user_social_type":"apple"}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))

        val user = checkNotNull(userRepository.findBySocialIdAndSocialType("apple-uid-1", "apple"))
        kotlin.test.assertEquals("", user.email)
        kotlin.test.assertEquals("fcm-apple", user.fcm)
    }

    @Test
    fun `signup should accept apple private relay email`() {
        mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"abc123@privaterelay.appleid.com","user_password":"","user_fcm":"fcm-apple-2","user_social_id":"apple-uid-2","user_social_type":"apple"}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resp_code").value(201))

        val user = checkNotNull(userRepository.findBySocialIdAndSocialType("apple-uid-2", "apple"))
        kotlin.test.assertEquals("abc123@privaterelay.appleid.com", user.email)
    }

    @Test
    fun `delete user should remove auth garden book and memo data`() {
        val signupBody = signup("delete@example.com", "pw1234", "fcm-delete")
        val accessToken = signupBody.path("data").path("access_token").asText()
        val user = userRepository.findByEmail("delete@example.com")
        checkNotNull(user)
        val userNo = checkNotNull(user.id)
        val gardenNo = checkNotNull(gardenMemberRepository.findAllByUserId(userNo).singleOrNull()?.garden?.id)

        val book = bookRepository.save(
            BookEntity(
                user = user,
                title = "테스트 책",
                author = "작가",
                publisher = "출판사",
                status = 1,
                page = 100,
                info = "소개",
            ),
        )
        val bookNo = book.id
        bookReadRepository.save(BookReadEntity(book = book))
        bookImageRepository.save(BookImageEntity(book = book, name = "img", url = "book.png"))

        val memo = memoRepository.save(
            MemoEntity(
                book = book,
                content = "메모",
                user = user,
            ),
        )
        val memoNo = memo.id
        memoImageRepository.save(MemoImageEntity(memo = memo, name = "memo", url = "memo.png"))

        mockMvc.perform(
            delete("/api/v1/auth")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resp_code").value(200))
            .andExpect(jsonPath("$.resp_msg").value("회원 탈퇴 성공"))

        kotlin.test.assertFalse(userRepository.findById(userNo).isPresent)
        kotlin.test.assertNull(refreshTokenRepository.findByUserId(userNo))
        kotlin.test.assertNull(pushSettingsRepository.findByUserId(userNo))
        kotlin.test.assertTrue(bookRepository.findAllByUserId(userNo).isEmpty())
        kotlin.test.assertTrue(memoRepository.findAllByUserId(userNo).isEmpty())
        kotlin.test.assertEquals(0L, gardenMemberRepository.countByUserId(userNo))
        kotlin.test.assertTrue(gardenRepository.findById(gardenNo).isPresent)
    }

    private fun signup(
        email: String,
        password: String,
        fcmToken: String,
    ) = objectMapper.readTree(
        mockMvc.perform(
            post("/api/v1/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"user_email":"$email","user_password":"$password","user_fcm":"$fcmToken","user_social_id":"","user_social_type":""}""",
                ),
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString,
    )

    class RecordingMailSender : MailSender {
        val sentMessages = mutableListOf<SentMessage>()

        override fun send(
            email: String,
            title: String,
            content: String,
        ) {
            sentMessages += SentMessage(email, title, content)
        }
    }

    data class SentMessage(
        val email: String,
        val title: String,
        val content: String,
    )

    class RecordingTaskScheduler : TaskScheduler, DisposableBean {
        private val delegate = ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("test-auth-scheduler-")
            initialize()
        }

        val scheduledTasks = CopyOnWriteArrayList<ScheduledTask>()
        private val scheduledFutures = CopyOnWriteArrayList<ScheduledFuture<*>>()

        override fun getClock() = delegate.clock

        override fun schedule(
            task: Runnable,
            startTime: Instant,
        ): ScheduledFuture<*> {
            scheduledTasks += ScheduledTask(startTime)
            val future = requireNotNull(delegate.schedule(task, startTime))
            scheduledFutures += future
            return future
        }

        override fun schedule(
            task: Runnable,
            trigger: Trigger,
        ): ScheduledFuture<*> = requireNotNull(delegate.schedule(task, trigger))

        override fun scheduleAtFixedRate(
            task: Runnable,
            startTime: Instant,
            period: Duration,
        ): ScheduledFuture<*> = requireNotNull(delegate.scheduleAtFixedRate(task, startTime, period))

        override fun scheduleAtFixedRate(
            task: Runnable,
            period: Duration,
        ): ScheduledFuture<*> = requireNotNull(delegate.scheduleAtFixedRate(task, period))

        override fun scheduleWithFixedDelay(
            task: Runnable,
            startTime: Instant,
            delay: Duration,
        ): ScheduledFuture<*> = requireNotNull(delegate.scheduleWithFixedDelay(task, startTime, delay))

        override fun scheduleWithFixedDelay(
            task: Runnable,
            delay: Duration,
        ): ScheduledFuture<*> = requireNotNull(delegate.scheduleWithFixedDelay(task, delay))

        fun clear() {
            scheduledFutures.forEach { it.cancel(false) }
            scheduledFutures.clear()
            scheduledTasks.clear()
        }

        override fun destroy() {
            clear()
            delegate.shutdown()
        }
    }

    data class ScheduledTask(
        val runAt: Instant,
    )

    class RecordingSchedulerJobExecutionRecorder : SchedulerJobExecutionRecorder {
        val records = mutableListOf<SchedulerJobExecutionRecord>()

        override fun record(record: SchedulerJobExecutionRecord) {
            records += record
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        @Primary
        fun mailSender(): RecordingMailSender = RecordingMailSender()

        @Bean
        @Primary
        fun taskScheduler(): RecordingTaskScheduler = RecordingTaskScheduler()

        @Bean
        @Primary
        fun schedulerJobExecutionRecorder(): RecordingSchedulerJobExecutionRecorder =
            RecordingSchedulerJobExecutionRecorder()
    }
}
