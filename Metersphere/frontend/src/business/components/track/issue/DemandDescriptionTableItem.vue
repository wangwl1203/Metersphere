<template>
  <ms-table-column
    :label="$t('test_track.demand.description')"
    prop="description"
    :field="field"
    :fields-width="fieldsWidth">
    <template v-slot:default="scope">
      <el-popover
        placement="right"
        width="500"
        trigger="hover" style="overflow: auto;">
        <mavon-editor :editable="false" default-open="preview" class="mavon-editor"
                      :xss-options="xssOptions"
                      :subfield="false" :toolbarsFlag="false" v-model="scope.row.description"  v-html="scope.row.description" ref="md"/>
        <el-button slot="reference" type="text">{{ $t('test_track.demand.preview') }}</el-button>
      </el-popover>
    </template>
  </ms-table-column>
</template>

<script>
import MsTableColumn from "@/business/components/common/components/table/MsTableColumn";
export default {
  name: "DemandDescriptionTableItem",
  components: {MsTableColumn},
  data(){
    return{
      readConfig: {toolbar: []},
      xssOptions: {
        whiteList: {
          img: ["src", "alt", "width", "height"],
        },
        stripIgnoreTagBody: true
      }
    };
  },
  props: {
    field: Object,
    fieldsWidth: Object
  }
}
</script>

<style>
.el-popover{
  height: 500px;
overflow: auto;
}
</style>
