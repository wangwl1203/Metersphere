<template>

  <div>

    <el-dialog :close-on-click-modal="false"
               :destroy-on-close="true"
               :title="operationType === 'edit' ? $t('test_track.plan.edit_plan') : $t('test_track.plan.create_plan')"
               :visible.sync="dialogFormVisible"
               @close="close"
               top="8vh"
               width="60%">

      <el-form :model="form" :rules="rules" ref="planFrom" v-if="isStepTableAlive">
        <el-row type="flex" :gutter="20">
          <el-col :span="12">
            <el-form-item
              :label="$t('test_track.plan.plan_name')"
              :label-width="formLabelWidth"
              prop="name">
              <el-input v-model="form.name" :placeholder="$t('test_track.plan.input_plan_name')" :size="itemSize"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('test_track.plan.affects_version')" :label-width="formLabelWidth" prop="versionId">
              <el-select v-model="form.versionId" :placeholder="$t('test_track.plan.input_affects_version')" style="width: 100%;"
                         filterable :size="itemSize" @visible-change="visibleChange" clearable>
                <el-option
                  v-for="item in planOptions"
                  :key="item.name"
                  :label="item.name"
                  :value="item.name">
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row type="flex" :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('test_track.plan.plan_principal')" :label-width="formLabelWidth" prop="principals">
              <el-select v-model="form.principals" :placeholder="$t('test_track.plan.input_plan_principal')"
                         style="width: 100%;"
                         filterable multiple :size="itemSize">
                <el-option
                  v-for="(item) in principalOptions"
                  :key="item.id"
                  :label="item.name + '(' + item.id + ')'"
                  :value="item.id">
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item :label="$t('test_track.plan.plan_stage')" :label-width="formLabelWidth" prop="stage">
              <el-select v-model="form.stage" clearable :placeholder="$t('test_track.plan.input_plan_stage')" style="width: 100%;" :size="itemSize">
                <el-option :label="$t('test_track.plan.smoke_test')" value="smoke"></el-option>
                <el-option :label="$t('test_track.plan.system_test')" value="system"></el-option>
                <el-option :label="$t('test_track.plan.regression_test')" value="regression"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!--start:xuxm??????????????????????????????????????????????????????????????????-->
        <el-row type="flex" :gutter="20">
          <el-col :span="12">
            <el-form-item
              :label="$t('test_track.plan.planned_start_time')"
              :label-width="formLabelWidth"
              prop="plannedStartTime">
              <el-date-picker :placeholder="$t('test_track.plan.planned_start_time')" v-model="form.plannedStartTime"
                              type="datetime" value-format="timestamp" style="width: 100%;"></el-date-picker>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item
              :label="$t('test_track.plan.planned_end_time')"
              :label-width="formLabelWidth"
              prop="plannedEndTime">
              <el-date-picker :placeholder="$t('test_track.plan.planned_end_time')" v-model="form.plannedEndTime"
                              type="datetime" value-format="timestamp" style="width: 100%;"></el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <!--end:xuxm??????????????????????????????????????????????????????????????????-->

        <el-row type="flex" justify="left" :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('test_track.plan.follow_people')" :label-width="formLabelWidth"
                          prop="follows">
              <el-select v-model="form.follows"
                         clearable
                         :placeholder="$t('test_track.plan.follow_people')" filterable multiple size="small">
                <el-option
                  v-for="(item) in principalOptions"
                  :key="item.id"
                  :label="item.name + '(' + item.id + ')'"
                  :value="item.id">
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('commons.tag')" :label-width="formLabelWidth" prop="tag">
              <ms-input-tag :currentScenario="form" ref="tag" :size="itemSize"/>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row type="flex" :gutter="20">
          <el-col :span="12">
            <el-form-item
              :label="$t('??????????????????')"
              :label-width="formLabelWidth"
              prop="automaticStatusUpdate">
              <el-switch v-model="form.automaticStatusUpdate"/>
              <ms-instructions-icon :content="'?????????????????????????????????????????????????????????????????????????????????????????????????????????'"/>
            </el-form-item>
          </el-col>
