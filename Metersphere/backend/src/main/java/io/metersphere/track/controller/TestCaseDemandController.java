package io.metersphere.track.controller;

import com.github.pagehelper.Page;
import io.metersphere.base.domain.Issues;
import io.metersphere.commons.constants.OperLogConstants;
import io.metersphere.commons.utils.PageUtils;
import io.metersphere.commons.utils.Pager;
import io.metersphere.log.annotation.MsAuditLog;
import io.metersphere.track.dto.DemandDTO;
import io.metersphere.track.request.demand.DemandRelevanceRequest;
import io.metersphere.track.request.testcase.IssuesRequest;
import io.metersphere.track.service.DemandService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RequestMapping("demand")
@RestController
public class TestCaseDemandController {
    @Resource
    private DemandService demandService;

    @GetMapping("/list/{projectId}")
    public List<DemandDTO> getDemandList(@PathVariable String projectId) {
        return demandService.getDemandList(projectId);
    }
    @GetMapping("/get/{id}")
    public List<DemandDTO> getDemands(@PathVariable String id) {
        return demandService.getDemands(id,"STORY_DETAIL_GET");
    }
    @PostMapping("/delete/relate")
    public void deleteRelate(@RequestBody IssuesRequest request) {
        demandService.deleteDemandRelate(request);
    }

    @GetMapping("/plan/list/{projectId}")
    public List<DemandDTO> getPlans(@PathVariable String projectId) {
        return demandService.getDemandPlan(projectId,"PLAN_GET");
    }

    @PostMapping("/list/relate/{pageID}/{recPerPage}")
    public Pager<List<DemandDTO>> relateList(@PathVariable int pageID, @PathVariable int recPerPage, @RequestBody IssuesRequest request) {
        List<DemandDTO> demandDtosList = demandService.syncZanTaoDemands(request,0,0,0,"STORY_BY_PLAN");
        Page<List<Issues>> page = new Page<>();
        page.setTotal(demandDtosList.size());
        page.setPageSize(recPerPage);
        page.setPages(pageID);
        page.setCount(true);
        return PageUtils.setPageInfo(page, demandService.syncZanTaoDemands(request,pageID,recPerPage,demandDtosList.size(),"STORY_BY_PLAN"));
    }

    @PostMapping("/relate")
    @MsAuditLog(module = "track_test_case", type = OperLogConstants.ASSOCIATE_DEMAND, content = "#msClass.getLogDetails(#request)", msClass = DemandService.class)
    public void relate(@RequestBody DemandRelevanceRequest request) {
        demandService.relate(request);
    }
}
