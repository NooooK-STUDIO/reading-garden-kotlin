package std.nooook.readinggardenkotlin.modules.book.service

import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.modules.book.controller.CreateReadRequest
import std.nooook.readinggardenkotlin.modules.book.controller.CreateReadResponse
import std.nooook.readinggardenkotlin.modules.book.controller.UpdateReadRequest
import std.nooook.readinggardenkotlin.modules.book.entity.BookReadEntity
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository

@Service
class BookReadService(
    private val bookRepository: BookRepository,
    private val bookReadRepository: BookReadRepository,
) {
    @Transactional
    fun createRead(
        userId: Long,
        request: CreateReadRequest,
    ): CreateReadResponse {
        val book = bookRepository.findByIdAndUserId(request.book_no, userId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책 정보가 없습니다.")
        val isFirstRead = bookReadRepository.findAllByBookIdOrderByCreatedAtDesc(request.book_no).isEmpty()
        val now = LocalDateTime.now()
        val currentPage = request.book_current_page
        val isFinalPage = book.page > 0 && currentPage == book.page
        val resolvedStartDate = when {
            isFirstRead && request.book_start_date == null -> now
            else -> request.book_start_date
        }
        val resolvedEndDate = if (isFinalPage) now else request.book_end_date
        bookReadRepository.save(
            BookReadEntity(
                book = book,
                currentPage = currentPage,
                startDate = resolvedStartDate,
                endDate = resolvedEndDate,
                createdAt = now,
            ),
        )
        when {
            isFinalPage -> {
                book.status = 1
                bookRepository.save(book)
            }
            isFirstRead && request.book_start_date == null -> {
                book.status = 0
                bookRepository.save(book)
            }
        }

        return CreateReadResponse(
            book_current_page = currentPage,
            percent = calculatePercent(currentPage, book.page),
        )
    }

    @Transactional
    fun updateRead(
        id: Long,
        request: UpdateReadRequest,
    ): String {
        val read = bookReadRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책 기록이 없습니다.") }
        request.book_start_date?.let { read.startDate = it }
        request.book_end_date?.let { read.endDate = it }
        bookReadRepository.save(read)
        return "독서 기록 수정 성공"
    }

    @Transactional
    fun deleteRead(id: Long): String {
        if (!bookReadRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책 기록이 없습니다.")
        }
        bookReadRepository.deleteById(id)
        return "책 기록 삭제 성공"
    }

    private fun calculatePercent(
        currentPage: Int,
        bookPage: Int,
    ): Double =
        if (bookPage > 0) {
            currentPage.toDouble() / bookPage * 100
        } else {
            0.0
        }
}
