package io.metersphere.track.issue;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import io.metersphere.base.domain.*;
import io.metersphere.commons.constants.IssuesManagePlatform;
import io.metersphere.commons.constants.IssuesStatus;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.LogUtil;
import io.metersphere.dto.UserDTO;
import io.metersphere.track.dto.DemandDTO;
import io.metersphere.track.issue.client.ZentaoClient;
import io.metersphere.track.issue.domain.PlatformUser;
import io.metersphere.track.issue.domain.zentao.AddIssueResponse;
import io.metersphere.track.issue.domain.zentao.ZentaoBuild;
import io.metersphere.track.issue.domain.zentao.ZentaoConfig;
import io.metersphere.track.request.testcase.IssuesRequest;
import io.metersphere.track.request.testcase.IssuesUpdateRequest;
import io.metersphere.track.service.TestCaseService;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ZentaoPlatform extends AbstractIssuePlatform {
    /**
     * zentao account
     */
    private final String account;
    /**
     * zentao password
     */
    private final String password;
    /**
     * zentao url eg:http://x.x.x.x/zentao
     */
    private final String url;

    private final ZentaoClient zentaoClient;

    protected String key = IssuesManagePlatform.Zentao.toString();

    public ZentaoPlatform(IssuesRequest issuesRequest) {
        super(issuesRequest);
        String config = getPlatformConfig(IssuesManagePlatform.Zentao.toString());
        // todo
        if (StringUtils.isBlank(config)) {
            MSException.throwException("未集成禅道平台!");
        }
        JSONObject object = JSON.parseObject(config);
        this.account = object.getString("account");
        this.password = object.getString("password");
        this.url = object.getString("url");
        String type = object.getString("request");
        this.workspaceId = issuesRequest.getWorkspaceId();
        this.zentaoClient = ZentaoFactory.getInstance(this.url, type);
    }

    @Override
    public String getProjectId(String projectId) {
        if (StringUtils.isNotBlank(projectId)) {
            return projectService.getProjectById(projectId).getZentaoId();
        }
        TestCaseWithBLOBs testCase = testCaseService.getTestCase(testCaseId);
        Project project = projectService.getProjectById(testCase.getProjectId());
        return project.getZentaoId();
    }

    @Override
    public List<IssuesDao> getIssue(IssuesRequest issuesRequest) {
        issuesRequest.setPlatform(IssuesManagePlatform.Zentao.toString());
        List<IssuesDao> issues;
        if (StringUtils.isNotBlank(issuesRequest.getProjectId())) {
            issues = extIssuesMapper.getIssues(issuesRequest);
        } else {
            issues = extIssuesMapper.getIssuesByCaseId(issuesRequest);
        }
        return issues;
    }

    @Override
    public List<DemandDTO> getDemandList(String projectId,String flag, String param,Integer pageID, Integer recPerPage, Integer recTotal, String demandId) {
        List<DemandDTO> list = new ArrayList<>();
        try {
            setConfig();
            String session = zentaoClient.login();
            String key = getProjectId(projectId);
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(new HttpHeaders());
            RestTemplate restTemplate = new RestTemplate();
            String storyGet = "";
            if(flag.equals("STORY_DETAIL_GET")){
                storyGet = zentaoClient.requestUrl.getStoryDetailGet();
                key = param;
            }else if(flag.equals("PLAN_GET")){
                storyGet = zentaoClient.requestUrl.getPlanGet();
                key = param;
            }else if(flag.equals("STORY_BY_PLAN")) {
                if(pageID == 0 && recPerPage == 0){
                    storyGet = zentaoClient.requestUrl.getStoryByPlanAll();
                }else{
                    storyGet = zentaoClient.requestUrl.getStoryByPlan();
                    storyGet = storyGet.replace("$pageID", pageID.toString()).replace("$recPerPage",recPerPage.toString()).replace("$recTotal",recTotal.toString());
                }
                key = param;
            }else{
                storyGet = zentaoClient.requestUrl.getStoryGet();
            }

            if(key.equals("")){
                return list;
            }
            ResponseEntity<String> responseEntity = restTemplate.exchange(storyGet + session,
                    HttpMethod.POST, requestEntity, String.class, key);
            String body = responseEntity.getBody();
            JSONObject obj = JSONObject.parseObject(body);

            LogUtil.info("project story: " + key + obj);

            if (obj != null) {
                String data = obj.getString("data");
                if (StringUtils.isBlank(data)) {
                    return list;
                }
                // 兼容处理11.5版本格式 [{obj},{obj}]
                if (data.charAt(0) == '[') {
                    JSONArray array = obj.getJSONArray("data");
                    for (int i = 0; i < array.size(); i++) {
                        JSONObject o = array.getJSONObject(i);
                        DemandDTO demandDTO = new DemandDTO();
                        demandDTO.setId(o.getString("id"));
                        demandDTO.setName(o.getString("title"));
                        demandDTO.setPlatform(IssuesManagePlatform.Zentao.name());
                        list.add(demandDTO);
                    }
                }
                // 处理格式 {{"id": {obj}},{"id",{obj}}}
                else if (data.charAt(0) == '{') {
                    JSONObject dataObject = obj.getJSONObject("data");
                    String s = JSON.toJSONString(dataObject);
                    Map<String, Object> map = JSONArray.parseObject(s, new TypeReference<Map<String, Object>>(){});
                    if(flag.equals("STORY_DETAIL_GET")){
                        JSONObject detailData = JSONObject.parseObject(map.get("story").toString());
                        DemandDTO demandDTO = new DemandDTO();
                        demandDTO.setId(detailData.getString("id"));

                        String moduleList = map.get("modulePath").toString();
                        JSONArray moduleArray = JSONArray.parseArray(moduleList);
                        String moduleName = "/";
                        if(moduleArray!=null && moduleArray.size()>0){
                            for (int i = 0; i < moduleArray.size(); i++) {
                                moduleName = moduleName + moduleArray.getJSONObject(i).getString("name") + "/";
                            }
                        }
                        moduleName =  moduleName.substring(0,moduleName.length() -1);

                        demandDTO.setModule(moduleName);
                        demandDTO.setTitle(detailData.getString("title"));
                        demandDTO.setPlatformStatus(detailData.getString("status"));
                        demandDTO.setStage(detailData.getString("stage"));
                        demandDTO.setDescription(detailData.getString("spec"));

                        String plan = detailData.get("plan").toString();
                        JSONObject jsonData =JSONObject.parseObject(detailData.get("planTitle").toString());
                        demandDTO.setPlan(jsonData.getString(plan));
                        list.add(demandDTO);
                    }else if(flag.equals("STORY_BY_PLAN")){
                        //获取所属模块
                        JSONObject moduleData = JSONObject.parseObject(JSON.toJSONString(map.get("modulePairs")));
                        String moduleStr = JSON.toJSONString(moduleData);
                        Map<String, String> module_map = JSONArray.parseObject(moduleStr, new TypeReference<Map<String, String>>(){});
                        //获取所有需求
                        JSONObject storiesData = JSONObject.parseObject(JSON.toJSONString(map.get("planStories")));
                        String storiesStr = JSON.toJSONString(storiesData);
                        Map<String, Object> stories_map = JSONArray.parseObject(storiesStr, new TypeReference<Map<String, Object>>(){});
                        for (String key_str : stories_map.keySet()) {
                            DemandDTO demandDTO = new DemandDTO();
                            //过滤调已经关联的需求
                            if(key_str.equals(demandId)){
                                continue;
                            }
                            demandDTO.setId(key_str);
                            JSONObject storiesDetailData = JSONObject.parseObject(JSON.toJSONString(stories_map.get(key_str)));
                            demandDTO.setModule(module_map.get(storiesDetailData.getString("module")));
                            demandDTO.setTitle(storiesDetailData.getString("title"));
                            demandDTO.setPlatformStatus(storiesDetailData.getString("status"));
                            demandDTO.setStage(storiesDetailData.getString("stage"));
                            list.add(demandDTO);
                        }
                    }else if(flag.equals("PLAN_GET")){
                        JSONObject plansData = JSONObject.parseObject(JSON.toJSONString(map.get("plans")));
                        String plansStr = JSON.toJSONString(plansData);
                        Map<String, String> plans_map = JSONArray.parseObject(plansStr, new TypeReference<Map<String, String>>(){});
                        for (String key_str : plans_map.keySet()) {
                            DemandDTO demandDTO = new DemandDTO();
                            demandDTO.setId(key_str);
                            JSONObject planDetailData = JSONObject.parseObject(plans_map.get(key_str));
                            demandDTO.setTitle(planDetailData.getString("title"));
                            list.add(demandDTO);
                        }
                    }else{
                        Collection<Object> values = map.values();
                        values.forEach(v -> {
                            JSONObject jsonObject = JSONObject.parseObject(JSON.toJSONString(v));
                            DemandDTO demandDTO = new DemandDTO();
                            demandDTO.setId(jsonObject.getString("id"));
                            demandDTO.setName(jsonObject.getString("title"));
                            demandDTO.setPlatform(IssuesManagePlatform.Zentao.name());
                            list.add(demandDTO);
                        });
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.error("get zentao demand fail " + e.getMessage());
        }
        list.sort((o1, o2) -> {
            if(Integer.valueOf(o1.getId()) < Integer.valueOf(o2.getId())){
                return 1;
            }else{
                return -1;
            }
        });
        return list;
    }

    public List<DemandDTO> getDemandListExt(String projectId,String type) {
        //getTestStories
        List<DemandDTO> list = new ArrayList<>();
        try {
            setConfig();
            String session = zentaoClient.login();
            String key = getProjectId(projectId);
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(new HttpHeaders());
            RestTemplate restTemplate = new RestTemplate();
            String storyGet = zentaoClient.requestUrl.getStoryGet();
            ResponseEntity<String> responseEntity = restTemplate.exchange(storyGet + session,
                    HttpMethod.POST, requestEntity, String.class, key);
            String body = responseEntity.getBody();
            JSONObject obj = JSONObject.parseObject(body);

            LogUtil.info("project story: " + key + obj);

            if (obj != null) {
                String data = obj.getString("data");
                if (StringUtils.isBlank(data)) {
                    return list;
                }
                // 兼容处理11.5版本格式 [{obj},{obj}]
                if (data.charAt(0) == '[') {
                    JSONArray array = obj.getJSONArray("data");
                    for (int i = 0; i < array.size(); i++) {
                        JSONObject o = array.getJSONObject(i);
                        DemandDTO demandDTO = new DemandDTO();
                        demandDTO.setId(o.getString("id"));
                        demandDTO.setName(o.getString("title"));
                        demandDTO.setPlatform(IssuesManagePlatform.Zentao.name());
                        list.add(demandDTO);
                    }
                }
                // 处理格式 {{"id": {obj}},{"id",{obj}}}
                else if (data.charAt(0) == '{') {
                    JSONObject dataObject = obj.getJSONObject("data");
                    String s = JSON.toJSONString(dataObject);
                    Map<String, Object> map = JSONArray.parseObject(s, new TypeReference<Map<String, Object>>(){});
                    Collection<Object> values = map.values();
                    values.forEach(v -> {
                        JSONObject jsonObject = JSONObject.parseObject(JSON.toJSONString(v));
                        DemandDTO demandDTO = new DemandDTO();
                        demandDTO.setId(jsonObject.getString("id"));
                        demandDTO.setName(jsonObject.getString("title"));
                        demandDTO.setPlatform(IssuesManagePlatform.Zentao.name());
                        list.add(demandDTO);
                    });
                }
            }
        } catch (Exception e) {
            LogUtil.error("get zentao demand fail " + e.getMessage());
        }
        return list;
    }

    public IssuesDao getZentaoIssues(IssuesDao issue) {
        JSONObject bug = zentaoClient.getBugById(issue.getPlatformId());
        String description = bug.getString("steps");
        String steps = description;
        try {
            steps = zentao2MsDescription(description);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
        }
        IssuesDao issues = new IssuesDao();
        issues.setPlatformStatus(bug.getString("status"));
        if (StringUtils.equals(bug.getString("deleted"),"1")) {
            issues.setPlatformStatus(IssuesStatus.DELETE.toString());
            issuesMapper.updateByPrimaryKeySelective(issues);
        }
        issues.setTitle(bug.getString("title"));
        issues.setDescription(steps);
        issues.setReporter(bug.getString("openedBy"));
        IssuesWithBLOBs issuesWithBLOBs = issuesMapper.selectByPrimaryKey(issue.getId());
        issuesWithBLOBs.setCustomFields(syncIssueCustomField(issuesWithBLOBs.getCustomFields(), bug));
        return issues;
    }

    @Override
    public void addIssue(IssuesUpdateRequest issuesRequest) {

        MultiValueMap<String, Object> param = buildUpdateParam(issuesRequest);
        AddIssueResponse.Issue issue = zentaoClient.addIssue(param);
        issuesRequest.setPlatformStatus(issue.getStatus());

        String id = issue.getId();
        if (StringUtils.isNotBlank(id)) {
            issuesRequest.setPlatformId(id);
            issuesRequest.setId(UUID.randomUUID().toString());

            IssuesExample issuesExample = new IssuesExample();
            issuesExample.createCriteria().andIdEqualTo(id)
                    .andPlatformEqualTo(IssuesManagePlatform.Zentao.toString());
            if (issuesMapper.selectByExample(issuesExample).size() <= 0) {
                // 插入缺陷表
                insertIssues(issuesRequest);
            }

            // 用例与第三方缺陷平台中的缺陷关联
            handleTestCaseIssues(issuesRequest);
        }
    }

    @Override
    public void updateIssue(IssuesUpdateRequest request) {
        MultiValueMap<String, Object> param = buildUpdateParam(request);
        handleIssueUpdate(request);
        zentaoClient.setConfig(getUserConfig());
        zentaoClient.updateIssue(request.getPlatformId(), param);
    }

    private MultiValueMap<String, Object> buildUpdateParam(IssuesUpdateRequest issuesRequest) {
        issuesRequest.setPlatform(IssuesManagePlatform.Zentao.toString());

        zentaoClient.setConfig(getUserConfig());
        String projectId = getProjectId(issuesRequest.getProjectId());
        if (StringUtils.isBlank(projectId)) {
            MSException.throwException("未关联禅道项目ID.");
        }
        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
        paramMap.add("product", projectId);
        paramMap.add("title", issuesRequest.getTitle());

        addCustomFields(issuesRequest, paramMap);

        String description = issuesRequest.getDescription();
        String zentaoSteps = description;

        // transfer description
        try {
            zentaoSteps = ms2ZentaoDescription(description);
            zentaoSteps = zentaoSteps.replaceAll("\\n", "<br/>");
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
        }
        LogUtil.info("zentao description transfer: " + zentaoSteps);

        paramMap.add("steps", zentaoSteps);
        if (!CollectionUtils.isEmpty(issuesRequest.getZentaoBuilds())) {
            List<String> builds = issuesRequest.getZentaoBuilds();
            builds.forEach(build -> paramMap.add("openedBuild[]", build));
        } else {
            paramMap.add("openedBuild", "trunk");
        }
        if (StringUtils.isNotBlank(issuesRequest.getZentaoAssigned())) {
            paramMap.add("assignedTo", issuesRequest.getZentaoAssigned());
        }
        return paramMap;
    }

    @Override
    public void deleteIssue(String id) {
        IssuesWithBLOBs issuesWithBLOBs = issuesMapper.selectByPrimaryKey(id);
        super.deleteIssue(id);
        zentaoClient.setConfig(getUserConfig());
        zentaoClient.deleteIssue(issuesWithBLOBs.getPlatformId());
    }

    @Override
    public void testAuth() {
        setConfig();
        zentaoClient.login();
    }

    @Override
    public void userAuth(UserDTO.PlatformInfo userInfo) {
        String config = getPlatformConfig(IssuesManagePlatform.Zentao.toString());
        ZentaoConfig zentaoConfig = JSONObject.parseObject(config, ZentaoConfig.class);
        zentaoConfig.setAccount(userInfo.getZentaoUserName());
        zentaoConfig.setPassword(userInfo.getZentaoPassword());
        zentaoClient.setConfig(zentaoConfig);
        zentaoClient.login();
    }

    public ZentaoConfig setConfig() {
        ZentaoConfig config = getConfig();
        zentaoClient.setConfig(config);
        return config;
    }

    public ZentaoConfig getConfig() {
        ZentaoConfig zentaoConfig = null;
        String config = getPlatformConfig(IssuesManagePlatform.Zentao.toString());
        zentaoConfig = JSONObject.parseObject(config, ZentaoConfig.class);
//        validateConfig(tapdConfig);
        return zentaoConfig;
    }

    public ZentaoConfig getUserConfig() {
        ZentaoConfig zentaoConfig = null;
        String config = getPlatformConfig(IssuesManagePlatform.Zentao.toString());
        if (StringUtils.isNotBlank(config)) {
            zentaoConfig = JSONObject.parseObject(config, ZentaoConfig.class);
            UserDTO.PlatformInfo userPlatInfo = getUserPlatInfo(this.workspaceId);
            if (userPlatInfo != null && StringUtils.isNotBlank(userPlatInfo.getZentaoUserName())
                    && StringUtils.isNotBlank(userPlatInfo.getZentaoPassword())) {
                zentaoConfig.setAccount(userPlatInfo.getZentaoUserName());
                zentaoConfig.setPassword(userPlatInfo.getZentaoPassword());
            }
        }
//        validateConfig(jiraConfig);
        return zentaoConfig;
    }

    @Override
    public List<PlatformUser> getPlatformUser() {
        setConfig();
        String session = zentaoClient.login();;
        HttpHeaders httpHeaders = new HttpHeaders();
        HttpEntity<MultiValueMap<String,String>> requestEntity = new HttpEntity<>(httpHeaders);
        RestTemplate restTemplate = new RestTemplate();
        String getUser = zentaoClient.requestUrl.getUserGet();
        ResponseEntity<String> responseEntity = restTemplate.exchange(getUser + session,
                HttpMethod.GET, requestEntity, String.class);
        String body = responseEntity.getBody();
        JSONObject obj = JSONObject.parseObject(body);

        LogUtil.info("zentao user " + obj);

        JSONArray data = obj.getJSONArray("data");

        List<PlatformUser> users = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            JSONObject o = data.getJSONObject(i);
            PlatformUser platformUser = new PlatformUser();
            String account = o.getString("account");
            String username = o.getString("realname");
            platformUser.setName(username);
            platformUser.setUser(account);
            users.add(platformUser);
        }
        return users;
    }

    @Override
    public void syncIssues(Project project, List<IssuesDao> issues) {
        issues.forEach(item -> {
            setConfig();
            IssuesDao issuesDao = getZentaoIssues(item);
            issuesDao.setId(item.getId());
            issuesMapper.updateByPrimaryKeySelective(issuesDao);
        });
    }

    public List<ZentaoBuild> getBuilds() {
        setConfig();
        String session = zentaoClient.login();;
        String projectId1 = getProjectId(projectId);
        HttpHeaders httpHeaders = new HttpHeaders();
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(httpHeaders);
        RestTemplate restTemplate = new RestTemplate();
        String buildGet = zentaoClient.requestUrl.getBuildsGet();
        ResponseEntity<String> responseEntity = restTemplate.exchange(buildGet + session,
                HttpMethod.GET, requestEntity, String.class, projectId1);
        String body = responseEntity.getBody();
        JSONObject obj = JSONObject.parseObject(body);

        LogUtil.info("zentao builds" + obj);

        JSONObject data = obj.getJSONObject("data");
        Map<String,Object> maps = data.getInnerMap();

        List<ZentaoBuild> list = new ArrayList<>();
        for (Map.Entry<String, Object> map : maps.entrySet()) {
            ZentaoBuild build = new ZentaoBuild();
            String id = map.getKey();
            if (StringUtils.isNotBlank(id)) {
                build.setId(map.getKey());
                build.setName((String) map.getValue());
                list.add(build);
            }
        }
        return list;
    }

    private String uploadFile(FileSystemResource resource) {
        String id = "";
        zentaoClient.setConfig(getUserConfig());
        String session = zentaoClient.login();
        HttpHeaders httpHeaders = new HttpHeaders();
        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(paramMap, httpHeaders);
        paramMap.add("files", resource);
        RestTemplate restTemplate = new RestTemplate();
        try {
            String fileUpload = zentaoClient.requestUrl.getFileUpload();
            ResponseEntity<String> responseEntity = restTemplate.exchange(fileUpload + session,
                    HttpMethod.POST, requestEntity, String.class);
            String body = responseEntity.getBody();
            JSONObject obj = JSONObject.parseObject(body);
            JSONObject data = obj.getJSONObject("data");
            Set<String> set = data.getInnerMap().keySet();
            if (!set.isEmpty()) {
                id = (String) set.toArray()[0];
            }
        } catch (Exception e) {
            LogUtil.error(e, e.getMessage());
        }
        LogUtil.info("upload file id: " + id);
        return id;
    }

    private String ms2ZentaoDescription(String msDescription) {
        String imgUrlRegex = "!\\[.*?]\\(/resource/md/get/(.*?\\..*?)\\)";
        String zentaoSteps = msDescription.replaceAll(imgUrlRegex, zentaoClient.requestUrl.getReplaceImgUrl());
        Matcher matcher = zentaoClient.requestUrl.getImgPattern().matcher(zentaoSteps);
        while (matcher.find()) {
            // get file name
            String fileName = matcher.group(1);
            // get file
            ResponseEntity<FileSystemResource> mdImage = resourceService.getMdImage(fileName);
            // upload zentao
            String id = uploadFile(mdImage.getBody());
            // todo delete local file
            int index = fileName.lastIndexOf(".");
            if (index != -1) {
                fileName = fileName.substring(0, index);
            }
            // replace id
            zentaoSteps = zentaoSteps.replaceAll(Pattern.quote(fileName), id);
        }
        // image link
        String netImgRegex = "!\\[(.*?)]\\((http.*?)\\)";
        return zentaoSteps.replaceAll(netImgRegex, "<img src=\"$2\" alt=\"$1\"/>");
    }

    private String zentao2MsDescription(String ztDescription) {
        // todo 图片回显
        String imgRegex = "<img src.*?/>";
        return ztDescription.replaceAll(imgRegex, "");
    }
}
