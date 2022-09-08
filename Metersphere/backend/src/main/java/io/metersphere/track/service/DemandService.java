package io.metersphere.track.service;

import io.metersphere.base.domain.Project;
import io.metersphere.base.domain.TestCaseWithBLOBs;
import io.metersphere.base.mapper.ProjectMapper;
import io.metersphere.commons.constants.IssuesManagePlatform;
import io.metersphere.service.ProjectService;
import io.metersphere.track.dto.DemandDTO;
import io.metersphere.track.issue.AbstractIssuePlatform;
import io.metersphere.track.issue.IssueFactory;
import io.metersphere.track.request.demand.DemandRelevanceRequest;
import io.metersphere.track.request.testcase.IssuesRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class DemandService {
    @Resource
    private IssuesService issuesService;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private TestCaseService testCaseService;
    @Resource
    private ProjectService projectService;

    public List<DemandDTO> common(Project project,String flag, String param, Integer goPage, Integer pageSize, Integer recTotal, String demandId){
        List<DemandDTO> list = new ArrayList<>();
        String workspaceId = "";
        String zentaoId = "";
        if(project!=null) {
            workspaceId = project.getWorkspaceId();
            zentaoId = project.getZentaoId();
        }
        boolean zentao = issuesService.isIntegratedPlatform(workspaceId, IssuesManagePlatform.Zentao.toString());
        List<String> platforms = new ArrayList<>();
        IssuesRequest issueRequest = new IssuesRequest();
        if (zentao) {
            if (StringUtils.isNotBlank(zentaoId)) {
                platforms.add(IssuesManagePlatform.Zentao.name());
            }
        }
        issueRequest.setWorkspaceId(workspaceId);
        List<AbstractIssuePlatform> platformList = IssueFactory.createPlatforms(platforms, issueRequest);
        platformList.forEach(platform -> {
            List<DemandDTO> demand = platform.getDemandList(project.getId(),flag,param,goPage,pageSize,recTotal,demandId);
            list.addAll(demand);
        });
        return list;
    }

    public List<DemandDTO> syncZanTaoDemands(IssuesRequest request, Integer pageID, Integer recPerPage, Integer recTotal, String flag){
        TestCaseWithBLOBs testCase = testCaseService.getTestCase(request.getCaseId());
        if (testCase == null) {
            return null;
        }
        String demandId = testCase.getDemandId();
        Project project = projectService.getProjectById(testCase.getProjectId());
        String plan = request.getPlanId();
        List<DemandDTO> list = common(project,flag,plan,pageID,recPerPage,recTotal,demandId);
        return list;
    }

    public List<DemandDTO> getDemandPlan(String projectId,String flag){
        Project project = projectMapper.selectByPrimaryKey(projectId);
        List<DemandDTO> list = common(project,flag,project.getZentaoId(),0,0,0,"");
        return list;
    }

    public List<DemandDTO> getDemands(String caseId,String flag){
        TestCaseWithBLOBs testCase = testCaseService.getTestCase(caseId);
        if (testCase == null) {
            return null;
        }
        Project project = projectService.getProjectById(testCase.getProjectId());
        String demandId = testCase.getDemandId();
        List<DemandDTO> list = new ArrayList<>();

        if(demandId !=null && !demandId.equals("")){
            list = common(project,flag,testCase.getDemandId(),0,0,0,"");
        }
        return list;
    }

    public void deleteDemandRelate(IssuesRequest request) {
        String caseId = request.getCaseId();
        String id = request.getId();
        TestCaseWithBLOBs testCase = testCaseService.getTestCase(caseId);
        if(id.equals(testCase.getDemandId())){
            testCase.setDemandId("");
            testCase.setDemandName("");
            testCaseService.editTestCase(testCase);
        }
    }

    public void relate(DemandRelevanceRequest request){
        String caseId = request.getCaseId();
        if (StringUtils.isNotBlank(caseId)) {
            TestCaseWithBLOBs testCase = testCaseService.getTestCase(caseId);
            List<String>  demandIds = request.getDemandIds();
            List<String>  demandNames = request.getDemandNames();
            //用例关联需求，仅允许关联一条
            if (!CollectionUtils.isEmpty(demandIds)) {
                for(int i=0; i<demandIds.size(); i++){
                    testCase.setDemandId(demandIds.get(i));
                    testCase.setDemandName(demandNames.get(i));
                    testCaseService.editTestCase(testCase);
                }
            }
        }
    }

    public List<DemandDTO> getDemandList(String projectId) {
        Project project = projectMapper.selectByPrimaryKey(projectId);

        String workspaceId = project.getWorkspaceId();
        boolean tapd = issuesService.isIntegratedPlatform(workspaceId, IssuesManagePlatform.Tapd.toString());
        boolean jira = issuesService.isIntegratedPlatform(workspaceId, IssuesManagePlatform.Jira.toString());
        boolean zentao = issuesService.isIntegratedPlatform(workspaceId, IssuesManagePlatform.Zentao.toString());
        boolean azureDevops = issuesService.isIntegratedPlatform(workspaceId, IssuesManagePlatform.AzureDevops.toString());
        List<DemandDTO> list = new ArrayList<>();
        List<String> platforms = new ArrayList<>();
        IssuesRequest issueRequest = new IssuesRequest();
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

        if (azureDevops) {
            String azureDevopsId = project.getAzureDevopsId();
            if (StringUtils.isNotBlank(azureDevopsId)) {
                platforms.add(IssuesManagePlatform.AzureDevops.name());
            }
        }

        issueRequest.setWorkspaceId(workspaceId);
        List<AbstractIssuePlatform> platformList = IssueFactory.createPlatforms(platforms, issueRequest);
        platformList.forEach(platform -> {
            List<DemandDTO> demand = platform.getDemandList(projectId,"","",0,0,0,"");
            list.addAll(demand);
        });

        return list;
    }
}
