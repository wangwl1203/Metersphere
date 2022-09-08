package io.metersphere.track.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class BugStatustics {

    private long bugTotalSize;
    private String rage;  // 总缺陷覆盖率
    private String passRate; // 总通过率
    private List<TestPlanBugCount> list = new ArrayList<>();
}
