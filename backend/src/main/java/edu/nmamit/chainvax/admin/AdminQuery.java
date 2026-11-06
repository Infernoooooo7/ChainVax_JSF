package edu.nmamit.chainvax.admin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Server side of react-admin's {@code ra-data-simple-rest} list contract
 * (https://github.com/marmelab/react-admin/tree/master/packages/ra-data-simple-rest):
 * {@code GET /resource?sort=["field","ASC"]&range=[0,24]&filter={"q":"text","field":value,"id":[1,2]}}
 * answered with the matching page and a {@code Content-Range: resource 0-24/319} header.
 * Filtering, sorting and paging run in memory - the admin tables of this demo hold at most a few
 * thousand rows. Every row must expose an {@code id}.
 */
@Component
public class AdminQuery {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<Object>> LIST = new TypeReference<>() {
    };

    private final ObjectMapper mapper;

    public AdminQuery(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Converts a view record to a JSON-like map (dates become ISO strings). */
    public Map<String, Object> row(Object view) {
        return mapper.convertValue(view, MAP);
    }

    @SuppressWarnings("unchecked")
    public ResponseEntity<List<Map<String, Object>>> list(String resource, List<?> views, String filter, String range,
            String sort) {
        List<Map<String, Object>> rows = new ArrayList<>(views.size());
        for (Object view : views) {
            rows.add(view instanceof Map<?, ?> map ? (Map<String, Object>) map : row(view));
        }
        Map<String, Object> criteria = blank(filter) ? Map.of() : mapper.readValue(filter, MAP);
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (matches(row, criteria)) {
                matched.add(row);
            }
        }
        if (!blank(sort)) {
            List<Object> s = mapper.readValue(sort, LIST);
            String field = String.valueOf(s.get(0));
            Comparator<Map<String, Object>> byField = (a, b) -> compare(a.get(field), b.get(field));
            matched.sort(s.size() > 1 && "DESC".equalsIgnoreCase(String.valueOf(s.get(1))) ? byField.reversed() : byField);
        }
        int total = matched.size();
        int start = 0;
        int end = total - 1;
        if (!blank(range)) {
            List<Object> r = mapper.readValue(range, LIST);
            start = Math.max(0, ((Number) r.get(0)).intValue());
            end = Math.min(total - 1, ((Number) r.get(1)).intValue());
        }
        List<Map<String, Object>> page = start > end ? List.of() : matched.subList(start, end + 1);
        String contentRange = resource + " " + (page.isEmpty() ? "0-0" : start + "-" + end) + "/" + total;
        return ResponseEntity.ok()
                .header("Content-Range", contentRange)
                .header("Access-Control-Expose-Headers", "Content-Range")
                .body(page);
    }

    private static boolean matches(Map<String, Object> row, Map<String, Object> criteria) {
        for (Map.Entry<String, Object> criterion : criteria.entrySet()) {
            Object wanted = criterion.getValue();
            if (wanted == null || "".equals(wanted)) {
                continue;
            }
            if ("q".equals(criterion.getKey())) { // free-text search across all columns
                String q = wanted.toString().toLowerCase(Locale.ROOT);
                if (row.values().stream().noneMatch(v -> v != null && v.toString().toLowerCase(Locale.ROOT).contains(q))) {
                    return false;
                }
                continue;
            }
            Object actual = row.get(criterion.getKey());
            if (wanted instanceof Collection<?> anyOf) { // getMany: {"id":[1,2,3]}
                if (anyOf.stream().noneMatch(w -> same(actual, w))) {
                    return false;
                }
            }
            else if (!same(actual, wanted)) {
                return false;
            }
        }
        return true;
    }

    private static boolean same(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Number x && b instanceof Number y) {
            return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString())) == 0;
        }
        return a.toString().equalsIgnoreCase(b.toString());
    }

    private static int compare(Object a, Object b) {
        if (a == b) {
            return 0;
        }
        if (a == null) {
            return 1; // nulls last
        }
        if (b == null) {
            return -1;
        }
        if (a instanceof Number x && b instanceof Number y) {
            return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString()));
        }
        if (a instanceof Boolean x && b instanceof Boolean y) {
            return Boolean.compare(x, y);
        }
        return a.toString().compareToIgnoreCase(b.toString());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