<!--          <el-col :span="12">-->
<!--            <el-form-item-->
<!--              :label="$t('????????????????????????')"-->
<!--              label-width="140px"-->
<!--              prop="automaticStatusUpdate">-->
<!--              <el-switch v-model="form.repeatCase"/>-->
<!--              <ms-instructions-icon :content="'????????????????????????????????????????????????????????????'"/>-->
<!--            </el-form-item>-->
<!--          </el-col>-->
        </el-row>

        <el-row type="flex" justify="left" :gutter="20">
          <el-col :span="24">
            <el-form-item :label="$t('commons.description')" :label-width="formLabelWidth" prop="description">
              <el-input v-model="form.description"
                        type="textarea"
                        :autosize="{ minRows: 2, maxRows: 4}"
                        :rows="2"
                        :placeholder="$t('commons.input_content')"></el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="operationType === 'edit'" type="flex" justify="left" :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('test_track.plan.plan_status')" :label-width="formLabelWidth" prop="status">
              <test-plan-status-button :status="form.status" @statusChange="statusChange"/>
            </el-form-item>
          </el-col>
        </el-row>

      </el-form>

      <template v-slot:footer>

        <div class="dialog-footer">
          <el-button
            @click="dialogFormVisible = false">
            {{ $t('test_track.cancel') }}
          </el-button>
          <el-button
            type="primary"
            @click="savePlan">
            {{ $t('test_track.confirm') }}
          </el-button>
          <el-button type="primary" @click="testPlanInfo">
            {{ $t('test_track.planning_execution') }}
          </el-button>
        </div>
      </template>
    </el-dialog>

  </div>


</template>

<script>

import TestPlanStatusButton from "../common/TestPlanStatusButton";
import {getCurrentProjectID, getCurrentWorkspaceId, listenGoBack, removeGoBackListener} from "@/common/js/utils";
import MsInputTag from "@/business/components/api/automation/scenario/MsInputTag";
import MsInstructionsIcon from "@/business/components/common/components/MsInstructionsIcon";

