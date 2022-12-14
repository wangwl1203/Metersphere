package io.metersphere.base.domain;

import java.io.Serializable;
import lombok.Data;

@Data
public class TestCaseIssues implements Serializable {
    private String id;

    private String testCaseId;

    private String issuesId;

    private String planId;

    private String platform;

    private static final long serialVersionUID = 1L;
}