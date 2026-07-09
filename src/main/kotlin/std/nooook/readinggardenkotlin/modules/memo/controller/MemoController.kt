package std.nooook.readinggardenkotlin.modules.memo.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import std.nooook.readinggardenkotlin.common.api.LegacyDataResponse
import std.nooook.readinggardenkotlin.common.api.LegacyHttpResponse
import std.nooook.readinggardenkotlin.common.api.LegacyResponses
import std.nooook.readinggardenkotlin.common.docs.OpenApiExamples
import std.nooook.readinggardenkotlin.common.security.LegacyAuthenticationPrincipal
import std.nooook.readinggardenkotlin.modules.memo.service.MemoCommandService
import std.nooook.readinggardenkotlin.modules.memo.service.MemoImageService
import std.nooook.readinggardenkotlin.modules.memo.service.MemoQueryService
import std.nooook.readinggardenkotlin.modules.memo.service.MemoService
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RequestMapping("/api/v1/memo")
@RestController
@Tag(name = "Memo", description = "메모 조회, 생성, 수정, 좋아요, 메모 이미지")
class MemoController(
    private val memoService: MemoService,
    private val memoQueryService: MemoQueryService,
    private val memoCommandService: MemoCommandService,
    private val memoImageService: MemoImageService,
) {
    @GetMapping("")
    @Operation(summary = "메모 목록 조회", description = "현재 사용자의 메모 목록을 페이지 단위로 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "메모 목록 조회 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = MemoListLegacyDataResponse::class),
                        examples = [ExampleObject(value = OpenApiExamples.MEMO_LIST_SUCCESS)],
                    ),
                ],
            ),
        ],
    )
    fun getMemoList(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "페이지 번호", example = "1")
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @Parameter(description = "페이지 크기", example = "10")
        @RequestParam(name = "page_size", required = false, defaultValue = "10") pageSize: Int,
    ): LegacyDataResponse<MemoListResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "메모 리스트 조회 성공",
            data = memoService.getMemoList(
                userId = principal.userId,
                page = page,
                pageSize = pageSize,
            ),
        )

    @GetMapping("/detail")
    @Operation(summary = "메모 상세 조회", description = "`id`에 해당하는 메모 상세 정보를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "메모 상세 조회 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = MemoDetailLegacyDataResponse::class),
                        examples = [ExampleObject(value = OpenApiExamples.MEMO_DETAIL_SUCCESS)],
                    ),
                ],
            ),
        ],
    )
    fun getMemoDetail(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "조회할 메모 id", example = "1")
        @RequestParam id: Long,
    ): LegacyDataResponse<MemoDetailResponse> =
        LegacyDataResponse(
            resp_code = 200,
            resp_msg = "메모 상세 조회 성공",
            data = memoQueryService.getMemoDetail(
                userId = principal.userId,
                id = id,
            ),
        )

    @PostMapping("")
    @Operation(
        summary = "메모 생성",
        description = "책에 연결된 메모를 생성합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = CreateMemoRequest::class))],
        ),
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "메모 생성 성공",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = CreateMemoLegacyDataResponse::class))],
            ),
        ],
    )
    fun createMemo(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @RequestBody request: CreateMemoRequest,
    ): ResponseEntity<LegacyDataResponse<CreateMemoResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            LegacyDataResponse(
                resp_code = 201,
                resp_msg = "메모 추가 성공",
                data = memoCommandService.createMemo(
                    userId = principal.userId,
                    request = request,
                ),
            ),
        )

    @PutMapping("")
    @Operation(
        summary = "메모 수정",
        description = "`id`에 해당하는 메모 내용을 수정합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = UpdateMemoRequest::class))],
        ),
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "메모 수정 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = LegacyHttpResponse::class),
                        examples = [ExampleObject(value = OpenApiExamples.MEMO_UPDATE_SUCCESS)],
                    ),
                ],
            ),
        ],
    )
    fun updateMemo(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "수정할 메모 id", example = "1")
        @RequestParam id: Long,
        @RequestBody request: UpdateMemoRequest,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            memoCommandService.updateMemo(
                userId = principal.userId,
                id = id,
                request = request,
            ),
        )

    @DeleteMapping("")
    @Operation(summary = "메모 삭제", description = "`id`에 해당하는 메모를 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "메모 삭제 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = LegacyHttpResponse::class),
                        examples = [ExampleObject(value = OpenApiExamples.MEMO_DELETE_SUCCESS)],
                    ),
                ],
            ),
        ],
    )
    fun deleteMemo(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "삭제할 메모 id", example = "1")
        @RequestParam id: Long,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            memoCommandService.deleteMemo(
                userId = principal.userId,
                id = id,
            ),
        )

    @PostMapping("/image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "메모 이미지 업로드",
        description = "`multipart/form-data`로 메모 이미지를 업로드합니다.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(mediaType = "multipart/form-data", schema = Schema(implementation = UploadMemoImageRequest::class))],
        ),
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "업로드 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = LegacyHttpResponse::class),
                        examples = [ExampleObject(value = OpenApiExamples.MEMO_IMAGE_UPLOAD_SUCCESS)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "multipart 요청 형식 오류",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = LegacyHttpResponse::class),
                        examples = [ExampleObject(value = OpenApiExamples.BAD_REQUEST)],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = LegacyHttpResponse::class),
                        examples = [ExampleObject(value = OpenApiExamples.UNAUTHORIZED)],
                    ),
                ],
            ),
        ],
    )
    fun uploadMemoImage(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "이미지를 연결할 메모 id", example = "1")
        @RequestParam id: Long,
        @Parameter(description = "업로드할 이미지 파일", required = true)
        @RequestParam(name = "file") file: MultipartFile,
    ): ResponseEntity<LegacyHttpResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            LegacyResponses.error(
                status = 201,
                message = memoImageService.uploadMemoImage(principal.userId, id, file),
            ),
        )

    @DeleteMapping("/image")
    @Operation(summary = "메모 이미지 삭제", description = "`id`에 연결된 메모 이미지를 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "메모 이미지 삭제 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = LegacyHttpResponse::class),
                        examples = [ExampleObject(value = OpenApiExamples.MEMO_IMAGE_DELETE_SUCCESS)],
                    ),
                ],
            ),
        ],
    )
    fun deleteMemoImage(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "이미지를 삭제할 메모 id", example = "1")
        @RequestParam id: Long,
    ): ResponseEntity<LegacyHttpResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            LegacyResponses.error(
                status = 201,
                message = memoImageService.deleteMemoImage(principal.userId, id),
            ),
        )

    @PutMapping("/like")
    @Operation(summary = "메모 좋아요 토글", description = "`id`에 해당하는 메모의 좋아요 상태를 토글합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "메모 좋아요 토글 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = LegacyHttpResponse::class),
                        examples = [ExampleObject(value = OpenApiExamples.MEMO_LIKE_SUCCESS)],
                    ),
                ],
            ),
        ],
    )
    fun toggleMemoLike(
        @AuthenticationPrincipal principal: LegacyAuthenticationPrincipal,
        @Parameter(description = "좋아요를 토글할 메모 id", example = "1")
        @RequestParam id: Long,
    ): LegacyHttpResponse =
        LegacyResponses.ok(
            memoCommandService.toggleMemoLike(
                userId = principal.userId,
                id = id,
            ),
        )
}
