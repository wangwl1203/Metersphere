package io.metersphere.excel.domain;

import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/**
 * @author wwl
 * @since 2022-03-01
 */
public class WorkSpaceUserExcelDataFactory implements ExcelDataFactory{

    @Override
    public Class getExcelDataByLocal() {
        Locale locale = LocaleContextHolder.getLocale();
        if (Locale.US.toString().equalsIgnoreCase(locale.toString())) {
            return WorkSpaceUserExcelDataUs.class;
        } else if (Locale.TRADITIONAL_CHINESE.toString().equalsIgnoreCase(locale.toString())) {
            return WorkSpaceUserExcelDataTw.class;
        }
        return WorkSpaceUserExcelDataCn.class;
    }
}
