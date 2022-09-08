package io.metersphere.base.domain;

import java.io.Serializable;
import lombok.Data;

@Data
public class TestCaseTest implements Serializable {
    private String testCaseId;

    private String testId;

    private String testType;

    private Long createTime;

    private Long updateTime;

    private String testCaseName;

    private String testCaseDescription;

    private String nodePath; //所属模块

    private String priority; //用例等级

    private Integer number; //用例编号（页面展示的编号）

    private String status; //用例状态

    private String createUser; //创建人

    private String projectId;

    private String projectName;

    private String prerequisite;

    private String remark;

    private String steps;

    private String stepDescription;

    private String expectedResult;

    private static final long serialVersionUID = 1L;
}