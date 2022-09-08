package io.metersphere.track.issue.client;

import com.alibaba.fastjson.JSONObject;
import io.metersphere.base.domain.Project;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.LogUtil;
import io.metersphere.track.issue.domain.Jira.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class JiraAbstractClient extends BaseClient {

    protected  String ENDPOINT;

    protected  String PREFIX;

    protected  String USER_NAME;

    protected  String PASSWD;

    protected String SEARCH;

    protected String startAt;

    protected String maxResults;

    protected String versions;

    protected String issueStatus;

    protected String issueCount;

    public JiraIssue getIssues(String issuesId) {
        LogUtil.info("getIssues: " + issuesId);
        ResponseEntity<String> responseEntity;
        responseEntity = restTemplate.exchange(getBaseUrl() + "/issue/" + issuesId, HttpMethod.GET, getAuthHttpEntity(), String.class);
        return  (JiraIssue) getResultForObject(JiraIssue.class, responseEntity);
    }

    public JiraAllIssue getAllIssues(Project project,String url) {
        LogUtil.info("JIRA项目关键字: " + project.getJiraKey());
        ResponseEntity<String> responseEntity;
        String req_url = ENDPOINT + SEARCH + project.getJiraKey() + url;
        responseEntity = restTemplate.exchange(req_url, HttpMethod.GET, getAuthHttpEntity(), String.class);
        return (JiraAllIssue) getResultForObject(JiraAllIssue.class, responseEntity);
    }

    public String getIssuesCount(Project project,String url) {
        LogUtil.info("JIRA项目关键字: " + project.getJiraKey());
        ResponseEntity<String> responseEntity;
        String req_url = ENDPOINT + SEARCH + project.getJiraKey() + url;
        responseEntity = restTemplate.exchange(req_url, HttpMethod.GET, getAuthHttpEntity(), String.class);
        return responseEntity.getBody();
    }

    public List<JiraIssueVersions> getIssueVersion(Project project) {
        ResponseEntity<String> responseEntity;
        String req_url = ENDPOINT + versions.replace("{projectIdOrKey}",project.getJiraKey());
        responseEntity = restTemplate.exchange(req_url, HttpMethod.GET, getAuthHttpEntity(), String.class);
        return JSONObject.parseArray(responseEntity.getBody(), JiraIssueVersions.class);
    }

    public List<String> getIssueStatus(Project project){
        ResponseEntity<String> responseEntity;
        String req_url = ENDPOINT + issueStatus.replace("{projectIdOrKey}",project.getJiraKey());
        responseEntity = restTemplate.exchange(req_url, HttpMethod.GET, getAuthHttpEntity(), String.class);

        List<String> statusType = JSONObject.parseArray(responseEntity.getBody(), String.class);
        List<String> statusValues = new ArrayList<>();
        statusType.forEach(item -> {
            JSONObject statusTypeData = JSONObject.parseObject(item);
            if(statusTypeData.get("name").equals("BUG")){
                List<String> statusesList = JSONObject.parseArray(statusTypeData.get("statuses").toString(),String.class);
                statusesList.forEach(item_item ->{
                    JSONObject statusesData = JSONObject.parseObject(item_item);
                    statusValues.add(statusesData.get("name").toString());
                });
            }
        });
        return statusValues;
    }

    public Integer getIssueStatusCount(Project project,String version, String status){
        ResponseEntity<String> responseEntity;
        String req_url = ENDPOINT + issueCount.replace("{projectIdOrKey}",project.getJiraKey()).replace("{status}",status);
        if(!version.equals("无")){
            req_url = req_url + " AND affectedVersion in (" + version + ")";
        }
        responseEntity = restTemplate.exchange(req_url, HttpMethod.GET, getAuthHttpEntity(), String.class);
        JSONObject statusCountData = JSONObject.parseObject(responseEntity.getBody().toString());
        return Integer.valueOf(statusCountData.get("total").toString());
    }

    public List<JiraField> getFields() {
        ResponseEntity<String> response = restTemplate.exchange(getBaseUrl() + "/field", HttpMethod.GET, getAuthHttpEntity(), String.class);
        return (List<JiraField>) getResultForList(JiraField.class, response);
    }

    public JiraAddIssueResponse addIssue(String body) {
        LogUtil.info("addIssue: " + body);
        HttpHeaders headers = getAuthHeader();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = null;
        try {
            response = restTemplate.exchange(getBaseUrl() + "/issue", HttpMethod.POST, requestEntity, String.class);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException(e.getMessage());
        }
        return (JiraAddIssueResponse) getResultForObject(JiraAddIssueResponse.class, response);
    }

    public void updateIssue(String id, String body) {
        LogUtil.info("addIssue: " + body);
        HttpHeaders headers = getAuthHeader();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);
        try {
           restTemplate.exchange(getBaseUrl() + "/issue/" + id, HttpMethod.PUT, requestEntity, String.class);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException(e.getMessage());
        }
    }

    public void deleteIssue(String id) {
        LogUtil.info("deleteIssue: " + id);
        restTemplate.exchange(getBaseUrl() + "/issue/" + id, HttpMethod.DELETE, getAuthHttpEntity(), String.class);
    }


    public void uploadAttachment(String issueKey, File file) {
        HttpHeaders authHeader = getAuthHeader();
        authHeader.add("X-Atlassian-Token", "no-check");
        authHeader.setContentType(MediaType.parseMediaType("multipart/form-data; charset=UTF-8"));

        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
        FileSystemResource fileResource = new FileSystemResource(file);
        paramMap.add("file", fileResource);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(paramMap, authHeader);
        ResponseEntity<String> response = null;
        try {
            response = restTemplate.exchange(getBaseUrl() + "/issue/" + issueKey + "/attachments", HttpMethod.POST, requestEntity, String.class);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException(e.getMessage());
        }
        System.out.println(response);
    }

    public void auth() {
        try {
            restTemplate.exchange(getBaseUrl() + "/myself", HttpMethod.GET, getAuthHttpEntity(), String.class);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException(e.getMessage());
        }
    }

    protected HttpEntity<MultiValueMap> getAuthHttpEntity() {
        return new HttpEntity<>(getAuthHeader());
    }

    protected HttpHeaders getAuthHeader() {
        return getBasicHttpHeaders(USER_NAME, PASSWD);
    }

    protected String getBaseUrl() {
        return ENDPOINT + PREFIX;
    }

    public void setConfig(JiraConfig config) {
        if (config == null) {
            MSException.throwException("config is null");
        }
        String url = config.getUrl();

        if (StringUtils.isNotBlank(url) && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        ENDPOINT = url;
        USER_NAME = config.getAccount();
        PASSWD = config.getPassword();
    }
}
