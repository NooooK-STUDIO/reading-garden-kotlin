package std.nooook.readinggardenkotlin.modules.book.controller

import io.swagger.v3.oas.annotations.media.Schema

typealias BookSearchResponse = Map<String, Any?>

typealias BookLookupResponse = Map<String, Any?>

@Schema(description = "책 등록 성공 응답")
data class CreateBookResponse(
    @field:Schema(description = "생성된 책 번호", example = "1")
    val book_no: Long,
)

@Schema(description = "독서 기록 생성 성공 응답")
data class CreateReadResponse(
    @field:Schema(description = "기록된 현재 페이지", example = "150")
    val book_current_page: Int,
    @field:Schema(description = "현재 독서 진행률", example = "46.7")
    val percent: Double,
)

@Schema(description = "독서 기록 이력 항목")
data class BookReadHistoryItemResponse(
    @field:Schema(description = "독서 기록 id", example = "1")
    val id: Long,
    @field:Schema(description = "해당 시점의 현재 페이지", example = "150")
    val book_current_page: Int,
    @field:Schema(description = "시작 시각", example = "2026-04-10T09:00:00", nullable = true)
    val book_start_date: String?,
    @field:Schema(description = "종료 시각", example = "2026-04-10T11:00:00", nullable = true)
    val book_end_date: String?,
    @field:Schema(description = "기록 생성 시각", example = "2026-04-10T11:00:00")
    val book_created_at: String,
)

@Schema(description = "독서 기록에 연결된 메모 항목")
data class BookReadMemoItemResponse(
    @field:Schema(description = "메모 id", example = "1")
    val id: Long,
    @field:Schema(description = "메모 내용", example = "좋았던 문장")
    val memo_content: String,
    @field:Schema(description = "좋아요 여부", example = "true")
    val memo_like: Boolean,
    @field:Schema(description = "메모 작성 시각", example = "2026-04-10T11:30:00")
    val memo_created_at: String,
    @field:Schema(description = "메모 이미지 URL", example = "https://example.com/memo.jpg", nullable = true)
    val image_url: String? = null,
)

@Schema(description = "책 상태 목록 항목")
data class BookStatusItemResponse(
    @field:Schema(description = "책 번호", example = "1")
    val book_no: Long,
    @field:Schema(description = "책 제목", example = "클린 코드")
    val book_title: String,
    @field:Schema(description = "저자", example = "로버트 C. 마틴")
    val book_author: String,
    @field:Schema(description = "출판사", example = "인사이트")
    val book_publisher: String,
    @field:Schema(description = "책 소개", example = "소개")
    val book_info: String,
    @field:Schema(description = "표지 이미지 URL", example = "https://example.com/book.jpg", nullable = true)
    val book_image_url: String?,
    @field:Schema(description = "분류명", example = "소설", nullable = true)
    val book_tree: String?,
    @field:Schema(description = "독서 상태 코드", example = "1")
    val book_status: Int,
    @field:Schema(description = "완독 별점(1~5)", example = "5", nullable = true)
    val book_rating: Int? = null,
    @field:Schema(description = "독서 진행률", example = "46.7")
    val percent: Double,
    @field:Schema(description = "전체 페이지 수", example = "321")
    val book_page: Int,
    @field:Schema(description = "소속 가든 번호", example = "10", nullable = true)
    val garden_no: Long?,
)

@Schema(description = "책 상태 목록 응답")
data class BookStatusResponse(
    @field:Schema(description = "현재 페이지 번호", example = "1")
    val current_page: Int,
    @field:Schema(description = "최대 페이지 수", example = "3")
    val max_page: Int,
    @field:Schema(description = "전체 항목 수", example = "25")
    val total_items: Int,
    @field:Schema(description = "페이지 크기", example = "10")
    val page_size: Int,
    @field:Schema(description = "현재 페이지 항목 목록")
    val list: List<BookStatusItemResponse>,
)

@Schema(description = "책 상세 조회 응답")
data class BookDetailResponse(
    @field:Schema(description = "알라딘 카테고리 id", example = "1")
    val searchCategoryId: Any,
    @field:Schema(description = "알라딘 카테고리명", example = "소설")
    val searchCategoryName: String,
    @field:Schema(description = "책 제목", example = "상세 책")
    val title: String,
    @field:Schema(description = "저자", example = "저자")
    val author: String,
    @field:Schema(description = "책 소개", example = "소개")
    val description: String,
    @field:Schema(description = "ISBN13", example = "9788937462788")
    val isbn13: String,
    @field:Schema(description = "표지 이미지 URL", example = "https://example.com/book.jpg")
    val cover: String,
    @field:Schema(description = "출판사", example = "출판사")
    val publisher: String,
    @field:Schema(description = "페이지 수", example = "321")
    val itemPage: Int,
    @field:Schema(description = "레거시 record placeholder 객체", example = "{}")
    val record: Map<String, Any?>,
    @field:Schema(description = "레거시 memo placeholder 객체", example = "{}")
    val memo: Map<String, Any?>,
)

