var initDataElement = $('#initial-data');
var initData = {};
if (initDataElement) {
  initData = angular.fromJson(initDataElement.html());
}
var app = angular.module('app', [])
    .value('initData', initData);

angular.element(document).ready(function() {
    angular.bootstrap(document, ['app']);
});
