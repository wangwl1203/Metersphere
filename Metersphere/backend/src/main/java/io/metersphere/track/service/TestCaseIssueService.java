package io.metersphere.track.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.metersphere.base.domain.*;
import io.metersphere.base.mapper.IssuesMapper;
import io.metersphere.base.mapper.TestCaseIssuesMapper;
import io.metersphere.base.mapper.TestPlanTestCaseMapper;
import io.metersphere.log.vo.OperatingLogDetails;
import io.metersphere.track.dto.TestCaseDTO;
import io.metersphere.track.request.issues.IssuesRelevanceRequest;
import io.metersphere.track.request.testcase.IssuesRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class TestCaseIssueService {

    @Resource
    private TestCaseIssuesMapper testCaseIssuesMapper;
    @Lazy
    @Resource
    private TestCaseService testCaseService;
    @Resource
    private IssuesMapper issuesMapper;
    @Resource
    @Lazy
    private IssuesService issuesService;
    @Resource
    private TestPlanTestCaseMapper testPlanTestCaseMapper;


    public void deleteByPlanId(String planId) {
        testCaseIssuesMapper.deleteByPlanId(planId);
    }

    public void deleteByPlanIdAndIssuesId(String planId,List<String> issuesIds){
        testCaseIssuesMapper.deleteByPlanIdAndIssuesId(planId,issuesIds);
    }

    public void delTestCaseIssues(String testCaseId) {
        TestCaseIssuesExample example = new TestCaseIssuesExample();
        example.createCriteria().andTestCaseIdEqualTo(testCaseId);
        List<TestCaseIssues> testCaseIssues = testCaseIssuesMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(testCaseIssues)) {
            List<String> list = testCaseIssues.stream().map(TestCaseIssues::getIssuesId).collect(Collectors.toList());
            list.forEach(id -> {
                issuesMapper.deleteByPrimaryKey(id);
            });
        }
        testCaseIssuesMapper.deleteByExample(example);
    }

    public List<TestCaseDTO> list(IssuesRelevanceRequest request) {
        List<String> testCaseIds = getTestCaseIdsByIssuesId(request.getIssuesId());
        List<TestCaseDTO> list = testCaseService.getTestCaseByIds(testCaseIds);
        testCaseService.addProjectName(list);
        return list;
    }

    public List<TestCaseIssues> getTestCaseIssuesByIssuesId(String issuesId) {
        TestCaseIssuesExample example = new TestCaseIssuesExample();
        example.createCriteria().andIssuesIdEqualTo(issuesId);
        return testCaseIssuesMapper.selectByExample(example);
    }

    public List<TestCaseIssues> getIssuesByCaseId(IssuesRequest issueRequest) {
        // 根据caseId和planId进行查询
        return testCaseIssuesMapper.getIssuesByCaseId(issueRequest);
    }

    public List<TestCaseIssues> getIssuesByPlanId(String planId) {
        return testCaseIssuesMapper.getIssuesByPlanId(planId);
    }

    public List<String> getTestCaseIdsByIssuesId(String issuesId) {
        return getTestCaseIssuesByIssuesId(issuesId).stream()
                .map(TestCaseIssues::getTestCaseId)
                .collect(Collectors.toList());
    }

    public void relate(IssuesRelevanceRequest request) {
        if (StringUtils.isNotBlank(request.getCaseId())) {
            List<String> issueIds = request.getIssueIds();
            if (!CollectionUtils.isEmpty(issueIds)) {
                issueIds.forEach(issueId -> {
                    create(request.getPlanId(),request.getCaseId(), issueId, request.getPlatform());
                });
            }
        } else if (StringUtils.isNotBlank(request.getIssuesId())) {
            List<String> caseIds = request.getTestCaseIds();
            if (!CollectionUtils.isEmpty(caseIds)) {
                caseIds.forEach(caseId -> {
                    create(request.getPlanId(),caseId, request.getIssuesId(), request.getPlatform());
                });
            }
        }
        updateIssuesCountExt(request.getCaseId(),request.getPlanId());
    }
    public void updateIssuesCountExt(String caseId,String planId) {
        /*
        * 更新test_plan_test_case表的issues和issues_count,issues字段值
        * issues字段通过实时查JIRA获取落库
        * 测试计划-功能测试用例列表，缺陷预览展示
        * */
        List<IssuesDao> issues = issuesService.getIssuesList(caseId,planId);
        int issuesCount = issues.size();
        TestPlanTestCaseExample example = new TestPlanTestCaseExample();
        example.createCriteria().andCaseIdEqualTo(caseId).andPlanIdEqualTo(planId);
        TestPlanTestCaseWithBLOBs testPlanTestCase = new TestPlanTestCaseWithBLOBs();
        testPlanTestCase.setIssuesCount(issuesCount);
        if (!CollectionUtils.isEmpty(issues)) {
            testPlanTestCase.setIssues(JSONObject.toJSONString(issues));
        }
        testPlanTestCaseMapper.updateByExampleSelective(testPlanTestCase, example);
    }

    public void updateIssuesCount(String caseId) {
        List<IssuesDao> issues = issuesService.getIssues(caseId);
        int issuesCount = issues.size();
        TestPlanTestCaseExample example = new TestPlanTestCaseExample();
        example.createCriteria().andCaseIdEqualTo(caseId);
        TestPlanTestCaseWithBLOBs testPlanTestCase = new TestPlanTestCaseWithBLOBs();
        testPlanTestCase.setIssuesCount(issuesCount);
        if (!CollectionUtils.isEmpty(issues)) {
            testPlanTestCase.setIssues(JSONObject.toJSONString(issues));
        }
        testPlanTestCaseMapper.updateByExampleSelective(testPlanTestCase, example);
    }

    public void updateIssuesCountById(String caseId, String planId) {
        List<IssuesDao> issues = issuesService.getIssuesExt(caseId, planId);
        int issuesCount = issues.size();
        TestPlanTestCaseExample example = new TestPlanTestCaseExample();
        example.createCriteria().andCaseIdEqualTo(caseId).andPlanIdEqualTo(planId);
        TestPlanTestCaseWithBLOBs testPlanTestCase = new TestPlanTestCaseWithBLOBs();
        testPlanTestCase.setIssuesCount(issuesCount);
        if (!CollectionUtils.isEmpty(issues)) {
            testPlanTestCase.setIssues(JSONObject.toJSONString(issues));
        }
        testPlanTestCaseMapper.updateByExampleSelective(testPlanTestCase, example);
    }

    public void create(String planId, String caseId, String issueId, String platform) {
        TestCaseIssues testCaseIssues = new TestCaseIssues();
        testCaseIssues.setId(UUID.randomUUID().toString());
        testCaseIssues.setTestCaseId(caseId);
        testCaseIssues.setIssuesId(issueId);
        testCaseIssues.setPlanId(planId);
        testCaseIssues.setPlatform(platform);
        testCaseIssuesMapper.insert(testCaseIssues);
    }


    public String getLogDetails(IssuesRelevanceRequest request) {
        TestCaseWithBLOBs bloBs = testCaseService.getTestCase(request.getCaseId());
        if (bloBs != null) {
            IssuesExample example = new IssuesExample();
            example.createCriteria().andIdIn(request.getIssueIds());
            List<Issues> issues = issuesMapper.selectByExample(example);
            List<String> names = issues.stream().map(Issues::getTitle).collect(Collectors.toList());
            OperatingLogDetails details = new OperatingLogDetails(JSON.toJSONString(request.getIssueIds()), bloBs.getProjectId(), bloBs.getName() + " 关联 " + names, bloBs.getCreateUser(), new LinkedList<>());
            return JSON.toJSONString(details);
        }
        return null;
    }
}
