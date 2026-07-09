package std.nooook.readinggardenkotlin.modules.book.controller

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "책 검색 요청 파라미터")
data class SearchBooksRequest(
    @field:Schema(description = "검색어", example = "클린 코드")
    val query: String,
    @field:Schema(description = "검색 시작 index", example = "1", defaultValue = "1")
    val start: Int = 1,
    @field:Schema(description = "최대 조회 개수", example = "100", defaultValue = "100")
    val maxResults: Int = 100,
)

@Schema(description = "ISBN 기반 조회 요청")
data class IsbnQueryRequest(
    @field:Schema(description = "ISBN13", example = "9788937462788")
    val query: String,
)

@Schema(description = "책 등록 요청")
data class CreateBookRequest(
    @field:Schema(description = "책 ISBN13, 없으면 null", example = "9788937462788", nullable = true)
    val book_isbn: String? = null,
    @field:Schema(description = "책을 담을 가든 번호", example = "10", nullable = true)
    val garden_no: Long? = null,
    @field:Schema(description = "책 제목", example = "클린 코드")
    val book_title: String,
    @field:Schema(description = "책 소개", example = "소개")
    val book_info: String,
    @field:Schema(description = "저자", example = "로버트 C. 마틴")
    val book_author: String,
    @field:Schema(description = "출판사", example = "인사이트")
    val book_publisher: String,
    @field:Schema(description = "카테고리 또는 분류명", example = "소설", nullable = true)
    val book_tree: String? = null,
    @field:Schema(description = "표지 이미지 URL", example = "https://example.com/book.jpg", nullable = true)
    val book_image_url: String? = null,
    @field:Schema(description = "독서 상태 코드", example = "1")
    val book_status: Int,
    @field:Schema(description = "전체 페이지 수", example = "321")
    val book_page: Int,
)

@Schema(description = "책 수정 요청")
data class UpdateBookRequest(
    @field:Schema(description = "이동할 가든 번호", example = "12", nullable = true)
    val garden_no: Long? = null,
    @field:Schema(description = "수정할 카테고리", example = "에세이", nullable = true)
    val book_tree: String? = null,
    @field:Schema(description = "수정할 독서 상태 코드", example = "2", nullable = true)
    val book_status: Int? = null,
    @field:Schema(description = "수정할 책 제목", example = "새 제목", nullable = true)
    val book_title: String? = null,
    @field:Schema(description = "수정할 저자", example = "새 저자", nullable = true)
    val book_author: String? = null,
    @field:Schema(description = "수정할 표지 이미지 URL", example = "https://example.com/new.jpg", nullable = true)
    val book_image_url: String? = null,
    @field:Schema(description = "완독 별점(1~5)", example = "5", nullable = true)
    val book_rating: Int? = null,
)

@Schema(description = "독서 기록 생성 요청")
data class CreateReadRequest(
    @field:Schema(description = "책 번호", example = "1")
    val book_no: Long,
    @field:Schema(description = "독서 시작 시각", example = "2026-04-10T09:00:00", nullable = true)
    val book_start_date: LocalDateTime? = null,
    @field:Schema(description = "독서 종료 시각", example = "2026-04-10T11:00:00", nullable = true)
    val book_end_date: LocalDateTime? = null,
    @field:Schema(description = "현재 읽은 페이지", example = "150")
    val book_current_page: Int,
)

@Schema(description = "독서 기록 수정 요청")
data class UpdateReadRequest(
    @field:Schema(description = "수정할 시작 시각", example = "2026-04-10T09:00:00", nullable = true)
    val book_start_date: LocalDateTime? = null,
    @field:Schema(description = "수정할 종료 시각", example = "2026-04-10T11:00:00", nullable = true)
    val book_end_date: LocalDateTime? = null,
)

@Schema(description = "책 이미지 업로드용 multipart 요청")
data class UploadBookImageRequest(
    @field:Schema(description = "이미지를 연결할 책 번호", example = "1")
    val book_no: Long,
    @field:Schema(description = "업로드할 이미지 파일", type = "string", format = "binary")
    val file: String,
)
