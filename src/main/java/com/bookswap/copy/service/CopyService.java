package com.bookswap.copy.service;

import com.bookswap.book.domain.Book;
import com.bookswap.book.domain.Isbn;
import com.bookswap.book.service.BookService;
import com.bookswap.common.web.PageResponse;
import com.bookswap.copy.domain.ConditionGrade;
import com.bookswap.copy.domain.Copy;
import com.bookswap.copy.domain.CopyNotFoundException;
import com.bookswap.copy.domain.CopyStatus;
import com.bookswap.copy.repository.CopyRepository;
import com.bookswap.copy.web.AddCopyRequest;
import com.bookswap.copy.web.CopyResponse;
import com.bookswap.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Every method takes the authenticated principal's id as {@code ownerId}; a copy that is not the caller's is
 * indistinguishable from a missing one. Step 4 will add "cancel proposed proposals" calls to the grade,
 * unlist and remove paths here: this is the single place those rules will live.
 */
@Service
public class CopyService {
    static final int MAX_PAGE_SIZE = 100;

    private final CopyRepository copies;
    private final BookService bookService;
    private final UserService userService;
    private final Clock clock;

    public CopyService(CopyRepository copies, BookService bookService, UserService userService, Clock clock) {
        this.copies = copies;
        this.bookService = bookService;
        this.userService = userService;
        this.clock = clock;
    }

    /** Not @Transactional: resolving the book may call the catalog over HTTP. */
    public CopyResponse add(UUID ownerId, AddCopyRequest request) {
        Isbn isbn = Isbn.parse(request.isbn());
        Book book = bookService.resolveForCopy(isbn, request.title(), request.author());
        return CopyResponse.from(copies.save(new Copy(ownerId, book, request.grade(), clock.instant())));
    }

    @Transactional(readOnly = true)
    public PageResponse<CopyResponse> list(UUID ownerId, CopyStatus status, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // Spring Data computes the offset as page * size and rejects it above Integer.MAX_VALUE (a 500 otherwise).
        int pageNumber = Math.min(Math.max(page, 0), Integer.MAX_VALUE / pageSize);
        Pageable pageable = PageRequest.of(pageNumber, pageSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        Page<Copy> result = status == null
                ? copies.findByOwnerId(ownerId, pageable)
                : copies.findByOwnerIdAndStatus(ownerId, status, pageable);
        return PageResponse.from(result.map(CopyResponse::from));
    }

    @Transactional(readOnly = true)
    public CopyResponse get(UUID ownerId, UUID id) {
        return CopyResponse.from(load(ownerId, id));
    }

    @Transactional
    public CopyResponse changeGrade(UUID ownerId, UUID id, ConditionGrade grade) {
        Copy copy = load(ownerId, id);
        copy.changeGrade(grade, clock.instant());
        return CopyResponse.from(copy);
    }

    @Transactional
    public CopyResponse listCopy(UUID ownerId, UUID id) {
        Copy copy = load(ownerId, id);
        copy.list(userService.isProfileComplete(ownerId), clock.instant());
        return CopyResponse.from(copy);
    }

    @Transactional
    public CopyResponse unlistCopy(UUID ownerId, UUID id) {
        Copy copy = load(ownerId, id);
        copy.unlist(clock.instant());
        return CopyResponse.from(copy);
    }

    @Transactional
    public void remove(UUID ownerId, UUID id) {
        Copy copy = load(ownerId, id);
        copy.requireRemovable();
        copies.delete(copy);
    }

    private Copy load(UUID ownerId, UUID id) {
        return copies.findByIdAndOwnerId(id, ownerId).orElseThrow(CopyNotFoundException::new);
    }
}
