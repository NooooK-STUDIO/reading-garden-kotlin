package std.nooook.readinggardenkotlin.modules.book.service

import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import std.nooook.readinggardenkotlin.modules.book.controller.BookReadDetailResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookReadMemoItemResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookStatusItemResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookStatusResponse
import std.nooook.readinggardenkotlin.modules.book.controller.BookReadHistoryItemResponse
import std.nooook.readinggardenkotlin.modules.book.repository.BookReadRepository
import std.nooook.readinggardenkotlin.modules.book.repository.BookRepository
import std.nooook.readinggardenkotlin.modules.garden.repository.GardenRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoImageRepository
import std.nooook.readinggardenkotlin.modules.memo.repository.MemoRepository

@Service
class BookQueryService(
    private val bookRepository: BookRepository,
    private val bookReadRepository: BookReadRepository,
    private val gardenRepository: GardenRepository,
    private val memoRepository: MemoRepository,
    private val memoImageRepository: MemoImageRepository,
) {
    companion object {
        private val LEGACY_DATE_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }

    @Transactional(readOnly = true)
    fun checkDuplication(
        userId: Long,
        isbn: String,
    ): String {
        if (bookRepository.findByIsbnAndUserId(isbn, userId) != null) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "책 중복")
        }
        return "책 등록 가능"
    }

    @Transactional(readOnly = true)
    fun getBookStatus(
        userId: Long,
        gardenNo: Long?,
        status: Int?,
        page: Int,
        pageSize: Int,
    ): BookStatusResponse {
        val books = when {
            gardenNo != null -> bookRepository.findAllByUserIdAndGardenId(userId, gardenNo)
            else -> bookRepository.findAllByUserId(userId)
        }
            .asSequence()
            .filter {
                when (status) {
                    null -> true
                    3 -> it.status == 0 || it.status == 1
                    else -> it.status == status
                }
            }
            .toList()

        val totalItems = books.size
        val maxPage = (totalItems + pageSize - 1) / pageSize
        val fromIndex = (page - 1) * pageSize
        val toIndex = minOf(fromIndex + pageSize, totalItems)
        val pageBooks = if (fromIndex >= totalItems) emptyList() else books.subList(fromIndex, toIndex)

        return BookStatusResponse(
            current_page = page,
            max_page = maxPage,
            total_items = totalItems,
            page_size = pageSize,
            list = pageBooks.map { book ->
                val latestRead = bookReadRepository.findTopByBookIdOrderByCreatedAtDesc(book.id)
                val percent = latestRead?.let { calculatePercent(it.currentPage, book.page) } ?: 0.0

                BookStatusItemResponse(
                    book_no = book.id,
                    book_title = book.title,
                    book_author = book.author,
                    book_publisher = book.publisher,
                    book_info = book.info,
                    book_image_url = book.imageUrl,
                    book_tree = book.tree,
                    book_status = book.status,
                    book_rating = book.rating,
                    percent = percent,
                    book_page = book.page,
                    garden_no = book.garden?.id,
                )
            },
        )
    }

    @Transactional(readOnly = true)
    fun getBookRead(bookNo: Long): BookReadDetailResponse {
        val book = bookRepository.findById(bookNo)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책 정보가 없습니다.") }
        val garden = book.garden
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "일치하는 책 정보가 없습니다.")
        val latestRead = bookReadRepository.findTopByBookIdOrderByCreatedAtDesc(bookNo)
        val reads = bookReadRepository.findAllByBookIdOrderByCreatedAtDesc(bookNo)
        val currentPage = latestRead?.currentPage ?: 0
        val percent = latestRead?.let { calculatePercent(it.currentPage, book.page) } ?: 0.0
        val memoList = memoRepository.findAllByBookIdOrderByIsLikedDescCreatedAtDesc(bookNo)
            .map { memo ->
                val memoImage = memoImageRepository.findFirstByMemoIdOrderByCreatedAtDesc(memo.id)
                BookReadMemoItemResponse(
                    id = memo.id,
                    memo_content = memo.content,
                    memo_like = memo.isLiked,
                    memo_created_at = formatDateTime(memo.createdAt),
                    image_url = memoImage?.url,
                )
            }

        return BookReadDetailResponse(
            book_no = bookNo,
            user_no = book.user.id,
            book_title = book.title,
            book_author = book.author,
            book_publisher = book.publisher,
            book_info = book.info,
            book_image_url = book.imageUrl,
            book_tree = book.tree,
            book_status = book.status,
            book_rating = book.rating,
            book_page = book.page,
            garden_no = book.garden?.id,
            garden_title = garden.title,
            garden_color = garden.color,
            book_current_page = currentPage,
            percent = percent,
            book_read_list = reads.map { read ->
                BookReadHistoryItemResponse(
                    id = read.id,
                    book_current_page = read.currentPage,
                    book_start_date = read.startDate?.let(::formatDateTime),
                    book_end_date = read.endDate?.let(::formatDateTime),
                    book_created_at = formatDateTime(read.createdAt),
                )
            },
            memo_list = memoList,
        )
    }

    private fun formatDateTime(value: LocalDateTime): String =
        value.format(LEGACY_DATE_TIME_FORMATTER)

    private fun calculatePercent(bookCurrentPage: Int, bookPage: Int): Double {
        if (bookPage <= 0) {
            return 0.0
        }
        return bookCurrentPage.toDouble() / bookPage * 100
    }
}
