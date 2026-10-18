package com.bookswap.copy;

import com.bookswap.AbstractIntegrationTest;
import com.bookswap.book.catalog.BookDetails;
import com.bookswap.copy.domain.ConditionGrade;
import com.bookswap.copy.domain.Copy;
import com.bookswap.copy.repository.CopyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.bookswap.support.Isbns.FOX;
import static com.bookswap.support.Isbns.valid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CopyHardeningIT extends AbstractIntegrationTest {

    @Autowired EntityManagerFactory emf;
    @Autowired CopyRepository copies;

    private void addFox(JsonNode tokens) throws Exception {
        catalog.put(FOX, new BookDetails("Fantastic Mr Fox", "Roald Dahl", null));
        mvc.perform(post("/api/copies").header("Authorization", bearer(tokens)).contentType(APPLICATION_JSON)
                .content("{\"isbn\":\"" + FOX + "\",\"grade\":\"GOOD\"}")).andExpect(status().isCreated());
    }

    @Test
    void libraryListQueryCountDoesNotGrowWithPageSize() throws Exception {
        JsonNode tokens = registerAndLogin("n1@example.com", "password123");
        for (int i = 1; i <= 12; i++) {
            catalog.put(valid(i), new BookDetails("Book " + i, "Author " + i, null));
            mvc.perform(post("/api/copies").header("Authorization", bearer(tokens)).contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("isbn", valid(i), "grade", "GOOD"))))
                    .andExpect(status().isCreated());
        }
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

        long small = statementsFor(tokens, 5, stats);
        long large = statementsFor(tokens, 10, stats);

        assertThat(large).as("statements for size=10 vs size=5").isEqualTo(small);
        assertThat(small).as("one page query + one count query").isLessThanOrEqualTo(2);
    }

    @Test
    void queryCountStaysFlatWhenManualAndCatalogCopiesAreMixed() throws Exception {
        JsonNode tokens = registerAndLogin("n2@example.com", "password123");
        for (int i = 1; i <= 12; i++) {
            if (i % 2 == 0) {
                catalog.put(valid(i), new BookDetails("Book " + i, "Author " + i, null));
            } // odd ISBNs are unknown to the catalog, so they become manual copies without a book row
            mvc.perform(post("/api/copies").header("Authorization", bearer(tokens)).contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("isbn", valid(i), "grade", "GOOD",
                                    "title", "Typed " + i, "author", "Me"))))
                    .andExpect(status().isCreated());
        }
        assertThat(jdbc.queryForObject("select count(*) from book", Integer.class)).isEqualTo(6);
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

        long small = statementsFor(tokens, 5, stats);
        long large = statementsFor(tokens, 10, stats);

        assertThat(large).as("statements for size=10 vs size=5").isEqualTo(small);
        assertThat(small).isLessThanOrEqualTo(2);
    }

    @Test
    void databaseRejectsInconsistentManualDetails() throws Exception {
        addFox(registerAndLogin("md@example.com", "password123"));

        assertThatThrownBy(() -> jdbc.update("update copy set title = 'T'"))
                .as("title without author").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update copy set title = '  ', author = 'A'"))
                .as("blank title").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update copy set isbn13 = '123'"))
                .as("non-ISBN-13 value").isInstanceOf(DataIntegrityViolationException.class);
        // a copy for an ISBN with no book row is legal (that is what a manual copy is)
        assertThat(jdbc.update("insert into copy (id, owner_id, isbn13, grade, status, title, author, created_at, updated_at, version) "
                + "select gen_random_uuid(), owner_id, '9790000000001', 'GOOD', 'UNLISTED', 'T', 'A', now(), now(), 0 from copy limit 1"))
                .isEqualTo(1);
    }

    private long statementsFor(JsonNode tokens, int size, Statistics stats) throws Exception {
        stats.clear();
        mvc.perform(get("/api/copies").param("size", String.valueOf(size)).header("Authorization", bearer(tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(size))
                .andExpect(jsonPath("$.content[0].title").exists());
        return stats.getPrepareStatementCount();
    }

    @Test
    void concurrentFirstAddsOfTheSameIsbnCreateOneBookAndAllCopies() throws Exception {
        int users = 8;
        List<JsonNode> tokens = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            tokens.add(registerAndLogin("c" + i + "@example.com", "password123"));
        }
        catalog.put(FOX, new BookDetails("Fantastic Mr Fox", "Roald Dahl", null));

        ExecutorService pool = Executors.newFixedThreadPool(users);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (JsonNode t : tokens) {
            results.add(pool.submit(() -> {
                go.await();
                return mvc.perform(post("/api/copies").header("Authorization", bearer(t))
                                .contentType(APPLICATION_JSON)
                                .content("{\"isbn\":\"" + FOX + "\",\"grade\":\"GOOD\"}"))
                        .andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        for (Future<Integer> f : results) {
            assertThat(f.get(30, TimeUnit.SECONDS)).isEqualTo(201);
        }
        pool.shutdown();

        assertThat(jdbc.queryForObject("select count(*) from book", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from copy", Integer.class)).isEqualTo(users);
    }

    @Test
    void staleCopyVersionIsRejectedByOptimisticLocking() throws Exception {
        addFox(registerAndLogin("v1@example.com", "password123"));
        UUID id = jdbc.queryForObject("select id from copy", UUID.class);

        Copy stale = copies.findById(id).orElseThrow();                        // detached: version 0
        jdbc.update("update copy set version = version + 1 where id = ?", id); // someone else wrote meanwhile
        stale.changeGrade(ConditionGrade.POOR, Instant.now());

        assertThatThrownBy(() -> copies.saveAndFlush(stale)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(jdbc.queryForObject("select grade from copy where id = ?", String.class, id)).isEqualTo("GOOD");
    }

    @Test
    void databaseRejectsWhatTheCodeShouldNeverWrite() throws Exception {
        addFox(registerAndLogin("db@example.com", "password123"));

        assertThatThrownBy(() -> jdbc.update("update copy set status = 'LOST'")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update copy set grade = 'MINT'")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into book (isbn13, title, author, created_at) values ('123', 'T', 'A', now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into book (isbn13, title, author, cover_url, created_at) "
                + "values ('9780306406157', 'T', 'A', 'http://x', now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into copy (id, owner_id, isbn13, grade, status, created_at, updated_at, version) "
                + "values (gen_random_uuid(), gen_random_uuid(), ?, 'GOOD', 'UNLISTED', now(), now(), 0)", FOX))
                .isInstanceOf(DataIntegrityViolationException.class); // owner does not exist
    }

    @Test
    void deletingAUserRemovesTheirCopiesButKeepsTheSharedBook() throws Exception {
        addFox(registerAndLogin("gone@example.com", "password123"));

        jdbc.update("delete from users where email = 'gone@example.com'");

        assertThat(jdbc.queryForObject("select count(*) from copy", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from book", Integer.class)).isEqualTo(1);
    }

    @Test
    void swaggerDocumentsTheLibraryEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/books/lookup']").exists())
                .andExpect(jsonPath("$.paths['/api/copies']").exists())
                .andExpect(jsonPath("$.paths['/api/copies/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/copies/{id}/list']").exists())
                .andExpect(jsonPath("$.paths['/api/copies/{id}/unlist']").exists())
                .andExpect(jsonPath("$.paths['/api/condition-grades']").exists());
    }
}
