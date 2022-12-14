package io.metersphere.track.service;


import com.alibaba.excel.EasyExcelFactory;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.api.dto.automation.ApiScenarioDTO;
import io.metersphere.api.dto.automation.ApiScenarioRequest;
import io.metersphere.api.dto.definition.ApiTestCaseDTO;
import io.metersphere.api.dto.definition.ApiTestCaseRequest;
import io.metersphere.api.service.ApiAutomationService;
import io.metersphere.api.service.ApiTestCaseService;
import io.metersphere.base.domain.*;
import io.metersphere.base.mapper.*;
import io.metersphere.base.mapper.ext.ExtTestCaseMapper;
import io.metersphere.commons.constants.TestCaseConstants;
import io.metersphere.commons.constants.TestCaseReviewStatus;
import io.metersphere.commons.constants.UserGroupType;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.user.SessionUser;
import io.metersphere.commons.utils.*;
import io.metersphere.controller.request.OrderRequest;
import io.metersphere.controller.request.ResetOrderRequest;
import io.metersphere.controller.request.member.QueryMemberRequest;
import io.metersphere.dto.*;
import io.metersphere.excel.domain.ExcelErrData;
import io.metersphere.excel.domain.ExcelResponse;
import io.metersphere.excel.domain.TestCaseExcelData;
import io.metersphere.excel.domain.TestCaseExcelDataFactory;
import io.metersphere.excel.handler.FunctionCaseTemplateWriteHandler;
import io.metersphere.excel.listener.TestCaseNoModelDataListener;
import io.metersphere.excel.utils.EasyExcelExporter;
import io.metersphere.excel.utils.FunctionCaseImportEnum;
import io.metersphere.i18n.Translator;
import io.metersphere.log.utils.ReflexObjectUtil;
import io.metersphere.log.vo.DetailColumn;
import io.metersphere.log.vo.OperatingLogDetails;
import io.metersphere.log.vo.track.TestCaseReference;
import io.metersphere.performance.service.PerformanceTestService;
import io.metersphere.service.*;
import io.metersphere.track.dto.TestCaseCommentDTO;
import io.metersphere.track.dto.TestCaseDTO;
import io.metersphere.track.request.testcase.EditTestCaseRequest;
import io.metersphere.track.request.testcase.QueryTestCaseRequest;
import io.metersphere.track.request.testcase.TestCaseBatchRequest;
import io.metersphere.track.request.testcase.TestCaseMinderEditRequest;
import io.metersphere.track.request.testplan.LoadCaseRequest;
import io.metersphere.xmind.XmindCaseParser;
import io.metersphere.xmind.pojo.TestCaseXmindData;
import io.metersphere.xmind.utils.XmindExportUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class TestCaseService {
    @Value("${auto_web}")
    private String auto_url;

    @Resource
    TestCaseNodeMapper testCaseNodeMapper;

    @Resource
    TestCaseMapper testCaseMapper;

    @Resource
    ExtTestCaseMapper extTestCaseMapper;

    @Resource
    UserService userService;

    @Resource
    UserMapper userMapper;

    @Resource
    TestPlanTestCaseMapper testPlanTestCaseMapper;

    @Resource
    ProjectMapper projectMapper;

    @Lazy
    @Resource
    ProjectService projectService;

    @Resource
    SqlSessionFactory sqlSessionFactory;

    @Resource
    TestCaseNodeService testCaseNodeService;

    @Resource
    ApiTestCaseMapper apiTestCaseMapper;

    @Resource
    TestCaseIssueService testCaseIssueService;
    @Resource
    TestCaseCommentService testCaseCommentService;
    @Resource
    FileService fileService;
    @Resource
    TestCaseFileMapper testCaseFileMapper;
    @Resource
    TestCaseTestMapper testCaseTestMapper;
    @Resource
    private GroupMapper groupMapper;
    @Resource
    private UserGroupMapper userGroupMapper;
    @Resource
    private LoadTestMapper loadTestMapper;
    @Resource
    private ApiScenarioMapper apiScenarioMapper;
    @Resource
    private TestCaseIssuesMapper testCaseIssuesMapper;
    @Resource
    private IssuesMapper issuesMapper;
    @Resource
    private RelationshipEdgeService relationshipEdgeService;
    @Resource
    @Lazy
    private ApiTestCaseService apiTestCaseService;
    @Resource
    @Lazy
    private ApiAutomationService apiAutomationService;
    @Resource
    @Lazy
    private PerformanceTestService performanceTestService;
    @Resource
    private TestCaseFollowMapper testCaseFollowMapper;
    @Resource
    @Lazy
    private TestPlanService testPlanService;

    private void setNode(TestCaseWithBLOBs testCase) {
        if (StringUtils.isEmpty(testCase.getNodeId()) || "default-module".equals(testCase.getNodeId())) {
            TestCaseNodeExample example = new TestCaseNodeExample();
            example.createCriteria().andProjectIdEqualTo(testCase.getProjectId()).andNameEqualTo("???????????????");
            List<TestCaseNode> nodes = testCaseNodeMapper.selectByExample(example);
            if (CollectionUtils.isNotEmpty(nodes)) {
                testCase.setNodeId(nodes.get(0).getId());
                testCase.setNodePath("/" + nodes.get(0).getName());
            }
        }
    }

    public TestCaseWithBLOBs addTestCase(EditTestCaseRequest request) {
        request.setName(request.getName());
        checkTestCaseExist(request);
        request.setId(request.getId());
        request.setCreateTime(System.currentTimeMillis());
        request.setUpdateTime(System.currentTimeMillis());
        checkTestCustomNum(request);
        request.setNum(getNextNum(request.getProjectId()));
        if (StringUtils.isBlank(request.getCustomNum())) {
            request.setCustomNum(request.getNum().toString());
        }
        request.setReviewStatus(TestCaseReviewStatus.Prepare.name());
        request.setStatus(TestCaseReviewStatus.Prepare.name());
        request.setDemandId(request.getDemandId());
        request.setDemandName(request.getDemandName());
        request.setCreateUser(SessionUtils.getUserId());
        this.setNode(request);
        request.setOrder(ServiceUtils.getNextOrder(request.getProjectId(), extTestCaseMapper::getLastOrder));
        testCaseMapper.insert(request);
        saveFollows(request.getId(), request.getFollows());
        return request;
    }

    private void saveFollows(String caseId, List<String> follows) {
        TestCaseFollowExample example = new TestCaseFollowExample();
        example.createCriteria().andCaseIdEqualTo(caseId);
        testCaseFollowMapper.deleteByExample(example);
        if (!CollectionUtils.isEmpty(follows)) {
            for (String follow : follows) {
                TestCaseFollow caseFollow = new TestCaseFollow();
                caseFollow.setCaseId(caseId);
                caseFollow.setFollowId(follow);
                testCaseFollowMapper.insert(caseFollow);
            }
        }
    }

    private void checkTestCustomNum(TestCaseWithBLOBs testCase) {
        if (StringUtils.isNotBlank(testCase.getCustomNum())) {
            String projectId = testCase.getProjectId();
            Project project = projectService.getProjectById(projectId);
            if (project != null) {
                Boolean customNum = project.getCustomNum();
                // ??????????????????ID
                if (!customNum) {
                    testCase.setCustomNum(null);
                } else {
                    checkCustomNumExist(testCase);
                }
            } else {
                MSException.throwException("add test case fail, project is not find.");
            }
        }
    }

    private void checkCustomNumExist(TestCaseWithBLOBs testCase) {
        TestCaseExample example = new TestCaseExample();
        example.createCriteria()
                .andCustomNumEqualTo(testCase.getCustomNum())
                .andProjectIdEqualTo(testCase.getProjectId())
                .andIdNotEqualTo(testCase.getId());
        List<TestCase> list = testCaseMapper.selectByExample(example);
        if (CollectionUtils.isNotEmpty(list)) {
            MSException.throwException(Translator.get("custom_num_is_exist"));
        }
    }

    public List<TestCase> getTestCaseByNodeId(List<String> nodeIds) {
        TestCaseExample testCaseExample = new TestCaseExample();
        testCaseExample.createCriteria().andNodeIdIn(nodeIds);
        return testCaseMapper.selectByExample(testCaseExample);
    }

    public TestCaseWithBLOBs getTestCase(String testCaseId) {
        return testCaseMapper.selectByPrimaryKey(testCaseId);
    }

    public int editTestCase(TestCaseWithBLOBs testCase) {
        checkTestCustomNum(testCase);
        testCase.setUpdateTime(System.currentTimeMillis());
        return testCaseMapper.updateByPrimaryKeySelective(testCase);
    }

    public TestCaseWithBLOBs checkTestCaseExist(TestCaseWithBLOBs testCase) {

        // ?????????????????????????????????????????????
        if (testCase != null) {

            /*
            ???????????????/??????5???????????????????????????????????????5????????????/??????5/??????????????????5/??????
            ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????/??????5??????
            ?????????5?????????/??????5/???????????????5/???????????????????????????????????????????????????
            ??????????????????node_path?????????/??????5??????????????????
             */
            String nodePath = testCase.getNodePath();
            if (!nodePath.startsWith("/")) {
                nodePath = "/" + nodePath;
            }
            if (nodePath.endsWith("/")) {
                nodePath = nodePath.substring(0, nodePath.length() - 1);
            }

            TestCaseExample example = new TestCaseExample();
            TestCaseExample.Criteria criteria = example.createCriteria();
            criteria.andNameEqualTo(testCase.getName())
                    .andProjectIdEqualTo(testCase.getProjectId())
                    .andNodePathEqualTo(nodePath)
                    .andTypeEqualTo(testCase.getType())
//                    .andMaintainerEqualTo(testCase.getMaintainer())
                    .andPriorityEqualTo(testCase.getPriority());
//                    .andMethodEqualTo(testCase.getMethod());

//            if (StringUtils.isNotBlank(testCase.getNodeId())) {
//                criteria.andNodeIdEqualTo(testCase.getTestId());
//            }

            if (StringUtils.isNotBlank(testCase.getTestId())) {
                criteria.andTestIdEqualTo(testCase.getTestId());
            }

            if (StringUtils.isNotBlank(testCase.getId())) {
                criteria.andIdNotEqualTo(testCase.getId());
            }
            if (StringUtils.isNotBlank(testCase.getStatus())){
                criteria.andStatusNotEqualTo(testCase.getStatus());
            }
            List<TestCaseWithBLOBs> caseList = testCaseMapper.selectByExampleWithBLOBs(example);

            // ?????????????????????????????????????????? remark ??? steps
            if (!CollectionUtils.isEmpty(caseList)) {
                String caseRemark = testCase.getRemark() == null ? "" : testCase.getRemark();
                String caseSteps = testCase.getSteps() == null ? "" : testCase.getSteps();
                String casePrerequisite = testCase.getPrerequisite() == null ? "" : testCase.getPrerequisite();
                for (TestCaseWithBLOBs tc : caseList) {
                    String steps = tc.getSteps() == null ? "" : tc.getSteps();
                    String remark = tc.getRemark() == null ? "" : tc.getRemark();
                    String prerequisite = tc.getPrerequisite() == null ? "" : tc.getPrerequisite();
                    if (StringUtils.equals(steps, caseSteps) && StringUtils.equals(remark, caseRemark) && StringUtils.equals(prerequisite, casePrerequisite)) {
                        //MSException.throwException(Translator.get("test_case_already_exists"));
                        return tc;
                    }
                }
            }
        }
        return null;
    }

    /**
     * ??????id???pojectId??????id??????????????????????????????
     * ??????????????????id?????????????????????,id???projectId?????????????????????
     */
    public String checkIdExist(Integer id, String projectId) {
        TestCaseExample example = new TestCaseExample();
        TestCaseExample.Criteria criteria = example.createCriteria();
        if (null != id) {
            criteria.andNumEqualTo(id);
            criteria.andProjectIdEqualTo(projectId);
            List<TestCase> testCaseList = testCaseMapper.selectByExample(example);    //????????????????????????ID?????????
            if (testCaseList.isEmpty()) {  //??????ID?????????
                return null;
            } else { //?????????ID?????????
                return testCaseList.get(0).getId();
            }
        }
        return null;
    }

    public String checkCustomIdExist(String id, String projectId) {
        TestCaseExample example = new TestCaseExample();
        TestCaseExample.Criteria criteria = example.createCriteria();
        if (null != id) {
            criteria.andCustomNumEqualTo(id);
            criteria.andProjectIdEqualTo(projectId);
            List<TestCase> testCaseList = testCaseMapper.selectByExample(example);    //????????????????????????ID?????????
            if (testCaseList.isEmpty()) {  //??????ID?????????
                return null;
            } else { //?????????ID?????????
                return testCaseList.get(0).getId();
            }
        }
        return null;
    }

    public int deleteTestCase(String testCaseId) {
        TestPlanTestCaseExample example = new TestPlanTestCaseExample();
        example.createCriteria().andCaseIdEqualTo(testCaseId);
        testPlanTestCaseMapper.deleteByExample(example);
        testCaseIssueService.delTestCaseIssues(testCaseId);
        testCaseCommentService.deleteCaseComment(testCaseId);
        TestCaseTestExample examples = new TestCaseTestExample();
        examples.createCriteria().andTestCaseIdEqualTo(testCaseId);
        testCaseTestMapper.deleteByExample(examples);
        relateDelete(testCaseId);
        relationshipEdgeService.delete(testCaseId); // ???????????????
        deleteFollows(testCaseId);
        return testCaseMapper.deleteByPrimaryKey(testCaseId);
    }

    private void deleteFollows(String testCaseId) {
        TestCaseFollowExample example = new TestCaseFollowExample();
        example.createCriteria().andCaseIdEqualTo(testCaseId);
        testCaseFollowMapper.deleteByExample(example);
    }

    public int deleteTestCaseToGc(String testCaseId) {
        TestCase testCase = new TestCase();
        testCase.setId(testCaseId);
        testCase.setDeleteUserId(SessionUtils.getUserId());
        testCase.setDeleteTime(System.currentTimeMillis());
        return extTestCaseMapper.deleteToGc(testCase);
    }

    public List<TestCaseDTO> listTestCase(QueryTestCaseRequest request) {
        this.initRequest(request, true);
        setDefaultOrder(request);
        if (request.getFilters() != null && !request.getFilters().containsKey("status")) {
            request.getFilters().put("status", new ArrayList<>(0));
        }
        List<TestCaseDTO> returnList = extTestCaseMapper.list(request);
        returnList = this.parseStatus(returnList);

        QueryMemberRequest queryMemberRequest = new QueryMemberRequest();
        queryMemberRequest.setWorkspaceId(SessionUtils.getCurrentProjectId());
        Map<String, String> userMap = userService.getMemberList(queryMemberRequest)
                .stream().collect(Collectors.toMap(User::getId, User::getName));
        returnList.forEach(item -> {
            item.setMaintainerName(userMap.get(item.getMaintainer()));
        });

        return returnList;
    }
    public void setDefaultOrder(QueryTestCaseRequest request) {
        List<OrderRequest> orders = ServiceUtils.getDefaultSortOrder(request.getOrders());
        OrderRequest order = new OrderRequest();
        // ????????????????????????????????????
        order.setName("sort");
        order.setType("desc");
        orders.add(order);
        request.setOrders(orders);
    }

    private List<TestCaseDTO> parseStatus(List<TestCaseDTO> returnList) {
        TestCaseExcelData excelData = new TestCaseExcelDataFactory().getTestCaseExcelDataLocal();
        for (TestCaseDTO data : returnList) {
            String dataStatus = excelData.parseStatus(data.getStatus());

            if (StringUtils.equalsAnyIgnoreCase(data.getStatus(), "Trash")) {
                try {
                    JSONArray arr = JSONArray.parseArray(data.getCustomFields());
                    JSONArray newArr = new JSONArray();
                    for (int i = 0; i < arr.size(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        if (obj.containsKey("name") && obj.containsKey("value")) {
                            String name = obj.getString("name");
                            if (StringUtils.equalsAny(name, "????????????", "????????????", "Case status")) {
                                obj.put("value", dataStatus);
                            }
                        }
                        newArr.add(obj);
                    }
                    data.setCustomFields(newArr.toJSONString());
                } catch (Exception e) {

                }
            }

            data.setStatus(dataStatus);
        }
        return returnList;
    }

    /**
     * ?????????????????????
     *
     * @param request
     * @param checkThisWeekData
     * @return
     */
    private void initRequest(QueryTestCaseRequest request, boolean checkThisWeekData) {
        if (checkThisWeekData) {
            Map<String, Date> weekFirstTimeAndLastTime = DateUtils.getWeedFirstTimeAndLastTime(new Date());
            Date weekFirstTime = weekFirstTimeAndLastTime.get("firstTime");
            if (request.isSelectThisWeedData()) {
                if (weekFirstTime != null) {
                    request.setCreateTime(weekFirstTime.getTime());
                }
            }
            if (request.isSelectThisWeedRelevanceData()) {
                if (weekFirstTime != null) {
                    request.setRelevanceCreateTime(weekFirstTime.getTime());
                }
            }

        }
    }

    public List<TestCaseDTO> listTestCaseMthod(QueryTestCaseRequest request) {
        return extTestCaseMapper.listByMethod(request);
    }


    /**
     * ??????????????????
     * ???????????????
     *
     * @param request
     * @return
     */
    public Pager<List<TestCase>> getTestCaseRelateList(QueryTestCaseRequest request, int goPage, int pageSize) {
        setDefaultOrder(request);
        request.getOrders().forEach(order -> {
            order.setPrefix("test_case");
        });
        if (testPlanService.isAllowedRepeatCase(request.getPlanId())) {
            request.setRepeatCase(true);
        }
        Page<Object> page = PageHelper.startPage(goPage, pageSize, true);
        return PageUtils.setPageInfo(page, getTestCaseByNotInPlan(request));
    }

    public List<TestCase> getTestCaseByNotInPlan(QueryTestCaseRequest request) {
        return extTestCaseMapper.getTestCaseByNotInPlan(request);
    }

    public List<TestCaseDTO> getTestCaseByNotInIssue(QueryTestCaseRequest request) {
        List<TestCaseDTO> list = extTestCaseMapper.getTestCaseByNotInIssue(request);
        addProjectName(list);
        return list;
    }

    public void addProjectName(List<TestCaseDTO> list) {
        List<String> projectIds = list.stream()
                .map(TestCase::getProjectId)
                .collect(Collectors.toList());
        List<Project> projects = projectService.getProjectByIds(projectIds);
        Map<String, String> projectMap = projects.stream()
                .collect(Collectors.toMap(Project::getId, Project::getName));
        list.forEach(item -> {
            String projectName = projectMap.get(item.getProjectId());
            if (StringUtils.isNotBlank(projectName)) {
                item.setProjectName(projectName);
            }
        });
    }

    public List<TestCase> getReviewCase(QueryTestCaseRequest request) {
        setDefaultOrder(request);
        request.getOrders().forEach(order -> {
            order.setPrefix("test_case");
        });
        return extTestCaseMapper.getTestCaseByNotInReview(request);
    }


    public List<TestCase> recentTestPlans(QueryTestCaseRequest request, int count) {
        PageHelper.startPage(1, count, true);
        TestCaseExample testCaseExample = new TestCaseExample();
        TestCaseExample.Criteria criteria = testCaseExample.createCriteria();
        criteria.andMaintainerEqualTo(request.getUserId());
        if (StringUtils.isNotBlank(request.getProjectId())) {
            criteria.andProjectIdEqualTo(request.getProjectId());
            testCaseExample.setOrderByClause("order desc, sort desc");
            return testCaseMapper.selectByExample(testCaseExample);
        }
        return new ArrayList<>();
    }

    public Project getProjectByTestCaseId(String testCaseId) {
        TestCaseWithBLOBs testCaseWithBLOBs = testCaseMapper.selectByPrimaryKey(testCaseId);
        if (testCaseWithBLOBs == null) {
            return null;
        }
        return projectMapper.selectByPrimaryKey(testCaseWithBLOBs.getProjectId());
    }


    public ExcelResponse testCaseImport(MultipartFile multipartFile, String projectId, String userId, String importType, HttpServletRequest request) {

        ExcelResponse excelResponse = new ExcelResponse();
        boolean isUpdated = false;  //???????????????????????????
        String currentWorkspaceId = SessionUtils.getCurrentWorkspaceId();
        QueryTestCaseRequest queryTestCaseRequest = new QueryTestCaseRequest();
        queryTestCaseRequest.setProjectId(projectId);
        boolean useCunstomId = projectService.useCustomNum(projectId);
        List<TestCase> testCases = extTestCaseMapper.getTestCaseNames(queryTestCaseRequest);
        Set<String> savedIds = new HashSet<>();
        Set<String> testCaseNames = new HashSet<>();
        for (TestCase testCase : testCases) {
            if (useCunstomId) {
                savedIds.add(testCase.getCustomNum());
            } else {
                savedIds.add(String.valueOf(testCase.getNum()));
            }

            testCaseNames.add(testCase.getName());
        }
        List<ExcelErrData<TestCaseExcelData>> errList = null;
        if (multipartFile == null) {
            MSException.throwException(Translator.get("upload_fail"));
        }
        if (multipartFile.getOriginalFilename().endsWith(".xmind")) {
            try {
                XmindCaseParser xmindParser = new XmindCaseParser(this, userId, projectId, testCaseNames, useCunstomId, importType);
                errList = xmindParser.parse(multipartFile);
                if (CollectionUtils.isEmpty(xmindParser.getNodePaths())
                        && CollectionUtils.isEmpty(xmindParser.getTestCase())
                        && CollectionUtils.isEmpty(xmindParser.getUpdateTestCase())) {
                    if (errList == null) {
                        errList = new ArrayList<>();
                    }
                    ExcelErrData excelErrData = new ExcelErrData(null, 1, Translator.get("upload_fail") + "???" + Translator.get("upload_content_is_null"));
                    errList.add(excelErrData);
                    excelResponse.setErrList(errList);
                }
                if (errList.isEmpty()) {
                    List<String> names = new LinkedList<>();
                    List<String> ids = new LinkedList<>();
                    if (CollectionUtils.isNotEmpty(xmindParser.getNodePaths())) {
                        testCaseNodeService.createNodes(xmindParser.getNodePaths(), projectId);
                    }
                    if (CollectionUtils.isNotEmpty(xmindParser.getTestCase())) {
//                        Collections.reverse(xmindParser.getTestCase());
                        this.saveImportData(xmindParser.getTestCase(), projectId);
                        names = xmindParser.getTestCase().stream().map(TestCase::getName).collect(Collectors.toList());
                        ids = xmindParser.getTestCase().stream().map(TestCase::getId).collect(Collectors.toList());
                    }
                    if (CollectionUtils.isNotEmpty(xmindParser.getUpdateTestCase())) {
                        this.updateImportData(xmindParser.getUpdateTestCase(), projectId);
                        names.addAll(xmindParser.getUpdateTestCase().stream().map(TestCase::getName).collect(Collectors.toList()));
                        ids.addAll(xmindParser.getUpdateTestCase().stream().map(TestCase::getId).collect(Collectors.toList()));
                    }
                    request.setAttribute("ms-req-title", String.join(",", names));
                    request.setAttribute("ms-req-source-id", JSON.toJSONString(ids));
                }
                xmindParser.clear();
            } catch (Exception e) {
                LogUtil.error(e.getMessage(), e);
                MSException.throwException(e.getMessage());
            }

        } else {

            QueryMemberRequest queryMemberRequest = new QueryMemberRequest();
            queryMemberRequest.setProjectId(projectId);
            Set<String> userIds = userService.getProjectMemberList(queryMemberRequest)
                    .stream()
                    .map(User::getId)
                    .collect(Collectors.toSet());

            try {
                //??????????????????????????????????????????????????????????????????????????????
                Class clazz = new TestCaseExcelDataFactory().getExcelDataByLocal();
                TestCaseTemplateService testCaseTemplateService = CommonBeanFactory.getBean(TestCaseTemplateService.class);
                TestCaseTemplateDao testCaseTemplate = testCaseTemplateService.getTemplate(projectId);
                List<CustomFieldDao> customFields = null;
                if (testCaseTemplate == null) {
                    customFields = new ArrayList<>();
                } else {
                    customFields = testCaseTemplate.getCustomFields();
                }
                TestCaseNoModelDataListener easyExcelListener = new TestCaseNoModelDataListener(false, clazz, customFields, projectId, testCaseNames, savedIds, userIds, useCunstomId, importType);
                //??????excel??????
                EasyExcelFactory.read(multipartFile.getInputStream(), easyExcelListener).sheet().doRead();
                request.setAttribute("ms-req-title", String.join(",", easyExcelListener.getNames()));
                request.setAttribute("ms-req-source-id", JSON.toJSONString(easyExcelListener.getIds()));

                errList = easyExcelListener.getErrList();
                isUpdated = easyExcelListener.isUpdated();
            } catch (Exception e) {
                LogUtil.error(e.getMessage(), e);
                MSException.throwException(e.getMessage());
            }

        }
        //?????????????????????????????????????????????
        if (!errList.isEmpty()) {
            excelResponse.setSuccess(false);
            excelResponse.setErrList(errList);
            excelResponse.setIsUpdated(isUpdated);
        } else {
            excelResponse.setSuccess(true);
        }
        return excelResponse;
    }

    public void saveImportData(List<TestCaseWithBLOBs> testCases, String projectId) {
        Map<String, String> nodePathMap = testCaseNodeService.createNodeByTestCases(testCases, projectId);
        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        Project project = projectService.getProjectById(projectId);
        TestCaseMapper mapper = sqlSession.getMapper(TestCaseMapper.class);
        Long nextOrder = ServiceUtils.getNextOrder(projectId, extTestCaseMapper::getLastOrder);
        nextOrder +=  (testCases.size()-1) * 5000;
        if (!testCases.isEmpty()) {
            AtomicInteger sort = new AtomicInteger();
            AtomicInteger num = new AtomicInteger();
            num.set(getNextNum(projectId) + testCases.size());
            for (TestCaseWithBLOBs testcase: testCases) {
                testcase.setId(UUID.randomUUID().toString());
                testcase.setCreateUser(SessionUtils.getUserId());
                testcase.setCreateTime(System.currentTimeMillis());
                testcase.setUpdateTime(System.currentTimeMillis());
                testcase.setNodeId(nodePathMap.get(testcase.getNodePath()));
                //testcase.setSort(sort.getAndIncrement());
                //int number = num.decrementAndGet();
                testcase.setSort(sort.getAndDecrement());
                int number = num.incrementAndGet();
                testcase.setNum(number);
                if (project.getCustomNum() && StringUtils.isBlank(testcase.getCustomNum())) {
                    testcase.setCustomNum(String.valueOf(number));
                }
                testcase.setReviewStatus(TestCaseReviewStatus.Prepare.name());
                testcase.setOrder(nextOrder);
                mapper.insert(testcase);
                nextOrder -= 5000;
            }
        }
        sqlSession.flushStatements();
    }

    public void updateImportData(List<TestCaseWithBLOBs> testCases, String projectId) {
        Map<String, String> nodePathMap = testCaseNodeService.createNodeByTestCases(testCases, projectId);
        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        TestCaseMapper mapper = sqlSession.getMapper(TestCaseMapper.class);
        if (!testCases.isEmpty()) {
            AtomicInteger sort = new AtomicInteger();
            AtomicInteger num = new AtomicInteger();
            num.set(getNextNum(projectId) + testCases.size());
            testCases.forEach(testcase -> {
                TestCaseWithBLOBs oldCase = testCaseMapper.selectByPrimaryKey(testcase.getId());
                String customFieldStr = this.updateCustomField(oldCase.getCustomFields(),testcase.getPriority());
                testcase.setUpdateTime(System.currentTimeMillis());
                testcase.setNodeId(nodePathMap.get(testcase.getNodePath()));
                testcase.setSort(sort.getAndIncrement());
                if (testcase.getNum() == null) {
                    testcase.setNum(num.decrementAndGet());
                }
                testcase.setReviewStatus(TestCaseReviewStatus.Prepare.name());
                testcase.setCustomFields(customFieldStr);
                mapper.updateByPrimaryKeySelective(testcase);
            });
        }
        sqlSession.flushStatements();
    }

    private String updateCustomField(String customFields, String priority) {
        try {
            JSONArray newArr = new JSONArray();
            JSONArray customArr = JSONArray.parseArray(customFields);
            for(int i = 0; i < customArr.size();i++){
                JSONObject obj = customArr.getJSONObject(i);
                if(obj.containsKey("name") && StringUtils.equalsIgnoreCase(obj.getString("name"),"????????????")){
                    obj.put("value",priority);
                }
                newArr.add(obj);
            }
            customFields = newArr.toJSONString();
        }catch (Exception e){

        }
        return customFields;
    }

    /**
     * ???Excel??????ID???????????????????????????
     * feat(????????????):??????Excel??????????????????ID??????????????????Excel???????????????????????? (#1727)
     *
     * @param testCases
     * @param projectId
     */
    public void updateImportDataCarryId(List<TestCaseWithBLOBs> testCases, String projectId) {
        Map<String, String> nodePathMap = testCaseNodeService.createNodeByTestCases(testCases, projectId);
        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        TestCaseMapper mapper = sqlSession.getMapper(TestCaseMapper.class);

        /*
        ????????????????????????????????????id??????????????????ID????????????
         */
        List<Integer> nums = testCases.stream()
                .map(TestCase::getNum)
                .collect(Collectors.toList());
        TestCaseExample example = new TestCaseExample();
        example.createCriteria().andNumIn(nums)
                .andProjectIdEqualTo(projectId);
        List<TestCase> testCasesList = testCaseMapper.selectByExample(example);
        Map<Integer, String> numIdMap = testCasesList.stream()
                .collect(Collectors.toMap(TestCase::getNum, TestCase::getId));


        if (!testCases.isEmpty()) {
            AtomicInteger sort = new AtomicInteger();
            testCases.forEach(testcase -> {
                testcase.setUpdateTime(System.currentTimeMillis());
                testcase.setNodeId(nodePathMap.get(testcase.getNodePath()));
                testcase.setSort(sort.getAndIncrement());
                testcase.setId(numIdMap.get(testcase.getNum()));
                mapper.updateByPrimaryKeySelective(testcase);
            });
        }
        sqlSession.flushStatements();
    }

    /**
     * ???Excel??????ID???????????????????????????
     * feat(????????????):??????Excel??????????????????ID??????????????????Excel???????????????????????? (#1727)
     *
     * @param testCases
     * @param projectId
     */
    public void updateImportDataCustomId(List<TestCaseWithBLOBs> testCases, String projectId) {
        Map<String, String> nodePathMap = testCaseNodeService.createNodeByTestCases(testCases, projectId);
        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        TestCaseMapper mapper = sqlSession.getMapper(TestCaseMapper.class);

        /*
        ????????????????????????????????????id??????????????????ID????????????
         */
        List<String> customIds = testCases.stream()
                .map(TestCase::getCustomNum)
                .collect(Collectors.toList());
        TestCaseExample example = new TestCaseExample();
        example.createCriteria().andCustomNumIn(customIds)
                .andProjectIdEqualTo(projectId);
        List<TestCase> testCasesList = testCaseMapper.selectByExample(example);
        Map<String, String> customIdMap = testCasesList.stream()
                .collect(Collectors.toMap(TestCase::getCustomNum, TestCase::getId, (k1,k2) -> k1));


        if (!testCases.isEmpty()) {
            AtomicInteger sort = new AtomicInteger();
            testCases.forEach(testcase -> {
                testcase.setUpdateTime(System.currentTimeMillis());
                testcase.setNodeId(nodePathMap.get(testcase.getNodePath()));
                testcase.setSort(sort.getAndIncrement());
                testcase.setId(customIdMap.get(testcase.getCustomNum()));
                mapper.updateByPrimaryKeySelective(testcase);
            });
        }
        sqlSession.flushStatements();
    }

    public void testCaseTemplateExport(String projectId, String importType, HttpServletResponse response) {
        try {
            TestCaseExcelData testCaseExcelData = new TestCaseExcelDataFactory().getTestCaseExcelDataLocal();


            boolean useCustomNum = projectService.useCustomNum(projectId);
            boolean importFileNeedNum = false;
            if (useCustomNum || StringUtils.equals(importType, FunctionCaseImportEnum.Update.name())) {
                //???????????? or ?????????????????????ID????????????ID???
                importFileNeedNum = true;
            }

            TestCaseTemplateService testCaseTemplateService = CommonBeanFactory.getBean(TestCaseTemplateService.class);
            TestCaseTemplateDao testCaseTemplate = testCaseTemplateService.getTemplate(projectId);
            List<CustomFieldDao> customFields = null;
            if (testCaseTemplate == null) {
                customFields = new ArrayList<>();
            } else {
                customFields = testCaseTemplate.getCustomFields();
            }

            List<List<String>> headList = testCaseExcelData.getHead(importFileNeedNum, customFields);
            EasyExcelExporter easyExcelExporter = new EasyExcelExporter(testCaseExcelData.getClass());
            Map<String,List<String>> caseLevelAndStatusValueMap = testCaseTemplateService.getCaseLevelAndStatusMapByProjectId(projectId);
            FunctionCaseTemplateWriteHandler handler = new FunctionCaseTemplateWriteHandler(importFileNeedNum,headList,caseLevelAndStatusValueMap);
            easyExcelExporter.exportByCustomWriteHandler(response,headList, generateExportDatas(importFileNeedNum),
                    Translator.get("test_case_import_template_name"), Translator.get("test_case_import_template_sheet"), handler);

        } catch (Exception e) {
            MSException.throwException(e);
        }
    }

    public void download(String fileName, HttpServletResponse res) throws IOException {
        if (StringUtils.isEmpty(fileName)) {
            fileName = "xmind.xml";
        }
        // ???????????????????????????
        byte[] buff = new byte[1024];
        try (OutputStream outputStream = res.getOutputStream();
             BufferedInputStream bis = new BufferedInputStream(TestCaseService.class.getResourceAsStream("/io/metersphere/xmind/template/" + fileName));) {
            int i = bis.read(buff);
            while (i != -1) {
                outputStream.write(buff, 0, buff.length);
                outputStream.flush();
                i = bis.read(buff);
            }
        } catch (Exception ex) {
            LogUtil.error(ex);
            MSException.throwException("??????????????????????????????");
        }
    }

    public void testCaseXmindTemplateExport(String projectId, String importType, HttpServletResponse response) {
        try {
            response.setContentType("application/octet-stream");
            response.setCharacterEncoding("utf-8");
            boolean isUseCustomId = projectService.useCustomNum(projectId);
            response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode("????????????????????????", "UTF-8") + ".xmind");
            String fileName = null;
            if (StringUtils.equals(importType, FunctionCaseImportEnum.Update.name())) {
                fileName = "xmind_update.xml";
            } else {
                if (isUseCustomId) {
                    fileName = "xmind_custom_id.xml";
                } else {
                    fileName = "xmind_system_id.xml";
                }
            }
            download(fileName, response);
        } catch (Exception ex) {

        }
    }

    private List<List<Object>> generateExportDatas(boolean needCustomId) {
        List<List<Object>> list = new ArrayList<>();
        StringBuilder path = new StringBuilder("");
        List<String> types = TestCaseConstants.Type.getValues();
        SessionUser user = SessionUtils.getUser();
        for (int i = 1; i <= 5; i++) {
            List<Object> rowData = new ArrayList<>();
            if (needCustomId) {
                rowData.add("");
            }
            rowData.add(Translator.get("test_case") + i);
            path.append("/" + Translator.get("module") + i);
            rowData.add(path.toString());
            rowData.add("");
            rowData.add(Translator.get("preconditions_optional"));
            rowData.add(Translator.get("remark_optional"));
            rowData.add("1. " + Translator.get("step_tip_separate") + "\n2. " + Translator.get("step_tip_order") + "\n3. " + Translator.get("step_tip_optional"));
            rowData.add("1. " + Translator.get("result_tip_separate") + "\n2. " + Translator.get("result_tip_order") + "\n3. " + Translator.get("result_tip_optional"));
            rowData.add("");
            rowData.add("P" + i % 4);
            list.add(rowData);
        }
        return list;
    }

    private List<TestCaseExcelData> generateExportTemplate() {
        List<TestCaseExcelData> list = new ArrayList<>();
        StringBuilder path = new StringBuilder("");
        List<String> types = TestCaseConstants.Type.getValues();
        SessionUser user = SessionUtils.getUser();
        TestCaseExcelDataFactory factory = new TestCaseExcelDataFactory();
        for (int i = 1; i <= 5; i++) {
            TestCaseExcelData data = factory.getTestCaseExcelDataLocal();
            data.setName(Translator.get("test_case") + i);
            path.append("/" + Translator.get("module") + i);
            data.setNodePath(path.toString());
            data.setPriority("P" + i % 4);
            String type = types.get(i % 3);
            data.setPrerequisite(Translator.get("preconditions_optional"));
            data.setStepDesc("1. " + Translator.get("step_tip_separate") +
                    "\n2. " + Translator.get("step_tip_order") + "\n3. " + Translator.get("step_tip_optional"));
            data.setStepResult("1. " + Translator.get("result_tip_separate") + "\n2. " + Translator.get("result_tip_order") + "\n3. " + Translator.get("result_tip_optional"));
            data.setMaintainer(user.getId());
            data.setRemark(Translator.get("remark_optional"));
            list.add(data);
        }

        list.add(new TestCaseExcelData());
        return list;
    }

    public void testCaseExport(HttpServletResponse response, TestCaseBatchRequest request) {
        try {
//            EasyExcelExporter easyExcelExporter = new EasyExcelExporter(new TestCaseExcelDataFactory().getExcelDataByLocal());
//            List<TestCaseExcelData> datas = generateTestCaseExcel(request);
//            easyExcelExporter.export(response,datas,Translator.get("test_case_import_template_name"), Translator.get("test_case_import_template_sheet"));

            TestCaseExcelData testCaseExcelData = new TestCaseExcelDataFactory().getTestCaseExcelDataLocal();
            List<TestCaseExcelData> datas = generateTestCaseExcel(request);
            boolean importFileNeedNum = true;
            TestCaseTemplateService testCaseTemplateService = CommonBeanFactory.getBean(TestCaseTemplateService.class);
            TestCaseTemplateDao testCaseTemplate = testCaseTemplateService.getTemplate(request.getProjectId());
            List<CustomFieldDao> customFields = null;
            if (testCaseTemplate == null) {
                customFields = new ArrayList<>();
            } else {
                customFields = testCaseTemplate.getCustomFields();
            }

            List<List<String>> headList = testCaseExcelData.getHead(importFileNeedNum, customFields);
            List<List<Object>> testCaseDataByExcelList = this.generateTestCaseExcel(headList,datas);
            EasyExcelExporter easyExcelExporter = new EasyExcelExporter(testCaseExcelData.getClass());
            easyExcelExporter.exportByCustomWriteHandler(response,headList, testCaseDataByExcelList,
                    Translator.get("test_case_import_template_name"), Translator.get("test_case_import_template_sheet"));


        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException(e);
        }
    }


    public void testCaseXmindExport(HttpServletResponse response, TestCaseBatchRequest request) {
        try {
            request.getCondition().setStatusIsNot("Trash");
            List<TestCaseDTO> testCaseDTOList= this.findByBatchRequest(request);

            TestCaseXmindData rootXmindData = this.generateTestCaseXmind(testCaseDTOList);
            boolean isUseCustomId = projectService.useCustomNum(request.getProjectId());
            XmindExportUtil xmindExportUtil = new XmindExportUtil(isUseCustomId);
            xmindExportUtil.exportXmind(response,rootXmindData);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException(e);
        }
    }

    private TestCaseXmindData generateTestCaseXmind(List<TestCaseDTO> testCaseDTOList) {
        Map<String,List<TestCaseDTO>> moduleTestCaseMap = new HashMap<>();
        for (TestCaseDTO dto : testCaseDTOList) {
            String moduleId = dto.getNodeId();
            if(StringUtils.isEmpty(moduleId)){
                moduleId = "default";
            }
             if(moduleTestCaseMap.containsKey(moduleId)){
                 moduleTestCaseMap.get(moduleId).add(dto);
             }else {
                 List<TestCaseDTO> list = new ArrayList<>();
                 list.add(dto);
                 moduleTestCaseMap.put(moduleId,list);
             }
        }

        TestCaseXmindData rootMind = new TestCaseXmindData("ROOT","ROOT");

        for (Map.Entry<String,List<TestCaseDTO>> entry:moduleTestCaseMap.entrySet()) {
            String moduleId = entry.getKey();
            List<TestCaseDTO> dataList = entry.getValue();

            if(StringUtils.equals(moduleId,"ROOT")){
                rootMind.setTestCaseList(dataList);
            }else {
                LinkedList<TestCaseNode> modulePathDataList = testCaseNodeService.getPathNodeById(moduleId);
                rootMind.setItem(modulePathDataList,dataList);
            }
        }

        return rootMind;
    }

    private List<List<Object>> generateTestCaseExcel(List<List<String>> headListParams,List<TestCaseExcelData> datas) {
        List<List<Object>> returnDatas = new ArrayList<>();
        //??????excel???
        List<String> headList = new ArrayList<>();
        for (List<String> list:headListParams){
            for (String head : list){
                headList.add(head);
            }
        }

        for(TestCaseExcelData model : datas){
            List<Object> list = new ArrayList<>();
            Map<String,String> customDataMaps = model.getCustomDatas();
            if(customDataMaps == null){
                customDataMaps = new HashMap<>();
            }
            for(String head : headList){
                if(StringUtils.equalsAnyIgnoreCase(head,"ID")){
                    list.add(model.getCustomNum());
                }else if(StringUtils.equalsAnyIgnoreCase(head,"Name","????????????","????????????")){
                    list.add(model.getName());
                }else if(StringUtils.equalsAnyIgnoreCase(head,"Module","????????????","????????????")){
                    list.add(model.getNodePath());
                }else if(StringUtils.equalsAnyIgnoreCase(head,"Tag","??????","??????")){
                    String tags = "";
                    try {
                        if(model.getTags()!=null){
                            JSONArray arr = JSONArray.parseArray(model.getTags());
                            for(int i = 0; i < arr.size(); i ++){
                                tags += arr.getString(i) + ",";
                            }
                        }
                    }catch (Exception e){}
                    list.add(tags);
                }else if(StringUtils.equalsAnyIgnoreCase(head,"Prerequisite","????????????","????????????")){
                    list.add(model.getPrerequisite());
                }else if(StringUtils.equalsAnyIgnoreCase(head,"Remark","??????","??????")){
                    list.add(model.getRemark());
                }else if(StringUtils.equalsAnyIgnoreCase(head,"Step description","????????????","????????????")){
                    list.add(model.getStepDesc());
                }else if(StringUtils.equalsAnyIgnoreCase(head,"Step result","????????????","????????????")){
                    list.add(model.getStepResult());
                }else if(StringUtils.equalsAnyIgnoreCase(head,"Edit Model","????????????","????????????")){
                    list.add(model.getStepModel());
                }else if(StringUtils.equalsAnyIgnoreCase(head,"Priority","????????????","????????????")){
                    list.add(model.getPriority());
                }else if(StringUtils.equalsAnyIgnoreCase(head,"Case status","????????????","????????????")){
                    list.add(model.getStatus());
                } else if (StringUtils.equalsAnyIgnoreCase(head, "Maintainer(ID)", "?????????(ID)", "?????????(ID)")) {
                    /*String value = customDataMaps.get("?????????");
                    value = value == null ? "" : value;*/
                    list.add(model.getMaintainer());
                } else {
                    String value = customDataMaps.get(head);
                    if (value == null) {
                        value = "";
                    }
                    list.add(value);
                }
            }
            returnDatas.add(list);
        }

        return returnDatas;
    }

    public List<TestCaseDTO> findByBatchRequest(TestCaseBatchRequest request){
        ServiceUtils.getSelectAllIds(request, request.getCondition(),
                (query) -> extTestCaseMapper.selectIds(query));
        QueryTestCaseRequest condition = request.getCondition();
        List<OrderRequest> orderList = new ArrayList<>();
        if (condition != null) {
            orderList = ServiceUtils.getDefaultSortOrder(request.getOrders());
        }
        OrderRequest order = new OrderRequest();
        order.setName("sort");
        order.setType("desc");
        orderList.add(order);
        request.setOrders(orderList);
        List<TestCaseDTO> testCaseList = extTestCaseMapper.listByTestCaseIds(request);
        return testCaseList;
    }

    private List<TestCaseExcelData> generateTestCaseExcel(TestCaseBatchRequest request) {
        request.getCondition().setStatusIsNot("Trash");
        List<TestCaseDTO> testCaseList = this.findByBatchRequest(request);
        boolean isUseCustomId = projectService.useCustomNum(request.getProjectId());
        List<TestCaseExcelData> list = new ArrayList<>();
        StringBuilder step = new StringBuilder("");
        StringBuilder result = new StringBuilder("");

        Map<String,Map<String,String>> customSelectValueMap = new HashMap<>();
        TestCaseTemplateService testCaseTemplateService = CommonBeanFactory.getBean(TestCaseTemplateService.class);
        TestCaseTemplateDao testCaseTemplate = testCaseTemplateService.getTemplate(request.getProjectId());

        List<CustomFieldDao> customFieldList = null;
        if (testCaseTemplate == null) {
            customFieldList = new ArrayList<>();
        } else {
            customFieldList = testCaseTemplate.getCustomFields();
        }
        for (CustomFieldDao dto :customFieldList) {
            Map<String,String> map = new HashMap<>();
            if(StringUtils.equals("select",dto.getType())){
                try {
                    JSONArray optionsArr = JSONArray.parseArray(dto.getOptions());
                    for (int i = 0; i < optionsArr.size();i++) {
                        JSONObject obj = optionsArr.getJSONObject(i);
                        if(obj.containsKey("text") && obj.containsKey("value")){
                            String value = obj.getString("value");
                            String text = obj.getString("text");
                            if(StringUtils.equals(text,"test_track.case.status_finished")){
                                text = Translator.get("test_case_status_finished");
                            }else if(StringUtils.equals(text,"test_track.case.status_prepare")){
                                text = Translator.get("test_case_status_prepare");
                            }else if(StringUtils.equals(text,"test_track.case.status_running")){
                                text = Translator.get("test_case_status_running");
                            }
                            if(StringUtils.isNotEmpty(value)){
                                map.put(value,text);
                            }
                        }
                    }
                }catch (Exception e){}
            }
            customSelectValueMap.put(dto.getName(),map);
        }


        testCaseList.forEach(t -> {
            TestCaseExcelData data = new TestCaseExcelData();
            data.setNum(t.getNum());
            data.setName(t.getName());
            data.setNodePath(t.getNodePath());
            data.setPriority(t.getPriority());
            if (isUseCustomId) {
                data.setCustomNum(t.getCustomNum());
            } else {
                data.setCustomNum(String.valueOf(t.getNum()));
            }
            if (StringUtils.isBlank(t.getStepModel())) {
                data.setStepModel(TestCaseConstants.StepModel.STEP.name());
            } else {
                data.setStepModel(t.getStepModel());
            }
            data.setPrerequisite(t.getPrerequisite());
            data.setTags(t.getTags());
            if (StringUtils.equals(t.getMethod(), "manual") || StringUtils.isBlank(t.getMethod())) {

                if (StringUtils.equals(data.getStepModel(), TestCaseConstants.StepModel.TEXT.name())) {
                    data.setStepDesc(t.getStepDescription());
                    data.setStepResult(t.getExpectedResult());
                } else {
                    String steps = t.getSteps();
                    String setp = "";
                    setp = steps;
                    JSONArray jsonArray = null;

                    //???????????????????????????????????????
                    try {
                        jsonArray = JSON.parseArray(setp);
                    } catch (Exception e) {
                        if (steps.contains("null") && !steps.contains("\"null\"")) {
                            setp = steps.replace("null", "\"\"");
                            jsonArray = JSON.parseArray(setp);
                        }
                    }

                    if (CollectionUtils.isNotEmpty(jsonArray)) {
                        for (int j = 0; j < jsonArray.size(); j++) {
                            int num = j + 1;
                            step.append(num + "." + jsonArray.getJSONObject(j).getString("desc") + "\n");
                            result.append(num + "." + jsonArray.getJSONObject(j).getString("result") + "\n");

                        }
                    }

                    data.setStepDesc(step.toString());
                    data.setStepResult(result.toString());
                    step.setLength(0);
                    result.setLength(0);
                }
                data.setRemark(t.getRemark());

            } else if ("auto".equals(t.getMethod()) && "api".equals(t.getType())) {
                data.setStepDesc("");
                data.setStepResult("");
                if (t.getTestId() != null && "other".equals(t.getTestId())) {
                    data.setRemark(t.getOtherTestName());
                } else {
                    data.setRemark("[" + t.getApiName() + "]" + "\n" + t.getRemark());
                }

            } else if ("auto".equals(t.getMethod()) && "performance".equals(t.getType())) {
                data.setStepDesc("");
                data.setStepResult("");
                if (t.getTestId() != null && "other".equals(t.getTestId())) {
                    data.setRemark(t.getOtherTestName());
                } else {
                    data.setRemark(t.getPerformName());
                }
            }
            data.setMaintainer(t.getMaintainer());
            data.setStatus(t.getStatus());
            String customFields = t.getCustomFields();
            try{
                JSONArray customFieldsArr = JSONArray.parseArray(customFields);
                Map<String,String> map = new HashMap<>();
                for(int index = 0; index < customFieldsArr.size(); index ++){
                    JSONObject obj = customFieldsArr.getJSONObject(index);
                    if(obj.containsKey("name") && obj.containsKey("value")){
                        //??????key value??????
                        String name = obj.getString("name");
                        String value = obj.getString("value");
                        if(customSelectValueMap.containsKey(name)){
                            if(customSelectValueMap.get(name).containsKey(value)){
                                value = customSelectValueMap.get(name).get(value);
                            }
                        }
                        map.put(name,value);
                    }
                }
                data.setCustomDatas(map);
            }catch (Exception e){}
            list.add(data);
        });
        return list;
    }

    /**
     * ?????????????????????
     * @param request
     */
    public void editTestCaseBath(TestCaseBatchRequest request) {
        ServiceUtils.getSelectAllIds(request, request.getCondition(),
                (query) -> extTestCaseMapper.selectIds(query));
        List<String> ids = request.getIds();
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
//        TestCaseExample example = new TestCaseExample();
//        example.createCriteria().andIdIn(request.getIds());

        if (request.getCustomField() != null) {
            List<TestCaseWithBLOBs> testCases = extTestCaseMapper.getCustomFieldsByIds(ids);
            testCases.forEach((testCase) -> {
                String customFields = testCase.getCustomFields();
                List<TestCaseBatchRequest.CustomFiledRequest> fields = null;
                if (StringUtils.isBlank(customFields)) {
                    fields = new ArrayList<>();
                } else {
                    fields = JSONObject.parseArray(customFields, TestCaseBatchRequest.CustomFiledRequest.class);
                }

                boolean hasField = false;
                for (int i = 0; i < fields.size(); i++) {
                    TestCaseBatchRequest.CustomFiledRequest field = fields.get(i);
                    if (StringUtils.equals(request.getCustomField().getName(), field.getName())) {
                        field.setValue(request.getCustomField().getValue());
                        testCase.setMaintainer(request.getCustomField().getValue().toString());
                        // ????????????????????????????????????test_case???custom_fields??????????????????????????????status????????????
                        if(request.getCustomField().getName().equals("????????????")){
                            testCase.setStatus(request.getCustomField().getValue().toString());
                        }
                        hasField = true;
                        break;
                    }
                }
                if (!hasField) {
                    fields.add(request.getCustomField());
                }
                if (StringUtils.equals(request.getCustomField().getName(), "????????????")) {
                    testCase.setPriority((String) request.getCustomField().getValue());
                }
                if (StringUtils.equals(request.getCustomField().getName(), "?????????")) {
                    testCase.setMaintainer((String) request.getCustomField().getValue());
                }
                testCase.setCustomFields(JSONObject.toJSONString(fields));
                testCase.setUpdateTime(System.currentTimeMillis());

                TestCaseExample example = new TestCaseExample();
                example.createCriteria().andIdEqualTo(testCase.getId());
                testCaseMapper.updateByExampleSelective(testCase, example);
            });
        } else {
            // ????????????
            TestCaseWithBLOBs batchEdit = new TestCaseWithBLOBs();
            BeanUtils.copyBean(batchEdit, request);
            batchEdit.setUpdateTime(System.currentTimeMillis());
            TestCaseExample example = new TestCaseExample();
            example.createCriteria().andIdIn(request.getIds());
            testCaseMapper.updateByExampleSelective(batchEdit, example);
        }
    }

    public void deleteTestCaseBath(TestCaseBatchRequest request) {
        TestCaseExample example = this.getBatchExample(request);
        deleteTestPlanTestCaseBath(request.getIds());
        relationshipEdgeService.delete(request.getIds()); // ???????????????

        request.getIds().forEach(testCaseId -> { // todo ???????????????
            testCaseIssueService.delTestCaseIssues(testCaseId);
            testCaseCommentService.deleteCaseComment(testCaseId);
            TestCaseTestExample examples = new TestCaseTestExample();
            examples.createCriteria().andTestCaseIdEqualTo(testCaseId);
            testCaseTestMapper.deleteByExample(examples);
            relateDelete(testCaseId);
            deleteFollows(testCaseId);
        });

        testCaseMapper.deleteByExample(example);
    }

    public TestCaseExample getBatchExample(TestCaseBatchRequest request) {
        ServiceUtils.getSelectAllIds(request, request.getCondition(),
                (query) -> extTestCaseMapper.selectIds(query));
        TestCaseExample example = new TestCaseExample();
        example.createCriteria().andIdIn(request.getIds());
        return example;
    }

    public void deleteTestPlanTestCaseBath(List<String> caseIds) {
        TestPlanTestCaseExample example = new TestPlanTestCaseExample();
        example.createCriteria().andCaseIdIn(caseIds);
        testPlanTestCaseMapper.deleteByExample(example);
    }

    public void deleteTestCaseByProjectId(String projectId) {
        TestCaseExample example = new TestCaseExample();
        example.createCriteria().andProjectIdEqualTo(projectId);
        testCaseMapper.deleteByExample(example);
    }

    /**
     * ??????????????????
     *
     * @param testId
     */
    public void checkIsRelateTest(String testId) {
        TestCaseExample testCaseExample = new TestCaseExample();
        testCaseExample.createCriteria().andTestIdEqualTo(testId);
        List<TestCase> testCases = testCaseMapper.selectByExample(testCaseExample);
        StringBuilder caseName = new StringBuilder();
        if (testCases.size() > 0) {
            for (TestCase testCase : testCases) {
                caseName = caseName.append(testCase.getName()).append(",");
            }
            String str = caseName.substring(0, caseName.length() - 1);
            MSException.throwException(Translator.get("related_case_del_fail_prefix") + " " + str + " " + Translator.get("related_case_del_fail_suffix"));
        }
    }

    /**
     * ?????????????????????num (???????????????ID)
     *
     * @return
     */
    private int getNextNum(String projectId) {
        TestCase testCase = extTestCaseMapper.getMaxNumByProjectId(projectId);
        if (testCase == null || testCase.getNum() == null) {
            return 100001;
        } else {
            return Optional.ofNullable(testCase.getNum() + 1).orElse(100001);
        }
    }


    /**
     * ??????????????????????????????????????????????????????
     *
     * @param testCaseWithBLOBs
     * @return
     */
    public boolean exist(TestCaseWithBLOBs testCaseWithBLOBs) {

        try {
            TestCaseWithBLOBs caseWithBLOBs = checkTestCaseExist(testCaseWithBLOBs);
            if (caseWithBLOBs != null)
                return true;
        } catch (MSException e) {
            return true;
        }

        return false;
    }

    public TestCase save(EditTestCaseRequest request, List<MultipartFile> files) {


        final TestCaseWithBLOBs testCaseWithBLOBs = addTestCase(request);

        // ???????????????????????????ID????????????
        if (!CollectionUtils.isEmpty(request.getFileIds())) {
            List<String> fileIds = request.getFileIds();
            fileIds.forEach(id -> {
                FileMetadata fileMetadata = fileService.copyFile(id);
                TestCaseFile testCaseFile = new TestCaseFile();
                testCaseFile.setCaseId(testCaseWithBLOBs.getId());
                testCaseFile.setFileId(fileMetadata.getId());
                testCaseFileMapper.insert(testCaseFile);
            });
        }

        if (files != null) {
            files.forEach(file -> {
                final FileMetadata fileMetadata = fileService.saveFile(file, testCaseWithBLOBs.getProjectId());
                TestCaseFile testCaseFile = new TestCaseFile();
                testCaseFile.setCaseId(testCaseWithBLOBs.getId());
                testCaseFile.setFileId(fileMetadata.getId());
                testCaseFileMapper.insert(testCaseFile);
            });
        }

        return testCaseWithBLOBs;
    }

    public TestCase edit(EditTestCaseRequest request, List<MultipartFile> files) {
        TestCaseWithBLOBs testCaseWithBLOBs = testCaseMapper.selectByPrimaryKey(request.getId());
        request.setNum(testCaseWithBLOBs.getNum());
        if (testCaseWithBLOBs == null) {
            MSException.throwException(Translator.get("edit_load_test_not_found") + request.getId());
        }

        // ????????????????????????????????????????????????
        List<FileMetadata> updatedFiles = request.getUpdatedFileList();
        List<FileMetadata> originFiles = fileService.getFileMetadataByCaseId(request.getId());
        List<String> updatedFileIds = updatedFiles.stream().map(FileMetadata::getId).collect(Collectors.toList());
        List<String> originFileIds = originFiles.stream().map(FileMetadata::getId).collect(Collectors.toList());
        // ??????
        List<String> deleteFileIds = ListUtils.subtract(originFileIds, updatedFileIds);
        fileService.deleteFileRelatedByIds(deleteFileIds);

        if (!CollectionUtils.isEmpty(deleteFileIds)) {
            TestCaseFileExample testCaseFileExample = new TestCaseFileExample();
            testCaseFileExample.createCriteria().andFileIdIn(deleteFileIds);
            testCaseFileMapper.deleteByExample(testCaseFileExample);
        }

        if (files != null) {
            files.forEach(file -> {
                final FileMetadata fileMetadata = fileService.saveFile(file, testCaseWithBLOBs.getProjectId());
                TestCaseFile testCaseFile = new TestCaseFile();
                testCaseFile.setFileId(fileMetadata.getId());
                testCaseFile.setCaseId(request.getId());
                testCaseFileMapper.insert(testCaseFile);
            });
        }
        this.setNode(request);
        editTestCase(request);
        saveFollows(request.getId(), request.getFollows());
        return testCaseWithBLOBs;
    }

    public String editTestCase(EditTestCaseRequest request, List<MultipartFile> files) {
        String testCaseId = testPlanTestCaseMapper.selectByPrimaryKey(request.getId()).getCaseId();
        request.setId(testCaseId);
        TestCaseWithBLOBs testCaseWithBLOBs = testCaseMapper.selectByPrimaryKey(testCaseId);
        if (testCaseWithBLOBs == null) {
            MSException.throwException(Translator.get("edit_load_test_not_found") + request.getId());
        }
        testCaseWithBLOBs.setRemark(request.getRemark());
        // ????????????????????????????????????????????????
        List<FileMetadata> updatedFiles = request.getUpdatedFileList();
        List<FileMetadata> originFiles = fileService.getFileMetadataByCaseId(testCaseId);
        List<String> updatedFileIds = updatedFiles.stream().map(FileMetadata::getId).collect(Collectors.toList());
        List<String> originFileIds = originFiles.stream().map(FileMetadata::getId).collect(Collectors.toList());
        // ??????
        List<String> deleteFileIds = ListUtils.subtract(originFileIds, updatedFileIds);
        fileService.deleteFileRelatedByIds(deleteFileIds);

        if (!CollectionUtils.isEmpty(deleteFileIds)) {
            TestCaseFileExample testCaseFileExample = new TestCaseFileExample();
            testCaseFileExample.createCriteria().andFileIdIn(deleteFileIds);
            testCaseFileMapper.deleteByExample(testCaseFileExample);
        }


        if (files != null) {
            files.forEach(file -> {
                final FileMetadata fileMetadata = fileService.saveFile(file, testCaseWithBLOBs.getProjectId());
                TestCaseFile testCaseFile = new TestCaseFile();
                testCaseFile.setFileId(fileMetadata.getId());
                testCaseFile.setCaseId(testCaseId);
                testCaseFileMapper.insert(testCaseFile);
            });
        }
        this.setNode(request);
        // test_case????????????????????????
        request.setStatus(null);
        editTestCase(request);
        return request.getId();
    }

    public List<TestCaseDTO> listTestCaseIds(QueryTestCaseRequest request) {
        setDefaultOrder(request);
        List<String> selectFields = new ArrayList<>();
        selectFields.add("id");
        selectFields.add("name");
        request.setSelectFields(selectFields);
        return extTestCaseMapper.listIds(request);
    }

    public void minderEdit(TestCaseMinderEditRequest request) {
        List<TestCaseMinderEditRequest.TestCaseMinderEditItem> data = request.getData();
        if (CollectionUtils.isNotEmpty(data)) {
            List<String> editIds = data.stream()
                    .filter(t -> StringUtils.isNotBlank(t.getId()) && t.getId().length() > 20)
                    .map(TestCaseWithBLOBs::getId).collect(Collectors.toList());

            Map<String, TestCaseWithBLOBs> testCaseMap = new HashMap<>();
            if (CollectionUtils.isNotEmpty(editIds)) {
                TestCaseExample example = new TestCaseExample();
                example.createCriteria().andIdIn(editIds);
                List<TestCaseWithBLOBs> testCaseWithBLOBs = testCaseMapper.selectByExampleWithBLOBs(example);
                testCaseMap = testCaseWithBLOBs.stream().collect(Collectors.toMap(TestCaseWithBLOBs::getId, t -> t));
            }

            Map<String, TestCaseWithBLOBs> finalTestCaseMap = testCaseMap;
            data.forEach(item -> {
                if (StringUtils.isBlank(item.getNodeId()) || item.getNodeId().equals("root")) {
                    item.setNodeId("");
                }
                item.setProjectId(request.getProjectId());
                if (StringUtils.isBlank(item.getId()) || item.getId().length() < 20) {
                    item.setId(UUID.randomUUID().toString());
                    item.setMaintainer(SessionUtils.getUserId());
                    EditTestCaseRequest editTestCaseRequest = new EditTestCaseRequest();
                    BeanUtils.copyBean(editTestCaseRequest, item);
                    addTestCase(editTestCaseRequest);
                    changeOrder(item, request.getProjectId());
                } else {
                    TestCaseWithBLOBs dbCase = finalTestCaseMap.get(item.getId());
                    if (editCustomFieldsPriority(dbCase, item.getPriority())) {
                        item.setCustomFields(dbCase.getCustomFields());
                    }
                    editTestCase(item);
                    changeOrder(item, request.getProjectId());
                }
            });
        }
        List<String> ids = request.getIds();
        deleteToGcBatch(ids);
    }

    private void changeOrder(TestCaseMinderEditRequest.TestCaseMinderEditItem item, String projectId) {
        if (StringUtils.isNotBlank(item.getTargetId())) {
            ResetOrderRequest resetOrderRequest = new ResetOrderRequest();
            resetOrderRequest.setGroupId(projectId);
            resetOrderRequest.setMoveId(item.getId());
            resetOrderRequest.setTargetId(item.getTargetId());
            resetOrderRequest.setMoveMode(item.getMoveMode());
            updateOrder(resetOrderRequest);
        }
    }

    /**
     * ?????????????????????????????????????????????????????????????????????????????????
     *
     * @param dbCase
     * @param priority
     * @return
     */
    private boolean editCustomFieldsPriority(TestCaseWithBLOBs dbCase, String priority) {
        String customFields = dbCase.getCustomFields();
        if (StringUtils.isNotBlank(customFields)) {
            JSONArray fields = JSONObject.parseArray(customFields);
            for (int i = 0; i < fields.size(); i++) {
                JSONObject field = fields.getJSONObject(i);
                if (field.getString("name").equals("????????????")) {
                    field.put("value", priority);
                    dbCase.setCustomFields(JSONObject.toJSONString(fields));
                    return true;
                }
            }
        }
        return false;
    }

    public List<TestCase> getTestCaseByProjectId(String projectId) {
        TestCaseExample example = new TestCaseExample();
        example.createCriteria().andProjectIdEqualTo(projectId).andStatusNotEqualTo("Trash");
        return testCaseMapper.selectByExample(example);
    }

    public List<TestCaseWithBLOBs> listTestCaseForMinder(QueryTestCaseRequest request) {
        setDefaultOrder(request);
        return extTestCaseMapper.listForMinder(request);
    }

    public List<TestCaseDTO> getTestCaseByIds(List<String> testCaseIds) {
        if (CollectionUtils.isNotEmpty(testCaseIds)) {
            return extTestCaseMapper.getTestCaseByIds(testCaseIds);
        } else {
            return new ArrayList<>();
        }
    }

    public List<TestCaseDTO> getTestCaseIssueRelateList(QueryTestCaseRequest request) {
        request.setOrders(ServiceUtils.getDefaultOrder(request.getOrders()));
        return getTestCaseByNotInIssue(request);
    }

    /**
     * ????????????????????????CustomNum???
     *
     * @param projectId ??????ID
     */
    public void updateTestCaseCustomNumByProjectId(String projectId) {
        extTestCaseMapper.updateTestCaseCustomNumByProjectId(projectId);
    }

    public ExcelResponse testCaseImportIgnoreError(MultipartFile multipartFile, String projectId, String userId, String importType, HttpServletRequest request) {

        ExcelResponse excelResponse = new ExcelResponse();
        boolean isUpdated = false;  //???????????????????????????
        String currentWorkspaceId = SessionUtils.getCurrentWorkspaceId();
        QueryTestCaseRequest queryTestCaseRequest = new QueryTestCaseRequest();
        queryTestCaseRequest.setProjectId(projectId);
        List<TestCase> testCases = extTestCaseMapper.getTestCaseNames(queryTestCaseRequest);
        boolean useCunstomId = projectService.useCustomNum(projectId);
        Set<String> savedIds = new HashSet<>();
        Set<String> testCaseNames = new HashSet<>();
        for (TestCase testCase : testCases) {
            if (useCunstomId) {
                savedIds.add(testCase.getCustomNum());
            } else {
                savedIds.add(String.valueOf(testCase.getNum()));
            }
            testCaseNames.add(testCase.getName());
        }
        List<ExcelErrData<TestCaseExcelData>> errList = null;
        if (multipartFile == null) {
            MSException.throwException(Translator.get("upload_fail"));
        }
        if (multipartFile.getOriginalFilename().endsWith(".xmind")) {
            try {
                XmindCaseParser xmindParser = new XmindCaseParser(this, userId, projectId, testCaseNames, useCunstomId, importType);
                errList = xmindParser.parse(multipartFile);
                if (CollectionUtils.isEmpty(xmindParser.getNodePaths())
                        && CollectionUtils.isEmpty(xmindParser.getTestCase())
                        && CollectionUtils.isEmpty(xmindParser.getUpdateTestCase())) {
                    if (errList == null) {
                        errList = new ArrayList<>();
                    }
                    ExcelErrData excelErrData = new ExcelErrData(null, 1, Translator.get("upload_fail") + "???" + Translator.get("upload_content_is_null"));
                    errList.add(excelErrData);
                    excelResponse.setErrList(errList);
                }
                List<TestCaseWithBLOBs> continueCaseList = xmindParser.getContinueValidatedCase();
                if (CollectionUtils.isNotEmpty(continueCaseList) || CollectionUtils.isNotEmpty(xmindParser.getUpdateTestCase())) {
                    List<String> names = new LinkedList<>();
                    List<String> ids = new LinkedList<>();

                    if (CollectionUtils.isNotEmpty(xmindParser.getUpdateTestCase())) {
                        continueCaseList.removeAll(xmindParser.getUpdateTestCase());
                        this.updateImportData(xmindParser.getUpdateTestCase(), projectId);
                        names = xmindParser.getTestCase().stream().map(TestCase::getName).collect(Collectors.toList());
                        ids = xmindParser.getTestCase().stream().map(TestCase::getId).collect(Collectors.toList());
                    }
                    List<String> nodePathList = xmindParser.getValidatedNodePath();
                    if (CollectionUtils.isNotEmpty(nodePathList)) {
                        testCaseNodeService.createNodes(nodePathList, projectId);
                    }
                    if (CollectionUtils.isNotEmpty(continueCaseList)) {
//                        Collections.reverse(continueCaseList);
                        this.saveImportData(continueCaseList, projectId);
                        names.addAll(continueCaseList.stream().map(TestCase::getName).collect(Collectors.toList()));
                        ids.addAll(continueCaseList.stream().map(TestCase::getId).collect(Collectors.toList()));

                    }
                    request.setAttribute("ms-req-title", String.join(",", names));
                    request.setAttribute("ms-req-source-id", JSON.toJSONString(ids));

                }
                xmindParser.clear();
            } catch (Exception e) {
                LogUtil.error(e.getMessage(), e);
                MSException.throwException(e.getMessage());
            }
        } else {
            GroupExample groupExample = new GroupExample();
            groupExample.createCriteria().andTypeIn(Arrays.asList(UserGroupType.WORKSPACE, UserGroupType.PROJECT));
            List<Group> groups = groupMapper.selectByExample(groupExample);
            List<String> groupIds = groups.stream().map(Group::getId).collect(Collectors.toList());

            UserGroupExample userGroupExample = new UserGroupExample();
            userGroupExample.createCriteria()
                    .andGroupIdIn(groupIds)
                    .andSourceIdEqualTo(currentWorkspaceId);
            Set<String> userIds = userGroupMapper.selectByExample(userGroupExample).stream().map(UserGroup::getUserId).collect(Collectors.toSet());

            try {
                //??????????????????????????????????????????????????????????????????????????????
                Class clazz = new TestCaseExcelDataFactory().getExcelDataByLocal();
                TestCaseTemplateService testCaseTemplateService = CommonBeanFactory.getBean(TestCaseTemplateService.class);
                TestCaseTemplateDao testCaseTemplate = testCaseTemplateService.getTemplate(projectId);
                List<CustomFieldDao> customFields = null;
                if (testCaseTemplate == null) {
                    customFields = new ArrayList<>();
                } else {
                    customFields = testCaseTemplate.getCustomFields();
                }
                TestCaseNoModelDataListener easyExcelListener = new TestCaseNoModelDataListener(true, clazz, customFields, projectId, testCaseNames, savedIds, userIds, useCunstomId, importType);
                //??????excel??????
                EasyExcelFactory.read(multipartFile.getInputStream(), easyExcelListener).sheet().doRead();
                request.setAttribute("ms-req-title", String.join(",", easyExcelListener.getNames()));
                request.setAttribute("ms-req-source-id", JSON.toJSONString(easyExcelListener.getIds()));
                errList = easyExcelListener.getErrList();
                isUpdated = easyExcelListener.isUpdated();
            } catch (Exception e) {

                e.printStackTrace();
                LogUtil.error(e.getMessage(), e);
                MSException.throwException(e.getMessage());
            }
        }
        //?????????????????????????????????????????????
        if (!errList.isEmpty()) {
            excelResponse.setSuccess(false);
            excelResponse.setErrList(errList);
            excelResponse.setIsUpdated(isUpdated);
        } else {
            excelResponse.setSuccess(true);
        }

        return excelResponse;
    }

    public String getLogDetails(String id) {
        TestCaseWithBLOBs bloBs = testCaseMapper.selectByPrimaryKey(id);
        if (bloBs != null) {
            List<DetailColumn> columns = ReflexObjectUtil.getColumns(bloBs, TestCaseReference.testCaseColumns);
            // ????????????????????????
            TestCaseTestExample example = new TestCaseTestExample();
            example.createCriteria().andTestCaseIdEqualTo(id);
            List<TestCaseTest> testCaseTests = testCaseTestMapper.selectByExample(example);
            StringBuilder nameBuilder = new StringBuilder();
            if (CollectionUtils.isNotEmpty(testCaseTests)) {
                List<String> testCaseIds = testCaseTests.stream()
                        .filter(user -> user.getTestType().equals("testcase")).map(TestCaseTest::getTestId)
                        .collect(Collectors.toList());

                List<String> performanceIds = testCaseTests.stream()
                        .filter(user -> user.getTestType().equals("performance")).map(TestCaseTest::getTestId)
                        .collect(Collectors.toList());

                List<String> automationIds = testCaseTests.stream()
                        .filter(user -> user.getTestType().equals("automation")).map(TestCaseTest::getTestId)
                        .collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(testCaseIds)) {
                    ApiTestCaseExample testCaseExample = new ApiTestCaseExample();
                    testCaseExample.createCriteria().andIdIn(testCaseIds);
                    List<ApiTestCase> testCases = apiTestCaseMapper.selectByExample(testCaseExample);
                    List<String> caseNames = testCases.stream().map(ApiTestCase::getName).collect(Collectors.toList());
                    if (CollectionUtils.isNotEmpty(caseNames)) {
                        nameBuilder.append("???????????????").append("\n").append(caseNames).append("\n");
                    }
                }
                if (CollectionUtils.isNotEmpty(performanceIds)) {
                    LoadTestExample loadTestExample = new LoadTestExample();
                    loadTestExample.createCriteria().andIdIn(performanceIds);
                    List<LoadTest> loadTests = loadTestMapper.selectByExample(loadTestExample);
                    List<String> caseNames = loadTests.stream().map(LoadTest::getName).collect(Collectors.toList());
                    if (CollectionUtils.isNotEmpty(caseNames)) {
                        nameBuilder.append("???????????????").append("\n").append(caseNames).append("\n");
                    }
                }
                if (CollectionUtils.isNotEmpty(automationIds)) {
                    ApiScenarioExample scenarioExample = new ApiScenarioExample();
                    scenarioExample.createCriteria().andIdIn(automationIds);
                    List<ApiScenario> scenarios = apiScenarioMapper.selectByExample(scenarioExample);
                    List<String> caseNames = scenarios.stream().map(ApiScenario::getName).collect(Collectors.toList());
                    if (CollectionUtils.isNotEmpty(caseNames)) {
                        nameBuilder.append("??????????????????").append("\n").append(caseNames).append("\n");
                    }
                }
            }
            DetailColumn column = new DetailColumn("????????????", "testcase", nameBuilder.toString(), null);
            columns.add(column);

            //????????????
            List<String> issuesNames = new LinkedList<>();
            TestCaseIssuesExample testCaseIssuesExample = new TestCaseIssuesExample();
            testCaseIssuesExample.createCriteria().andTestCaseIdEqualTo(bloBs.getId());
            List<TestCaseIssues> testCaseIssues = testCaseIssuesMapper.selectByExample(testCaseIssuesExample);
            if (CollectionUtils.isNotEmpty(testCaseIssues)) {
                List<String> issuesIds = testCaseIssues.stream().map(TestCaseIssues::getIssuesId).collect(Collectors.toList());
                IssuesExample issuesExample = new IssuesExample();
                issuesExample.createCriteria().andIdIn(issuesIds);
                List<Issues> issues = issuesMapper.selectByExample(issuesExample);
                if (CollectionUtils.isNotEmpty(issues)) {
                    issuesNames = issues.stream().map(Issues::getTitle).collect(Collectors.toList());
                }
            }
            DetailColumn issuesColumn = new DetailColumn("???????????? ", "issues", String.join(",", issuesNames), null);
            columns.add(issuesColumn);
            //??????
            List<FileMetadata> originFiles = fileService.getFileMetadataByCaseId(id);
            List<String> fileNames = new LinkedList<>();
            if (CollectionUtils.isNotEmpty(originFiles)) {
                fileNames = originFiles.stream().map(FileMetadata::getName).collect(Collectors.toList());
            }
            DetailColumn fileColumn = new DetailColumn("?????? ", "files", String.join(",", fileNames), null);
            columns.add(fileColumn);

            // ??????????????????
            List<TestCaseCommentDTO> dtos = testCaseCommentService.getCaseComments(id);
            List<String> names = new LinkedList<>();
            if (CollectionUtils.isNotEmpty(dtos)) {
                names = dtos.stream().map(TestCaseCommentDTO::getDescription).collect(Collectors.toList());
            }
            DetailColumn detailColumn = new DetailColumn("??????", "comment", String.join("\n", names), null);
            columns.add(detailColumn);

            OperatingLogDetails details = new OperatingLogDetails(JSON.toJSONString(id), bloBs.getProjectId(), bloBs.getName(), bloBs.getCreateUser(), columns);
            return JSON.toJSONString(details);
        }
        return null;
    }

    public String getLogBeforeDetails(String id) {
        TestPlanTestCaseWithBLOBs bloBs = testPlanTestCaseMapper.selectByPrimaryKey(id);
        if (bloBs != null) {
            String testCaseId = testPlanTestCaseMapper.selectByPrimaryKey(id).getCaseId();
            TestCaseWithBLOBs testCaseWithBLOBs = testCaseMapper.selectByPrimaryKey(testCaseId);
            List<DetailColumn> columns = ReflexObjectUtil.getColumns(bloBs, TestCaseReference.testCaseColumns);
            // ??????????????????
            List<TestCaseCommentDTO> dtos = testCaseCommentService.getCaseComments(id);
            if (CollectionUtils.isNotEmpty(dtos)) {
                List<String> names = dtos.stream().map(TestCaseCommentDTO::getDescription).collect(Collectors.toList());
                DetailColumn detailColumn = new DetailColumn("??????", "comment", String.join("\n", names), null);
                columns.add(detailColumn);
            }
            OperatingLogDetails details = new OperatingLogDetails(JSON.toJSONString(testCaseWithBLOBs.getId()), testCaseWithBLOBs.getProjectId(), testCaseWithBLOBs.getName(), testCaseWithBLOBs.getCreateUser(), columns);
            return JSON.toJSONString(details);
        }
        return null;
    }

    public String getLogDetails(List<String> ids) {
        if (CollectionUtils.isNotEmpty(ids)) {
            TestCaseExample example = new TestCaseExample();
            example.createCriteria().andIdIn(ids);
            List<TestCase> definitions = testCaseMapper.selectByExample(example);
            List<String> names = definitions.stream().map(TestCase::getName).collect(Collectors.toList());
            OperatingLogDetails details = new OperatingLogDetails(JSON.toJSONString(ids), definitions.get(0).getProjectId(), String.join(",", names), definitions.get(0).getCreateUser(), new LinkedList<>());
            return JSON.toJSONString(details);
        }
        return null;
    }

    public void reduction(TestCaseBatchRequest request) {
        TestCaseExample example = this.getBatchExample(request);
        if (CollectionUtils.isNotEmpty(request.getIds())) {
            extTestCaseMapper.checkOriginalStatusByIds(request.getIds());

            //??????????????????????????????
            example = new TestCaseExample();
            example.createCriteria().andIdIn(request.getIds());
            List<TestCase> reductionCaseList = testCaseMapper.selectByExample(example);
            Map<String, List<TestCase>> nodeMap = reductionCaseList.stream().collect(Collectors.groupingBy(TestCase::getNodeId));
            for (Map.Entry<String, List<TestCase>> entry : nodeMap.entrySet()) {
                String nodeId = entry.getKey();
                long nodeCount = testCaseNodeService.countById(nodeId);
                if (nodeCount <= 0) {
                    String projectId = request.getProjectId();
                    TestCaseNode node = testCaseNodeService.getDefaultNode(projectId);
                    List<TestCase> testCaseList = entry.getValue();
                    for (TestCase testCase : testCaseList) {

                        TestCaseWithBLOBs updateCase = new TestCaseWithBLOBs();
                        updateCase.setId(testCase.getId());
                        updateCase.setNodeId(node.getId());
                        updateCase.setNodePath("/" + node.getName());

                        testCaseMapper.updateByPrimaryKeySelective(updateCase);
                    }
                }
            }
            extTestCaseMapper.reduction(request.getIds());
        }
    }

    public void deleteToGcBatch(List<String> ids) {
        if (CollectionUtils.isNotEmpty(ids)) {
            for (String id : ids) {
                this.deleteTestCaseToGc(id);
            }
        }
    }

    public String getCaseLogDetails(TestCaseMinderEditRequest request) {
        if (CollectionUtils.isNotEmpty(request.getData())) {
            List<String> ids = request.getData().stream().map(TestCase::getId).collect(Collectors.toList());
            TestCaseExample example = new TestCaseExample();
            example.createCriteria().andIdIn(ids);
            List<TestCase> cases = testCaseMapper.selectByExample(example);
            List<String> names = cases.stream().map(TestCase::getName).collect(Collectors.toList());
            List<DetailColumn> columnsList = new LinkedList<>();
            DetailColumn column = new DetailColumn("??????", "name", String.join(",", names), null);
            columnsList.add(column);

            OperatingLogDetails details = new OperatingLogDetails(JSON.toJSONString(ids), request.getProjectId(), String.join(",", names), SessionUtils.getUserId(), columnsList);
            return JSON.toJSONString(details);
        }
        return null;
    }

    public List<ApiTestCaseDTO> getTestCaseApiCaseRelateList(ApiTestCaseRequest request) {
        return testCaseTestMapper.relevanceApiList(request);
    }

    public void relateTest(String type, String caseId, List<String> apiIds) {
        // ??????caseId????????????id
        TestCase testCase = testCaseMapper.selectByPrimaryKey(caseId);
        String projectId = testCase.getProjectId();
        apiIds.forEach(testId -> {
            TestCaseTest testCaseTest = new TestCaseTest();
            testCaseTest.setTestType(type);
            testCaseTest.setTestCaseId(caseId);
            testCaseTest.setTestId(testId);
            testCaseTest.setCreateTime(System.currentTimeMillis());
            testCaseTest.setUpdateTime(System.currentTimeMillis());
            testCaseTest.setProjectId(projectId);
            testCaseTestMapper.insert(testCaseTest);
        });
    }

    public void relateDelete(String caseId, String testId) {
        TestCaseTestExample example = new TestCaseTestExample();
        example.createCriteria()
                .andTestCaseIdEqualTo(caseId)
                .andTestIdEqualTo(testId);
        testCaseTestMapper.deleteByExample(example);
    }

    public void relateDelete(String caseId) {
        TestCaseTestExample example = new TestCaseTestExample();
        example.createCriteria()
                .andTestCaseIdEqualTo(caseId);
        testCaseTestMapper.deleteByExample(example);
    }

    public HttpResponse autoWeb(List<String> paramList) throws IOException {
        String function = "AutoWeb/queryAutoRelationCase";
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(auto_url + function);
        JSONObject param= new JSONObject();
        param.put("autoCaseIds",paramList);
        StringEntity stringEntity = new StringEntity(param.toString());
        stringEntity.setContentType("application/json");
        httpPost.setEntity(stringEntity);
        //??????
        return httpClient.execute(httpPost);
    }

    public List<TestCaseTestDao> getRelateTest(String caseId) throws IOException {
        TestCaseTestExample example = new TestCaseTestExample();
        example.createCriteria()
                .andTestCaseIdEqualTo(caseId);
        List<TestCaseTest> testCaseTests = testCaseTestMapper.selectByExample(example);
        Map<String, TestCaseTest> testCaseTestsMap = testCaseTests.stream()
                .collect(Collectors.toMap(TestCaseTest::getTestId, i -> i));
        List<ApiTestCase> apiCases = apiTestCaseService.getApiCaseByIds(
                getTestIds(testCaseTests, "testcase")
        );

        // ????????????????????????????????????id, ???????????????????????????
        HttpResponse response = this.autoWeb(getTestIds(testCaseTests, "automation"));

        // ????????????????????????????????????????????????
        //int responseCode = response.getStatusLine().getStatusCode(); //???????????????
        String result = EntityUtils.toString(response.getEntity(), HTTP.UTF_8);
        JSONObject jsonObject = JSONObject.parseObject(result);
        List<ApiScenario> apiScenarios =  new ArrayList<>();
        if((Boolean) jsonObject.get("success")){
            List<String> autoCases = JSONObject.parseArray(jsonObject.get("data").toString(),String.class);
            autoCases.forEach(item ->{
                JSONObject autoCaseItem = JSONObject.parseObject(item);
                ApiScenario apiScenario = new ApiScenarioDTO();
                // ?????? id
                apiScenario.setId(autoCaseItem.get("id").toString());
                // ?????? name
                apiScenario.setName(autoCaseItem.get("comment").toString());
                // ?????? ??????????????? number
                apiScenario.setAutoWebNumber(autoCaseItem.get("number").toString());
                apiScenarios.add(apiScenario);
            });
        }
        /*
        // ??????????????????????????????????????????
        List<ApiScenario> apiScenarios = apiAutomationService.getScenarioCaseByIds(
                getTestIds(testCaseTests, "automation")
        );*/

        List<LoadTest> apiLoadTests = performanceTestService.getLoadCaseByIds(
                getTestIds(testCaseTests, "performance")
        );
        List<TestCaseTestDao> testCaseTestList = new ArrayList<>();
        apiCases.forEach(item -> {
            getTestCaseTestDaoList("testcase", item.getNum(), item.getName(), item.getId(),
                    testCaseTestList, testCaseTestsMap);
        });
        apiScenarios.forEach(item -> {
            getTestCaseTestDaoList("automation", item.getAutoWebNumber(), item.getName(), item.getId(),
                    testCaseTestList, testCaseTestsMap);
        });
        apiLoadTests.forEach(item -> {
            getTestCaseTestDaoList("performance", item.getNum(), item.getName(), item.getId(),
                    testCaseTestList, testCaseTestsMap);
        });
        return testCaseTestList;
    }
    public List<TestCaseTest> getRelateAutoTest(String testId) {
        TestCaseTestExample example = new TestCaseTestExample();
        example.createCriteria().andTestIdEqualTo(testId).andTestTypeEqualTo("automation");

        List<TestCaseTest> testCaseTests = testCaseTestMapper.selectByExample(example);

        List<TestCaseTest> testCaseTestList = new ArrayList<>(); // ???????????????????????????
        testCaseTests.forEach(item -> {
            // ?????????????????????????????????????????????
            String testCaseId = item.getTestCaseId();   // ????????????id
            TestCaseWithBLOBs testCaseWithBLOBs = testCaseMapper.selectByPrimaryKey(testCaseId);

            // ????????????id???????????????
            String projectId = testCaseWithBLOBs.getProjectId(); // ??????id
            Project project = projectService.getProjectById(projectId);

            item.setTestCaseName(testCaseWithBLOBs.getName());
            item.setNodePath(testCaseWithBLOBs.getNodePath());
            item.setPriority(testCaseWithBLOBs.getPriority());
            item.setNumber(testCaseWithBLOBs.getNum());
            item.setStatus(testCaseWithBLOBs.getStatus());
            item.setCreateUser(userMapper.selectByPrimaryKey(testCaseWithBLOBs.getCreateUser()).getName());
            item.setProjectId(projectId);
            item.setProjectName(project.getName());

            item.setPrerequisite(testCaseWithBLOBs.getPrerequisite());
            item.setRemark(testCaseWithBLOBs.getRemark());
            item.setSteps(testCaseWithBLOBs.getSteps());
            item.setStepDescription(testCaseWithBLOBs.getStepDescription());
            item.setExpectedResult(testCaseWithBLOBs.getExpectedResult());
            testCaseTestList.add(item);
        });
        return testCaseTestList;
    }

    public void getTestCaseTestDaoList(String type, Object num, String name, String testId,
                                       List<TestCaseTestDao> testCaseTestList, Map<String, TestCaseTest> testCaseTestsMap) {
        TestCaseTestDao testCaseTestDao = new TestCaseTestDao();
        BeanUtils.copyBean(testCaseTestDao, testCaseTestsMap.get(testId));
        testCaseTestDao.setNum(num.toString());
        testCaseTestDao.setName(name);
        testCaseTestDao.setTestType(type);
        testCaseTestList.add(testCaseTestDao);
    }

    public List<String> getTestIds(List<TestCaseTest> testCaseTests, String type) {
        List<String> caseIds = testCaseTests.stream()
                .filter(item -> item.getTestType().equals(type))
                .map(TestCaseTest::getTestId)
                .collect(Collectors.toList());
        return caseIds;
    }

    public List<ApiScenarioDTO> getTestCaseScenarioCaseRelateList(ApiScenarioRequest request) {
        return testCaseTestMapper.relevanceScenarioList(request);
    }

    public List<LoadTestDTO> getTestCaseLoadCaseRelateList(LoadCaseRequest request) {
        return testCaseTestMapper.relevanceLoadList(request);
    }

    public TestCaseWithBLOBs getTestCaseStep(String testCaseId) {
        return extTestCaseMapper.getTestCaseStep(testCaseId);
    }

    public void initOrderField() {
        ServiceUtils.initOrderField(TestCaseWithBLOBs.class, TestCaseMapper.class,
                extTestCaseMapper::selectProjectIds,
                extTestCaseMapper::getIdsOrderByUpdateTime);
    }

    /**
     * ?????????????????????
     * @param request
     */
    public void updateOrder(ResetOrderRequest request) {
        ServiceUtils.updateOrderField(request, TestCaseWithBLOBs.class,
                testCaseMapper::selectByPrimaryKey,
                extTestCaseMapper::getPreOrder,
                extTestCaseMapper::getLastOrder,
                testCaseMapper::updateByPrimaryKeySelective);
    }

    public Pager<List<TestCase>> getRelationshipRelateList(QueryTestCaseRequest request, int goPage, int pageSize) {
        setDefaultOrder(request);
        List<String> relationshipIds = relationshipEdgeService.getRelationshipIds(request.getId());
        request.setTestCaseContainIds(relationshipIds);
        Page<Object> page = PageHelper.startPage(goPage, pageSize, true);
        return PageUtils.setPageInfo(page, extTestCaseMapper.getTestCase(request));
    }

    public List<RelationshipEdgeDTO> getRelationshipCase(String id, String relationshipType) {

        List<RelationshipEdge> relationshipEdges= relationshipEdgeService.getRelationshipEdgeByType(id, relationshipType);
        List<String> ids = relationshipEdgeService.getRelationIdsByType(relationshipType, relationshipEdges);

        if (CollectionUtils.isNotEmpty(ids)) {
            TestCaseExample example = new TestCaseExample();
            example.createCriteria().andIdIn(ids).andStatusNotEqualTo("Trash");
            List<TestCaseWithBLOBs> testCaseList = testCaseMapper.selectByExampleWithBLOBs(example);
            buildUserInfo(testCaseList);
            Map<String, TestCase> caseMap = testCaseList.stream().collect(Collectors.toMap(TestCase::getId, i -> i));
            List<RelationshipEdgeDTO> results = new ArrayList<>();
            for (RelationshipEdge relationshipEdge : relationshipEdges) {
                RelationshipEdgeDTO relationshipEdgeDTO = new RelationshipEdgeDTO();
                BeanUtils.copyBean(relationshipEdgeDTO, relationshipEdge);
                TestCase testCase;
                if (StringUtils.equals(relationshipType, "PRE")) {
                    testCase = caseMap.get(relationshipEdge.getTargetId());
                } else {
                    testCase = caseMap.get(relationshipEdge.getSourceId());
                }
                if (testCase == null) {
                    continue; // ????????????????????????
                }
                relationshipEdgeDTO.setTargetName(testCase.getName());
                relationshipEdgeDTO.setCreator(testCase.getCreateUser());
                relationshipEdgeDTO.setTargetNum(testCase.getNum());
                relationshipEdgeDTO.setTargetCustomNum(testCase.getCustomNum());
                relationshipEdgeDTO.setStatus(testCase.getStatus());
                results.add(relationshipEdgeDTO);
            }
            return results;
        }
        return new ArrayList<>();
    }

    public void buildUserInfo(List<? extends TestCase> testCases) {
        List<String> userIds = new ArrayList();
        userIds.addAll(testCases.stream().map(TestCase::getCreateUser).collect(Collectors.toList()));
        userIds.addAll(testCases.stream().map(TestCase::getDeleteUserId).collect(Collectors.toList()));
        userIds.addAll(testCases.stream().map(TestCase::getMaintainer).collect(Collectors.toList()));
        if (!CollectionUtils.isEmpty(userIds)) {
            Map<String, String> userMap = ServiceUtils.getUserNameMap(userIds);
            testCases.forEach(caseResult -> {
                caseResult.setCreateUser(userMap.get(caseResult.getCreateUser()));
                caseResult.setDeleteUserId(userMap.get(caseResult.getDeleteUserId()));
                caseResult.setMaintainer(userMap.get(caseResult.getMaintainer()));
            });
        }
    }

    public int getRelationshipCount(String id) {
        return relationshipEdgeService.getRelationshipCount(id, extTestCaseMapper::countByIds);
    }

    public List<String> getFollows(String caseId) {
        List<String> result = new ArrayList<>();
        if (StringUtils.isBlank(caseId)) {
            return result;
        }
        TestCaseFollowExample example = new TestCaseFollowExample();
        example.createCriteria().andCaseIdEqualTo(caseId);
        List<TestCaseFollow> follows = testCaseFollowMapper.selectByExample(example);
        return follows.stream().map(TestCaseFollow::getFollowId).distinct().collect(Collectors.toList());
    }
}
