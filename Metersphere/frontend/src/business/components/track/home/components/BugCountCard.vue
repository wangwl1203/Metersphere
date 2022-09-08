<template>
  <el-card class="table-card" v-loading="result.loading" body-style="padding:10px;">
    <div slot="header">
      <span class="title">
        {{ $t('test_track.home.bug_coverage') }}
      </span>
    </div>
    <el-container>
<!--      <el-aside width="150px">
        <div class="main-number-show">
          <span class="count-number">
            {{ bugTotalSize }}
          </span>
          <span style="color: #6C317C;">
            {{ $t('api_test.home_page.unit_of_measurement') }}
          </span>
          <div>
            {{ $t('test_track.home.percentage') }}
            <span class="rage">
              {{rage}}
            </span>
          </div>
        </div>
      </el-aside>-->
      <el-table ref="tableData" border :data="tableData" :show-summary="isShowSummary" :summary-method="getSummaries" class="adjust-table table-content" height="300">
        <el-table-column prop="index" :label="$t('test_track.home.serial_number')"
                         width="50" show-overflow-tooltip/>
        <el-table-column prop="planName" :label="$t('test_track.home.test_plan_name')"
                         show-overflow-tooltip/>
        <el-table-column prop="createTime" :label="$t('commons.create_time')" width="110" show-overflow-tooltip>
          <template v-slot:default="scope">
            <span>{{ scope.row.createTime | timestampFormatDate }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="status"
          column-key="status"
          :label="$t('test_track.plan.plan_status')"
          width="80"
          show-overflow-tooltip>
          <template v-slot:default="scope">
          <span @click.stop="clickt = 'stop'">
            <plan-status-table-item :value="scope.row.status"/>
          </span>
          </template>
        </el-table-column>
        <el-table-column prop="caseSize" :label="$t('test_track.home.case_size')"
                         width="65" show-overflow-tooltip/>
        <el-table-column prop="passCaseSize" :label="$t('test_track.home.pass_case_size')"
                         width="95" show-overflow-tooltip/>
        <el-table-column prop="passRage" :label="$t('test_track.home.passing_rate')"
                         width="65" show-overflow-tooltip/>
        <el-table-column prop="bugSize" :label="$t('test_track.home.bug_size')"
                         width="65" show-overflow-tooltip/>
        <el-table-column prop="bugCoverRate" :label="$t('test_track.home.bug_coverage')"
                         width="95" show-overflow-tooltip/>
      </el-table>
    </el-container>
  </el-card>
</template>

<script>
import {getCurrentProjectID} from "@/common/js/utils";
import PlanStatusTableItem from "@/business/components/track/common/tableItems/plan/PlanStatusTableItem";

export default {
  name: "BugCountCard",
  components: {
    PlanStatusTableItem
  },
  data() {
    return {
      tableData: [],
      result: {},
      bugTotalSize: 0,
      rage: '0%',
      passRate: '0%',
      isShowSummary: false
    }
  },
  methods: {
    getSummaries(param) {
      //param 是固定的对象，里面包含 columns与 data参数的对象 {columns: Array[4], data: Array[5]},包含了表格的所有的列与数据信息
      const { columns, data } = param;
      const sums = [];
      columns.forEach((column, index) => {
        if (index === 0) {
          sums[index] = this.$t('test_track.home.collect');
          return;
        }else if (index === 1 || index === 2 || index === 3){
          sums[index] = '--';
          return;
        }
        const values = data.map(item => Number(item[column.property]));
        //验证每个value值是否是数字，如果是执行if
        if (!values.every(value => isNaN(value))) {
          sums[index] = values.reduce((prev, curr) => {
            //const value = Number(curr);
            //if (!isNaN(value)) {
            return prev + curr;
            // } else {
            //   return prev;
            // }
          }, 0);
        } else {
          sums[index] = '--';
        }
        // 计算通过率
        if (index === 6) {
          /*let bugCount = Number(sums[5]);
          let caseCount = Number(sums[4]);
          sums[index] = String(100-parseFloat(String(bugCount/caseCount*100)).toFixed(1)) + '%';*/
          sums[index] = this.passRate;
        }
        // 计算缺陷覆盖率
        if (index === 8) {
          sums[index] = this.rage;
        }
      });
      return sums;
    },
    init() {
      this.result = this.$get("/track/bug/count/" + getCurrentProjectID(), res => {
        let data = res.data;
        this.tableData = data.list;
        this.bugTotalSize = data.bugTotalSize;
        this.rage = data.rage;
        this.passRate = data.passRate;
        if(this.tableData.length) {
          this.isShowSummary = true;
        }
      })
    }
  },
  created() {
    this.init()
  },
  activated() {
    this.init()
  },
  updated() {
    this.$nextTick(() => {
      this.$refs['tableData'].doLayout();
    })
  }
}
</script>

<style scoped>

.el-card /deep/ .el-card__header {
  border-bottom: 0px solid #EBEEF5;
}

.el-aside {
  line-height: 100px;
  text-align: center;
  overflow-y: hidden;
}

.count-number {
  font-family: 'ArialMT', 'Arial', sans-serif;
  font-size: 33px;
  color: var(--count_number);
}

.rage {
  font-family: 'ArialMT', 'Arial', sans-serif;
  font-size: 18px;
  color: var(--count_number);
}

.main-number-show {
  width: 100px;
  height: 100px;
  border-style: solid;
  border-width: 7px;
  border-color: var(--count_number_shallow);
  border-radius: 50%;

}

.count-number-show {
  margin: 20px auto;
}
</style>
