package com.bookswap.proposal.domain;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** Which side of the inbox to list: proposals made to me, or proposals I made. */
public enum ProposalRole {
    INCOMING, OUTGOING;

    public static ProposalRole parse(String value) {
        if (value != null) {
            for (ProposalRole role : values()) {
                if (role.name().equalsIgnoreCase(value.strip())) {
                    return role;
                }
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "role must be incoming or outgoing");
    }
}
