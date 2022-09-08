<template>
  <ms-edit-dialog
    :append-to-body="true"
    :visible.sync="visible"
    :title="$t('test_track.related_requirements')"
    @confirm="save"
    ref="relevanceDialog">
    <el-form :model="form" :inline="true" size="small" ref="form">
        <el-form-item :label="$t('test_track.demand.plan')" :label-width="labelWidth"
                      prop="plan">
          <el-select filterable :disabled="readOnly" v-model="form.id" @visible-change="visibleChange"
                     :placeholder="$t('test_track.please_select_plan')" class="ms-case-input">
            <el-option
              v-for="item in planOptions"
              :key="item.id"
              :label="item.title"
              :value="item.id">
            </el-option>
          </el-select>
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
      @handlePageChange="getDemandsByPlan"
      @refresh="getDemandsByPlan"
      ref="table">
      <ms-table-column
        :label="$t('test_track.demand.id')"
        prop="id" v-if="false">
      </ms-table-column>
      <ms-table-column
        :label="$t('test_track.demand.id')"
        prop="id">
      </ms-table-column>
      <ms-table-column
        :label="$t('test_track.demand.module')"
        prop="module">
      </ms-table-column>
      <ms-table-column
        :label="$t('test_track.demand.title')"
        prop="title">
      </ms-table-column>
      <ms-table-column
        :label="$t('test_track.demand.platform_status')"
        prop="platformStatus">
        <template v-slot="scope">
          {{ scope.row.platformStatus ? demandStatusMap[scope.row.platformStatus]: '--'}}
        </template>
      </ms-table-column>
      <ms-table-column
        :label="$t('test_track.demand.stage')"
        prop="stage">
        <template v-slot="scope">
          {{ scope.row.stage ? demandStageMap[scope.row.stage] : '--'}}
        </template>
      </ms-table-column>
    </ms-table>
    <ms-table-pagination :change="getDemandsByPlan" :current-page.sync="page.currentPage" :page-size.sync="page.pageSize" :total="page.total"/>
  </ms-edit-dialog>
</template>

<script>
import MsEditDialog from "@/business/components/common/components/MsEditDialog";
import MsTable from "@/business/components/common/components/table/MsTable";
import MsTableColumn from "@/business/components/common/components/table/MsTableColumn";
import MsTablePagination from "@/business/components/common/pagination/TablePagination";
import {getRelateDemands, testCaseDemandRelate} from "@/network/demand";
import {ZEN_TAO} from "@/common/js/constants";
import {DEMAND_STAGE_MAP, DEMAND_STATUS_MAP} from "@/common/js/table-constants";
import {getPageInfo} from "@/common/js/tableUtils";
import {getCurrentProjectID} from "@/common/js/utils";

export default {
  name: "DemandRelateList",
  components: {MsTable, MsTableColumn, MsTablePagination, MsEditDialog},
  data(){
    return{
      page: getPageInfo(),
      visible: false,
      planOptions: [],
      form:{
        title:''
      }
    };
  },
  props: ['caseId', 'isThirdPart','labelWidth','readOnly'],
  computed: {
    demandStageMap() {
      return DEMAND_STAGE_MAP;
    },
    demandStatusMap() {
      return DEMAND_STATUS_MAP;
    }
  },
  methods:{
    open() {
      this.getDemandsByPlan();
      this.visible = true;
    },
    resetForm() {
      this.form.id = ''
    },
    search() {
      this.getDemandsByPlan();
    },
    getDemandsByPlan() {
      if(this.form.id != undefined){
        this.page.condition.planId = this.form.id;
        this.page.condition.projectId = this.projectId;
        this.page.condition.caseId = this.caseId;
        this.page.result = getRelateDemands(this.page);
      }
    },
    visibleChange(flag) {
      if (flag) {
        this.getPlanOptions();
      }
    },
    getPlanOptions() {
      let projectId = getCurrentProjectID();
      if (this.planOptions.length === 0) {
        this.result = {loading: true};
        this.$get("demand/plan/list/" + projectId).then(response => {
          this.planOptions = response.data.data;
          // this.planOptions.unshift({id: 'other', name: this.$t('test_track.case.other'), platform: ZEN_TAO});
          this.result = {loading: false};
        }).catch(() => {
          // this.planOptions.unshift({id: 'other', name: this.$t('test_track.case.other'), platform: ZEN_TAO});
          this.result = {loading: false};
        });
      }
    },
    getDemands() {
      this.page.condition.projectId = this.projectId;
      this.page.condition.caseId = this.caseId;
      this.page.result = getRelateDemands(this.page);
    },
    save() {
      let param = {};
      param.caseId = this.caseId;
      param.demandIds = Array.from(this.$refs.table.selectRows).map(i => i.id);
      param.demandNames = Array.from(this.$refs.table.selectRows).map(i => i.title);
      if (param.demandIds.length > 1){
        this.$alert(this.$t('test_track.please_select_one_requirement'));
      }else{
        testCaseDemandRelate(param, () => {
          this.visible = false;
          this.$emit('refresh');
        });
      }
    }
  }
}
</script>

<style scoped>

</style>
