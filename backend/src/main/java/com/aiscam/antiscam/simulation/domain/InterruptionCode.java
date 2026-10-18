package com.aiscam.antiscam.simulation.domain;

/** Server-decided interruption causes; the user-visible text is fixed here and never taken from a client. */
public enum InterruptionCode {
    USER_EXIT("你已离开演绎页面，本次演绎已中断"),
    DIALOGUE_FAILED("角色对话生成失败，本次演绎已中断"),
    ANALYSIS_FAILED("解说与风险分析生成失败，本次演绎已中断"),
    TTS_FAILED("角色语音准备失败，本次演绎已中断"),
    MEDIA_FAILED("画面或语音播放出现问题，本次演绎已中断"),
    PERSISTENCE_FAILED("演绎内容保存失败，本次演绎已中断"),
    TIME_LIMIT("已达到时长上限，当前轮尚未完成，本次演绎已中断"),
    CONNECTION_LOST("与演绎页面的连接已断开，本次演绎已中断"),
    SERVICE_RESTARTED("服务已重新启动，本次演绎已中断");
    private final String message;
    InterruptionCode(String message) { this.message = message; }
    public String message() { return message; }
}