@Schema(description = "독서 기록 상세 응답")
data class BookReadDetailResponse(
    @field:Schema(description = "책 번호", example = "1")
    val book_no: Long,
    @field:Schema(description = "사용자 번호", example = "1")
    val user_no: Long,
    @field:Schema(description = "책 제목", example = "클린 코드")
    val book_title: String,
    @field:Schema(description = "저자", example = "로버트 C. 마틴")
    val book_author: String,
    @field:Schema(description = "출판사", example = "인사이트")
    val book_publisher: String,
    @field:Schema(description = "책 소개", example = "소개")
    val book_info: String,
    @field:Schema(description = "표지 이미지 URL", example = "https://example.com/book.jpg", nullable = true)
    val book_image_url: String?,
    @field:Schema(description = "분류명", example = "소설", nullable = true)
    val book_tree: String?,
    @field:Schema(description = "독서 상태 코드", example = "1")
    val book_status: Int,
    @field:Schema(description = "완독 별점(1~5)", example = "5", nullable = true)
    val book_rating: Int? = null,
    @field:Schema(description = "전체 페이지 수", example = "321")
    val book_page: Int,
    @field:Schema(description = "가든 번호", example = "10", nullable = true)
    val garden_no: Long?,
    @field:Schema(description = "가든 제목", example = "나의 가든")
    val garden_title: String = "",
    @field:Schema(description = "가든 색상", example = "blue")
    val garden_color: String = "",
    @field:Schema(description = "현재 읽은 페이지", example = "150")
    val book_current_page: Int,
    @field:Schema(description = "현재 진행률", example = "46.7")
    val percent: Double,
    @field:Schema(description = "독서 기록 이력")
    val book_read_list: List<BookReadHistoryItemResponse>,
    @field:Schema(description = "연결된 메모 목록")
    val memo_list: List<BookReadMemoItemResponse>,
)

@Schema(description = "알라딘 책 검색 결과 항목")
data class BookSearchItemDocument(
    @field:Schema(description = "책 제목", example = "클린 코드 결과")
    val title: String,
    @field:Schema(description = "저자", example = "로버트 C. 마틴")
    val author: String,
    @field:Schema(description = "ISBN13", example = "9780132350884")
    val isbn13: String,
    @field:Schema(description = "표지 이미지 URL", example = "https://example.com/cover.jpg")
    val cover: String,
    @field:Schema(description = "출판사", example = "프래그마틱")
    val publisher: String,
)

@Schema(description = "알라딘 책 검색 응답 payload")
data class BookSearchPayloadDocument(
    @field:Schema(description = "검색어", example = "클린 코드")
    val query: String,
    @field:Schema(description = "시작 index", example = "1")
    val startIndex: Int,
    @field:Schema(description = "조회한 최대 개수", example = "100")
    val itemsPerPage: Int,
    @field:Schema(description = "검색 결과 목록")
    val item: List<BookSearchItemDocument>,
)

@Schema(description = "ISBN 검색 응답 payload")
data class BookLookupPayloadDocument(
    @field:Schema(description = "책 제목", example = "클린 코드")
    val title: String,
    @field:Schema(description = "저자", example = "로버트 C. 마틴")
    val author: String,
    @field:Schema(description = "ISBN13", example = "9780132350884")
    val isbn13: String,
    @field:Schema(description = "표지 이미지 URL", example = "https://example.com/cover.jpg")
    val cover: String,
    @field:Schema(description = "출판사", example = "프래그마틱")
    val publisher: String,
)

@Schema(name = "BookSearchLegacyDataResponse", description = "책 검색 성공 레거시 envelope")
data class BookSearchLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "책 검색 성공")
    val resp_msg: String,
    @field:Schema(description = "책 검색 데이터")
    val data: BookSearchPayloadDocument,
)

@Schema(name = "BookLookupLegacyDataResponse", description = "ISBN 검색 성공 레거시 envelope")
data class BookLookupLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "책 검색(ISBN) 성공")
    val resp_msg: String,
    @field:Schema(description = "ISBN 검색 데이터")
    val data: BookLookupPayloadDocument,
)

@Schema(name = "BookDetailLegacyDataResponse", description = "책 상세 조회 성공 레거시 envelope")
data class BookDetailLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "책 상세 조회 성공")
    val resp_msg: String,
    @field:Schema(description = "책 상세 데이터")
    val data: BookDetailResponse,
)

@Schema(name = "CreateBookLegacyDataResponse", description = "책 등록 성공 레거시 envelope")
data class CreateBookLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "201")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "책 등록 성공")
    val resp_msg: String,
    @field:Schema(description = "생성된 책 데이터")
    val data: CreateBookResponse,
)

@Schema(name = "BookStatusLegacyDataResponse", description = "책 상태 목록 조회 성공 레거시 envelope")
data class BookStatusLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "책 상태 조회 성공")
    val resp_msg: String,
    @field:Schema(description = "책 상태 목록 데이터")
    val data: BookStatusResponse,
)

@Schema(name = "BookReadDetailLegacyDataResponse", description = "독서 기록 상세 조회 성공 레거시 envelope")
data class BookReadDetailLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "200")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "독서 기록 조회 성공")
    val resp_msg: String,
    @field:Schema(description = "독서 기록 상세 데이터")
    val data: BookReadDetailResponse,
)

@Schema(name = "CreateReadLegacyDataResponse", description = "독서 기록 생성 성공 레거시 envelope")
data class CreateReadLegacyDataResponse(
    @field:Schema(description = "HTTP 상태 코드와 동일한 레거시 응답 코드", example = "201")
    val resp_code: Int,
    @field:Schema(description = "레거시 응답 메시지", example = "책 기록 성공")
    val resp_msg: String,
    @field:Schema(description = "생성된 독서 기록 데이터")
    val data: CreateReadResponse,
)
