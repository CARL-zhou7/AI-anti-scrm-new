package com.aiscam.antiscam.admin.web.dto;

import jakarta.validation.constraints.*;
import com.aiscam.antiscam.content.domain.*;
import com.aiscam.antiscam.common.web.RequestContent;

public record VictimWriteRequest(
        @NotBlank(message = "此项不能为空") String name,
        @NotNull(message = "此项不能为空") @Min(value = 1, message = "年龄须为1至65535") @Max(value = 65535, message = "年龄须为1至65535") Integer age,
        @NotBlank(message = "此项不能为空") String gender,
        @NotBlank(message = "此项不能为空") String identity,
        @NotBlank(message = "此项不能为空") String description,
        @NotBlank(message = "此项不能为空") String contextTime,
        @NotBlank(message = "此项不能为空") String contextPlace,
        @NotBlank(message = "此项不能为空") String currentState,
        @NotNull(message = "此项不能为空") Level antiFraudAwareness,
        @NotNull(message = "此项不能为空") Level riskTendency,
        @NotBlank(message = "此项不能为空") String personalityTraits,
        @NotBlank(message = "此项不能为空") @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "头像标识格式不正确") String avatarKey,
        @NotBlank(message = "此项不能为空") @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "头像标识格式不正确") String speakingAvatarKey) {
    public void validateText() {
        RequestContent.checkField("name", name, 64);
        RequestContent.checkField("gender", gender, 16);
        RequestContent.checkField("identity", identity, 128);
        RequestContent.checkField("description", description, RequestContent.MAX_TEXT_CHARS);
        RequestContent.checkField("contextTime", contextTime, 128);
        RequestContent.checkField("contextPlace", contextPlace, 128);
        RequestContent.checkField("currentState", currentState, RequestContent.MAX_TEXT_CHARS);
        RequestContent.checkField("personalityTraits", personalityTraits, RequestContent.MAX_TEXT_CHARS);
    }
    public VictimProfileValues values(String storedAvatarKey, String storedSpeakingAvatarKey) {
        return new VictimProfileValues(name, age, gender, identity, description, contextTime, contextPlace, currentState, antiFraudAwareness, riskTendency, personalityTraits, storedAvatarKey, storedSpeakingAvatarKey);
    }
}
