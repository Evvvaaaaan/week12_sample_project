# Week 12 – Book REST API 고도화 및 API 문서화

- **GitHub Repository**: https://github.com/2026-2-WebService/assign12-c01-22300404
- **Render Deployment**: `https://<서비스이름>.onrender.com` ← 배포 후 기재
  - API: `https://<서비스이름>.onrender.com/api/books`
  - Swagger UI: `https://<서비스이름>.onrender.com/swagger-ui.html`

## 1. Project Information

제공된 Book REST API Sample Project(Spring Boot + JPA CRUD)를 12주차 강의 및 Lab 1~4 내용으로 고도화한 프로젝트입니다.

| 구분 | 적용 내용 |
|---|---|
| DTO | `BookRequest`(요청), `BookResponse`(응답) record로 Entity와 분리 |
| HTTP 응답 | `ResponseEntity`로 POST 201, GET/PUT 200, DELETE 204 |
| Validation | `@NotBlank`(title, author), `@Min(0)`(price) + `@Valid` |
| 예외 처리 | `@RestControllerAdvice` + `ApiError(code, message)` 공통 오류 응답 (400/404) |
| Pagination | `Pageable` + `Page<BookResponse>`, `@PageableDefault(size = 5, sort = "id")` |
| 문서화 | springdoc-openapi 2.10.0, `@Tag` / `@Operation` / `@ApiResponse` / `@Parameter` |

### 패키지 구조

```
src/main/java/edu/handong/week12
├── Week12Application.java
├── book
│   ├── Book.java                   # JPA Entity (books 테이블)
│   ├── BookRepository.java         # JpaRepository<Book, Long>
│   ├── BookService.java            # 비즈니스 로직, Entity ↔ DTO 변환, 404 예외 발생
│   ├── BookController.java         # REST API, ResponseEntity, Swagger 어노테이션
│   ├── BookRequest.java            # 요청 DTO (+ Validation)
│   ├── BookResponse.java           # 응답 DTO
│   └── BookNotFoundException.java  # 사용자 정의 예외
└── common
    ├── ApiError.java               # 공통 오류 응답 DTO (code, message)
    └── GlobalExceptionHandler.java # @RestControllerAdvice
```

## 2. Installation

- Java 17, Spring Boot 3.5.7, Gradle Wrapper 9.3.0 (포함됨)
- DB: 기본은 메모리 H2, `mariadb` 프로필에서 MariaDB 사용

### H2로 바로 실행

```bash
./gradlew bootRun
```

### MariaDB로 실행 (로컬)

```bash
# DB/계정 준비 (최초 1회) — 또는 docker compose up -d (포트 3307)
mariadb -e "CREATE DATABASE week12;
            CREATE USER 'week12'@'localhost' IDENTIFIED BY '<비밀번호>';
            GRANT ALL PRIVILEGES ON week12.* TO 'week12'@'localhost';"

SPRING_PROFILES_ACTIVE=mariadb \
DB_URL=jdbc:mariadb://localhost:3306/week12 \
DB_USERNAME=week12 \
DB_PASSWORD=<비밀번호> \
DDL_AUTO=update \
./gradlew bootRun
```

- 처음 실행할 때만 `DDL_AUTO=update`로 테이블을 만들고, 이후에는 `validate`를 권장합니다.
- DB 비밀번호는 코드나 설정 파일에 쓰지 않고 환경 변수로만 전달합니다. (`.env`는 `.gitignore`에 포함)

### 테스트

```bash
./gradlew test
```

`BookControllerTest`가 201/200/204, 400(검증 실패·잘못된 JSON), 404(조회·수정·삭제), 페이징/정렬, Swagger 노출을 확인합니다.

## 3. Source Code Exploration

### Project Structure & JPA

**Q1. BookController, BookService, BookRepository의 역할과 요청 처리 흐름**

`BookController`는 HTTP 요청을 받는 입구로, URL과 HTTP Method를 메서드에 연결하고 `ResponseEntity`로 상태 코드를 결정합니다. `BookService`는 비즈니스 로직을 담당하며 DTO를 Entity로 바꾸고, 없는 ID일 때 `BookNotFoundException`을 던집니다. `BookRepository`는 DB 접근만 담당합니다. 예를 들어 `POST /api/books`는 `BookController.create()`, `BookService.create()`, `BookRepository.save()` 순서로 처리되고, 저장된 `Book`이 `BookResponse`로 바뀌어 같은 길을 거꾸로 돌아가 응답됩니다.

