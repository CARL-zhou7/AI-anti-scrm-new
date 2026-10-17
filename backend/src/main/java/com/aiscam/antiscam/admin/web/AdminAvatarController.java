package com.aiscam.antiscam.admin.web;

import com.aiscam.antiscam.admin.web.dto.AvatarUploadView;
import com.aiscam.antiscam.common.api.ApiResponse;
import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.content.avatar.*;
import com.aiscam.antiscam.content.page.PageQuery;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/anti.scam/v1/admin/avatars")
public class AdminAvatarController {
    private final AvatarStore avatars;
    public AdminAvatarController(AvatarStore avatars) { this.avatars = avatars; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AvatarUploadView> upload(MultipartHttpServletRequest request) throws IOException, ServletException {
        if (request.getQueryString() != null || request.getParts().size() != 1
                || request.getFiles("file").size() != 1 || request.getFileMap().size() != 1) {
            throw ApiException.field("file", "VALUE_INVALID", "仅允许上传一个file字段");
        }
        var file = request.getFile("file");
        if (file == null) throw ApiException.field("file", "REQUIRED", "请选择头像文件");
        if (file.getSize() > 5 * 1024 * 1024) throw ApiException.fileTooLarge();
        String storedKey = avatars.store(file.getBytes());
        String key = AvatarKey.publicKey(storedKey);
        return ApiResponse.ok(new AvatarUploadView(key, "/anti.scam/v1/admin/avatars/" + key,
                storedKey.endsWith(".png") ? "image/png" : "image/jpeg", file.getSize()));
    }

    @GetMapping({"/{key}", "", "/"})
    public ResponseEntity<byte[]> preview(@PathVariable(required = false) String key,
            @RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        var resource = avatars.load(avatars.storedKey(key));
        return ResponseEntity.ok().header("Cache-Control", "private, no-store")
                .header("X-Content-Type-Options", "nosniff").header("Content-Type", resource.mediaType()).body(resource.bytes());
    }
}
