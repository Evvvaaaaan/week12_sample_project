package edu.handong.week12.book;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class BookService {
  private final BookRepository repository;

  public BookService(BookRepository repository) {
    this.repository = repository;
  }

  public List<BookResponse> findAll() {
    return repository.findAll().stream().map(BookResponse::from).toList();
  } // TODO STEP 6: Pagination/Sorting

  public BookResponse findById(Long id) {
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
