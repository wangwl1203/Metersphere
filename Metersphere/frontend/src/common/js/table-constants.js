// 模板
import i18n from "@/i18n/i18n";
import {TEST_CASE_PRIORITY} from "@/business/components/common/components/search/search-components";

export const CUSTOM_FIELD_TYPE_OPTION = [
  {value: 'input',text: i18n.t('workspace.custom_filed.input')},
  {value: 'textarea',text: i18n.t('workspace.custom_filed.textarea')},
  {value: 'select',text: i18n.t('workspace.custom_filed.select')},
  {value: 'multipleSelect',text: i18n.t('workspace.custom_filed.multipleSelect')},
  {value: 'radio',text: i18n.t('workspace.custom_filed.radio')},
  {value: 'checkbox',text: i18n.t('workspace.custom_filed.checkbox')},
  {value: 'member',text: i18n.t('workspace.custom_filed.member')},
  {value: 'multipleMember',text: i18n.t('workspace.custom_filed.multipleMember')},
  {value: 'data',text: i18n.t('workspace.custom_filed.data')},
  {value: 'int',text: i18n.t('workspace.custom_filed.int')},
  {value: 'float',text: i18n.t('workspace.custom_filed.float')},
  {value: 'multipleInput',text: i18n.t('workspace.custom_filed.multipleInput')}
];

export const CUSTOM_FIELD_SCENE_OPTION = [
  {value: 'TEST_CASE',text: i18n.t('workspace.case_template_manage')},
  {value: 'ISSUE',text: i18n.t('workspace.issue_template_manage')},
];

export const CASE_TYPE_OPTION = [
  {value: 'functional',text: '功能用例'},
];

export const ISSUE_PLATFORM_OPTION = [
  {value: 'Local',text: 'Local'},
  {value: 'Jira',text: 'JIRA'},
  {value: 'Tapd',text: 'Tapd'},
  {value: 'Zentao',text: '禅道'},
  {value: 'AzureDevops',text: 'Azure Devops'},
];

export const FIELD_TYPE_MAP = {
  input: 'workspace.custom_filed.input',
  textarea: 'workspace.custom_filed.textarea',
  select: 'workspace.custom_filed.select',
  multipleSelect: 'workspace.custom_filed.multipleSelect',
  radio: 'workspace.custom_filed.radio',
  checkbox: 'workspace.custom_filed.checkbox',
  member: 'workspace.custom_filed.member',
  multipleMember: 'workspace.custom_filed.multipleMember',
  data: 'workspace.custom_filed.data',
  int: 'workspace.custom_filed.int',
  float: 'workspace.custom_filed.float',
  multipleInput: 'workspace.custom_filed.multipleInput'
};

export const SCENE_MAP = {
  ISSUE: 'workspace.issue_template_manage',
  TEST_CASE: 'workspace.case_template_manage'
};

export const SYSTEM_FIELD_NAME_MAP = {
  //用例字段
  用例状态: 'custom_field.case_status',
  责任人: 'custom_field.case_maintainer',
  用例等级: 'custom_field.case_priority',
  //缺陷字段
  创建人: 'custom_field.issue_creator',
  处理人: 'custom_field.issue_processor',
  状态: 'custom_field.issue_status',
  严重程度: 'custom_field.issue_severity',
  报告人: 'custom_field.issue_reporter',
  是否修复引入缺陷: 'custom_field.issue_import',
  设计类缺陷类型: 'custom_field.issue_design',
  需求缺陷类型: 'custom_field.issue_demand',
  优先级: 'custom_field.issue_priority',
  测试类型: 'custom_field.issue_testType',
  提交次数: 'custom_field.issue_submitNum',
  缺陷类型: 'custom_field.issue_type',
  引入阶段: 'custom_field.issue_introductionPhase',
  发现阶段: 'custom_field.issue_discoveryPhase',
  修复的版本: 'custom_field.issue_fixedVersion',
  模块: 'custom_field.issue_module',
  影响版本: 'custom_field.issue_affectsVersion',
  缺陷归属: 'custom_field.issue_belongTo',
  是否单元测试逃逸: 'custom_field.issue_escape',
  缺陷引入人: 'custom_field.issue_introducePeople',
  经办人: 'custom_field.issue_operator',
}


export const ISSUE_STATUS_MAP = {
  'new': '新建',
  'closed': '已关闭',
  'resolved': '已解决',
  'active': '激活',
  'delete': '已删除',
  'processing': '处理中',
  'putIntoProduction': '待投产',
  'modified': '已修改',
  'refused': '被拒绝',
  'repairFailure': '修复失败',
  'assigned': '已分配',
  'extendedModified': '延期修改',
  'NORUN': 'NORUN',
  'FAILED': 'FAILED',
  'PASSED': 'PASSED',
  'BLOCKED': 'BLOCKED',
  'N/A': 'N/A'
}

export const DEMAND_STAGE_MAP = {
  'wait': '未开始',
  'planned': '已计划',
  'projected': '已立项',
  'developing': '研发中',
  'developed': '研发完毕',
  'testing': '测试中',
  'tested': '测试完毕',
  'verified': '已验收',
  'released': '需求拒绝',
  'closed': '已关闭',
}

export const DEMAND_STATUS_MAP = {
  'draft': '草稿',
  'active': '激活',
  'closed': '已关闭',
  'changed': '已变更'
}

export const API_SCENARIO_FILTERS = {
  LEVEL_FILTERS: TEST_CASE_PRIORITY,
  RESULT_FILTERS: [
    {text: i18n.t('api_test.automation.fail'), value: 'Fail'},
    {text: i18n.t('api_test.automation.success'), value: 'Success'}
  ],
  STATUS_FILTERS: [
    {text: i18n.t('test_track.plan.plan_status_prepare'), value: 'Prepare'},
    {text: i18n.t('test_track.plan.plan_status_running'), value: 'Underway'},
    {text: i18n.t('test_track.plan.plan_status_completed'), value: 'Completed'},
    {text: i18n.t('test_track.plan.plan_status_trash'), value: 'Trash'},
  ],
}

export const USER_GROUP_SCOPE = {
  'SYSTEM': 'group.system',
  'WORKSPACE': 'group.workspace',
  'PROJECT': 'group.project'
}

export const PROJECT_GROUP_SCOPE = {
  'TRACK': '测试跟踪',
  'API': '接口测试',
  'PERFORMANCE': '性能测试',
  'REPORT': '报告统计'
}
