package io.metersphere.excel.domain;

import com.alibaba.excel.annotation.ExcelIgnore;
import lombok.Getter;
import lombok.Setter;

/**
 * @author wwl
 * @since 2022-03-01
 */
@Getter
@Setter
public class WorkSpaceUserExcelData {
    @ExcelIgnore
    private String id;
    @ExcelIgnore
    private String name;
    @ExcelIgnore
    private String email;
    @ExcelIgnore
    private String password;
    @ExcelIgnore
    private String phone;
    @ExcelIgnore
    private String userIsProjectMember;
    @ExcelIgnore
    private String proMemberProject;
}
