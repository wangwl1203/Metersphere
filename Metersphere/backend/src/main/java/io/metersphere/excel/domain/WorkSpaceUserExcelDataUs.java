package io.metersphere.excel.domain;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * @author wwl
 * @since 2022-03-01
 */
@Data
@ColumnWidth(15)
public class WorkSpaceUserExcelDataUs extends WorkSpaceUserExcelData{
    @NotBlank(message = "{cannot_be_null}")
    @Length(max = 255)
    @ExcelProperty("Id")
    private String id;

    @NotBlank(message = "{cannot_be_null}")
    @Length(max = 255)
    @ColumnWidth(20)
    @ExcelProperty("Name")
    private String name;

    @NotBlank(message = "{cannot_be_null}")
    @Length(max = 255)
    @ExcelProperty("E-mail")
    @ColumnWidth(30)
    @Pattern(regexp = "^[a-zA-Z0-9_._-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$", message = "{user_import_format_wrong}")
    private String email;

    @NotBlank(message = "{cannot_be_null}")
    @Length(max = 255)
    @ExcelProperty("Password")
    @ColumnWidth(30)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[\\s\\S]{8,30}$", message = "{user_import_format_wrong}")
    private String password;

    @ExcelProperty("Phone")
    @Length(max = 11)
    @ColumnWidth(30)
    @Pattern(regexp = "^1(3|4|5|6|7|8|9)\\d{9}$", message = "{user_import_format_wrong}")
    private String phone;

    @NotBlank(message = "{cannot_be_null}")
    @ColumnWidth(30)
    @ExcelProperty("User is project member(Yes/No)")
    private String userIsProjectMember;

    @Length(max = 100)
    @ColumnWidth(30)
    @ExcelProperty("Project member of project member")
    private String proMemberProject;
}
