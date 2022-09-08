package io.metersphere.track.issue.domain.Jira;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class JiraAllIssue {
    private String expand;
    private String startAt;
    private String maxResults;
    private Integer total;
    private List<String> issues;
}
