package io.metersphere.track.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.base.domain.*;
import io.metersphere.base.mapper.*;
import io.metersphere.base.mapper.ext.ExtIssuesMapper;
import io.metersphere.commons.constants.IssuesManagePlatform;
import io.metersphere.commons.constants.IssuesStatus;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.*;
import io.metersphere.controller.request.IntegrationRequest;
import io.metersphere.controller.request.OrderRequest;
import io.metersphere.log.utils.ReflexObjectUtil;
import io.metersphere.log.vo.DetailColumn;
import io.metersphere.log.vo.OperatingLogDetails;
import io.metersphere.log.vo.track.TestPlanReference;
import io.metersphere.notice.service.NoticeSendService;
import io.metersphere.service.IntegrationService;
import io.metersphere.service.IssueTemplateService;
import io.metersphere.service.ProjectService;
import io.metersphere.track.dto.PlanReportIssueDTO;
import io.metersphere.track.dto.TestCaseReportStatusResultDTO;
import io.metersphere.track.dto.TestPlanFunctionResultReportDTO;
import io.metersphere.track.dto.TestPlanSimpleReportDTO;
import io.metersphere.track.issue.*;
import io.metersphere.track.issue.domain.PlatformUser;
import io.metersphere.track.issue.domain.zentao.ZentaoBuild;
import io.metersphere.track.request.testcase.AuthUserIssueRequest;
import io.metersphere.track.request.testcase.IssuesRequest;
import io.metersphere.track.request.testcase.IssuesUpdateRequest;
import io.metersphere.track.request.testcase.TestCaseBatchRequest;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class IssuesService {

    @Resource
    private IntegrationService integrationService;
    @Resource
    private ProjectService projectService;
    @Resource
    private TestPlanService testPlanService;
    @Lazy
    @Resource
    private TestCaseService testCaseService;
    @Resource
    private IssuesMapper issuesMapper;
    @Resource
    private NoticeSendService noticeSendService;
    @Resource
    private TestCaseIssuesMapper testCaseIssuesMapper;
    @Resource
    private IssueTemplateMapper issueTemplateMapper;
    @Resource
    private ExtIssuesMapper extIssuesMapper;
    @Resource
    private WorkspaceMapper workspaceMapper;
    @Resource
    private IssueTemplateService issueTemplateService;
    @Resource
    private TestCaseMapper testCaseMapper;
    @Resource
    private TestCaseIssueService testCaseIssueService;
    @Resource
    private TestPlanTestCaseService testPlanTestCaseService;
    @Resource
    private TestCaseIssueService testCseIssueService;
    @Resource
    private UserMapper userMapper;

    public void testAuth(String workspaceId, String platform) {
        IssuesRequest issuesRequest = new IssuesRequest();
        issuesRequest.setWorkspaceId(workspaceId);
        AbstractIssuePlatform abstractPlatform = IssueFactory.createPlatform(platform, issuesRequest);
        abstractPlatform.testAuth();
    }


    public void addIssues(IssuesUpdateRequest issuesRequest) {
        List<AbstractIssuePlatform> platformList = getAddPlatforms(issuesRequest);
        platformList.forEach(platform -> {
            platform.addIssue(issuesRequest);
        });
        /*issuesRequest.getTestCaseIds().forEach(l -> {
            testCaseIssueService.updateIssuesCountById(l, issuesRequest.getResourceId());
        });*/
    }


    public void updateIssues(IssuesUpdateRequest issuesRequest) {
        issuesRequest.getId();
        List<AbstractIssuePlatform> platformList = getUpdatePlatforms(issuesRequest);
        platformList.forEach(platform -> {
            platform.updateIssue(issuesRequest);
        });
        // todo 缺陷更新事件？
    }

    public List<AbstractIssuePlatform> getAddPlatforms(IssuesUpdateRequest updateRequest) {
        List<String> platforms = new ArrayList<>();
        if (StringUtils.isNotBlank(updateRequest.getTestCaseId())) {
            // 测试计划关联
            String p = getPlatformsByCaseId(updateRequest.getTestCaseId());
            platforms.add(p);
        } else {
            // 缺陷管理关联
            String t = getIssueTemplate(updateRequest.getProjectId());
            platforms.add(t);
        }

        if (CollectionUtils.isEmpty(platforms)) {
            platforms.add(IssuesManagePlatform.Local.toString());
        }
        IssuesRequest issuesRequest = new IssuesRequest();
        BeanUtils.copyBean(issuesRequest, updateRequest);
        return IssueFactory.createPlatforms(platforms, issuesRequest);
    }

    public List<AbstractIssuePlatform> getUpdatePlatforms(IssuesUpdateRequest updateRequest) {
        String id = updateRequest.getId();
        IssuesWithBLOBs issuesWithBLOBs = issuesMapper.selectByPrimaryKey(id);
        String platform = issuesWithBLOBs.getPlatform();
        List<String> platforms = new ArrayList<>();
        if (StringUtils.isBlank(platform)) {
            platforms.add(IssuesManagePlatform.Local.toString());
        } else {
            platforms.add(platform);
        }
        IssuesRequest issuesRequest = new IssuesRequest();
        BeanUtils.copyBean(issuesRequest, updateRequest);
        return IssueFactory.createPlatforms(platforms, issuesRequest);
    }

    public IssuesRequest getIssuesRequest(String caseId, String planId){
        IssuesRequest issueRequest = new IssuesRequest();
        issueRequest.setTestCaseId(caseId);
        issueRequest.setPlanId(planId);
        ServiceUtils.getDefaultOrder(issueRequest.getOrders());
        Project project = getProjectByPlanId(planId);
        // project 不存在
        if (project == null) {
            return null;
        }
        String workspaceId = project.getWorkspaceId();
        TestCase testCase = testCaseMapper.selectByPrimaryKey(caseId);
        String userId = testCase.getMaintainer();
        issueRequest.setWorkspaceId(workspaceId);
        issueRequest.setUserId(userId);
        return issueRequest;
    }

    public List<IssuesDao> getIssuesExt(String caseId, String planId) {
        IssuesRequest issueRequest = getIssuesRequest(caseId,planId);
        return getIssuesByProjectIdOrCaseId(issueRequest);
    }

    public List<IssuesDao> getIssues(String caseId) {
        IssuesRequest issueRequest = getIssuesRequest(caseId,"");
        return getIssuesByProjectIdOrCaseId(issueRequest);
    }

    public List<IssuesDao> getIssuesByProjectIdOrCaseId(IssuesRequest issueRequest) {
        List<IssuesDao> issues;
        if (StringUtils.isNotBlank(issueRequest.getProjectId())) {
            issues = extIssuesMapper.getIssues(issueRequest);
        } else {
            issues = extIssuesMapper.getIssuesByCaseId(issueRequest);
        }
        return issues;
    }

    public List<IssuesDao> getIssuesByIssuesRequest(IssuesRequest issueRequest) {
        List<IssuesDao> issues;
        if (StringUtils.isNotBlank(issueRequest.getProjectId())) {
            issues = extIssuesMapper.getIssues(issueRequest);
        } else {
            issues = extIssuesMapper.getIssuesByCaseId(issueRequest);
        }
        if(issues!=null && issues.size()>0){
            for(IssuesDao issuesDao: issues) {
                List<String> jsonData = JSONObject.parseArray(issuesDao.getCustomFields(), String.class);
                if (jsonData != null && jsonData.size() > 0) {
                    issuesDao.setKey(issuesDao.getNum().toString());
                    issuesDao.setId(issuesDao.getId());
                    issuesDao.setNum(issuesDao.getNum());
                    issuesDao.setTitle(issuesDao.getTitle());
                    issuesDao.setPlatformStatus(issuesDao.getPlatformStatus());
                    issuesDao.setStatus(issuesDao.getStatus());
                    issuesDao.setPlatform(IssuesManagePlatform.Local.toString());
                    issuesDao.setDescription(issuesDao.getDescription());
                    issuesDao.setCount(issuesDao.getCount());
                    issuesDao.setProjectId(issueRequest.getProjectId());
                    Project project = projectService.getProjectById(issueRequest.getProjectId());
                    issuesDao.setProjectName(project.getName());
                    User user = userMapper.selectByPrimaryKey(issuesDao.getCreator());
                    issuesDao.setCreatorName(user.getName());
                    for (String str : jsonData) {
                        String name = JSONObject.parseObject(str).getString("name");
                        if (name.equals("缺陷类型")) {
                            String issueType = JSONObject.parseObject(str).getString("value");
                            issuesDao.setIssueType(issueType);
                        }
                        if (name.equals("严重程度")) {
                            String severityLevel = JSONObject.parseObject(str).getString("value");
                            issuesDao.setSeverityLevel(severityLevel);
                            break;
                        }
                    }
                }
            }
        }
        return issues;
    }

    public List<IssuesDao> getIssuesList(String caseId, String planId) {
        IssuesRequest issueRequest = getIssuesRequest(caseId,planId);
        Project project = getProjectByPlanId(planId);
        List<IssuesDao> issues = getIssuesByCaseIdAndPlanId(issueRequest,project.getId());
        issueRequest.setPlatform(IssuesManagePlatform.Local.toString());
        // 本地缺陷，增加排序
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setName("num");
        orderRequest.setType("desc");
        List<OrderRequest> orderRequestList = new ArrayList<>();
        orderRequestList.add(orderRequest);
        issueRequest.setOrders(orderRequestList);
        List<IssuesDao> issuesExt = getIssuesByIssuesRequest(issueRequest);
        if(issuesExt !=null && issuesExt.size()>0){
            issues.addAll(issuesExt);
        }
        return issues;
    }

    public List<IssuesDao> getIssuesByCaseIdAndPlanId(IssuesRequest issueRequest,String projectId) {
        issueRequest.setProjectId(projectId);
        List<IssuesDao> issues = syncJiraIssues(issueRequest, 0, 0, getTssuesIdList(issueRequest,true,""));
        return issues;
    }

    public String getTssuesIdList(IssuesRequest issueRequest, Boolean flag, String title){
        /*
        * 用于拼接查询JIRA的语句，分页查询语句、模糊查询语句
        * */
        issueRequest.setPlatform(IssuesManagePlatform.Jira.toString());
        List<TestCaseIssues> testCaseIssuesList= testCseIssueService.getIssuesByCaseId(issueRequest);
        String issuesIdList = "";
        if(testCaseIssuesList.size()!=0){
            if(flag){
                issuesIdList = " and id in (";
            }else{
                issuesIdList = " and id not in (";
            }
            for(TestCaseIssues testCaseIssues: testCaseIssuesList){
                issuesIdList +=  testCaseIssues.getIssuesId() + ",";
            }
            issuesIdList =  issuesIdList.substring(0,issuesIdList.length() -1) + ")";

            if(title!=null && !title.equals("")){
                issuesIdList = issuesIdList + " AND summary ~ " + title;
            }
        }else{
            if(flag) {
                issuesIdList = "testCaseIssuesList.size=0";
            }else{
                if(title!=null && !title.equals("")){
                    issuesIdList = " AND summary ~" + title;
                }
            }
        }
        return issuesIdList;
    }

    public String getPlatformsByCaseId(String caseId) {
        TestCaseWithBLOBs testCase = testCaseService.getTestCase(caseId);
        Project project = projectService.getProjectById(testCase.getProjectId());
        return getIssueTemplate(project.getId());
    }

    public String getIssueTemplate(String projectId) {
        Project project = projectService.getProjectById(projectId);
        IssueTemplate issueTemplate = null;
        String id = project.getIssueTemplateId();
        if (StringUtils.isBlank(id)) {
            issueTemplate = issueTemplateService.getDefaultTemplate(project.getWorkspaceId());
        } else {
            issueTemplate = issueTemplateMapper.selectByPrimaryKey(id);
        }
        if (issueTemplate == null) {
            MSException.throwException("project issue template id is null.");
        }
        String platform = issueTemplate.getPlatform();
        if (StringUtils.equals(platform, "metersphere")) {
            return IssuesManagePlatform.Local.name();
        }
        return platform;
    }

    public List<String> getPlatforms(Project project) {
        String workspaceId = project.getWorkspaceId();
        boolean tapd = isIntegratedPlatform(workspaceId, IssuesManagePlatform.Tapd.toString());
        boolean jira = isIntegratedPlatform(workspaceId, IssuesManagePlatform.Jira.toString());
        boolean zentao = isIntegratedPlatform(workspaceId, IssuesManagePlatform.Zentao.toString());

        List<String> platforms = new ArrayList<>();
        if (tapd) {
            // 是否关联了项目
            String tapdId = project.getTapdId();
            if (StringUtils.isNotBlank(tapdId)) {
                platforms.add(IssuesManagePlatform.Tapd.name());
            }

        }

        if (jira) {
            String jiraKey = project.getJiraKey();
            if (StringUtils.isNotBlank(jiraKey)) {
                platforms.add(IssuesManagePlatform.Jira.name());
            }
        }

        if (zentao) {
            String zentaoId = project.getZentaoId();
            if (StringUtils.isNotBlank(zentaoId)) {
                platforms.add(IssuesManagePlatform.Zentao.name());
            }
        }
        return platforms;
    }

    private Project getProjectByPlanId(String planId) {
        TestPlan testPlan = testPlanService.getTestPlan(planId);
        // testPlan 不存在
        if (testPlan == null) {
            return null;
        }
        return projectService.getProjectById(testPlan.getProjectId());
    }

    private String getTapdProjectId(String testCaseId) {
        TestCaseWithBLOBs testCase = testCaseService.getTestCase(testCaseId);
        Project project = projectService.getProjectById(testCase.getProjectId());
        return project.getTapdId();
    }

    private String getJiraProjectKey(String testCaseId) {
        TestCaseWithBLOBs testCase = testCaseService.getTestCase(testCaseId);
        Project project = projectService.getProjectById(testCase.getProjectId());
        return project.getJiraKey();
    }

    private String getZentaoProjectId(String testCaseId) {
        TestCaseWithBLOBs testCase = testCaseService.getTestCase(testCaseId);
        Project project = projectService.getProjectById(testCase.getProjectId());
        return project.getZentaoId();
    }

    /**
     * 是否关联平台
     */
    public boolean isIntegratedPlatform(String workspaceId, String platform) {
        IntegrationRequest request = new IntegrationRequest();
        request.setPlatform(platform);
        request.setWorkspaceId(workspaceId);
        ServiceIntegration integration = integrationService.get(request);
        return StringUtils.isNotBlank(integration.getId());
    }

    public void closeLocalIssue(String issueId) {
        IssuesWithBLOBs issues = new IssuesWithBLOBs();
        issues.setId(issueId);
        issues.setStatus("closed");
        issuesMapper.updateByPrimaryKeySelective(issues);
    }

    public List<PlatformUser> getTapdProjectUsers(IssuesRequest request) {
        AbstractIssuePlatform platform = IssueFactory.createPlatform(IssuesManagePlatform.Tapd.name(), request);
        return platform.getPlatformUser();
    }

    public List<PlatformUser> getZentaoUsers(IssuesRequest request) {
        AbstractIssuePlatform platform = IssueFactory.createPlatform(IssuesManagePlatform.Zentao.name(), request);
        return platform.getPlatformUser();
    }

    public void deleteIssue(IssuesRequest request) {
        issuesMapper.deleteByPrimaryKey(request.getId());
        deleteIssueRelate(request);
    }

    public void deleteIssueRelate(IssuesRequest request) {
        String caseId = request.getCaseId();
        String id = request.getId();
        String planId = request.getPlanId();
        TestCaseIssuesExample example = new TestCaseIssuesExample();
        example.createCriteria().andTestCaseIdEqualTo(caseId).andIssuesIdEqualTo(id).andPlanIdEqualTo(planId);
        testCaseIssuesMapper.deleteByExample(example);
        testCaseIssueService.updateIssuesCountExt(caseId,planId);
    }

    public void delete(String id) {
        IssuesWithBLOBs issuesWithBLOBs = issuesMapper.selectByPrimaryKey(id);
        List platforms = new ArrayList<>();
        platforms.add(issuesWithBLOBs.getPlatform());
        String projectId = issuesWithBLOBs.getProjectId();
        Project project = projectService.getProjectById(projectId);
        IssuesRequest issuesRequest = new IssuesRequest();
        issuesRequest.setWorkspaceId(project.getWorkspaceId());
        AbstractIssuePlatform platform = IssueFactory.createPlatform(issuesWithBLOBs.getPlatform(), issuesRequest);
        platform.deleteIssue(id);
    }

    public IssuesWithBLOBs get(String id) {
        return issuesMapper.selectByPrimaryKey(id);
    }

    public List<ZentaoBuild> getZentaoBuilds(IssuesRequest request) {
        ZentaoPlatform platform = (ZentaoPlatform) IssueFactory.createPlatform(IssuesManagePlatform.Zentao.name(), request);
        return platform.getBuilds();
    }

    public List<IssuesDao> list(IssuesRequest request) {
        request.setOrders(ServiceUtils.getDefaultOrder(request.getOrders()));
        List<IssuesDao> issues = extIssuesMapper.getIssuesLocal(request); //获取所有本地缺陷

        List<String> ids = issues.stream()
                .map(IssuesDao::getCreator)
                .collect(Collectors.toList());
        Map<String, User> userMap = ServiceUtils.getUserMap(ids);
        List<String> resourceIds = issues.stream()
                .map(IssuesDao::getResourceId)
                .collect(Collectors.toList());

        List<TestPlan> testPlans = testPlanService.getTestPlanByIds(resourceIds);
        Map<String, String> planMap = testPlans.stream()
                .collect(Collectors.toMap(TestPlan::getId, TestPlan::getName));

        issues.forEach(item -> {
            User createUser = userMap.get(item.getCreator());
            if (createUser != null) {
                item.setCreatorName(createUser.getName());
            }
            if (planMap.get(item.getResourceId()) != null) {
                item.setResourceName(planMap.get(item.getResourceId()));
            }
            TestCaseIssuesExample example = new TestCaseIssuesExample();
            example.createCriteria().andIssuesIdEqualTo(item.getId());
            List<TestCaseIssues> testCaseIssues = testCaseIssuesMapper.selectByExample(example);
            List<String> caseIds = testCaseIssues.stream()
                    .map(TestCaseIssues::getTestCaseId)
                    .collect(Collectors.toList());
            item.setCaseIds(caseIds);
            item.setCaseCount(testCaseIssues.size());
        });
        return issues;
    }

    public Map<String, List<IssuesDao>> getIssueMap(List<IssuesDao> issues) {
        Map<String, List<IssuesDao>> issueMap = new HashMap<>();
        issues.forEach(item -> {
            String platForm = item.getPlatform();
            if (StringUtils.equalsIgnoreCase(IssuesManagePlatform.Local.toString(), item.getPlatform())) {
                // 可能有大小写的问题
                platForm = IssuesManagePlatform.Local.toString();
            }
            List<IssuesDao> issuesDao = issueMap.get(platForm);
            if (issuesDao == null) {
                issuesDao = new ArrayList<>();
            }
            issuesDao.add(item);
            issueMap.put(platForm, issuesDao);
        });
        return issueMap;
    }

    public Map<String, AbstractIssuePlatform> getPlatformMap(IssuesRequest request) {
        Project project = projectService.getProjectById(request.getProjectId());
        List<String> platforms = getPlatforms(project);
        platforms.add(IssuesManagePlatform.Local.toString());
        return IssueFactory.createPlatformsForMap(platforms, request);
    }

    public void syncThirdPartyIssues() {
        List<String> projectIds = projectService.getProjectIds();
        projectIds.forEach(id -> {
            try {
                syncThirdPartyIssues(id);
            } catch (Exception e) {
                LogUtil.error(e.getMessage(), e);
            }
        });
    }

    public void issuesCount() {
        LogUtil.info("测试计划-测试用例同步缺陷信息开始");
        int pageSize = 100;
        int pages = 1;
        for (int i = 0; i < pages; i++) {
            Page<List<TestPlanTestCase>> page = PageHelper.startPage(i, pageSize, true);
            List<TestPlanTestCaseWithBLOBs> list = testPlanTestCaseService.listAll();
            pages = page.getPages();// 替换成真实的值
            list.forEach(l -> {
                testCaseIssueService.updateIssuesCount(l.getCaseId());
            });
        }
        LogUtil.info("测试计划-测试用例同步缺陷信息结束");
    }

    public void syncThirdPartyIssues(String projectId) {
        if (StringUtils.isNotBlank(projectId)) {
            Project project = projectService.getProjectById(projectId);
            List<IssuesDao> issues = extIssuesMapper.getIssueForSync(projectId);

            if (CollectionUtils.isEmpty(issues)) {
                return;
            }

            List<IssuesDao> tapdIssues = issues.stream()
                    .filter(item -> item.getPlatform().equals(IssuesManagePlatform.Tapd.name()))
                    .collect(Collectors.toList());
            List<IssuesDao> jiraIssues = issues.stream()
                    .filter(item -> item.getPlatform().equals(IssuesManagePlatform.Jira.name()))
                    .collect(Collectors.toList());
            List<IssuesDao> zentaoIssues = issues.stream()
                    .filter(item -> item.getPlatform().equals(IssuesManagePlatform.Zentao.name()))
                    .collect(Collectors.toList());
            List<IssuesDao> azureDevopsIssues = issues.stream()
                    .filter(item -> item.getPlatform().equals(IssuesManagePlatform.AzureDevops.name()))
                    .collect(Collectors.toList());

            IssuesRequest issuesRequest = new IssuesRequest();
            issuesRequest.setProjectId(projectId);
            issuesRequest.setWorkspaceId(project.getWorkspaceId());
            if (CollectionUtils.isNotEmpty(tapdIssues)) {
                TapdPlatform tapdPlatform = new TapdPlatform(issuesRequest);
                syncThirdPartyIssues(tapdPlatform::syncIssues, project, tapdIssues);
            }
            if (CollectionUtils.isNotEmpty(jiraIssues)) {
                JiraPlatform jiraPlatform = new JiraPlatform(issuesRequest);
                syncThirdPartyIssues(jiraPlatform::syncIssues, project, jiraIssues);
            }
            if (CollectionUtils.isNotEmpty(zentaoIssues)) {
                ZentaoPlatform zentaoPlatform = new ZentaoPlatform(issuesRequest);
                syncThirdPartyIssues(zentaoPlatform::syncIssues, project, zentaoIssues);
            }
            if (CollectionUtils.isNotEmpty(azureDevopsIssues)) {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                try {
                    Class clazz = loader.loadClass("io.metersphere.xpack.issue.azuredevops.AzureDevopsPlatform");
                    Constructor cons = clazz.getDeclaredConstructor(new Class[]{IssuesRequest.class});
                    AbstractIssuePlatform azureDevopsPlatform = (AbstractIssuePlatform) cons.newInstance(issuesRequest);
                    syncThirdPartyIssues(azureDevopsPlatform::syncIssues, project, azureDevopsIssues);
                } catch (Throwable e) {
                    LogUtil.error(e);
                }
            }
        }
    }

    public Integer issueCount(IssuesRequest request){
        String projectId = request.getProjectId();
        Project project = projectService.getProjectById(projectId);
        String version = testPlanService.getVersion(request.getPlanId(),projectId,project.getWorkspaceId());
        // 查询条件（标题）
        String input_title = request.getTitle();
        // 请求JIRA的url拼接
        String req_url = "";
        if(StringUtils.isNotBlank(projectId)) {
            request.setTestCaseId(request.getCaseId());
            req_url = getTssuesIdList(request,false,input_title);
            if(!version.equals("无")){
                req_url = req_url + " AND affectedVersion = " + version;
            }
        }
        IssuesRequest issuesRequest = new IssuesRequest();
        issuesRequest.setProjectId(projectId);
        issuesRequest.setWorkspaceId(project.getWorkspaceId());

        JiraPlatform jiraPlatform = new JiraPlatform(issuesRequest);
        return jiraPlatform.jiraIssueCount(project,req_url);
    }

    public List<IssuesDao> relateLocalIssues(IssuesRequest request){
        // 返回结果，本地所有去除已经关联的本地缺陷
        List<IssuesDao> issues = extIssuesMapper.getNotRelateIssues(request);
        if(issues!=null && issues.size()>0){
            for(IssuesDao issuesDao: issues) {
                List<String> jsonData = JSONObject.parseArray(issuesDao.getCustomFields(), String.class);
                if (jsonData != null && jsonData.size() > 0) {
                    issuesDao.setKey(issuesDao.getNum().toString());
                    issuesDao.setId(issuesDao.getId());
                    issuesDao.setNum(issuesDao.getNum());
                    issuesDao.setTitle(issuesDao.getTitle());
                    issuesDao.setPlatformStatus(issuesDao.getPlatformStatus());
                    issuesDao.setStatus(issuesDao.getStatus());
                    issuesDao.setPlatform(request.getPlatform());
                    issuesDao.setDescription(issuesDao.getDescription());
                    issuesDao.setCount(issuesDao.getCount());
                    issuesDao.setProjectId(request.getProjectId());
                    Project project = projectService.getProjectById(request.getProjectId());
                    issuesDao.setProjectName(project.getName());
                    User user = userMapper.selectByPrimaryKey(issuesDao.getCreator());
                    issuesDao.setCreatorName(user.getName());
                    for (String str : jsonData) {
                        String name = JSONObject.parseObject(str).getString("name");
                        if (name.equals("缺陷类型")) {
                            String issueType = JSONObject.parseObject(str).getString("value");
                            issuesDao.setIssueType(issueType);
                        }
                        if (name.equals("严重程度")) {
                            String severityLevel = JSONObject.parseObject(str).getString("value");
                            issuesDao.setSeverityLevel(severityLevel);
                            break;
                        }
                    }
                }
            }
        }
        return issues;
    }

    public Integer issueCountLocal(IssuesRequest request){
        // 返回条数
        return extIssuesMapper.issueCountLocal(request);
    }

    public List<IssuesDao> syncJiraIssues(IssuesRequest request, Integer goPage, Integer pageSize, String issuesIdList) {
        String req_url = "";
        String projectId = request.getProjectId();
        String input_title = request.getTitle();
        List<IssuesDao> list = new ArrayList<>();
        Project project = projectService.getProjectById(projectId);
        String version = testPlanService.getVersion(request.getPlanId(),projectId,project.getWorkspaceId());

        if (StringUtils.isNotBlank(projectId)) {
            if(issuesIdList.equals("")){
                request.setTestCaseId(request.getCaseId());
                issuesIdList = getTssuesIdList(request,false,input_title);
                if(goPage !=0 && pageSize !=0){
                    Integer startAtValue = 0 ;
                    Integer maxResultsValue = pageSize;
                    //第1页
                    if(goPage == 1){
                        startAtValue = 0;
                    }else{
                        startAtValue = maxResultsValue*(goPage-1);
                    }
                    if(!version.equals("无")){
                        req_url = issuesIdList + " AND affectedVersion = " + version + " ORDER BY created DESC&startAt=" + startAtValue.toString() + "&maxResults=" + maxResultsValue.toString();
                    }else{
                        req_url = issuesIdList  + " ORDER BY created DESC&startAt=" + startAtValue.toString() + "&maxResults=" + maxResultsValue.toString();
                    }
                }
            }else{
                //关联缺陷：列表, 查询条件：id in ()
                if(issuesIdList.equals("testCaseIssuesList.size=0")){
                    return list;
                }else{
                    req_url = issuesIdList;
                }
            }

            IssuesRequest issuesRequest = new IssuesRequest();
            issuesRequest.setProjectId(projectId);
            issuesRequest.setWorkspaceId(project.getWorkspaceId());

            JiraPlatform jiraPlatform = new JiraPlatform(issuesRequest);
            return jiraPlatform.syncJiraIssues(project,req_url);
        }else{
            return list;
        }
    }

    public void syncThirdPartyIssues(BiConsumer<Project, List<IssuesDao>> syncFuc, Project project, List<IssuesDao> issues) {
        try {
            syncFuc.accept(project, issues);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
        }
    }

    private String getConfig(String orgId, String platform) {
        IntegrationRequest request = new IntegrationRequest();
        if (StringUtils.isBlank(orgId)) {
            MSException.throwException("organization id is null");
        }
        request.setWorkspaceId(orgId);
        request.setPlatform(platform);

        ServiceIntegration integration = integrationService.get(request);
        return integration.getConfiguration();
    }

    public String getLogDetails(String id) {
        IssuesWithBLOBs issuesWithBLOBs = issuesMapper.selectByPrimaryKey(id);
        if (issuesWithBLOBs != null) {
            List<DetailColumn> columns = ReflexObjectUtil.getColumns(issuesWithBLOBs, TestPlanReference.issuesColumns);
            OperatingLogDetails details = new OperatingLogDetails(JSON.toJSONString(issuesWithBLOBs.getId()), issuesWithBLOBs.getProjectId(), issuesWithBLOBs.getTitle(), issuesWithBLOBs.getCreator(), columns);
            return JSON.toJSONString(details);
        }
        return null;
    }

    public String getLogDetails(IssuesUpdateRequest issuesRequest) {
        if (issuesRequest != null) {
            issuesRequest.setCreator(SessionUtils.getUserId());
            List<DetailColumn> columns = ReflexObjectUtil.getColumns(issuesRequest, TestPlanReference.issuesColumns);
            OperatingLogDetails details = new OperatingLogDetails(null, issuesRequest.getProjectId(), issuesRequest.getTitle(), issuesRequest.getCreator(), columns);
            return JSON.toJSONString(details);
        }
        return null;
    }

    public List<IssuesDao> relateList(IssuesRequest request) {
        return extIssuesMapper.getRelateIssues(request);
    }

    public void userAuth(AuthUserIssueRequest authUserIssueRequest) {
        IssuesRequest issuesRequest = new IssuesRequest();
        issuesRequest.setWorkspaceId(authUserIssueRequest.getWorkspaceId());
        AbstractIssuePlatform abstractPlatform = IssueFactory.createPlatform(authUserIssueRequest.getPlatform(), issuesRequest);
        abstractPlatform.userAuth(authUserIssueRequest);
    }

    public Set<String> getIssuesFromTestPlanTestCase(String planId){
        List<String> issuesList = testPlanTestCaseService.getTestPlanIssues(planId);
        Set<String> set = new HashSet<>();
        issuesList.forEach(issues_item ->{
            List<String> issuesData = JSONObject.parseArray(issues_item,String.class);
            issuesData.forEach(issuesDataItem ->{
                JSONObject issues = JSONObject.parseObject(issuesDataItem);
                set.add(issues.get("id").toString());
            });
        });
        return set;
    }
    public void calculatePlanReport(String planId, TestPlanSimpleReportDTO report, String projectId) {
        TestPlanFunctionResultReportDTO functionResult = report.getFunctionResult();
        List<TestCaseReportStatusResultDTO> statusResult = new ArrayList<>();

        Project project = projectService.getProjectById(projectId);
        String workSpaceId = project.getWorkspaceId();
        IssuesRequest issuesRequest = new IssuesRequest();
        issuesRequest.setProjectId(projectId);
        issuesRequest.setWorkspaceId(workSpaceId);
        JiraPlatform jiraPlatform = new JiraPlatform(issuesRequest);
        // 查出JIRA对应项目的所有缺陷状态
        List<String> statusType = jiraPlatform.getIssueStatus(project);
        statusType.add("已关联用例");
        String version = testPlanService.getVersion(planId,projectId,workSpaceId);
        statusType.forEach(item ->{
            Integer issueCountByStatus = 0;
            if(item.equals("已关联用例")){
                //通过test_plan_test_case表issues查询所有已关联的缺陷
                Set<String> set = getIssuesFromTestPlanTestCase(planId);
                issueCountByStatus = set.size();
            }else{
                //根据状态查询JIRA对应缺陷数量
                issueCountByStatus = jiraPlatform.getIssueStatusCount(project,version,item);
            }
            TestCaseReportStatusResultDTO testCaseReportStatusResultDTO = new TestCaseReportStatusResultDTO();
            testCaseReportStatusResultDTO.setStatus(item);
            testCaseReportStatusResultDTO.setCount(issueCountByStatus);
            statusResult.add(testCaseReportStatusResultDTO);
        });
        functionResult.setIssueData(statusResult);
    }

//    public void calculatePlanReport(String planId, TestPlanSimpleReportDTO report) {
//        List<PlanReportIssueDTO> planReportIssueDTOS = extIssuesMapper.selectForPlanReport(planId);
//        TestPlanFunctionResultReportDTO functionResult = report.getFunctionResult();
//        List<TestCaseReportStatusResultDTO> statusResult = new ArrayList<>();
//        Map<String, TestCaseReportStatusResultDTO> statusResultMap = new HashMap<>();
//
//        planReportIssueDTOS.forEach(item -> {
//            String status = null;
//            // 本地缺陷
//            if (StringUtils.equalsIgnoreCase(item.getPlatform(), IssuesManagePlatform.Local.name())
//                    || StringUtils.isBlank(item.getPlatform())) {
//                status = item.getStatus();
//            } else {
//                status = item.getPlatformStatus();
//            }
//            if (StringUtils.isBlank(status)) {
//                status = IssuesStatus.NEW.toString();
//            }
//            TestPlanUtils.getStatusResultMap(statusResultMap, status);
//        });
//        Set<String> status = statusResultMap.keySet();
//        status.forEach(item -> {
//            TestPlanUtils.addToReportStatusResultList(statusResultMap, statusResult, item);
//        });
//        functionResult.setIssueData(statusResult);
//    }

    public List<IssuesDao> getIssuesByPlanoId(String planId) {
        IssuesRequest issueRequest = new IssuesRequest();
        issueRequest.setResourceId(planId);
        return extIssuesMapper.getIssues(issueRequest);
    }

    public void changeStatus(IssuesRequest request) {
        String issuesId = request.getId();
        String status = request.getStatus();
        if (StringUtils.isBlank(issuesId) || StringUtils.isBlank(status)) {
            return;
        }

        IssuesWithBLOBs issues = issuesMapper.selectByPrimaryKey(issuesId);
        String customFields = issues.getCustomFields();
        if (StringUtils.isBlank(customFields)) {
            return;
        }

        List<TestCaseBatchRequest.CustomFiledRequest> fields = JSONObject.parseArray(customFields, TestCaseBatchRequest.CustomFiledRequest.class);
        for (TestCaseBatchRequest.CustomFiledRequest field : fields) {
            if (StringUtils.equals("状态", field.getName())) {
                field.setValue(status);
                break;
            }
        }
        issues.setStatus(status);
        issues.setCustomFields(JSONObject.toJSONString(fields));
        issuesMapper.updateByPrimaryKeySelective(issues);
    }

    public List<IssuesDao> getCountByStatus(IssuesRequest request){
        return extIssuesMapper.getCountByStatus(request);

    }
}
