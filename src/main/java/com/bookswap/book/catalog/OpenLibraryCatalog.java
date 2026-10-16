package com.bookswap.book.catalog;

import com.bookswap.book.domain.Isbn;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

public class OpenLibraryCatalog implements BookCatalog {

    private static final Logger log = LoggerFactory.getLogger(OpenLibraryCatalog.class);
    private static final int MAX_TEXT = 500;
    private static final int MAX_COVER_URL = 1000;
    static final String UNKNOWN_AUTHOR = "Unknown author";

    private final RestClient client;

    public OpenLibraryCatalog(RestClient client) {
        this.client = client;
    }

    @Override
    public Optional<BookDetails> lookup(Isbn isbn) {
        JsonNode body;
        try {
            // Only the normalized, digits-only ISBN is ever placed in the URL.
            body = client.get()
                    .uri("/api/books?bibkeys=ISBN:{isbn}&format=json&jscmdata=data", isbn.value())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Open Library lookup failed for {}: {}", isbn, e.toString());
            throw new CatalogUnavailableException(e);
        }
        return parse(isbn, body);
    }

    private Optional<BookDetails> parse(Isbn isbn, JsonNode body) {
        if (body == null || !body.isObject()) {
            throw malformed(isbn, "response is not a JSON object");
        }
        JsonNode entry = body.get("ISBN:" + isbn.value());
        if (entry == null || entry.isNull()) {
            return Optional.empty();
        }
        if (!entry.isObject()) {
            throw malformed(isbn, "entry is not an object");
        }
        String title = clean(text(entry.get("title")));
        if (title == null) {
            throw malformed(isbn, "entry has no title");
        }
        String author = null;
        JsonNode authors = entry.get("authors");
        if (authors != null && authors.isArray() && !authors.isEmpty()) {
            author = clean(text(authors.get(0).get("name")));
        }
        return Optional.of(new BookDetails(title, author != null ? author : UNKNOWN_AUTHOR, cover(entry.get("cover"))));
    }

    private static String cover(JsonNode cover) {
        if (cover == null || !cover.isObject()) {
            return null;
        }
        String url = text(cover.get("medium"));
        if (url == null || !url.startsWith("https://") || url.length() > MAX_COVER_URL || url.indexOf('\u0000') >= 0) {
            return null;
        }
        return url;
    }

    private static String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    /** Catalog text goes into the DB: drop NULs (PostgreSQL rejects them), trim, cap the length, reject blanks. */
    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String cleaned = s.replace("\u0000", "").strip();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > MAX_TEXT ? cleaned.substring(0, MAX_TEXT) : cleaned;
    }

    private static CatalogUnavailableException malformed(Isbn isbn, String why) {
        log.warn("Open Library returned an unusable payload for {}: {}", isbn, why);
        return new CatalogUnavailableException();
    }
}