**Q2. `JpaRepository<Book, Long>`만 선언해도 `findAll()`, `save()`를 사용할 수 있는 이유**

Spring Data JPA가 애플리케이션 시작 시 `JpaRepository`를 상속한 인터페이스를 찾아서 구현 객체(`SimpleJpaRepository` 기반 프록시)를 자동으로 만들고 Bean으로 등록하기 때문입니다. 제네릭 `<Book, Long>`으로 다룰 Entity와 ID 타입을 알려 주면 `findAll()`, `save()`, `findById()`, `existsById()`, `deleteById()`, `findAll(Pageable)` 같은 공통 CRUD 메서드를 바로 쓸 수 있습니다. `BookService`는 생성자 주입으로 이 구현 객체를 받아 사용합니다. (`BookRepository`, `BookService` 생성자)

### DTO & HTTP Response

**Q3. 기존 API가 Book Entity를 직접 요청·응답에 사용할 때 발생할 수 있는 문제**

원본 `BookController.create(@RequestBody Book input)`은 클라이언트 JSON을 Entity에 그대로 바인딩해서 서버가 관리해야 할 `id`까지 받았습니다. 실제로 원본에서 `"id": 1`을 넣고 POST하면 기존 1번 도서가 덮어써졌고, 없는 `"id": 999`를 넣으면 `StaleObjectStateException`으로 500 오류가 났습니다. 또 Entity 필드를 바꾸면 API 요청·응답 형식도 같이 바뀌고, 노출하면 안 되는 필드나 연관관계의 순환 참조가 응답에 그대로 나갈 수 있습니다.

**Q4. Request DTO와 Response DTO를 분리하는 이유와 ResponseEntity가 필요한 상황**

요청에는 클라이언트가 입력할 수 있는 값만, 응답에는 보여 줄 값만 있어야 하므로 둘의 역할이 다릅니다. `BookRequest`에는 `id`가 없고 검증 어노테이션이 붙어 있으며, `BookResponse`에는 `id`가 포함되고 `from()`으로 Entity를 변환합니다. `ResponseEntity`는 200이 아닌 상태 코드를 보내야 할 때 필요합니다. 예를 들어 `BookController.create()`는 `status(HttpStatus.CREATED)`로 201을, `delete()`는 `noContent()`로 본문 없는 204를 반환합니다.

### Validation & Exception Handling

**Q5. `@Valid`, `@NotBlank`, `@Min`은 각각 어느 시점에 어떤 동작을 수행하나요?**

`@NotBlank`와 `@Min`은 `BookRequest` 필드에 규칙을 선언만 하고, 실제 검사는 `@Valid`가 붙은 파라미터에서 일어납니다. JSON이 `BookRequest`로 변환된 직후, 컨트롤러 메서드 본문이 실행되기 전에 검사합니다. `@NotBlank`는 `null`, 빈 문자열, 공백만 있는 문자열(`" "`)을 거부하고, `@Min(0)`은 `price`가 0보다 작으면 거부합니다. 하나라도 실패하면 `BookController.create()`/`update()` 본문은 실행되지 않고 `MethodArgumentNotValidException`이 발생합니다.

**Q6. `@RestControllerAdvice`와 `@ExceptionHandler`를 사용하면 예외 처리 흐름이 어떻게 바뀌나요?**

이 어노테이션들이 없으면 처리되지 않은 예외가 Spring 기본 오류 응답으로 나가서, 원본에서 없는 ID를 조회하면 `timestamp`, `status`, `error`, `path`만 있는 500 응답이 왔습니다. `@RestControllerAdvice`가 붙은 `GlobalExceptionHandler`는 모든 컨트롤러에서 올라온 예외를 가로채고, `@ExceptionHandler`로 지정한 예외 타입별 메서드(`handleNotFound()`, `handleValidation()`, `handleInvalidJson()`)가 알맞은 상태 코드와 `ApiError(code, message)`를 만듭니다. 그래서 `BookService.findById()`는 예외만 던지면 되고, 오류 응답 형식은 한 곳에서 일관되게 관리됩니다.

