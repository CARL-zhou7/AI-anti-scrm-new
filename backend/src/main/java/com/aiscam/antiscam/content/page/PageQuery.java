package com.aiscam.antiscam.content.page;

import com.aiscam.antiscam.common.error.ApiException;
import java.util.Set;
import org.springframework.util.MultiValueMap;

public record PageQuery(String cursor, int limit) {
    public static PageQuery from(MultiValueMap<String, String> query) {
        validate(query, Set.of("cursor", "limit", "enabled"));
        if (query.containsKey("enabled") && !"true".equals(query.getFirst("enabled"))) {
            throw ApiException.field("enabled", "VALUE_INVALID", "只允许查询可用画像");
        }
        int limit = 20;
        if (query.containsKey("limit")) {
            String raw = query.getFirst("limit");
            try {
                if (raw == null || !raw.matches("[0-9]+")) throw new NumberFormatException();
                limit = Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                throw ApiException.field("limit", "TYPE_INVALID", "分页大小必须为整数");
            }
            if (limit < 1 || limit > 100) throw ApiException.field("limit", "VALUE_INVALID", "分页大小须为1至100");
        }
        return new PageQuery(query.getFirst("cursor"), limit);
    }

    public static void validate(MultiValueMap<String, String> query, Set<String> allowed) {
        query.forEach((key, values) -> {
            if (!allowed.contains(key)) throw ApiException.field(key, "UNKNOWN_FIELD", "不支持此查询参数");
            if (values.size() != 1) throw ApiException.field(key, "VALUE_INVALID", "查询参数不能重复");
        });
    }

    public static long id(String value) {
        if (!value.matches("[1-9][0-9]{0,19}")) {
            throw ApiException.field("id", "VALUE_INVALID", "资源标识格式不正确");
        }
        try { return Long.parseLong(value); }
        // shortcut: IDs use the existing Long convention; adopt unsigned mapping before IDs exceed Long.MAX_VALUE.
        catch (NumberFormatException e) { throw ApiException.notFound(); }
    }
}
