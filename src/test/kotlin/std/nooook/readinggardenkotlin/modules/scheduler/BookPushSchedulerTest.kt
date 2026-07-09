package std.nooook.readinggardenkotlin.modules.scheduler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.scheduling.annotation.Scheduled
import std.nooook.readinggardenkotlin.modules.auth.entity.UserEntity
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.push.entity.PushSettingsEntity
import std.nooook.readinggardenkotlin.modules.push.integration.FcmClient
import std.nooook.readinggardenkotlin.modules.push.repository.PushSettingsRepository
import std.nooook.readinggardenkotlin.modules.push.service.PushDeliveryService
import std.nooook.readinggardenkotlin.modules.push.service.PushPreferenceService
import std.nooook.readinggardenkotlin.modules.push.service.PushService
import std.nooook.readinggardenkotlin.modules.scheduler.service.BookPushScheduler
import std.nooook.readinggardenkotlin.modules.scheduler.service.InMemorySchedulerOverlapGuard
import std.nooook.readinggardenkotlin.modules.scheduler.service.SchedulerJobExecutionPhase
import std.nooook.readinggardenkotlin.modules.scheduler.service.SchedulerJobExecutionRecord
import std.nooook.readinggardenkotlin.modules.scheduler.service.SchedulerJobExecutionRecorder
import std.nooook.readinggardenkotlin.modules.scheduler.service.SchedulerJobExecutionRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class BookPushSchedulerTest {
    private val fixedInstant = Instant.parse("2026-04-09T12:34:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val pushSettingsRepository: PushSettingsRepository = mock(PushSettingsRepository::class.java)
    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val gardenRepository: GardenRepository = mock(GardenRepository::class.java)
    private val fcmClient: FcmClient = mock(FcmClient::class.java)
    private val pushPreferenceService = PushPreferenceService(pushSettingsRepository, userRepository)
    private val pushDeliveryService = PushDeliveryService(
        pushSettingsRepository = pushSettingsRepository,
        userRepository = userRepository,
        gardenRepository = gardenRepository,
        fcmClient = fcmClient,
        pushClock = clock,
    )
    private val pushService = PushService(pushPreferenceService, pushDeliveryService)
    private val recorder = RecordingSchedulerJobExecutionRecorder()
    private val scheduler = BookPushScheduler(
        pushService = pushService,
        schedulerJobExecutionRunner = SchedulerJobExecutionRunner(
            recorder = recorder,
            overlapGuard = InMemorySchedulerOverlapGuard(),
            utcClock = clock,
        ),
    )

    @Test
    fun `scheduled method should keep cron contract`() {
        val method = BookPushScheduler::class.java.getDeclaredMethod("sendBookPush")
        val scheduled = method.getAnnotation(Scheduled::class.java)

        assertEquals("\${app.push.book-cron:0 * * * * *}", scheduled.cron)
        assertEquals("Asia/Seoul", scheduled.zone)
    }

    @Test
    fun `scheduled method should execute through runner and push service`() {
        val mockUser = UserEntity(id = 7L, fcm = "token-7")
        given(pushSettingsRepository.findAllByBookOkTrueAndPushTimeIsNotNull()).willReturn(
            listOf(
                PushSettingsEntity(
                    user = mockUser,
                    appOk = true,
                    bookOk = true,
                    pushTime = LocalDateTime.ofInstant(fixedInstant, ZoneOffset.UTC),
                ),
            ),
        )
        given(userRepository.findAllByIdIn(listOf(7L))).willReturn(
            listOf(mockUser),
        )
        given(
            fcmClient.sendToMany(
                listOf("token-7"),
                "💧물 주는 시간이에요!",
                "책 어디까지 읽으셨나요? 독서가든에서 기록해보세요!",
                emptyMap(),
            ),
        ).willReturn(listOf(mapOf("messageId" to "mock-message-id")))

        scheduler.sendBookPush()

        assertEquals(
            listOf(
                SchedulerJobExecutionPhase.STARTED,
                SchedulerJobExecutionPhase.SUCCEEDED,
            ),
            recorder.records.map { it.phase },
        )
        assertEquals(listOf("book-push:cron", "book-push:cron"), recorder.records.map { it.jobId })
        assertEquals(
            mapOf(
                "job_type" to "book_push",
                "trigger_source" to "scheduled_cron",
            ),
            recorder.records.first().context,
        )
        verify(fcmClient).sendToMany(
            listOf("token-7"),
            "💧물 주는 시간이에요!",
            "책 어디까지 읽으셨나요? 독서가든에서 기록해보세요!",
            emptyMap(),
        )
    }

    private class RecordingSchedulerJobExecutionRecorder : SchedulerJobExecutionRecorder {
        val records = mutableListOf<SchedulerJobExecutionRecord>()

        override fun record(record: SchedulerJobExecutionRecord) {
            records += record
        }
    }
}
