app.controller('ProfileCtrl', ['$scope', '$http', 'initData', function($scope, $http, initData) {
  $scope.processing = false;

  $scope.init = function() {
    $scope.userInfo = _.defaults(angular.copy(initData), {
      gender: ""
    });

    $scope.successMessage = null;
    $scope.errorMessage = null;
  };

  $scope.save = function() {
    // TODO: Validate input
    // TODO: Move the error or success state in another controller
    $scope.successMessage = null;
    $scope.errorMessage = null;
    // TODO: Move the processing state in another controller
    $scope.processing = true;
    $http.put('/a/profile', $scope.userInfo)
      .success(function() {
        $scope.successMessage = "Your profile is saved.";
        $scope.processing = false;
      })
      .error(function() {
        $scope.errorMessage = "Error.";
        // TODO: Display a more precise message
        $scope.processing = false;
      });
  };

  $scope.init();
}]);
