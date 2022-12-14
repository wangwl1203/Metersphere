<template>
  <div>
    <span class="kv-description" v-if="description">
      {{description}}
    </span>
    <div class="kv-row" v-for="(item, index) in items" :key="index">
      <el-row type="flex" :gutter="5" justify="space-between" align="middle">
        <el-col class="kv-checkbox">
          <input type="checkbox" v-if="!isDisable(index)" @change="change" :value="item.uuid" v-model="item.enable"
                 :disabled="isDisable(index) || isReadOnly"/>
        </el-col>

        <el-col>
          <ms-api-variable-input :show-copy="showCopy" :show-variable="showVariable" :is-read-only="isReadOnly" v-model="item.name" size="small" maxlength="200" @change="change"
                                 :placeholder="$t('api_test.variable_name')" show-word-limit/>
        </el-col>
        <el-col>
          <el-autocomplete
            size="small"
            v-model="item.value"
            :fetch-suggestions="funcSearch"
            :placeholder="$t('api_test.value')"
            value-key="name"
            highlight-first-item>
            <i slot="suffix" class="el-input__icon el-icon-edit pointer" @click="advanced"></i>
          </el-autocomplete>
        </el-col>
        <el-col>
          <el-input v-model="item.description" size="small" maxlength="200"
                    :placeholder="$t('commons.description')" show-word-limit>
          </el-input>
        </el-col>
        <el-col class="kv-copy">
          <el-button size="mini" class="el-icon-document-copy" circle @click="copy(item, index)"
                     :disabled="isDisable(index) || isReadOnly"/>
        </el-col>
        <el-col class="kv-delete">
          <el-button size="mini" class="el-icon-delete-solid" circle @click="remove(index)"
                     :disabled="isDisable(index) || isReadOnly"/>
        </el-col>
      </el-row>
    </div>
    <ms-api-variable-advance ref="variableAdvance"/>
  </div>
</template>

<script>
  import {KeyValue} from "../model/ApiTestModel";
  import MsApiVariableInput from "./ApiVariableInput";
  import {JMETER_FUNC, MOCKJS_FUNC} from "@/common/js/constants";
  import MsApiVariableAdvance from "../../test/components/ApiVariableAdvance";

  export default {
    name: "MsApiScenarioVariables",
    components: {MsApiVariableInput, MsApiVariableAdvance},
    props: {
      description: String,
      items: Array,
      isReadOnly: {
        type: Boolean,
        default: false
      },
      showVariable: {
        type: Boolean,
        default: true
      },
      showCopy: {
        type: Boolean,
        default: true
      },
    },
    data() {
      return {
      }
    },
    methods: {
      remove: function (index) {
        this.items.splice(index, 1);
        this.$emit('change', this.items);
      },
      copy: function (item, index) {
        let copy = {};
        Object.assign(copy, item);
        this.items.splice(index + 1, 0, copy);
      },
      change: function () {
        let isNeedCreate = true;
        let removeIndex = -1;
        this.items.forEach((item, index) => {
          if (!item.name && !item.value) {
            // ???????????????
            if (index !== this.items.length - 1) {
              removeIndex = index;
            }
            // ?????????????????????????????????
            isNeedCreate = false;
          }
        });
        if (isNeedCreate) {
          this.items.push(new KeyValue({enable: true}));
        }
        this.$emit('change', this.items);
        // TODO ??????key??????
      },
      isDisable: function (index) {
        return this.items.length - 1 === index;
      },
      advanced() {
        this.$refs.variableAdvance.open();
      },
      createFilter(queryString) {
        return (variable) => {
          return (variable.value.toLowerCase().indexOf(queryString.toLowerCase()) === 0);
        };
      },
      funcFilter(queryString) {
        return (func) => {
          return (func.name.toLowerCase().indexOf(queryString.toLowerCase()) > -1);
        };
      },
      funcSearch(queryString, cb) {
        let funcs = MOCKJS_FUNC.concat(JMETER_FUNC);
        let results = queryString ? funcs.filter(this.funcFilter(queryString)) : funcs;
        // ?????? callback ???????????????????????????
        cb(results);
      },
    },

    created() {
      if (this.items.length === 0) {
        this.items.push(new KeyValue({enable: true}));
      }
    }
  }
</script>

<style scoped>
  .kv-description {
    font-size: 13px;
  }

  .kv-checkbox {
    width: 20px;
    margin-right: 10px;
  }

  .kv-row {
    margin-top: 10px;
  }

  .kv-delete,.kv-copy {
    width: 60px;
  }
</style>
