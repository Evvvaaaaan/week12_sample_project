# Week 12 – Book REST API 고도화 및 API 문서화

- GitHub Repository: https://github.com/2026-2-WebService/assign12-c01-22300404
- Render Deployment: https://<서비스이름>.onrender.com (배포 후 기재)
  - API: https://<서비스이름>.onrender.com/api/books
  - Swagger UI: https://<서비스이름>.onrender.com/swagger-ui.html

## 1. Project Information

- 제공된 Book REST API Sample Project(Spring Boot + JPA CRUD)를 12주차 강의 및 Lab 1~4 내용으로 고도화한 프로젝트
- DTO: BookRequest(요청), BookResponse(응답) record로 Entity와 분리
- HTTP 응답: ResponseEntity로 POST 201(Location 헤더 포함), GET/PUT 200, DELETE 204
- Validation: title, author에 @NotBlank, price에 @Min(0), 요청 파라미터에 @Valid
- 예외 처리: @RestControllerAdvice와 ExceptionResponse(timestamp, httpStatus, code, message)로 400/404/500 공통 오류 응답
- Pagination: Pageable과 Page 반환, 기본값 @PageableDefault(size = 5, sort = "id"), VIA_DTO로 응답 구조 고정
- 문서화: springdoc-openapi 2.10.0, @Tag, @Operation, @ApiResponse, @Parameter

### 패키지 구조

- src/main/java/edu/handong/week12
  - Week12Application.java
  - book
    - Book.java: JPA Entity (books 테이블)
    - BookRepository.java: JpaRepository 상속 (Book, Long)
    - BookService.java: 비즈니스 로직, Entity와 DTO 변환, 404 예외 발생
    - BookController.java: REST API, ResponseEntity, Swagger 어노테이션
    - BookRequest.java: 요청 DTO와 Validation
    - BookResponse.java: 응답 DTO
  - exception
    - CustomException.java: ExceptionCode를 담아 던지는 사용자 정의 예외
    - constants/ExceptionCode.java: 에러 코드, HTTP 상태, 메시지 enum
    - response/ExceptionResponse.java: 공통 오류 응답 DTO (timestamp, httpStatus, code, message)
    - handler/CustomizedResponseEntityExceptionHandler.java: @RestControllerAdvice

## 2. Installation

- Java 17, Spring Boot 3.5.7, Gradle Wrapper 9.3.0 (포함)
- DB: 기본은 메모리 H2, mariadb 프로필에서 MariaDB 사용

### H2로 실행

- ./gradlew bootRun

### MariaDB로 실행 (로컬)

- DB와 계정 준비 (최초 1회): week12 데이터베이스와 week12 계정을 만들고 권한 부여, 또는 docker compose up -d (포트 3307)
- 실행: SPRING_PROFILES_ACTIVE=mariadb DB_URL=jdbc:mariadb://localhost:3306/week12 DB_USERNAME=week12 DB_PASSWORD=<비밀번호> DDL_AUTO=update ./gradlew bootRun
- 처음 실행할 때만 DDL_AUTO=update로 테이블을 만들고, 이후에는 validate 권장
- DB 비밀번호는 코드나 설정 파일에 쓰지 않고 환경 변수로만 전달 (.env는 .gitignore에 포함)

### 테스트

- ./gradlew test
- BookControllerTest가 201(Location 헤더)/200/204, 400(검증 실패, 잘못된 JSON, 잘못된 경로 변수, 없는 정렬 필드), 404(조회, 수정, 삭제), 페이징/정렬, Swagger 노출 확인

## 3. Source Code Exploration

### Q1. BookController, BookService, BookRepository의 역할과 요청 처리 흐름

- BookController: HTTP 요청을 받아 URL과 HTTP Method를 메서드에 연결하고, ResponseEntity로 상태 코드 결정
- BookService: 비즈니스 로직 담당, DTO를 Entity로 변환, 없는 ID이면 CustomException(BOOK_NOT_FOUND) 발생
- BookRepository: DB 접근만 담당
- POST /api/books는 BookController.create(), BookService.create(), BookRepository.save() 순서로 처리되고, 저장된 Book이 BookResponse로 바뀌어 응답됨