### Pagination & OpenAPI

**Q7. `Pageable`과 `Page<T>`의 역할, `sort=price,desc` 처리 방식**

`Pageable`은 요청 정보(페이지 번호, 페이지 크기, 정렬 조건)를 담은 객체이고, `Page<T>`는 조회 결과로 현재 페이지 데이터(`content`)와 `totalElements`, `totalPages`, `number` 같은 메타데이터를 함께 담습니다. `sort=price,desc`는 Spring Data가 `Sort.by("price").descending()`으로 바꿔 `Pageable`에 넣고, `BookRepository.findAll(pageable)`이 `ORDER BY price DESC`와 `LIMIT`/`OFFSET`을 붙인 쿼리를 실행합니다. 이후 `BookService.findAll()`에서 `Page.map(BookResponse::from)`으로 내용만 DTO로 바꾸고 페이징 정보는 그대로 유지합니다.

**Q8. `/v3/api-docs`와 `/swagger-ui.html`의 역할 구분**

`/v3/api-docs`는 springdoc이 컨트롤러와 `@Tag`, `@Operation`, DTO 구조를 분석해서 자동으로 만든 OpenAPI 명세 JSON으로, 프로그램이 읽기 위한 문서입니다. `/swagger-ui.html`은 그 JSON을 불러와서 사람이 보기 쉬운 화면으로 보여 주고, Try it out으로 실제 요청까지 보낼 수 있는 UI입니다. 즉 `/v3/api-docs`는 데이터(명세), `/swagger-ui.html`은 그 명세를 보여 주고 테스트하는 도구입니다. (`/swagger-ui.html`은 `/swagger-ui/index.html`로 리다이렉트됩니다.)

### 줄 단위 설명 주석을 작성한 메서드 3개

| 메서드 | 파일 | 설명 포인트 |
|---|---|---|
| `BookController.create()` | `src/main/java/edu/handong/week12/book/BookController.java` | `@Valid`, DTO, `ResponseEntity`, 201 응답 |
| `BookService.findById()` | `src/main/java/edu/handong/week12/book/BookService.java` | Repository 조회, `Optional`, 404 예외 발생 |
| `BookController.list()` | `src/main/java/edu/handong/week12/book/BookController.java` | `Pageable`, `Page`, DTO 변환 |

> 각 메서드 본문에 줄 단위로 데이터 흐름을 설명하는 주석을 작성했습니다.

## 4. Refactoring – DTO와 ResponseEntity 변경 전/후

### 코드 변경

| 구분 | 변경 전 (Sample) | 변경 후 |
|---|---|---|
| 요청 타입 | `@RequestBody Book input` (Entity) | `@Valid @RequestBody BookRequest request` |
| 응답 타입 | `Book`, `List<Book>` (Entity) | `ResponseEntity<BookResponse>`, `ResponseEntity<Page<BookResponse>>` |
| 삭제 응답 | `void` | `ResponseEntity<Void>` (`noContent()`) |
| 없는 ID | `orElseThrow()` → `NoSuchElementException` | `orElseThrow(() -> new BookNotFoundException(id))` |

```java
// 변경 전
@PostMapping
public Book create(@RequestBody Book input) {
    return service.create(input);
}

// 변경 후
@PostMapping
public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
    BookResponse saved = service.create(request);
    return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(saved);
}
```

### API 요청/응답 차이