export default {
  name: "TestPlanEdit",
  components: {MsInstructionsIcon, TestPlanStatusButton, MsInputTag},
  data() {
    return {
      planOptions: [],
      isStepTableAlive: true,
      dialogFormVisible: false,
      itemSize: "medium",
      form: {
        name: '',
        projectIds: [],
        principals: [],
        stage: '',
        versionId:'',
        description: '',
        plannedStartTime: '',
        plannedEndTime: '',
        automaticStatusUpdate: false,
        repeatCase: false,
        follows: []
      },
      version_old: '',
      rules: {
        name: [
          {required: true, message: this.$t('test_track.plan.input_plan_name'), trigger: 'blur'},
          {max: 30, message: this.$t('test_track.length_less_than') + '30', trigger: 'blur'}
        ],
        principals: [{required: true, message: this.$t('test_track.plan.input_plan_principal'), trigger: 'change'}],
        stage: [{required: true, message: this.$t('test_track.plan.input_plan_stage'), trigger: 'change'}],
        versionId: [{required: true, message: this.$t('test_track.plan.input_affects_version'), trigger: 'change'}],
        description: [{max: 200, message: this.$t('test_track.length_less_than') + '200', trigger: 'blur'}]
      },
      formLabelWidth: "100px",
      operationType: '',
      principalOptions: [],
    };
  },
  created() {
    //??????????????????????????????????????????????????????
    this.form.stage = 'smoke';
  },
  methods: {
    visibleChange(flag) {
      if (flag) {
        this.getPlanOptions();
      }
    },
    getPlanOptions() {
      let projectId = getCurrentProjectID();
      if (this.planOptions.length === 0) {
        this.result = {loading: true};
        this.$get("/test/plan/version/list/" + projectId).then(response => {
          this.planOptions = response.data.data;
          this.planOptions.push({name:'???'})
          this.result = {loading: false};
        }).catch(() => {
          this.result = {loading: false};
        });
      }
    },
    reload() {
      this.isStepTableAlive = false;
      this.$nextTick(() => (this.isStepTableAlive = true));
    },
    openTestPlanEditDialog(testPlan) {
      console.log(testPlan)
      this.resetForm();
      this.setPrincipalOptions();
      this.operationType = 'add';
      if (testPlan) {
        //??????
        this.operationType = 'edit';
        this.version_old = testPlan.versionId;
        let tmp = {};
        Object.assign(tmp, testPlan);
        Object.assign(this.form, tmp);
      } else {
        this.form.tags = [];
      }
      listenGoBack(this.close);
      this.dialogFormVisible = true;
      this.reload();
    },
    testPlanInfo() {
      this.$refs['planFrom'].validate((valid) => {
        if (valid) {
          let param = {};
          Object.assign(param, this.form);
          param.name = param.name.trim();
          if (param.name === '') {
            this.$warning(this.$t('test_track.plan.input_plan_name'));
            return;
          }
          if (param.versionId === '') {
            this.$warning(this.$t('test_track.plan.input_affects_version'));
            return false;
          }
          param.workspaceId = getCurrentWorkspaceId();
          if (this.form.tags instanceof Array) {
            this.form.tags = JSON.stringify(this.form.tags);
          }
          param.tags = this.form.tags;
          param.versionId = this.form.versionId
          if(this.operationType === 'edit'){
            if(this.version_old === this.form.versionId){
              this.testPlanOperator(param);
            }else{
              this.$confirm("[????????????]??????:  [????????????]????????????????????????????????????[??????]??????????????????",'', {
                confirmButtonText: this.$t('commons.confirm'),
                cancelButtonText: this.$t('commons.cancel'),
                type: 'warning'
              }).then(() => {
                this.$confirm("???????????????, ??????????????????", '',{
                  confirmButtonText: this.$t('commons.confirm'),
                  cancelButtonText: this.$t('commons.cancel'),
                  type: 'warning'
                }).then(() => {
                  param.versionIdOld = this.version_old;
                  this.testPlanOperator(param);
                }).catch(() => {
                });
              }).catch(() => {
              });
            }
          }else{
            this.testPlanOperator(param);
          }
        } else {
          return false;
        }
      });
    },
    testPlanOperator(param){
      this.$post('/test/plan/' + this.operationType, param, response => {
        this.$success(this.$t('commons.save_success'));
        this.dialogFormVisible = false;
        this.$router.push('/track/plan/view/' + response.data.id);
      });
    },
    operator(param){
      this.$post('/test/plan/' + this.operationType, param, () => {
        this.$success(this.$t('commons.save_success'));
        this.dialogFormVisible = false;
        this.$emit("refresh");
      });
    },
    savePlan() {
      this.$refs['planFrom'].validate((valid) => {
        if (valid) {
          let param = {};
          Object.assign(param, this.form);
          param.name = param.name.trim();
          if (param.name === '') {
            this.$warning(this.$t('test_track.plan.input_plan_name'));
            return;
          }
          if (param.versionId === '') {
            this.$warning(this.$t('test_track.plan.input_affects_version'));
            return false;
          }
          param.workspaceId = getCurrentWorkspaceId();
          if (this.form.tags instanceof Array) {
            this.form.tags = JSON.stringify(this.form.tags);
          }
          param.tags = this.form.tags;
          param.versionId = this.form.versionId

          if(this.operationType === 'edit'){
            if(this.version_old === this.form.versionId){
              this.operator(param);
            }else{
              this.$confirm("[????????????]??????:  [????????????]????????????????????????????????????[??????]??????????????????",'', {
                confirmButtonText: this.$t('commons.confirm'),
                cancelButtonText: this.$t('commons.cancel'),
                type: 'warning'
              }).then(() => {
                this.$confirm("???????????????, ??????????????????", '',{
                  confirmButtonText: this.$t('commons.confirm'),
                  cancelButtonText: this.$t('commons.cancel'),
                  type: 'warning'
                }).then(() => {
                  param.versionIdOld = this.version_old;
                  this.operator(param);
                }).catch(() => {
                });
              }).catch(() => {
              });
            }
          }else{
            this.operator(param);
          }
        } else {
          return false;
        }
      });
    },
    validate(param) {
      if (param.name === '') {
        this.$warning(this.$t('test_track.plan.input_plan_name'));
        return false;
      }
      if (param.versionId === '') {
        this.$warning(this.$t('test_track.plan.input_affects_version'));
        return false;
      }
      if (param.plannedStartTime > param.plannedEndTime) {
        this.$warning(this.$t('commons.date.data_time_error'));
        return false;
      }
      return true;
    },
    setPrincipalOptions() {
      this.$post('/user/project/member/tester/list', {projectId: getCurrentProjectID()},response => {
        this.principalOptions = response.data;
      });
    },
    statusChange(status) {
      this.form.status = status;
      this.$forceUpdate();
    },
    close() {
      removeGoBackListener(this.close);
      this.dialogFormVisible = false;
    },
    resetForm() {
      //????????????????????????????????????????????????
      if (this.$refs['planFrom']) {
        this.$refs['planFrom'].validate(() => {
          this.$refs['planFrom'].resetFields();
          this.form.name = '';
          this.form.projectIds = [];
          this.form.principals = [];
          this.form.follows = [];
          this.form.automaticStatusUpdate = false;
          this.form.stage = 'smoke';
          this.form.versionId = '';
          this.form.description = '';
          this.form.status = null;
          this.form.plannedStartTime = null;
          this.form.plannedEndTime = null;
          return true;
        });
      }
    }
  }
}
</script>

<style scoped>
.instructions-icon {
  margin-left: 10px;
}
</style>