### Q2. JpaRepository만 선언해도 findAll(), save()를 사용할 수 있는 이유

- Spring Data JPA가 시작 시 JpaRepository를 상속한 인터페이스를 찾아 구현 객체(SimpleJpaRepository 기반 프록시)를 자동으로 만들고 Bean으로 등록
- 제네릭으로 Entity(Book)와 ID 타입(Long)을 알려 주면 findAll(), save(), findById(), existsById(), deleteById(), findAll(Pageable)을 바로 사용 가능
- BookService는 생성자 주입으로 이 구현 객체를 받아 사용

### Q3. Book Entity를 직접 요청·응답에 사용할 때의 문제

- 원본 create(@RequestBody Book input)은 서버가 관리해야 할 id까지 클라이언트에게서 받음
- 원본에서 "id": 1을 넣고 POST하면 기존 1번 도서가 덮어써짐
- 없는 "id": 999를 넣으면 StaleObjectStateException으로 500 오류 발생
- Entity 필드를 바꾸면 API 형식도 같이 바뀌고, 노출하면 안 되는 필드나 연관관계 순환 참조가 응답에 나갈 수 있음

### Q4. Request DTO와 Response DTO를 분리하는 이유와 ResponseEntity가 필요한 상황

- 요청에는 클라이언트가 입력할 값만, 응답에는 보여 줄 값만 있어야 함
- BookRequest: id 없음, 검증 어노테이션 포함
- BookResponse: id 포함, of()로 Entity 변환
- ResponseEntity는 상태 코드나 헤더를 직접 정할 때 사용: create()는 201과 Location 헤더, delete()는 본문 없는 204 반환

### Q5. @Valid, @NotBlank, @Min의 동작 시점

- @NotBlank, @Min은 BookRequest 필드에 규칙만 선언
- 실제 검사는 @Valid가 붙은 파라미터에서, JSON이 BookRequest로 변환된 직후 컨트롤러 메서드 실행 전에 수행
- @NotBlank: null, 빈 문자열, 공백만 있는 문자열 거부
- @Min(0): price가 0보다 작으면 거부
- 실패하면 create()/update() 본문은 실행되지 않고 MethodArgumentNotValidException 발생

### Q6. @RestControllerAdvice와 @ExceptionHandler 사용 시 예외 처리 흐름

- 없으면 처리되지 않은 예외가 Spring 기본 오류 응답(timestamp, status, error, path만 있는 500)으로 나감
- CustomizedResponseEntityExceptionHandler가 모든 컨트롤러의 예외를 가로챔
- ResponseEntityExceptionHandler를 상속해 검증 실패, 잘못된 JSON, 경로 변수 타입 불일치 메서드를 오버라이드하고, @ExceptionHandler로 CustomException, 없는 정렬 필드, 그 외 예외를 처리
- 각 메서드가 ExceptionCode의 상태 코드와 ExceptionResponse(timestamp, httpStatus, code, message) 생성
- Service는 예외만 던지면 되고, 오류 응답 형식은 한 곳에서 관리

### Q7. Pageable과 Page의 역할, sort=price,desc 처리 방식

- Pageable: 페이지 번호, 페이지 크기, 정렬 조건을 담은 요청 정보
- Page: 현재 페이지 데이터(content)와 page(size, number, totalElements, totalPages) 메타데이터
- sort=price,desc는 Sort.by("price").descending()으로 바뀌어 Pageable에 들어감
- findAll(pageable)이 ORDER BY price DESC와 LIMIT/OFFSET 쿼리 실행
- BookService.findAll()에서 Page.map(BookResponse::of)으로 내용만 DTO로 변환하고 페이징 정보 유지

### Q8. /v3/api-docs와 /swagger-ui.html의 역할 구분

- /v3/api-docs: springdoc이 컨트롤러, 어노테이션, DTO 구조를 분석해 만든 OpenAPI 명세 JSON (프로그램용)
- /swagger-ui.html: 그 JSON을 사람이 보기 쉬운 화면으로 보여 주고 Try it out으로 실제 요청을 보내는 UI
- /swagger-ui.html은 /swagger-ui/index.html로 리다이렉트됨

