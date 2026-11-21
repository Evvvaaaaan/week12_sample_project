package edu.handong.week12.book;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Book", description = "도서 관리 API")
@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    @Operation(
            summary = "도서 목록 조회",
            description = "등록된 도서를 페이지 단위로 조회합니다. 예) ?page=0&size=5&sort=price,desc"
    )
    @GetMapping
    public ResponseEntity<Page<BookResponse>> list(
            // Swagger UI에서 Pageable 객체를 page, size, sort 쿼리 파라미터 3개로 풀어서 보여준다.
            @ParameterObject
            // 요청에 page/size/sort가 없을 때 사용할 기본값이다.
            @PageableDefault(
                    size = 5,                       // 한 페이지에 5개
                    sort = "id",                    // id 기준으로 정렬
                    direction = Sort.Direction.ASC  // 오름차순
            ) Pageable pageable) {
        // Spring이 ?page=0&size=5&sort=price,desc 를 읽어서 Pageable(PageRequest) 객체를 만들어 넣어 준다.
        // page=0은 첫 번째 페이지(0부터 시작), size=5는 페이지 크기, sort=price,desc는 price 내림차순 Sort가 된다.

        // Service에서 repository.findAll(pageable)이 ORDER BY ... LIMIT ... OFFSET ... 쿼리로 해당 페이지만 조회하고,
        // Page.map(BookResponse::from)으로 Book Entity를 BookResponse DTO로 바꾼다.
        // map()은 content만 바꾸고 totalElements, totalPages 같은 페이징 정보는 그대로 유지한다.
        Page<BookResponse> result = service.findAll(pageable);
        // content와 페이징 메타데이터(totalElements, totalPages, number, size 등)를 JSON으로 담아 200 OK로 응답한다.
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "도서 단건 조회", description = "ID로 도서 한 권을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "존재하지 않는 도서 ID")
    @GetMapping("/{id}")
    public ResponseEntity<BookResponse> get(
            @Parameter(description = "도서 ID", example = "1") @PathVariable Long id) {
        BookResponse result = service.findById(id);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "도서 등록", description = "새 도서를 등록하고 201 Created를 반환합니다.")
    @ApiResponse(responseCode = "201", description = "등록 성공")
    @ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 잘못된 JSON")
    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
        // @RequestBody: 요청 본문의 JSON을 Jackson이 BookRequest(record) 객체로 변환한다.
        // BookRequest에는 id 필드가 없으므로 클라이언트가 id를 보내도 무시되고, id는 DB가 생성한다.
        // @Valid: 이 메서드가 실행되기 전에 BookRequest의 @NotBlank(title, author), @Min(0)(price) 규칙을 검사한다.
        // 검사에 실패하면 아래 코드는 실행되지 않고 MethodArgumentNotValidException이 발생하며,
        // GlobalExceptionHandler.handleValidation()이 400 Bad Request 응답을 만든다.

        // 검증을 통과한 DTO를 Service에 넘긴다.
        // Service는 DTO를 Book Entity로 바꿔 save()로 저장하고, 저장된 Entity를 BookResponse로 바꿔 돌려준다.
        BookResponse saved = service.create(request);
        // ResponseEntity로 HTTP 상태 코드와 본문을 직접 지정한다.
        return ResponseEntity
                .status(HttpStatus.CREATED) // 새 리소스가 만들어졌으므로 200이 아닌 201 Created를 반환한다.
                .body(saved);               // 본문에는 Entity가 아닌 BookResponse(id, title, author, price)를 담는다.
    }

    @Operation(summary = "도서 수정", description = "ID에 해당하는 도서의 제목, 저자, 가격을 수정합니다.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 잘못된 JSON")
    @ApiResponse(responseCode = "404", description = "존재하지 않는 도서 ID")
    @PutMapping("/{id}")
    public ResponseEntity<BookResponse> update(
            @Parameter(description = "도서 ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody BookRequest request) {
        BookResponse updated = service.update(id, request);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "도서 삭제", description = "ID에 해당하는 도서를 삭제하고 204 No Content를 반환합니다.")
    @ApiResponse(responseCode = "204", description = "삭제 성공")
    @ApiResponse(responseCode = "404", description = "존재하지 않는 도서 ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "도서 ID", example = "1") @PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
