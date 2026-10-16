package com.bookswap.book.catalog;

import com.bookswap.book.domain.Isbn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenLibraryCatalogTest {
    private static final String URL = "https://ol.test/api/books?bibkeys=ISBN:9780140328721&format=json&jscmd=data";
    private static final Isbn ISBN = Isbn.parse("9780140328721");

    private MockRestServiceServer server;
    private OpenLibraryCatalog catalog;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ol.test");
        server = MockRestServiceServer.bindTo(builder).build();
        catalog = new OpenLibraryCatalog(builder.build());
    }

    private void respond(String json) {
        server.expect(requestTo(URL)).andExpect(method(GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    void parsesTitleFirstAuthorAndMediumCover() {
        respond("""
                {"ISBN:9780140328721":{"title":"Fantastic Mr Fox",
                 "authors":[{"name":"Roald Dahl"},{"name":"Quentin Blake"}],
                 "cover":{"small":"https://c/s.jpg","medium":"https://c/m.jpg","large":"https://c/l.jpg"}}}""");
        Optional<BookDetails> d = catalog.lookup(ISBN);
        assertThat(d).contains(new BookDetails("Fantastic Mr Fox", "Roald Dahl", "https://c/m.jpg"));
        server.verify();
    }

    @Test
    void emptyObjectMeansNotFound() {
        respond("{}");
        assertThat(catalog.lookup(ISBN)).isEmpty();
    }

    @Test
    void missingCoverGivesNullCover() {
        respond("""
                {"ISBN:9780140328721":{"title":"T","authors":[{"name":"A"}]}}""");
        assertThat(catalog.lookup(ISBN)).contains(new BookDetails("T", "A", null));
    }

    @Test
    void missingAuthorsFallBackToUnknownAuthor() {
        respond("""
                {"ISBN:9780140328721":{"title":"T"}}""");
        assertThat(catalog.lookup(ISBN)).contains(new BookDetails("T", "Unknown author", null));
    }

    @Test
    void nonHttpsCoverIsDropped() {
        respond("""
                {"ISBN:9780140328721":{"title":"T","authors":[{"name":"A"}],"cover":{"medium":"http://c/m.jpg"}}}""");
        assertThat(catalog.lookup(ISBN).orElseThrow().coverUrl()).isNull();
    }

    @Test
    void overLongCoverUrlIsDropped() {
        respond("{\"ISBN:9780140328721\":{\"title\":\"T\",\"authors\":[{\"name\":\"A\"}],"
                + "\"cover\":{\"medium\":\"https://c/" + "x".repeat(1000) + "\"}}}");
        assertThat(catalog.lookup(ISBN).orElseThrow().coverUrl()).isNull();
    }

    @Test
    void nulCharactersAreStrippedAndLongStringsTruncated() {
        respond("{\"ISBN:9780140328721\":{\"title\":\"Fa\\u0000ntastic " + "x".repeat(600) + "\","
                + "\"authors\":[{\"name\":\"Ro\\u0000ald\"}]}}");
        BookDetails d = catalog.lookup(ISBN).orElseThrow();
        assertThat(d.title()).doesNotContain("\u0000").hasSize(500).startsWith("Fantastic ");
        assertThat(d.author()).isEqualTo("Roald");
    }

    @Test
    void entryWithoutUsableTitleIsTreatedAsMalformed() {
        respond("""
                {"ISBN:9780140328721":{"authors":[{"name":"A"}]}}""");
        assertThatThrownBy(() -> catalog.lookup(ISBN)).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void garbageBodiesAreUnavailableNotCrashes() {
        for (String body : new String[]{"not json at all", "[]", "\"str\"", "{\"ISBN:9780140328721\":42}"}) {
            server.reset();
            respond(body);
            assertThatThrownBy(() -> catalog.lookup(ISBN))
                    .as(body).isInstanceOf(CatalogUnavailableException.class);
        }
    }

    @Test
    void serverErrorsAreUnavailable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> catalog.lookup(ISBN)).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void timeoutsAreUnavailable() {
        server.expect(requestTo(URL)).andRespond(request -> { throw new SocketTimeoutException("read timed out"); });
        assertThatThrownBy(() -> catalog.lookup(ISBN)).isInstanceOf(CatalogUnavailableException.class);
    }
}
