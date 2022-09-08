package io.metersphere.base.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class IssuesDao extends IssuesWithBLOBs {
    private String model;
    private String projectName;
    private String creatorName;
    private String resourceName;
    private long caseCount;
    private List<String> caseIds;
    private int totalIssueCount;
    private String severityLevel; //严重等级
    private String issueType; //缺陷类型
}
