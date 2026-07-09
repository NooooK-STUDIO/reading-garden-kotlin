package std.nooook.readinggardenkotlin.common.docs

object OpenApiExamples {
    const val BAD_REQUEST = """
        {
          "resp_code": 400,
          "resp_msg": "Request body validation failed.",
          "errors": [
            {
              "field": "user_email",
              "message": "must match legacy email format",
              "rejectedValue": "wrong-email"
            }
          ]
        }
    """

    const val UNAUTHORIZED = """
        {
          "resp_code": 401,
          "resp_msg": "Unauthorized",
          "errors": null
        }
    """

    const val APP_VERSION_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "앱 버전 조회 성공",
          "data": {
            "platform": "ios",
            "latest_version": "1.2.0",
            "min_supported_version": "1.0.0",
            "store_url": "https://apps.apple.com/app/id1234567890"
          }
        }
    """

    const val APP_VERSION_BAD_REQUEST = """
        {
          "resp_code": 400,
          "resp_msg": "platform must be one of: ios, android",
          "errors": null
        }
    """

    const val APP_VERSION_NOT_FOUND = """
        {
          "resp_code": 404,
          "resp_msg": "App version not found for platform: ios",
          "errors": null
        }
    """

    const val AUTH_SIGNUP_SUCCESS = """
        {
          "resp_code": 201,
          "resp_msg": "회원가입 성공",
          "data": {
            "access_token": "fixture-access-token",
            "refresh_token": "fixture-refresh-token",
            "user_nick": "임의닉네임"
          }
        }
    """

    const val AUTH_LOGIN_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "로그인 성공",
          "data": {
            "access_token": "fixture-access-token",
            "refresh_token": "fixture-refresh-token"
          }
        }
    """

    const val AUTH_LOGIN_EMAIL_NOT_FOUND = """
        {
          "resp_code": 400,
          "resp_msg": "등록되지 않은 이메일 주소입니다.",
          "errors": null
        }
    """

    const val AUTH_LOGIN_PASSWORD_MISMATCH = """
        {
          "resp_code": 400,
          "resp_msg": "비밀번호가 일치하지 않습니다.",
          "errors": null
        }
    """

    const val AUTH_LOGOUT_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "로그아웃 성공",
          "data": {}
        }
    """

    const val AUTH_REFRESH_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "토큰 발급 성공",
          "data": "fixture-access-token"
        }
    """

    const val AUTH_DELETE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "회원 탈퇴 성공",
          "data": {}
        }
    """

    const val AUTH_FIND_PASSWORD_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "메일이 발송되었습니다. 확인해주세요.",
          "data": {}
        }
    """

    const val AUTH_EMAIL_NOT_FOUND = """
        {
          "resp_code": 400,
          "resp_msg": "등록되지 않은 이메일 주소입니다.",
          "errors": null
        }
    """

    const val AUTH_FIND_PASSWORD_CHECK_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "인증 성공",
          "data": {}
        }
    """

    const val AUTH_FIND_PASSWORD_CHECK_MISMATCH = """
        {
          "resp_code": 400,
          "resp_msg": "인증번호 불일치",
          "errors": null
        }
    """

    const val AUTH_PASSWORD_UPDATE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "비밀번호 변경 성공",
          "errors": null
        }
    """

    const val AUTH_PROFILE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "조회 성공",
          "data": {
            "user_no": 1,
            "user_nick": "임의닉네임",
            "user_email": "user@example.com",
            "user_social_type": "",
            "user_image": "데이지",
            "user_created_at": "2026-04-09T16:00:00",
            "garden_count": 1,
            "read_book_count": 0,
            "like_book_count": 0
          }
        }
    """

    const val AUTH_PROFILE_UPDATE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "프로필 변경 성공",
          "data": {
            "user_no": 1,
            "user_nick": "임의닉네임",
            "user_email": "user@example.com",
            "user_image": "데이지",
            "user_fcm": "fcm-token-value",
            "user_social_id": "",
            "user_social_type": "",
            "user_created_at": "2026-04-09T16:00:00"
          }
        }
    """

    const val BOOK_SEARCH_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "책 검색 성공",
          "data": {
            "query": "클린 코드",
            "startIndex": 1,
            "itemsPerPage": 100,
            "item": [
              {
                "title": "클린 코드 결과",
                "author": "로버트 C. 마틴",
                "isbn13": "9780132350884",
                "cover": "https://example.com/cover.jpg",
                "publisher": "프래그마틱"
              }
            ]
          }
        }
    """

    const val BOOK_DUPLICATION_AVAILABLE = """
        {
          "resp_code": 200,
          "resp_msg": "책 등록 가능",
          "errors": null
        }
    """

    const val BOOK_DUPLICATION_CONFLICT = """
        {
          "resp_code": 403,
          "resp_msg": "책 중복",
          "errors": null
        }
    """

    const val BOOK_LOOKUP_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "책 검색(ISBN) 성공",
          "data": {
            "title": "클린 코드",
            "author": "로버트 C. 마틴",
            "isbn13": "9780132350884",
            "cover": "https://example.com/cover.jpg",
            "publisher": "프래그마틱"
          }
        }
    """

    const val BOOK_DETAIL_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "책 상세 조회 성공",
          "data": {
            "searchCategoryId": 1,
            "searchCategoryName": "소설",
            "title": "상세 책",
            "author": "저자",
            "description": "소개",
            "isbn13": "9788937462788",
            "cover": "https://example.com/book.jpg",
            "publisher": "출판사",
            "itemPage": 321,
            "record": {},
            "memo": {}
          }
        }
    """

    const val BOOK_STATUS_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "책 상태 조회 성공",
          "data": {
            "current_page": 1,
            "max_page": 1,
            "total_items": 1,
            "page_size": 10,
            "list": [
              {
                "book_no": 1,
                "book_title": "클린 코드",
                "book_author": "로버트 C. 마틴",
                "book_publisher": "인사이트",
                "book_info": "소개",
                "book_image_url": "https://example.com/book.jpg",
                "book_tree": "소설",
                "book_status": 1,
                "book_rating": 5,
                "percent": 100.0,
                "book_page": 321,
                "garden_no": 10
              }
            ]
          }
        }
    """

    const val BOOK_READ_DETAIL_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "독서 기록 조회 성공",
          "data": {
            "book_no": 1,
            "user_no": 1,
            "book_title": "클린 코드",
            "book_author": "로버트 C. 마틴",
            "book_publisher": "인사이트",
            "book_info": "소개",
            "book_image_url": "https://example.com/book.jpg",
            "book_tree": "소설",
            "book_status": 1,
            "book_rating": 5,
            "book_page": 321,
            "garden_no": 10,
            "garden_title": "나의 가든",
            "garden_color": "blue",
            "book_current_page": 321,
            "percent": 100.0,
            "book_read_list": [],
            "memo_list": []
          }
        }
    """

    const val BOOK_UPDATE_RATING_REQUEST = """
        {
          "book_rating": 5
        }
    """

    const val BOOK_UPDATE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "책 수정 성공",
          "errors": null
        }
    """

    const val BOOK_DELETE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "책 삭제 성공",
          "errors": null
        }
    """

    const val BOOK_READ_UPDATE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "독서 기록 수정 성공",
          "errors": null
        }
    """

    const val BOOK_READ_DELETE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "책 기록 삭제 성공",
          "errors": null
        }
    """

    const val BOOK_IMAGE_UPLOAD_SUCCESS = """
        {
          "resp_code": 201,
          "resp_msg": "이미지 업로드 성공",
          "errors": null
        }
    """

    const val BOOK_IMAGE_DELETE_SUCCESS = """
        {
          "resp_code": 201,
          "resp_msg": "이미지 삭제 성공",
          "errors": null
        }
    """

    const val GARDEN_LIST_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "가든 리스트 조회 성공",
          "data": [
            {
              "garden_no": 10,
              "garden_title": "메인 가든",
              "garden_info": "첫 번째",
              "garden_color": "green",
              "garden_members": 2,
              "book_count": 1,
              "garden_created_at": "2024-01-05T08:30:00"
            }
          ]
        }
    """

    const val GARDEN_UPDATE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "가든 수정 성공",
          "data": {}
        }
    """

    const val GARDEN_DELETE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "가든 삭제 성공",
          "errors": null
        }
    """

    const val GARDEN_MOVE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "가든 책 이동 성공",
          "errors": null
        }
    """

    const val GARDEN_LEAVE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "가든 탈퇴 성공",
          "errors": null
        }
    """

    const val GARDEN_LEADER_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "가든 멤버 변경 성공",
          "errors": null
        }
    """

    const val GARDEN_MAIN_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "가든 메인 변경 성공",
          "errors": null
        }
    """

    const val GARDEN_INVITE_SUCCESS = """
        {
          "resp_code": 201,
          "resp_msg": "가든 초대 완료",
          "errors": null
        }
    """

    const val MEMO_LIST_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "메모 리스트 조회 성공",
          "data": {
            "current_page": 1,
            "max_page": 3,
            "total": 20,
            "page_size": 10,
            "list": [
              {
                "id": 1,
                "book_no": 1,
                "book_title": "클린 코드",
                "book_author": "저자",
                "book_image_url": "https://example.com/book.jpg",
                "memo_content": "좋았던 문장",
                "memo_like": true,
                "image_url": "https://example.com/memo.jpg",
                "memo_created_at": "2026-04-09T16:30:00"
              }
            ]
          }
        }
    """

    const val MEMO_DETAIL_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "메모 상세 조회 성공",
          "data": {
            "id": 1,
            "book_no": 1,
            "book_title": "상세 메모 책",
            "book_author": "저자",
            "book_publisher": "출판사",
            "book_info": "메모 상세용 책 소개",
            "memo_content": "상세 메모 내용",
            "image_url": null,
            "memo_created_at": "2026-04-09T16:30:00"
          }
        }
    """

    const val MEMO_UPDATE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "메모 수정 성공",
          "errors": null
        }
    """

    const val MEMO_DELETE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "메모 삭제 성공",
          "errors": null
        }
    """

    const val MEMO_LIKE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "메모 즐겨찾기 추가/해제",
          "errors": null
        }
    """

    const val MEMO_IMAGE_DELETE_SUCCESS = """
        {
          "resp_code": 201,
          "resp_msg": "이미지 삭제 성공",
          "errors": null
        }
    """

    const val MEMO_IMAGE_UPLOAD_SUCCESS = """
        {
          "resp_code": 201,
          "resp_msg": "이미지 업로드 성공",
          "errors": null
        }
    """

    const val PUSH_GET_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "푸시 알림 조회 성공",
          "data": {
            "user_no": 1,
            "push_app_ok": true,
            "push_book_ok": false,
            "push_time": null
          }
        }
    """

    const val PUSH_UPDATE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "푸시 알림 수정 성공",
          "errors": null
        }
    """

    const val PUSH_BOOK_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "독서 알림 푸시 전송 성공",
          "data": [
            {
              "token": "fcm-token-value",
              "result": "sent",
              "message_id": "message-1"
            }
          ]
        }
    """

    const val PUSH_NOTICE_SUCCESS = """
        {
          "resp_code": 200,
          "resp_msg": "공지사항 푸시 전송 성공",
          "data": [
            {
              "token": "fcm-token-value",
              "result": "sent",
              "message_id": "message-2"
            }
          ]
        }
    """

    const val CREATED_EMPTY_SUCCESS = """
        {
          "resp_code": 201,
          "resp_msg": "이미지 업로드 성공",
          "errors": null
        }
    """
}
