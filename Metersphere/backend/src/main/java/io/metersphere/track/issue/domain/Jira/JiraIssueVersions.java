package io.metersphere.track.issue.domain.Jira;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JiraIssueVersions {
    private String self;
    private String id;
    private String name;
    private Boolean archived;
    private Boolean released;
    private String projectId;
}
