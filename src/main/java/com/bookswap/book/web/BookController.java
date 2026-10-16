package com.bookswap.book.web;

import com.bookswap.book.domain.Isbn;
import com.bookswap.book.service.BookService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    /** Looks a book up for confirmation; saves nothing. */
    @GetMapping("/lookup")
    public LookupResponse lookup(@RequestParam("isbn") String isbn) {
        return bookService.lookup(Isbn.parse(isbn));
    }
}
