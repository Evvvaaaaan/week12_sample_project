package edu.handong.week12.book;

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
            .map(BookResponse::from);
  }

  public BookResponse findById(Long id) {
    // TODO(직접 작성): 줄 단위 설명 주석 — Repository 조회, Optional, 404 예외 발생
    Book book = repository.findById(id)
            .orElseThrow(() -> new BookNotFoundException(id));
    return BookResponse.from(book);
  }

  public BookResponse create(BookRequest request) {
    Book book = new Book(
            request.title(),
            request.author(),
            request.price()
    );
    Book savedBook = repository.save(book);
    return BookResponse.from(savedBook);
  }

  public BookResponse update(Long id, BookRequest request) {
    Book book = repository.findById(id)
            .orElseThrow(() -> new BookNotFoundException(id));

    book.update(
            request.title(),
            request.author(),
            request.price()
    );

    Book savedBook = repository.save(book);
    return BookResponse.from(savedBook);
  }

  public void delete(Long id) {
    if (!repository.existsById(id)) {
      throw new BookNotFoundException(id);
    }
    repository.deleteById(id);
  }
}