### 줄 단위 설명 주석을 작성한 메서드

- BookController.create() (book/BookController.java): @Valid, DTO, ResponseEntity, 201 응답과 Location 헤더
- BookService.findById() (book/BookService.java): Repository 조회, Optional, 404 예외 발생
- BookController.list() (book/BookController.java): Pageable, Page, DTO 변환

## 4. Refactoring – DTO와 ResponseEntity 변경 전/후

### 코드 변경

- 요청 타입: Book Entity에서 @Valid @RequestBody BookRequest로 변경
- 응답 타입: Book, Book 목록에서 BookResponse를 담은 ResponseEntity와 Page로 변경
- 삭제 응답: void에서 ResponseEntity.noContent()로 변경
- 없는 ID: NoSuchElementException에서 CustomException(BOOK_NOT_FOUND)으로 변경

### API 요청/응답 차이

- POST /api/books: 200 OK에서 201 Created, Location: /api/books/{id} 헤더, BookResponse로 변경
- POST 본문에 기존 ID "id": 1 포함: 기존 1번 도서가 덮어써지던 문제가 사라지고 id를 무시해 새 ID로 생성
- POST 본문에 없는 ID "id": 999 포함: 500(StaleObjectStateException)에서 새 ID로 생성(201)으로 변경
- GET /api/books: JSON 배열에서 content와 페이징 메타데이터가 있는 페이지 객체로 변경
- GET /api/books/99999: 500에서 404 {"timestamp":"...","httpStatus":"NOT_FOUND","code":"BOOK_NOT_FOUND","message":"책이 존재하지 않습니다."}로 변경
- GET /api/books/abc: Spring 기본 오류 형식의 400에서 400 INVALID_PARAMETER로 변경
- GET /api/books?sort=foo,desc: 500에서 400 INVALID_PARAMETER로 변경
- DELETE /api/books/{id}: 200 OK에서 204 No Content로 변경
- DELETE /api/books/99999: 없는 ID도 200이던 응답에서 404 BOOK_NOT_FOUND로 변경
- POST {"title":"","author":" ","price":-1000}: 검증 없이 저장(200)되던 요청이 400 VALIDATION_FAILED로 변경
- 변경 전 결과는 원본 Sample 커밋(3faf455)을 H2로 실행해 확인
- Lab 1에서 BookResponse.from()이 author 자리에 title을 넣던 오류도 함께 수정

## 5. New Features

### Validation

- BookRequest: title, author는 @NotBlank, price는 @Min(0)
- 오류 메시지: "제목은 필수입니다.", "저자는 필수입니다.", "가격은 0 이상이어야 합니다."
- POST와 PUT 모두 @Valid 적용

### 공통 예외 처리

- ExceptionCode enum에 에러 코드, HTTP 상태, 메시지를 모아 두고, CustomizedResponseEntityExceptionHandler가 예외를 ExceptionResponse JSON으로 변환
- CustomException(BOOK_NOT_FOUND): 없는 ID 조회/수정/삭제, 404, BOOK_NOT_FOUND
- MethodArgumentNotValidException: 빈 제목/저자, 음수 가격, 400, VALIDATION_FAILED
- HttpMessageNotReadableException: JSON 문법 오류, 타입 불일치, 400, INVALID_JSON
- TypeMismatchException: 경로 변수 타입 불일치 (예: /api/books/abc), 400, INVALID_PARAMETER
- PropertyReferenceException: 존재하지 않는 정렬 필드 (예: sort=foo,desc), 400, INVALID_PARAMETER
- 그 외 예외: 로그를 남기고 500, INTERNAL_SERVER_ERROR

### Pagination / Sorting

- 요청 예시: GET /api/books?page=0&size=5&sort=price,desc
- 파라미터 생략 시 @PageableDefault(size = 5, sort = "id") 적용 (id 오름차순)
- @EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)로 응답이 content와 page(size, number, totalElements, totalPages)로만 나옴

### Swagger / OpenAPI

- 의존성: org.springdoc:springdoc-openapi-starter-webmvc-ui:2.10.0
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- @Tag(name = "Book"), 전체 5개 API에 @Operation, @ApiResponse, @Parameter 적용

## 6. Testing & Deployment

