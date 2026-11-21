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
            @ParameterObject
            @PageableDefault(
                    size = 5,
                    sort = "id",
                    direction = Sort.Direction.ASC
            ) Pageable pageable) {
        // TODO(직접 작성): 줄 단위 설명 주석 — Pageable, Page, DTO 변환
        Page<BookResponse> result = service.findAll(pageable);
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
        // TODO(직접 작성): 줄 단위 설명 주석 — @Valid, DTO, ResponseEntity, 201 응답
        BookResponse saved = service.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(saved);
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
