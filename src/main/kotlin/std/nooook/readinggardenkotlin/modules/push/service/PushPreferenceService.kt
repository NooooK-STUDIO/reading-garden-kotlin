package std.nooook.readinggardenkotlin.modules.push.service

import org.springframework.transaction.annotation.Transactional
import org.springframework.stereotype.Service
import std.nooook.readinggardenkotlin.modules.auth.repository.UserRepository
import std.nooook.readinggardenkotlin.modules.push.controller.PushResponse
import std.nooook.readinggardenkotlin.modules.push.entity.PushSettingsEntity
import std.nooook.readinggardenkotlin.modules.push.repository.PushSettingsRepository
import java.time.LocalDateTime

@Service
class PushPreferenceService(
    private val pushSettingsRepository: PushSettingsRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun getPush(userId: Long): PushResponse {
        val push = findOrCreatePushSettings(userId)

        return PushResponse(
            user_no = push.user.id,
            push_app_ok = push.appOk,
            push_book_ok = push.bookOk,
            push_time = push.pushTime,
        )
    }

    @Transactional
    fun updatePush(
        userId: Long,
        push_app_ok: Boolean?,
        push_book_ok: Boolean?,
        push_time: LocalDateTime?,
    ) {
        val push = findOrCreatePushSettings(userId)

        if (push_app_ok != null) {
            push.appOk = push_app_ok
        }
        if (push_book_ok != null) {
            push.bookOk = push_book_ok
        }
        if (push_time != null) {
            push.pushTime = push_time
        }

        pushSettingsRepository.save(push)
    }

    private fun findOrCreatePushSettings(userId: Long): PushSettingsEntity =
        pushSettingsRepository.findByUserId(userId)
            ?: createDefaultPushSettings(userId)

    private fun createDefaultPushSettings(userId: Long): PushSettingsEntity {
        val user = requireNotNull(userRepository.findByIdForUpdate(userId)) {
            "User not found for push settings $userId"
        }

        return pushSettingsRepository.findByUserId(userId)
            ?: pushSettingsRepository.save(
                PushSettingsEntity(
                    user = user,
                    appOk = true,
                ),
            )
    }
}
