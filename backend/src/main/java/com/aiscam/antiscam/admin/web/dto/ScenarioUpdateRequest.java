package com.aiscam.antiscam.admin.web.dto;

import jakarta.validation.constraints.*;

public record ScenarioUpdateRequest(@NotBlank(message = "名称不能为空") String name,
        @NotBlank(message = "简介不能为空") String description,
        @NotNull(message = "请提供内容版本") @Min(value = 0, message = "版本不能为负数") Integer version) {}
