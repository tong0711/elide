/*
 * Copyright 2015, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package com.yahoo.elide.core.pagination;

import com.yahoo.elide.annotation.Paginate;
import com.yahoo.elide.core.exceptions.InvalidValueException;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.mutable.MutableBoolean;

import javax.ws.rs.core.MultivaluedMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates the pagination strategy.
 */

@ToString
public class Pagination {

    /**
     * Denotes the internal field names for paging.
     */
    public enum PaginationKey { offset, limit, page, totals }

    public static final int DEFAULT_PAGE_SIZE = 500;
    public static final int MAX_PAGE_SIZE = 10000;

    private static final Pagination DEFAULT_PAGINATION = new Pagination(new HashMap<>());

    // For requesting total pages/records be included in the response
    public static String PAGE_TOTALS_KEY = "page[totals]";

    public static final Map<String, PaginationKey> PAGE_KEYS = new HashMap<>();
    static {
        PAGE_KEYS.put("page[size]", PaginationKey.limit);
        PAGE_KEYS.put("page[limit]", PaginationKey.limit);
        PAGE_KEYS.put("page[number]", PaginationKey.page);
        PAGE_KEYS.put("page[offset]", PaginationKey.offset);
        PAGE_KEYS.put(PAGE_TOTALS_KEY, PaginationKey.totals);
    }

    // For holding the page query parameters until they can be evaluated
    private Map<PaginationKey, Integer> pageData;

    @Getter
    private int offset;

    @Getter
    private int limit;

    @Getter
    private boolean generateTotals;


    private Pagination(Map<PaginationKey, Integer> pageData) {
        this.pageData = pageData;
    }

    /**
     * Know if this is the default instance.
     * @return The default pagination values.
     */
    public boolean isDefaultInstance() {
        return pageData.isEmpty();
    }

    /**
     * Alias for isDefault.
     * @return true if there are no pagination rules
     */
    public boolean isEmpty() {
        return isDefaultInstance();
    }

    /**
     * Given json-api paging params, generate page and pageSize values from query params.
     * @param queryParams The page queryParams (ImmuatableMultiValueMap).
     * @return The new Page object.
     */
    public static Pagination parseQueryParams(final MultivaluedMap<String, String> queryParams)
            throws InvalidValueException {
        final Map<PaginationKey, Integer> pageData = new HashMap<>();
        final MutableBoolean pageTotalsRequested = new MutableBoolean(false);

        queryParams.entrySet()
                .forEach(paramEntry -> {
                    final String queryParamKey = paramEntry.getKey();
                    if (PAGE_KEYS.containsKey(queryParamKey)) {
                        PaginationKey paginationKey = PAGE_KEYS.get(queryParamKey);
                        if (queryParamKey.equals(PAGE_TOTALS_KEY)) {
                            // page[totals] is a valueless parameter, use value of 0 just so that its presence can
                            // be recorded in the map
                            pageData.put(paginationKey, 0);
                        }
                        else {
                            final String value = paramEntry.getValue().get(0);
                            try {
                                int intValue = Integer.parseInt(value, 10);
                                if (paginationKey == PaginationKey.limit && intValue <= 0) {
                                    throw new InvalidValueException(
                                            "page[size] and page[limit] must contain positive values.");
                                }
                                pageData.put(paginationKey, intValue);
                            } catch (ClassCastException e) {
                                throw new InvalidValueException("page values must be integers");
                            }
                        }
                    } else if (queryParamKey.startsWith("page[")) {
                        throw new InvalidValueException("Invalid Pagination Parameter. Accepted values are page[number]"
                                + ",page[size],page[offset],page[limit],page[totals]");
                    }
                });
        return new Pagination(pageData).evaluate(null);
    }

    /**
     * Evaluates the pagination variables. Uses the Paginate annotation if it has been set for the entity to be
     * queried.
     * @param entityClass
     */
    public Pagination evaluate(final Class entityClass) {
        Paginate paginate = entityClass != null ? (Paginate) entityClass.getAnnotation(Paginate.class) : null;

        int defaultLimit = paginate != null ? paginate.defaultLimit() : this.DEFAULT_PAGE_SIZE;
        int maxLimit = paginate != null ? paginate.maxLimit() : this.MAX_PAGE_SIZE;

        limit = pageData.containsKey(PaginationKey.limit) ? pageData.get(PaginationKey.limit) : defaultLimit;
        limit = Math.min(maxLimit, limit);

        Integer page = pageData.get(PaginationKey.page);
        if (page != null) {
            pageData.put(PaginationKey.offset, (page > 0 ? page - 1 : 0) * limit);
        }

        offset = pageData.getOrDefault(PaginationKey.offset, 0);

        generateTotals = pageData.containsKey(PaginationKey.totals) && (paginate == null || paginate.countable());

        return this;
    }

    /**
     * Default Instance.
     * @return The default instance.
     */
    public static Pagination getDefaultPagination() {
        return DEFAULT_PAGINATION;
    }
}
