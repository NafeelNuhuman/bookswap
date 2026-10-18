package com.bookswap.copy.service;

import com.bookswap.book.domain.Isbn;
import com.bookswap.book.service.BookService;
import com.bookswap.common.web.PageResponse;
import com.bookswap.common.web.Paging;
import com.bookswap.copy.domain.ConditionGrade;
import com.bookswap.copy.domain.Copy;
import com.bookswap.copy.domain.CopyNotFoundException;
import com.bookswap.copy.domain.CopyStatus;
import com.bookswap.copy.repository.CopyRepository;
import com.bookswap.copy.web.AddCopyRequest;
import com.bookswap.copy.web.CopyResponse;
import com.bookswap.proposal.service.ProposalService;
import com.bookswap.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Every method takes the authenticated principal's id as {@code ownerId}; a copy that is not the caller's is
 * indistinguishable from a missing one. Changing a copy's grade, unlisting it or removing it cancels the open
 * proposals that contain it, in the same transaction: this is the single place that rule lives.
 */
@Service
public class CopyService {
    private final CopyRepository copies;
    private final BookService bookService;
    private final UserService userService;
    private final ProposalService proposals;
    private final Clock clock;

    public CopyService(CopyRepository copies, BookService bookService, UserService userService,
                       ProposalService proposals, Clock clock) {
        this.copies = copies;
        this.bookService = bookService;
        this.userService = userService;
        this.proposals = proposals;
        this.clock = clock;
    }

    /** Not @Transactional: resolving the book may call the catalog over HTTP. */
    public CopyResponse add(UUID ownerId, AddCopyRequest request) {
        Isbn isbn = Isbn.parse(request.isbn());
        BookService.BookResolution resolved = bookService.resolveForCopy(isbn, request.title(), request.author());
        Copy copy = resolved.book() != null
                ? new Copy(ownerId, resolved.book(), request.grade(), clock.instant())
                : Copy.withManualDetails(ownerId, resolved.isbn13(), resolved.manualTitle(), resolved.manualAuthor(),
                        request.grade(), clock.instant());
        return CopyResponse.from(copies.save(copy));
    }

    @Transactional(readOnly = true)
    public PageResponse<CopyResponse> list(UUID ownerId, CopyStatus status, int page, int size) {
        Pageable pageable = Paging.pageable(page, size,
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
        boolean changed = copy.getGrade() != grade;
        copy.changeGrade(grade, clock.instant());
        if (changed) {
            proposals.cancelOpenForCopy(copy.getId(), clock.instant());
        }
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
        proposals.cancelOpenForCopy(copy.getId(), clock.instant());
        return CopyResponse.from(copy);
    }

    @Transactional
    public void remove(UUID ownerId, UUID id) {
        Copy copy = load(ownerId, id);
        copy.requireRemovable();
        proposals.cancelOpenForCopy(copy.getId(), clock.instant());
        copies.delete(copy);
    }

    private Copy load(UUID ownerId, UUID id) {
        return copies.findByIdAndOwnerId(id, ownerId).orElseThrow(CopyNotFoundException::new);
    }
}
