<template>
  <div>
    <el-button class="add-btn" v-if=!(isIssuesFlag) v-permission="['PROJECT_TRACK_PLAN:READ+RELEVANCE_OR_CANCEL']" :disabled="readOnly" type="primary" size="mini" @click="relateDemand">{{ $t('test_track.related_requirements_zenTao') }}</el-button>
    <ms-table
      v-loading="page.result.loading"
      :show-select-all="false"
      :data="page.data"
      :fields.sync="fields"
      :operators="getOperators"
      :enable-selection="false"
      ref="table"
      @refresh="getDemands">
<!--      <span v-for="(item) in fields" :key="item.key">-->
      <span>
        <ms-table-column
          :label="$t('test_track.demand.id')"
          prop="id" v-if="false">
        </ms-table-column>
        <ms-table-column
          :label="$t('test_track.demand.id')"
          prop="id">
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
        <ms-table-column
          :label="$t('test_track.demand.plan')"
          prop="plan">
        </ms-table-column>
        <ms-table-column
          :label="$t('test_track.demand.module')"
          prop="module">
        </ms-table-column>
        <demand-description-table-item/>
      </span>
    </ms-table>

    <DemandRelateList :is-third-part="isThirdPart" :case-id="caseId" @refresh="getDemands" ref="demandRelate"/>
  </div>
</template>

<script>
import MsTable from "@/business/components/common/components/table/MsTable";
import MsTableColumn from "@/business/components/common/components/table/MsTableColumn";
import {getDemandByCaseId} from "@/network/demand";
import DemandRelateList from "@/business/components/track/case/components/DemandRelateList";
import DemandDescriptionTableItem from "@/business/components/track/issue/DemandDescriptionTableItem";
import {getCustomFieldValue} from "@/common/js/tableUtils";
import {DEMAND_STAGE_MAP, DEMAND_STATUS_MAP} from "@/common/js/table-constants";
import {getCurrentProjectID, getCurrentUser} from "@/common/js/utils";
import {ZEN_TAO} from "@/common/js/constants";


export default {
  name: "TestCaseDemandRelate",
  components:{DemandRelateList, DemandDescriptionTableItem, MsTable, MsTableColumn},
  data(){
    return{
      page: {
        data: [],
        result: {},
      },
      fields: [],
      isThirdPart: false,
      demandTemplate: {},
      // operators:[
        // {
        //   tip: this.$t('test_track.case.unlink'),
        //   icon: "el-icon-unlock",
        //   type: "danger",
        //   exec: this.deleteDemand
        // },
        // {
        //   tip: this.$t('test_track.case.detailMessage'),
        //   icon: "el-icon-edit",
        //   type: "danger",
        //   exec: this.detailDemand
        // }
      // ]
    };
  },
  props: ['caseId', 'readOnly','planId', 'isIssuesFlag'],
  computed: {
    demandStageMap() {
      return DEMAND_STAGE_MAP;
    },
    demandStatusMap() {
      return DEMAND_STATUS_MAP;
    },
    getOperators: function (){
      let operators = []
      if(this.isIssuesFlag === false){
        operators.push({
            tip: this.$t('test_track.case.unlink'),
            icon: "el-icon-unlock",
            type: "danger",
            exec: this.deleteDemand
          },
          {
            tip: this.$t('test_track.case.detailMessage'),
            icon: "el-icon-edit",
            type: "danger",
            exec: this.detailDemand
          })
      }else{
        operators.push(
          {
            tip: this.$t('test_track.case.detailMessage'),
            icon: "el-icon-edit",
            type: "danger",
            exec: this.detailDemand
          })
      }
      return operators;
    }
  },
  methods:{
    detailDemand(row){
      if (!this.caseId) {
        this.$warning(this.$t('api_test.automation.save_case_info'));
        return;
      }
      let projectId = getCurrentProjectID();
      const {lastWorkspaceId} = getCurrentUser();

      let param = {};
      param.platform = ZEN_TAO;
      param.workspaceId = lastWorkspaceId;

      this.$get('/project/get/' + projectId, res => {
        let project = res.data;
        if(project){
          this.$parent.result = this.$post("service/integration/type", param, response => {
            let data = response.data;
            let config = JSON.parse(data.configuration);
            let url_zenTao = config.url
            let url = url_zenTao + 'story-view-' + row.id + '.html'
            window.open(url,'_blank');
          });
        }
      })
    },
    getCustomFieldValue(row, field) {
      return getCustomFieldValue(row, field, this.members);
    },
    relateDemand(){
      if (!this.caseId) {
        this.$warning(this.$t('api_test.automation.save_case_info'));
        return;
      }
      this.$refs.demandRelate.open();
    },
    getDemands() {
      let result = getDemandByCaseId(this.caseId, this.page);
      if (result) {
        this.page.result = result;
      }
    },
    deleteDemand(row){
      this.page.result = this.$post("/demand/delete/relate", {id: row.id, caseId: this.caseId}, () => {
        this.getDemands();
        this.$success(this.$t('commons.delete_success'));
      })
    }
  }
}
</script>

<style scoped>
.add-btn {
  display: inline-block;
  margin-right: 5px;
}
.el-dropdown-link {
  cursor: pointer;
  color: #783887;
}
</style>
