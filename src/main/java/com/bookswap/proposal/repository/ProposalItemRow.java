package com.bookswap.proposal.repository;

import com.bookswap.copy.domain.ConditionGrade;
import com.bookswap.copy.domain.Copy;
import com.bookswap.proposal.domain.Side;

import java.util.UUID;

/** One proposal item with its stored book details, read in a single query for a whole page of proposals. */
public record ProposalItemRow(UUID proposalId, Side side, UUID copyId, ConditionGrade gradeAtProposal, String isbn13,
                              String title, String author, String coverUrl, Copy.DetailsSource detailsSource) {}
