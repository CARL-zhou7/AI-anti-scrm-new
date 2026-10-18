package com.aiscam.antiscam.timing;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.timing.TimingSampleStore.TimingSample;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Maps the client's monotonic click time onto the server time line (interface spec §7 TimingEvidence, §9.1).
 * Every failure is a field error; there is deliberately no fallback to the request arrival time.
 */
@Component
public class TimingCalibrator {
    private final TimingSampleStore samples;
    private final SimulationProperties properties;

    public TimingCalibrator(TimingSampleStore samples, SimulationProperties properties) {
        this.samples = samples;
        this.properties = properties;
    }

    public CalibratedStart calibrate(TimingEvidence evidence, String httpSessionId, long userId, Instant receivedAt) {
        if (evidence == null) throw ApiException.field("timing", "REQUIRED", "缺少计时证据");
        if (evidence.methodVersion() == null) throw ApiException.field("timing.methodVersion", "REQUIRED", "此项不能为空");
        if (evidence.sampleId() == null || evidence.sampleId().isBlank()) {
            throw ApiException.field("timing.sampleId", "REQUIRED", "此项不能为空");
        }
        if (evidence.clientSendMonotonicMs() == null) throw ApiException.field("timing.clientSendMonotonicMs", "REQUIRED", "此项不能为空");
        if (evidence.clientReceiveMonotonicMs() == null) throw ApiException.field("timing.clientReceiveMonotonicMs", "REQUIRED", "此项不能为空");
        if (evidence.clientClickMonotonicMs() == null) throw ApiException.field("timing.clientClickMonotonicMs", "REQUIRED", "此项不能为空");
        if (!TimingSampleStore.METHOD_VERSION.equals(evidence.methodVersion())) {
            throw invalid("timing.methodVersion", "计时方法版本不受支持");
        }
        TimingSample sample = samples.find(evidence.sampleId())
                .orElseThrow(() -> invalid("timing.sampleId", "计时样本不存在或已失效，请重新开始"));
        if (!sample.expiresAt().isAfter(receivedAt)) throw invalid("timing.sampleId", "计时样本已过期，请重新开始");
        if (!sample.httpSessionId().equals(httpSessionId) || sample.userId() != userId) {
            throw invalid("timing.sampleId", "计时样本不属于当前登录会话");
        }
        double send = evidence.clientSendMonotonicMs(), receive = evidence.clientReceiveMonotonicMs();
        double click = evidence.clientClickMonotonicMs();
        if (!(send >= 0) || Double.isInfinite(send)) throw invalid("timing.clientSendMonotonicMs", "发送时刻不合法");
        if (!(receive >= send) || Double.isInfinite(receive)) {
            throw invalid("timing.clientReceiveMonotonicMs", "接收时刻不能早于发送时刻");
        }
        if (!(click >= receive) || Double.isInfinite(click)) {
            throw invalid("timing.clientClickMonotonicMs", "点击时刻不能早于样本接收时刻");
        }
        double uncertainty = (receive - send) / 2;
        if (uncertainty > properties.getTiming().getMaxUncertaintyMs()) {
            throw invalid("timing.clientReceiveMonotonicMs", "计时样本误差过大，请重新开始");
        }
        long offsetMs = Math.round(click - (send + receive) / 2);
        Instant startedAt = sample.serverSampledAt().plusMillis(offsetMs);
        if (startedAt.isAfter(receivedAt.plusMillis(Math.round(uncertainty)))) {
            throw invalid("timing.clientClickMonotonicMs", "点击时刻晚于请求到达时刻");
        }
        if (receivedAt.toEpochMilli() - startedAt.toEpochMilli() > properties.getTiming().getMaxClickToArrivalMs()) {
            throw invalid("timing.clientClickMonotonicMs", "点击到请求到达的间隔过长，请重新开始");
        }
        if (!startedAt.plusMillis(600_000).isAfter(receivedAt)) {
            throw invalid("timing.clientClickMonotonicMs", "有效演绎时长已耗尽");
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("method_version", sample.methodVersion());
        json.put("sample_id", sample.sampleId());
        json.put("client_send_monotonic_ms", send);
        json.put("client_receive_monotonic_ms", receive);
        json.put("client_click_monotonic_ms", click);
        json.put("server_sampled_at", sample.serverSampledAt().toString());
        json.put("estimated_offset_ms", offsetMs);
        json.put("uncertainty_ms", uncertainty);
        return new CalibratedStart(startedAt, receivedAt, json);
    }

    private static ApiException invalid(String field, String message) {
        return ApiException.field(field, "VALUE_INVALID", message);
    }

    public record TimingEvidence(String methodVersion, String sampleId, Double clientSendMonotonicMs,
            Double clientReceiveMonotonicMs, Double clientClickMonotonicMs) {}

    public record CalibratedStart(Instant startedAt, Instant receivedAt, Map<String, Object> evidenceJson) {}
}
