package com.aiscam.antiscam.admin.web.dto;

import jakarta.validation.constraints.*;
import com.aiscam.antiscam.content.domain.*;
import com.aiscam.antiscam.common.web.RequestContent;

public record ScammerUpdateRequest(
        @NotBlank(message = "此项不能为空") String name,
        @NotNull(message = "此项不能为空") @Min(value = 1, message = "年龄须为1至65535") @Max(value = 65535, message = "年龄须为1至65535") Integer age,
        @NotBlank(message = "此项不能为空") String gender,
        @NotBlank(message = "此项不能为空") String identity,
        @NotBlank(message = "此项不能为空") String description,
        @NotBlank(message = "此项不能为空") String contextTime,
        @NotBlank(message = "此项不能为空") String contextPlace,
        @NotBlank(message = "此项不能为空") String currentState,
        @NotNull(message = "此项不能为空") Level fraudExperience,
        @NotNull(message = "此项不能为空") Level speechSkill,
        @NotNull(message = "此项不能为空") Level manipulationSkill,
        @NotBlank(message = "此项不能为空") String behaviorTraits,
        @NotBlank(message = "此项不能为空") @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "头像标识格式不正确") String avatarKey,
        @NotBlank(message = "此项不能为空") @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "头像标识格式不正确") String speakingAvatarKey,
        @NotNull(message = "请提供内容版本") @Min(value = 0, message = "版本不能为负数") Integer version) {
    public ScammerWriteRequest content() {
        return new ScammerWriteRequest(name, age, gender, identity, description, contextTime, contextPlace, currentState, fraudExperience, speechSkill, manipulationSkill, behaviorTraits, avatarKey, speakingAvatarKey);
    }
}
