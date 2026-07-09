package std.nooook.readinggardenkotlin.modules.memo.service

import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.common.storage.ImageStorage
import std.nooook.readinggardenkotlin.modules.memo.entity.MemoImageEntity
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository

@Service
class MemoImageService(
    private val memoRepository: MemoRepository,
    private val memoImageRepository: MemoImageRepository,
    private val imageStorage: ImageStorage,
) {
    @Transactional
    fun uploadMemoImage(
        userId: Long,
        id: Long,
        file: MultipartFile,
    ): String {
        val memo = memoRepository.findByIdAndUserId(id, userId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 메모가 없습니다.")

        if (file.size > MAX_IMAGE_SIZE_BYTES) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 용량은 5MB를 초과할 수 없습니다.")
        }

        val existingImages = memoImageRepository.findAllByMemoIdIn(listOf(id))
        val stagedDeletes = mutableListOf<ImageStorage.StagedDelete>()
        var savedImageUrl: String? = null
        try {
            existingImages.forEach { existingImage ->
                stagedDeletes += imageStorage.stageDelete(existingImage.url)
            }
            if (existingImages.isNotEmpty()) {
                memoImageRepository.deleteAll(existingImages)
            }

            val imageUrl = imageStorage.save("memo", file.originalFilename ?: file.name, file.bytes)
            savedImageUrl = imageUrl
            memoImageRepository.save(
                MemoImageEntity(
                    memo = memo,
                    name = file.originalFilename ?: file.name,
                    url = imageUrl,
                ),
            )
            registerRollbackCleanup(imageUrl)
            finalizeStagedDeletes(stagedDeletes)
            return "이미지 업로드 성공"
        } catch (exception: Exception) {
            savedImageUrl?.let { runCatching { imageStorage.delete(it) } }
            rollbackStagedDeletes(stagedDeletes)
            throw when (exception) {
                is RuntimeException -> exception
                else -> IllegalStateException("Failed to upload memo image resources.", exception)
            }
        }
    }

    @Transactional
    fun deleteMemoImage(
        userId: Long,
        id: Long,
    ): String {
        memoRepository.findByIdAndUserId(id, userId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 메모가 없습니다.")

        val existingImages = memoImageRepository.findAllByMemoIdIn(listOf(id))
        if (existingImages.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 이미지가 없습니다.")
        }

        val stagedDeletes = mutableListOf<ImageStorage.StagedDelete>()
        try {
            existingImages.forEach { existingImage ->
                stagedDeletes += imageStorage.stageDelete(existingImage.url)
            }
            memoImageRepository.deleteAll(existingImages)
            finalizeStagedDeletes(stagedDeletes)
            return "이미지 삭제 성공"
        } catch (exception: Exception) {
            rollbackStagedDeletes(stagedDeletes)
            throw when (exception) {
                is RuntimeException -> exception
                else -> IllegalStateException("Failed to delete memo image resources.", exception)
            }
        }
    }

    private fun finalizeStagedDeletes(stagedDeletes: List<ImageStorage.StagedDelete>) {
        if (stagedDeletes.isEmpty()) {
            return
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            commitStagedDeletes(stagedDeletes)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    commitStagedDeletes(stagedDeletes)
                }

                override fun afterCompletion(status: Int) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        rollbackStagedDeletes(stagedDeletes)
                    }
                }
            },
        )
    }

    private fun registerRollbackCleanup(relativePath: String) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        runCatching { imageStorage.delete(relativePath) }
                    }
                }
            },
        )
    }

    private fun commitStagedDeletes(stagedDeletes: List<ImageStorage.StagedDelete>) {
        stagedDeletes.forEach { stagedDelete ->
            try {
                stagedDelete.commit()
            } catch (exception: Exception) {
                logger.warn("Failed to finalize staged memo image deletion.", exception)
            }
        }
    }

    private fun rollbackStagedDeletes(stagedDeletes: List<ImageStorage.StagedDelete>) {
        stagedDeletes
            .asReversed()
            .forEach { stagedDelete ->
                try {
                    stagedDelete.rollback()
                } catch (exception: Exception) {
                    logger.warn("Failed to restore staged memo image deletion.", exception)
                }
            }
    }

    companion object {
        private const val MAX_IMAGE_SIZE_BYTES = 5L * 1024L * 1024L
        private val logger = LoggerFactory.getLogger(MemoImageService::class.java)
    }
}
