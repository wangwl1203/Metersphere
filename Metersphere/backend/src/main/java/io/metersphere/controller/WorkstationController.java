package io.metersphere.controller;

import io.metersphere.service.WorkstationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@RequestMapping("workstation")
@RestController
public class WorkstationController {

    @Resource
    private WorkstationService workstationService;

    @PostMapping("/creat_case_count/list")
    public Map<String,Integer> list() {

        return  workstationService.getMyCreatedCaseGroupContMap();
    }
}
