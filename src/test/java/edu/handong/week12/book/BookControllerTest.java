package edu.handong.week12.book;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BookControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    BookRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void createReturns201AndResponseDto() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Clean Code\",\"author\":\"Martin\",\"price\":35000}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("http://localhost/api/books/\\d+")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title").value("Clean Code"))
                .andExpect(jsonPath("$.author").value("Martin"))
                .andExpect(jsonPath("$.price").value(35000));
    }

    @Test
    void getAndUpdateReturn200() throws Exception {
        Book saved = repository.save(new Book("Old", "Kim", 1000));

        mockMvc.perform(get("/api/books/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("Kim"));

        mockMvc.perform(put("/api/books/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New\",\"author\":\"Lee\",\"price\":2000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New"))
                .andExpect(jsonPath("$.author").value("Lee"))
                .andExpect(jsonPath("$.price").value(2000));
    }

    @Test
    void deleteReturns204() throws Exception {
        Book saved = repository.save(new Book("T", "A", 1000));

        mockMvc.perform(delete("/api/books/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void clientCannotSetServerGeneratedId() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":999,\"title\":\"T\",\"author\":\"A\",\"price\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(not(999)));
    }

    @Test
    void blankTitleAuthorAndNegativePriceReturn400() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"author\":\" \",\"price\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void invalidUpdateBodyReturns400() throws Exception {
        Book saved = repository.save(new Book("T", "A", 1000));

        mockMvc.perform(put("/api/books/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"author\":\"A\",\"price\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"author\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void missingIdReturns404ForGetUpdateDelete() throws Exception {
        mockMvc.perform(get("/api/books/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"))
                .andExpect(jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("책이 존재하지 않습니다."))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        mockMvc.perform(put("/api/books/99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"author\":\"A\",\"price\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));

        mockMvc.perform(delete("/api/books/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
    }

    @Test
    void listSupportsPaginationAndSorting() throws Exception {
        for (int i = 1; i <= 7; i++) {
            repository.save(new Book("Book " + i, "Author " + i, i * 1000));
        }

        mockMvc.perform(get("/api/books?page=0&size=5&sort=price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.content[0].price").value(7000))
                .andExpect(jsonPath("$.content[4].price").value(3000))
                .andExpect(jsonPath("$.page.totalElements").value(7))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(5))
                .andExpect(jsonPath("$.pageable").doesNotExist());

        mockMvc.perform(get("/api/books?page=1&size=5&sort=price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].price").value(2000));
    }

    @Test
    void invalidPathVariableAndSortFieldReturn400() throws Exception {
        mockMvc.perform(get("/api/books/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mockMvc.perform(get("/api/books?sort=foo,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void openApiDocsAndSwaggerUiAreAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/books']", notNullValue()));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
