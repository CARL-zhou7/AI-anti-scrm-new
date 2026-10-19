package com.aiscam.antiscam.content.page;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.common.web.SignedCursor;
import org.springframework.stereotype.Component;

@Component
public class ContentCursor {
    public static final String SCOPE_SCAMMERS = "SCAMMER|enabled=true|idAsc";
    public static final String SCOPE_VICTIMS = "VICTIM|enabled=true|idAsc";
    private final SignedCursor signed = new SignedCursor();

    public String encode(String scope, String account, long lastId) {
        if (lastId < 1) throw ApiException.invalidCursor();
        return signed.encode(scope, account, Long.toString(lastId));
    }

    public long decode(String scope, String account, String cursor) {
        if (cursor == null || cursor.isEmpty()) return 0;
        try {
            String id = signed.decode(scope, account, cursor).orElseThrow(ApiException::invalidCursor);
            if (!id.matches("[1-9][0-9]{0,18}")) throw ApiException.invalidCursor();
            return Long.parseLong(id);
        } catch (IllegalArgumentException e) {
            throw ApiException.invalidCursor();
        }
    }

}
