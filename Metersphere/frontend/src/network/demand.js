import {post, get} from "@/common/js/ajax";
import {getPageDate} from "@/common/js/tableUtils";
import {getCurrentProjectID} from "@/common/js/utils";
import {baseGet} from "@/network/base-network";

export function buildIssues(page) {
  let data = page.data;
  for (let i = 0; i < data.length; i++) {
    if (data[i]) {
      if (data[i].customFields) {
        data[i].customFields = JSON.parse(data[i].customFields);
      }
    }
  }
}
export function getRelateDemands(page) {
  return post('demand/list/relate/' + page.currentPage + '/' + page.pageSize, page.condition, (response) => {
    getPageDate(response, page);
    buildIssues(page);
  });
}

export function getDemands(page) {
  return post('demand/list/' + page.currentPage + '/' + page.pageSize, page.condition, (response) => {
    getPageDate(response, page);
    buildIssues(page);
  });
}

export function testCaseDemandRelate(param, success) {
  return post('/demand/relate', param, (response) => {
    if (success) {
      success(response);
    }
  });
}

export function getDemandByCaseId(caseId, page) {
  if (caseId) {
    return get('demand/get/' + caseId, (response) => {
      page.data = response.data;
      buildIssues(page);
    });
  }

}