| 요청 | 변경 전 | 변경 후 |
|---|---|---|
| `POST /api/books` | `200 OK` | `201 Created` + `BookResponse` |
| `POST` 본문에 기존 ID `"id": 1` 포함 | `200 OK`, **기존 1번 도서가 덮어써짐** | `BookRequest`에 `id`가 없어 무시, 새 ID로 생성 (`201`) |
| `POST` 본문에 없는 ID `"id": 999` 포함 | `500` (`StaleObjectStateException`) | `id` 무시, 새 ID로 생성 (`201`) |
| `GET /api/books` | 전체 목록 JSON 배열 `[...]` | 페이지 객체 `{ "content": [...], "totalElements", "totalPages", "number", "size", ... }` |
| `GET /api/books/99999` | `500 Internal Server Error` (`NoSuchElementException`) | `404` `{"code":"BOOK_NOT_FOUND","message":"Book not found: 99999"}` |
| `DELETE /api/books/{id}` | `200 OK` (빈 본문) | `204 No Content` |
| `DELETE /api/books/99999` | `200 OK` (없는 ID도 성공처럼 응답) | `404` `BOOK_NOT_FOUND` |
| `POST` `{"title":"","author":" ","price":-1000}` | 검증 없이 저장됨 (`200`) | `400` `VALIDATION_FAILED` |

> 변경 전 결과는 원본 Sample 커밋(`3faf455`)을 H2로 실행해 확인한 값입니다.

변경 후 `POST` 응답 예시:

```http
POST /api/books
Content-Type: application/json

{"id":999,"title":"DDD","author":"Evans","price":45000}
```

```http
HTTP/1.1 201 Created

{"id":8,"title":"DDD","author":"Evans","price":45000}
```

> Lab 1 단계에서 `BookResponse.from()`이 `author` 자리에 `book.getTitle()`을 넣고 있어 응답의 author가 제목으로 나오던 오류도 함께 수정했습니다.

## 5. New Features

### Validation

```java
public record BookRequest(
        @NotBlank(message = "제목은 필수입니다.")
        String title,
        @NotBlank(message = "저자는 필수입니다.")
        String author,
        @Min(value = 0, message = "가격은 0 이상이어야 합니다.")
        int price
) {}
```

POST와 PUT 모두 `@Valid`를 적용했습니다.

### 공통 예외 처리

`GlobalExceptionHandler`(`@RestControllerAdvice`)가 예외를 `ApiError(code, message)` JSON으로 변환합니다.

| 예외 | 상황 | 상태 | code |
|---|---|---|---|
| `BookNotFoundException` | 없는 ID 조회/수정/삭제 | 404 | `BOOK_NOT_FOUND` |
| `MethodArgumentNotValidException` | 빈 제목/저자, 음수 가격 | 400 | `VALIDATION_FAILED` |
| `HttpMessageNotReadableException` | JSON 문법 오류, 타입 불일치 | 400 | `INVALID_JSON` |

```json
{"code":"VALIDATION_FAILED","message":"price: 가격은 0 이상이어야 합니다., title: 제목은 필수입니다., author: 저자는 필수입니다."}
```

### Pagination / Sorting

```
GET /api/books?page=0&size=5&sort=price,desc
```

- 파라미터를 생략하면 `@PageableDefault(size = 5, sort = "id", direction = ASC)`가 적용됩니다.
- `Page<BookResponse>`를 반환하므로 `content`와 `totalElements`, `totalPages`, `number`, `size` 등 페이징 메타데이터가 함께 응답됩니다.
- Spring Data 3.3 이상은 `PageImpl`을 그대로 직렬화하면 "JSON 구조 안정성이 보장되지 않는다"는 WARN 로그를 남깁니다. 강의 예제와 같은 응답 구조를 유지하기 위해 `Page`를 그대로 반환했습니다.

### Swagger / OpenAPI

- 의존성: `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.10.0`
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- `@Tag(name = "Book")`, 전체 5개 API에 `@Operation`, `@ApiResponse`, `@Parameter` 적용

## 6. Testing & Deployment

### Local Testing 결과 (`results/`)

| 파일 | 내용 |
|---|---|
| ![01](results/01_sample.png) | 01 – 초기 Sample CRUD 테스트 |
| ![02](results/02_dto_created.png) | 02 – DTO 적용 후 POST 성공 (201) |
| ![03](results/03_validation.png) | 03 – 잘못된 입력 요청 (400) |
| ![04](results/04_not_found.png) | 04 – 존재하지 않는 ID (404) |
| ![05](results/05_pagination.png) | 05 – 페이지/정렬 적용 결과 |
| ![06](results/06_swagger.png) | 06 – Swagger UI / API 테스트 화면 |

### Render 배포

