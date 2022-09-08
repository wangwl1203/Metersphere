package io.metersphere.track.issue.client;

import org.springframework.stereotype.Component;

@Component
public class JiraClientV2 extends JiraAbstractClient {
    {
        PREFIX = "/rest/api/2";

        SEARCH = "/rest/api/2/search?jql=project=";

        startAt = "&startAt=";

        maxResults = "&maxResults=";

        /*
        * 查询项目下所有影响版本
        * */
        versions = "/rest/api/2/project/{projectIdOrKey}/versions";

        /*
         * 查询项目下所有状态
         * */
        issueStatus = "/rest/api/2/project/{projectIdOrKey}/statuses";

        /*
         * 查询项目下不同状态缺陷数量
         * */
        issueCount = "/rest/api/2/search?jql=project={projectIdOrKey} AND status = {status}";
    }
}
