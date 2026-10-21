package com.aiscam.antiscam.admin.web.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.util.List;
import com.aiscam.antiscam.content.domain.*;
import com.aiscam.antiscam.common.web.RequestContent;

public record VictimUpdateRequest(
        @NotBlank(message = "此项不能为空") String name,
        @NotNull(message = "此项不能为空") @Min(value = 1, message = "年龄须为1至65535") @Max(value = 65535, message = "年龄须为1至65535") Integer age,
        @NotBlank(message = "此项不能为空") String gender,
        @NotBlank(message = "此项不能为空") String identity,
        @NotBlank(message = "此项不能为空") String description,
        @NotBlank(message = "此项不能为空") String familyStatus,
        @NotBlank(message = "此项不能为空") String incomeDescription,
        @NotNull(message = "此项不能为空") @Min(value = 0, message = "金额不能为负数") Integer monthlyExpenseYuan,
        @NotNull(message = "此项不能为空") @Min(value = 0, message = "金额不能为负数") Integer initialSavingsYuan,
        @NotNull(message = "此项不能为空") @Min(value = 0, message = "金额不能为负数") Integer availableFundsYuan,
        @NotNull(message = "此项不能为空") @Min(value = 0, message = "金额不能为负数") Integer reservedFundsYuan,
        @NotBlank(message = "此项不能为空") String reservedFundsNote,
        @NotBlank(message = "此项不能为空") String generalDecisionTraits,
        @NotNull(message = "请填写三套场景资料") @Size(min = 3, max = 3, message = "请填写三套场景资料") List<@NotNull @Valid VictimScenarioProfileRequest> scenarioProfiles,
        @NotBlank(message = "此项不能为空") String contextPlace,
        @NotBlank(message = "此项不能为空") String currentState,
        @NotNull(message = "此项不能为空") Level antiFraudAwareness,
        @NotNull(message = "此项不能为空") Level riskTendency,
        @NotBlank(message = "此项不能为空") String personalityTraits,
        @NotBlank(message = "此项不能为空") @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "头像标识格式不正确") String avatarKey,
        @NotBlank(message = "此项不能为空") @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "头像标识格式不正确") String speakingAvatarKey,
        @NotNull(message = "请提供内容版本") @Min(value = 0, message = "版本不能为负数") Integer version) {
    public VictimWriteRequest content() {
        return new VictimWriteRequest(name, age, gender, identity, description, familyStatus, incomeDescription, monthlyExpenseYuan, initialSavingsYuan, availableFundsYuan, reservedFundsYuan, reservedFundsNote, generalDecisionTraits, scenarioProfiles, contextPlace, currentState, antiFraudAwareness, riskTendency, personalityTraits, avatarKey, speakingAvatarKey);
    }
}
