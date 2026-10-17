package com.aiscam.antiscam.admin.web.dto;

import jakarta.validation.constraints.*;
import com.aiscam.antiscam.content.domain.*;
import com.aiscam.antiscam.common.web.RequestContent;

public record VictimUpdateRequest(
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
        @NotNull(message = "请提供内容版本") @Min(value = 0, message = "版本不能为负数") Integer version) {
    public VictimWriteRequest content() {
        return new VictimWriteRequest(name, age, gender, identity, description, contextTime, contextPlace, currentState, antiFraudAwareness, riskTendency, personalityTraits, avatarKey);
    }
}
