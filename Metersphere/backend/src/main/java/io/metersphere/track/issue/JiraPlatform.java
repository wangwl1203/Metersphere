package io.metersphere.track.issue;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.metersphere.base.domain.*;
import io.metersphere.commons.constants.IssuesManagePlatform;
import io.metersphere.commons.constants.IssuesStatus;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.EncryptUtils;
import io.metersphere.commons.utils.LogUtil;
import io.metersphere.dto.CustomFieldItemDTO;
import io.metersphere.dto.UserDTO;
import io.metersphere.service.CustomFieldService;
import io.metersphere.track.dto.DemandDTO;
import io.metersphere.track.issue.client.JiraClientV2;
import io.metersphere.track.issue.domain.Jira.*;
import io.metersphere.track.issue.domain.PlatformUser;
import io.metersphere.track.request.testcase.IssuesRequest;
import io.metersphere.track.request.testcase.IssuesUpdateRequest;
import javafx.scene.image.PixelFormat;
import net.sf.saxon.expr.instruct.ForEach;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.formula.functions.T;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JiraPlatform extends AbstractIssuePlatform {

    protected String key = IssuesManagePlatform.Jira.toString();

    private JiraClientV2 jiraClientV2 = new JiraClientV2();

    public JiraPlatform(IssuesRequest issuesRequest) {
        super(issuesRequest);
    }

    public JiraConfig getConfig() {
        String config = getPlatformConfig(IssuesManagePlatform.Jira.toString());
        JiraConfig jiraConfig = JSONObject.parseObject(config, JiraConfig.class);
        validateConfig(jiraConfig);
        return jiraConfig;
    }

    public JiraConfig getUserConfig() {
        JiraConfig jiraConfig = null;
        String config = getPlatformConfig(IssuesManagePlatform.Jira.toString());
        if (StringUtils.isNotBlank(config)) {
            jiraConfig = JSONObject.parseObject(config, JiraConfig.class);
            UserDTO.PlatformInfo userPlatInfo = getUserPlatInfo(this.workspaceId);
            if (userPlatInfo != null && StringUtils.isNotBlank(userPlatInfo.getJiraAccount())
                    && StringUtils.isNotBlank(userPlatInfo.getJiraPassword())) {
                jiraConfig.setAccount(userPlatInfo.getJiraAccount());
                jiraConfig.setPassword(userPlatInfo.getJiraPassword());
            }
        }
        validateConfig(jiraConfig);
        return jiraConfig;
    }

    @Override
    public List<IssuesDao> getIssue(IssuesRequest issuesRequest) {
        issuesRequest.setPlatform(IssuesManagePlatform.Jira.toString());
        List<IssuesDao> issues;
        if (StringUtils.isNotBlank(issuesRequest.getProjectId())) {
            issues = extIssuesMapper.getIssues(issuesRequest);
        } else {
            issues = extIssuesMapper.getIssuesByCaseId(issuesRequest);
        }
        return issues;
    }

    public void parseIssue(IssuesWithBLOBs item, JiraIssue jiraIssue, String customFieldsStr) {
        String lastmodify = "";
        String status = "";
        JSONObject fields = jiraIssue.getFields();

        status = getStatus(fields);
        JSONObject assignee = (JSONObject) fields.get("assignee");
        String description = fields.getString("description");

        Parser parser = Parser.builder().build();
        if (StringUtils.isNotBlank(description)) {
            Node document = parser.parse(description);
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            description = renderer.render(document);
        }

        if (assignee != null) {
            lastmodify = assignee.getString("displayName");
        }
        item.setTitle(fields.getString("summary"));
        item.setCreateTime(fields.getLong("created"));
        item.setLastmodify(lastmodify);
        item.setDescription(description);
        item.setPlatformStatus(status);
        item.setPlatform(IssuesManagePlatform.Jira.toString());
        item.setCustomFields(syncIssueCustomField(customFieldsStr, jiraIssue.getFields()));
    }
    public void parseJiraIssue(IssuesWithBLOBs item, JiraIssue jiraIssue) {
        JSONObject fields = jiraIssue.getFields();
        JSONObject assignee = (JSONObject) fields.get("assignee");

//        JSONObject assignee = (JSONObject) fields.get("assignee");
//        String description = fields.getString("description");
//
//        Parser parser = Parser.builder().build();
//        if (StringUtils.isNotBlank(description)) {
//            Node document = parser.parse(description);
//            HtmlRenderer renderer = HtmlRenderer.builder().build();
//            description = renderer.render(document);
//        }
//
//        if (assignee != null) {
//            lastmodify = assignee.getString("displayName");
//        }
//        item.setTitle(fields.getString("summary"));
//        item.setCreateTime(fields.getLong("created"));
//        item.setLastmodify(lastmodify);
//        item.setDescription(description);
//        item.setPlatformStatus(status);
//        item.setPlatform(IssuesManagePlatform.Jira.toString());
//        item.setCustomFields(syncIssueCustomField(customFieldsStr, jiraIssue.getFields()));
    }
    private String getStatus(JSONObject fields) {
        JSONObject statusObj = (JSONObject) fields.get("status");
        if (statusObj != null) {
            JSONObject statusCategory = (JSONObject) statusObj.get("statusCategory");
            return statusCategory.getString("name");
        }
        return "";
    }

    @Override
    public List<DemandDTO> getDemandList(String projectId,String flag, String param,Integer pageID, Integer recPerPage, Integer recTotal,String demandId) {
        List<DemandDTO> list = new ArrayList<>();

        try {
            String key = validateJiraKey(projectId);
            String config = getPlatformConfig(IssuesManagePlatform.Jira.toString());
            JSONObject object = JSON.parseObject(config);

            if (object == null) {
                MSException.throwException("jira config is null");
            }

            String account = object.getString("account");
            String password = object.getString("password");
            String url = object.getString("url");
            String type = object.getString("storytype");
            String auth = EncryptUtils.base64Encoding(account + ":" + password);
            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.add("Authorization", "Basic " + auth);
            requestHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            //HttpEntity
            HttpEntity<String> requestEntity = new HttpEntity<>(requestHeaders);
            RestTemplate restTemplate = new RestTemplate();
            //post
            ResponseEntity<String> responseEntity = null;
            int maxResults = 50, startAt = 0, total = 0, currentStartAt = 0;
            do {
                String jql = url + "rest/api/2/search?jql=project=" + key + " AND issuetype= " + type
                        + "&maxResults=" + maxResults + "&startAt=" + startAt + "&fields=summary,issuetype";
                responseEntity = restTemplate.exchange(jql,
                        HttpMethod.GET, requestEntity, String.class);
                String body = responseEntity.getBody();
                JSONObject jsonObject = JSONObject.parseObject(body);
                JSONArray jsonArray = jsonObject.getJSONArray("issues");
                if (jsonArray.size() == 0) {
                    break;
                }
                total = jsonObject.getInteger("total");
                startAt = startAt + maxResults;
                currentStartAt = jsonObject.getInteger("startAt");
                for (int i = 0; i < jsonArray.size(); i++) {
                    JSONObject o = jsonArray.getJSONObject(i);
                    String issueKey = o.getString("key");
                    JSONObject fields = o.getJSONObject("fields");
                    String summary = fields.getString("summary");
                    DemandDTO demandDTO = new DemandDTO();
                    demandDTO.setName(summary);
                    demandDTO.setId(issueKey);
                    demandDTO.setPlatform(IssuesManagePlatform.Jira.name());
                    list.add(demandDTO);
                }
            } while (currentStartAt + maxResults < total);


        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
        }

        return list;
    }

    private void validateConfig(JiraConfig config) {
        jiraClientV2.setConfig(config);
        if (config == null) {
            MSException.throwException("jira config is null");
        }
        if (StringUtils.isBlank(config.getIssuetype())) {
            MSException.throwException("Jira ??????????????????");
        }
    }

    private String validateJiraKey(String projectId) {
        String jiraKey = getProjectId(projectId);
        if (StringUtils.isBlank(jiraKey)) {
            MSException.throwException("?????????Jira ??????Key");
        }
        return jiraKey;
    }

    @Override
    public void addIssue(IssuesUpdateRequest issuesRequest) {

        JSONObject addJiraIssueParam = buildUpdateParam(issuesRequest);

        JiraAddIssueResponse result = jiraClientV2.addIssue(JSONObject.toJSONString(addJiraIssueParam));
        JiraIssue issues = jiraClientV2.getIssues(result.getId());

        List<File> imageFiles = getImageFiles(issuesRequest.getDescription());

        imageFiles.forEach(img -> {
            jiraClientV2.uploadAttachment(result.getKey(), img);
        });

        String status = getStatus(issues.getFields());
        issuesRequest.setPlatformStatus(status);

        issuesRequest.setPlatformId(result.getKey());
        issuesRequest.setId(UUID.randomUUID().toString());

        // ???????????????
        insertIssues(issuesRequest);

        // ????????????????????????????????????????????????
        handleTestCaseIssues(issuesRequest);
    }

    private JSONObject buildUpdateParam(IssuesUpdateRequest issuesRequest) {

        issuesRequest.setPlatform(IssuesManagePlatform.Jira.toString());

        JiraConfig config = getUserConfig();
        jiraClientV2.setConfig(config);

        String jiraKey = validateJiraKey(issuesRequest.getProjectId());

        JSONObject fields = new JSONObject();
        JSONObject project = new JSONObject();

        String desc = issuesRequest.getDescription();
        desc = removeImage(desc);

        fields.put("project", project);
        project.put("key", jiraKey);

        JSONObject issuetype = new JSONObject();
        issuetype.put("name", config.getIssuetype());

        fields.put("summary", issuesRequest.getTitle());
//        fields.put("description", new JiraIssueDescription(desc));
        fields.put("description", desc);
        fields.put("issuetype", issuetype);

        JSONObject addJiraIssueParam = new JSONObject();
        addJiraIssueParam.put("fields", fields);

        List<CustomFieldItemDTO> customFields = CustomFieldService.getCustomFields(issuesRequest.getCustomFields());
        jiraClientV2.setConfig(config);

        customFields.forEach(item -> {
            String fieldName = item.getCustomData();
            if (StringUtils.isNotBlank(fieldName)) {
                if (item.getValue() != null) {
                    if (StringUtils.isNotBlank(item.getType()) &&
                            StringUtils.equalsAny(item.getType(), "select", "radio", "member")) {
                        JSONObject param = new JSONObject();
                        if (fieldName.equals("assignee") || fieldName.equals("reporter")) {
                            param.put("name", item.getValue());
                        } else {
                            param.put("id", item.getValue());
                        }
                        fields.put(fieldName, param);
                    } else if (StringUtils.isNotBlank(item.getType()) &&
                            StringUtils.equalsAny(item.getType(),  "multipleSelect", "checkbox", "multipleMember")) {
                        JSONArray attrs = new JSONArray();
                        if (item.getValue() != null) {
                            JSONArray values = (JSONArray)item.getValue();
                            values.forEach(v -> {
                                JSONObject param = new JSONObject();
                                param.put("id", v);
                                attrs.add(param);
                            });
                        }
                        fields.put(fieldName, attrs);
                    } else {
                        fields.put(fieldName, item.getValue());
                    }
                }
            }
        });

        return addJiraIssueParam;
    }

    @Override
    public void updateIssue(IssuesUpdateRequest request) {
        JSONObject param = buildUpdateParam(request);
        handleIssueUpdate(request);
        jiraClientV2.updateIssue(request.getPlatformId(), JSONObject.toJSONString(param));
    }

    @Override
    public void deleteIssue(String id) {
        IssuesWithBLOBs issuesWithBLOBs = issuesMapper.selectByPrimaryKey(id);
        super.deleteIssue(id);
        setConfig();
        jiraClientV2.deleteIssue(issuesWithBLOBs.getPlatformId());
    }

    @Override
    public void testAuth() {
        setConfig();
        jiraClientV2.auth();
    }

    @Override
    public void userAuth(UserDTO.PlatformInfo userInfo) {
        String config = getPlatformConfig(IssuesManagePlatform.Jira.toString());
        JiraConfig jiraConfig = JSONObject.parseObject(config, JiraConfig.class);
        jiraConfig.setAccount(userInfo.getJiraAccount());
        jiraConfig.setPassword(userInfo.getJiraPassword());
        validateConfig(jiraConfig);
        jiraClientV2.setConfig(jiraConfig);
        jiraClientV2.auth();
    }

    @Override
    public List<PlatformUser> getPlatformUser() {
        return null;
    }

    @Override
    public void syncIssues(Project project, List<IssuesDao> issues) {
        issues.forEach(item -> {
            setConfig();
            try {
                IssuesWithBLOBs issuesWithBLOBs = issuesMapper.selectByPrimaryKey(item.getId());
                parseIssue(item, jiraClientV2.getIssues(item.getPlatformId()), issuesWithBLOBs.getCustomFields());
                String desc = htmlDesc2MsDesc(item.getDescription());
                // ???????????????????????????
                String images = getImages(issuesWithBLOBs.getDescription());
                item.setDescription(desc + "\n" + images);

                issuesMapper.updateByPrimaryKeySelective(item);
            } catch (HttpClientErrorException e) {
                if (e.getRawStatusCode() == 404) {
                    // ???????????????
                    item.setPlatformStatus(IssuesStatus.DELETE.toString());
                    issuesMapper.deleteByPrimaryKey(item.getId());
                }
            } catch (Exception e) {
                LogUtil.error(e);
            }
        });
    }

    public List<JiraIssueVersions> getIssueVersion(Project project){
        /*
            ??????Jira??????????????????????????????
        * */
        setConfig();
        List<JiraIssueVersions> jiraIssueVersionsList = new ArrayList<>();
        try{
            return jiraClientV2.getIssueVersion(project);
        }catch (Exception e){
            LogUtil.error(e);
            return jiraIssueVersionsList;
        }
    }

    public List<String> getIssueStatus(Project project){
        /*
            ??????Jira????????????????????????????????????
        * */
        setConfig();
        List<String> list = new ArrayList<>();
        try{
            return jiraClientV2.getIssueStatus(project);
        }catch (Exception e){
            LogUtil.error(e);
            return list;
        }
    }

    public Integer getIssueStatusCount(Project project,String version, String status){
        /*
            ??????Jira???????????????????????????????????????
        * */
        setConfig();
        try{
            return jiraClientV2.getIssueStatusCount(project,version,status);
        }catch (Exception e){
            LogUtil.error(e);
            return 0;
        }
    }

    public Integer jiraIssueCount(Project project,String req_url){
        /*
            ??????Jira????????????
        * */
        Integer total = 0;
        setConfig();
        try {
            String jiraIssueCount = jiraClientV2.getIssuesCount(project,req_url);
            JSONObject jiraIssueData = JSONObject.parseObject(jiraIssueCount);
            total = Integer.valueOf(jiraIssueData.get("total").toString());

        } catch (Exception e) {
            LogUtil.error(e);
        }
        return total;
    }

    public List<IssuesDao> syncJiraIssues(Project project,String req_url) {
        /*
            ??????JIRA????????????
        * */
        setConfig();
        List<IssuesDao> issuesDaoList = new ArrayList<>();
        try {
            JiraAllIssue jiraAllIssue = jiraClientV2.getAllIssues(project,req_url);
            //??????jiraAllIssue.Issues
            for(int i=0; i<jiraAllIssue.getIssues().size();i++){
                JSONObject jsonData = JSONObject.parseObject(jiraAllIssue.getIssues().get(i).toString());

                String id = jsonData.getString("id");
                String key = jsonData.getString("key");
                JSONObject fields = (JSONObject) jsonData.get("fields");
                String title = fields.getString("summary");
                String platformStatus = JSONObject.parseObject(fields.getString("status")).getString("name");
                String description = fields.getString("description");
                String severityLevel = JSONObject.parseObject(fields.getString("customfield_10014")).getString("value");
                String issueType = JSONObject.parseObject(fields.getString("customfield_10013")).getString("value");
                String creator = JSONObject.parseObject(fields.getString("creator")).getString("displayName");

                IssuesDao issuesDao = new IssuesDao();
                issuesDao.setKey(key);
                issuesDao.setId(id);
                issuesDao.setNum(Integer.parseInt(id));
                issuesDao.setTitle(title);
                issuesDao.setPlatformStatus(platformStatus);
                issuesDao.setStatus(platformStatus);
                issuesDao.setPlatform("Jira");
                issuesDao.setDescription(description);
                issuesDao.setCount(jiraAllIssue.getTotal());
                issuesDao.setProjectId(project.getId());
                issuesDao.setProjectName(project.getName());
                issuesDao.setSeverityLevel(severityLevel);
                issuesDao.setIssueType(issueType);
                issuesDao.setCreatorName(creator);
                issuesDaoList.add(issuesDao);
            }
            issuesDaoList.sort((o1, o2) -> {
                if(Integer.valueOf(o1.getId()) < Integer.valueOf(o2.getId())){
                    return 1;
                }else{
                    return -1;
                }
            });
            return issuesDaoList;
        } catch (Exception e) {
            LogUtil.error(e);
            return issuesDaoList;
        }
    }

    @Override
    public String getProjectId(String projectId) {
        if (StringUtils.isNotBlank(projectId)) {
            return projectService.getProjectById(projectId).getJiraKey();
        }
        TestCaseWithBLOBs testCase = testCaseService.getTestCase(testCaseId);
        Project project = projectService.getProjectById(testCase.getProjectId());
        return project.getJiraKey();
    }

    public JiraConfig setConfig() {
        JiraConfig config = getConfig();
        jiraClientV2.setConfig(config);
        return config;
    }
}
