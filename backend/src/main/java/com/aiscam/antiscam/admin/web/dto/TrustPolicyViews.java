package com.aiscam.antiscam.admin.web.dto;

import com.aiscam.antiscam.simulation.domain.TrustPolicy;
import com.aiscam.antiscam.common.error.ApiException;
import jakarta.validation.constraints.*;

public final class TrustPolicyViews {
    private TrustPolicyViews() {}
    public record View(int thresholdAccept,int thresholdSmall,int thresholdLarge,int version,String rulesetVersion,String updatedAt) {
        /** New sessions start on trust-v3; a running session keeps reading its frozen trust-v2 snapshot. */
        public static View from(TrustPolicy p) { return new View(p.getTrustThresholdAccept(),p.getTrustThresholdSmall(),p.getTrustThresholdLarge(),
                p.getRowVersion(),"trust-v3",com.aiscam.antiscam.common.time.UtcTime.format(p.getUpdatedAt())); }
    }
    public record Update(@NotNull @Min(0) @Max(100) Integer thresholdAccept,@NotNull @Min(0) @Max(100) Integer thresholdSmall,
            @NotNull @Min(0) @Max(100) Integer thresholdLarge,@NotNull @Min(0) Integer version) {
        public void validate() {
            if (thresholdAccept==null || thresholdSmall==null || thresholdLarge==null || thresholdAccept<0 || thresholdLarge>100
                    || thresholdAccept>=thresholdSmall || thresholdSmall>=thresholdLarge)
                throw ApiException.field("thresholdSmall","VALUE_INVALID","三项阈值须在0至100之间严格递增");
        }
    }
}