### Local Testing 결과

- results/01_sample.png: 초기 Sample CRUD 테스트
- results/02_dto_created.png: DTO 적용 후 POST 성공 (201)
- results/03_validation.png: 잘못된 입력 요청 (400)
- results/04_not_found.png: 존재하지 않는 ID (404)
- results/05_pagination.png: 페이지/정렬 적용 결과
- results/06_swagger.png: Swagger UI / API 테스트 화면

### Render 배포

- Render는 Java 런타임을 기본 제공하지 않으므로 Dockerfile로 배포
- New Web Service에서 이 GitHub 저장소 연결, Runtime은 Docker 선택
- 환경 변수 (비밀번호는 Render 대시보드에만 입력)
  - SPRING_PROFILES_ACTIVE: mariadb
  - DB_URL: jdbc:mariadb://<외부 DB 호스트>:<포트>/<DB명>
  - DB_USERNAME: <DB 계정>
  - DB_PASSWORD: <DB 비밀번호>
  - DDL_AUTO: 최초 배포 시 update, 테이블 생성 후 validate
- PORT는 Render가 주입하고 application.yml의 server.port: ${PORT:8080}이 사용
- 배포 후 /api/books, /swagger-ui.html 확인

### 배포 중 발생한 문제와 조치

- 배포 후 실제 오류 로그, 원인, 조치를 기록

## 7. Weekly Review

### 새롭게 배운 내용 3가지

- Entity를 요청에 직접 쓰면 위험하다: 원본에서 POST 본문에 "id": 1을 넣자 기존 도서가 덮어써지는 것을 확인하고, Request DTO에서 서버 생성 필드를 빼는 이유를 이해함
- 예외 처리는 한 곳에 모을 수 있다: Service는 CustomException만 던지고 @RestControllerAdvice가 400/404/500 응답을 ExceptionResponse 형식으로 통일해 Controller에 try-catch가 필요 없어짐
- 페이징과 문서화가 거의 자동이다: Pageable 파라미터 하나로 page/size/sort 처리와 쿼리가 만들어지고, springdoc 의존성과 어노테이션만으로 Swagger UI에서 API를 테스트할 수 있음

### 어려웠던 점과 해결 방법

- 응답의 author가 제목으로 나오는 문제: Lab 1에서 BookResponse.from()에 getAuthor() 대신 getTitle()을 넣은 실수로, 응답 필드를 검사하는 BookControllerTest를 작성하며 발견해 수정
- Page 반환 시 WARN 로그: Spring Data 3.3부터 PageImpl을 그대로 직렬화하면 구조가 안정적이지 않다는 경고가 나와, @EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)를 설정해 응답을 content와 page로 고정하고 경고를 없앰
- 없는 정렬 필드로 500 오류: sort=foo,desc 요청 시 PropertyReferenceException이 처리되지 않아 500이 나와, @ExceptionHandler로 잡아 400 INVALID_PARAMETER로 응답하도록 수정
- Render 배포 준비: 멀티 스테이지 Dockerfile을 작성하고 PORT 환경 변수를 server.port: ${PORT:8080}으로 받도록 설정, DB 비밀번호는 환경 변수로만 전달

### AI 활용 내용 및 느낀 점

- 활용 내용: AI 코딩 도구(Claude Code)를 코드 검증과 관련 코드 학습에 활용
  - 작성한 DTO, Validation, 예외 처리, 페이징 코드에 실수가 없는지 검토 요청
  - @Valid, @RestControllerAdvice, Pageable, springdoc 어노테이션처럼 처음 접하는 코드의 내부 동작을 질문하며 학습
  - 설명받은 내용은 테스트(11개 통과)와 MariaDB 연결 실행으로 직접 확인
- 느낀 점: 코드 검토와 설명 덕분에 각 어노테이션과 계층이 왜 필요한지 빠르게 이해함
  - 설명만 믿지 않고 원본 Sample을 실행해 비교한 덕분에 기존 데이터 덮어쓰기, 없는 ID 삭제 시 200 응답 같은 예상과 다른 동작을 직접 확인함
  - AI의 답변도 실행과 테스트로 검증하는 과정이 중요하다고 느낌
