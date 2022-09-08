<template>
  <ms-edit-dialog
    :append-to-body="true"
    :visible.sync="visible"
    :title="$t('test_track.case.relate_issue')"
    @confirm="save"
    ref="relevanceDialog">
    <el-form :model="form" :inline="true" size="small" ref="form">
      <el-form-item :label="$t('test_track.issue.issue_resource')" v-if="hasJira">
        <el-radio :disabled="isReadOnly" v-model="form.platform" label="Local" @change="changeIssuesType">
          Local
        </el-radio>
        <el-radio :disabled="isReadOnly" v-model="form.platform" label="Jira" @change="changeIssuesType">
          Jira
        </el-radio>
      </el-form-item><br/>
      <el-form-item prop="title" :label="$t('test_track.issue.title')">
        <el-input v-model="form.title" placeholder="请输入缺陷概要/标题"></el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">{{ $t('commons.adv_search.search') }}</el-button>
      </el-form-item>
      <el-form-item>
        <el-button @click="resetForm()">{{ $t('commons.adv_search.reset') }}</el-button>
      </el-form-item>
    </el-form>
    <ms-table
      v-loading="page.result.loading"
      :data="page.data"
      :condition="page.condition"
      :total="page.total"
      :page-size.sync="page.pageSize"
      :show-select-all="false"
      @handlePageChange="getIssues"
      @refresh="getIssues"
      ref="table">

      <ms-table-column
        :label="$t('test_track.issue.id')"
        prop="id" v-if="false">
      </ms-table-column>
      <ms-table-column
        :label="$t('test_track.issue.id')"
        prop="key">
      </ms-table-column>

      <ms-table-column
        width="150px"
        :label="$t('test_track.issue.title')"
        prop="title">
      </ms-table-column>

      <ms-table-column
        :label="$t('test_track.issue.platform_status')"
        v-if="isThirdPart"
        prop="platformStatus">
        <template v-slot="scope">
          {{ scope.row.platformStatus ? scope.row.platformStatus : '--'}}
        </template>
      </ms-table-column>

      <ms-table-column
        v-else
        :label="$t('test_track.issue.status')"
        prop="status">
        <template v-slot="scope">
          <span>{{ issueStatusMap[scope.row.status] ? issueStatusMap[scope.row.status] : scope.row.status }}</span>
        </template>
      </ms-table-column>
      <ms-table-column
        :label="$t('test_track.issue.issueSeverity')"
        prop="severityLevel">
      </ms-table-column>
      <ms-table-column
        :label="$t('test_track.issue.issueType')"
        prop="issueType">
      </ms-table-column>
      <ms-table-column
        :label="$t('test_track.issue.creatorName')"
        prop="creatorName">
      </ms-table-column>

      <issue-description-table-item/>

    </ms-table>

    <ms-table-pagination :change="getIssues" :current-page.sync="page.currentPage" :page-size.sync="page.pageSize" :total="page.total"/>

  </ms-edit-dialog>
</template>

<script>
import MsEditDialog from "@/business/components/common/components/MsEditDialog";
import MsTable from "@/business/components/common/components/table/MsTable";
import MsTableColumn from "@/business/components/common/components/table/MsTableColumn";
import {getRelateIssues, testCaseIssueRelate} from "@/network/Issue";
import IssueDescriptionTableItem from "@/business/components/track/issue/IssueDescriptionTableItem";
import {ISSUE_STATUS_MAP} from "@/common/js/table-constants";
import MsTablePagination from "@/business/components/common/pagination/TablePagination";
import {getPageInfo} from "@/common/js/tableUtils";
import {getCurrentProjectID} from "@/common/js/utils";
export default {
  name: "IssueRelateList",
  components: {MsTablePagination, IssueDescriptionTableItem, MsTableColumn, MsTable, MsEditDialog},
  data() {
    return {
      page: getPageInfo(),
      visible: false,
      form:{
        title:'',
        platform: 'Local'
      },
      planId:'',
      isReadOnly: false,
      hasJira: false,
    }
  },
  computed: {
    issueStatusMap() {
      return ISSUE_STATUS_MAP;
    },
    projectId() {
      return getCurrentProjectID();
    }
  },
  props: ['caseId', 'isThirdPart','plan_Id'],
  methods: {
    changeIssuesType(scope){
      this.form.platform = scope
      this.initTableData();
    },
    initTableData() {
      this.page.currentPage = 1;
      this.page.condition.title = this.form.title;
      this.page.condition.projectId = this.projectId;
      this.page.condition.caseId = this.caseId;
      this.page.condition.platform = this.form.platform;
      this.page.result = getRelateIssues(this.page);
    },
    search() {
      this.initTableData();
    },
    resetForm() {
      this.form.title = ''
    },
    open(plan_Id,has_Jira) {
      this.planId = plan_Id;
      this.hasJira = has_Jira;
      this.getIssues();
      this.visible = true;
    },
    getIssues() {
      this.page.condition.planId = this.planId;
      this.page.condition.title = this.form.title;
      this.page.condition.projectId = this.projectId;
      this.page.condition.caseId = this.caseId;
      this.page.condition.platform = this.form.platform;
      this.page.result = getRelateIssues(this.page);
    },
    save() {
      let param = {};
      param.planId = this.planId;
      param.caseId = this.caseId;
      param.issueIds = Array.from(this.$refs.table.selectRows).map(i => i.id);
      param.caseId = this.caseId;
      param.platform = this.form.platform
      testCaseIssueRelate(param, () => {
        this.visible = false;
        this.$emit('refresh');
      });
    }
  }
}
</script>

<style scoped>

</style>