Render에는 Java 런타임이 기본 제공되지 않으므로 `Dockerfile`로 배포합니다.

1. Render → **New Web Service** → 이 GitHub 저장소 연결 → Runtime: **Docker**
2. Environment Variables 설정 (비밀번호는 Render 대시보드에만 입력)

   | Key | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `mariadb` |
   | `DB_URL` | `jdbc:mariadb://<외부 DB 호스트>:<포트>/<DB명>` |
   | `DB_USERNAME` | `<DB 계정>` |
   | `DB_PASSWORD` | `<DB 비밀번호>` |
   | `DDL_AUTO` | 최초 배포 `update` → 테이블 생성 후 `validate` |

3. `PORT`는 Render가 주입하며 `application.yml`의 `server.port: ${PORT:8080}`이 사용합니다.
4. 배포 후 확인: `/api/books`, `/swagger-ui.html`

### 배포 중 발생한 문제와 조치

<!-- Render에서 외부 MariaDB 연결 시 발생한 실제 오류 메시지와 조치 내역을 기록합니다. 예: 접속 거부(방화벽/허용 IP), 인증 실패, 테이블 없음(DDL_AUTO) 등 -->

| 오류 (로그 원문) | 원인 | 조치 |
|---|---|---|
| | | |

## 7. Weekly Review

**새롭게 배운 내용 3가지**

1. **Entity를 요청에 직접 쓰면 위험하다.** 원본 Sample에서 POST 본문에 `"id": 1`을 넣자 기존 도서가 덮어써지는 것을 직접 확인했습니다. Request DTO에서 서버 생성 필드를 빼는 이유를 실제 결과로 이해했습니다.
2. **예외 처리는 한 곳에 모을 수 있다.** Service는 `BookNotFoundException`만 던지고, `@RestControllerAdvice`가 400/404 응답을 `ApiError(code, message)` 형식으로 통일합니다. Controller에 try-catch가 필요 없어졌습니다.
3. **페이징과 문서화가 거의 자동이다.** `Pageable` 파라미터 하나로 `page/size/sort` 처리와 `LIMIT`/`ORDER BY` 쿼리가 만들어지고, springdoc 의존성과 어노테이션만으로 Swagger UI에서 API를 바로 테스트할 수 있었습니다.

**어려웠던 점과 해결 방법**

- **응답의 author가 제목으로 나오는 문제:** Lab 1에서 `BookResponse.from()`에 `book.getAuthor()` 대신 `book.getTitle()`을 넣은 실수였습니다. 응답 필드를 하나씩 검사하는 테스트(`BookControllerTest`)를 작성하면서 발견해 수정했습니다.
- **`Page`를 그대로 반환할 때 나오는 WARN 로그:** Spring Data 3.3부터 `PageImpl` 직렬화 구조가 안정적이지 않다는 경고가 나옵니다. 원인을 확인한 뒤, 강의 예제와 같은 응답 구조를 유지하기 위해 `Page<BookResponse>` 반환을 유지했습니다.
- **Render 배포 준비:** Render는 Java 런타임을 기본 제공하지 않아서 멀티 스테이지 `Dockerfile`을 작성했고, Render가 주는 `PORT` 환경 변수를 `server.port: ${PORT:8080}`으로 받도록 했습니다. DB 비밀번호는 환경 변수로만 전달합니다.

**AI 활용 내용 및 느낀 점**

- **활용 내용:** AI 코딩 도구(Claude Code)로 DTO/Validation/예외 처리/페이징/Swagger 코드 구현, 통합 테스트 작성, Dockerfile 작성, README 작성(분석 답변과 코드 설명 주석 포함), Swagger UI 스크린샷 캡처를 진행했습니다. 생성된 코드는 테스트(11개 통과)와 MariaDB 연결 실행으로 직접 확인했습니다.
- **느낀 점:** AI가 코드를 빠르게 만들어 주지만, 원본 Sample을 실제로 실행해서 비교한 덕분에 예상과 다른 동작(기존 데이터 덮어쓰기, 없는 ID 삭제 시 200 응답)을 확인할 수 있었습니다. 결과를 그대로 믿기보다 실행과 테스트로 검증하는 과정이 중요하다고 느꼈습니다.
