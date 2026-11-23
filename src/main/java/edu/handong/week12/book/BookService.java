package edu.handong.week12.book;

import edu.handong.week12.exception.CustomException;
import edu.handong.week12.exception.constants.ExceptionCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class BookService {
  private final BookRepository repository;

  public BookService(BookRepository repository) {
    this.repository = repository;
  }

  public Page<BookResponse> findAll(Pageable pageable) {
    return repository.findAll(pageable)
            .map(BookResponse::of);
  }

  public BookResponse findById(Long id) {
    // 조회한 Entity를 그대로 반환하지 않고, 응답에 필요한 필드만 담은 BookResponse DTO로 변환해 반환한다.
    return BookResponse.of(
            // JpaRepository가 제공하는 findById()로 기본 키가 id인 행을 조회한다. (SELECT ... WHERE id = ?)
            // 해당 행이 없을 수도 있으므로 null 대신 Optional<Book>으로 감싸서 반환된다.
            repository.findById(id)
                    // Optional에 값이 있으면 Book을 꺼내고, 비어 있으면 CustomException(BOOK_NOT_FOUND)을 던진다.
                    // 이 예외는 Controller 밖으로 전달되어 CustomizedResponseEntityExceptionHandler.handleCustomException()이 받고,
                    // 404 Not Found와 {"code":"BOOK_NOT_FOUND","message":"책이 존재하지 않습니다."} 응답으로 바꾼다.
                    .orElseThrow(() -> new CustomException(ExceptionCode.BOOK_NOT_FOUND))
    );
  }

  public BookResponse create(BookRequest input) {
    Book book = new Book(input.title(), input.author(), input.price());
    return BookResponse.of(repository.save(book));
  }

  public BookResponse update(Long id, BookRequest input) {
    Book book = repository.findById(id)
            .orElseThrow(() -> new CustomException(ExceptionCode.BOOK_NOT_FOUND));
    book.update(input.title(), input.author(), input.price());
    return BookResponse.of(repository.save(book));
  }

  public void delete(Long id) {
    if (!repository.existsById(id)) {
      throw new CustomException(ExceptionCode.BOOK_NOT_FOUND);
    }
    repository.deleteById(id);
  }
}
