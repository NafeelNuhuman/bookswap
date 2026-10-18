package com.bookswap.discovery.service;

import com.bookswap.common.web.PageResponse;
import com.bookswap.common.web.Paging;
import com.bookswap.discovery.repository.DiscoveryRepository;
import com.bookswap.discovery.repository.DiscoveryRow;
import com.bookswap.discovery.web.CopyCard;
import com.bookswap.copy.domain.Copy;
import com.bookswap.user.service.UserService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/** Every method takes the authenticated caller's id; the caller's own copies are never returned. */
@Service
public class DiscoveryService {
    static final int MAX_CITY_LENGTH = 100;

    private final DiscoveryRepository repository;
    private final UserService userService;

    public DiscoveryService(DiscoveryRepository repository, UserService userService) {
        this.repository = repository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public PageResponse<CopyCard> feed(UUID me, String city, int page, int size) {
        String cityLower = resolveCity(me, city);
        return PageResponse.from(repository.feed(me, cityLower, pageable(page, size)).map(DiscoveryService::toCard));
    }

    /** The city to search in, trimmed and lower-cased: the explicit one, else the caller's profile city. */
    String resolveCity(UUID me, String cityParam) {
        String city = cityParam == null ? "" : cityParam.strip();
        if (city.isEmpty()) {
            String own = userService.getProfile(me).city();
            city = own == null ? "" : own.strip();
        }
        if (city.isEmpty()) {
            throw new LocationRequiredException();
        }
        if (city.length() > MAX_CITY_LENGTH || city.indexOf('\u0000') >= 0) {
            throw new InvalidLocationException();
        }
        return city.toLowerCase(Locale.ROOT);
    }

    static Pageable pageable(int page, int size) {
        return Paging.pageable(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
    }

    static CopyCard toCard(DiscoveryRow row) {
        return new CopyCard(row.copyId(), row.isbn13(), row.title(), row.author(), row.coverUrl(), row.grade(),
                row.fromCatalog() ? Copy.DetailsSource.CATALOG : Copy.DetailsSource.MANUAL,
                new CopyCard.Owner(row.ownerName(), row.ownerCity()));
    }
}
