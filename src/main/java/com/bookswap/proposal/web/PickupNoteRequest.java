package com.bookswap.proposal.web;

/** The shared note about where and when to meet; blank or missing clears it. */
public record PickupNoteRequest(String note) {}
