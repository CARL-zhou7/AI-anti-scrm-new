package com.aiscam.antiscam.admin.web.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.util.List;
import com.aiscam.antiscam.content.domain.*;
import com.aiscam.antiscam.common.web.RequestContent;

public record VictimWriteRequest(
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
        @NotNull(message = "此项不能为空") @Min(value = 0, message = "金额不能为负数") Integer generalReserveFundsYuan,
        @NotNull(message = "此项不能为空") @Min(value = 0, message = "金额不能为负数") Integer emergencyReserveFundsYuan,
        @NotBlank(message = "此项不能为空") String reservedFundsNote,
        @NotBlank(message = "此项不能为空") String generalDecisionTraits,
        @NotNull(message = "请填写三套场景资料") @Size(min = 3, max = 3, message = "请填写三套场景资料") List<@NotNull @Valid VictimScenarioProfileRequest> scenarioProfiles,
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
        RequestContent.checkField("contextPlace", contextPlace, 128);
        RequestContent.checkField("currentState", currentState, RequestContent.MAX_TEXT_CHARS);
        RequestContent.checkField("personalityTraits", personalityTraits, RequestContent.MAX_TEXT_CHARS);
        values(avatarKey, speakingAvatarKey).validate();
    }
    public VictimProfileValues values(String storedAvatarKey, String storedSpeakingAvatarKey) {
        return new VictimProfileValues(name, age, gender, identity, description, familyStatus, incomeDescription, monthlyExpenseYuan, initialSavingsYuan, availableFundsYuan, generalReserveFundsYuan, emergencyReserveFundsYuan, reservedFundsNote, generalDecisionTraits, contextPlace, currentState, antiFraudAwareness, riskTendency, personalityTraits, storedAvatarKey, storedSpeakingAvatarKey, scenarioProfiles == null ? null : scenarioProfiles.stream().map(p -> p == null ? null : p.values()).toList());
    }
}
