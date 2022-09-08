package io.metersphere.track.request.testplan;

import io.metersphere.base.domain.TestPlan;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SaveTestPlanRequest extends TestPlan {
    private List<String> projectIds;
    private List<String> userIds;
    private List<String> followIds;
}
