/**
 * Shift Application file.
 */

var shift = angular.module("Shift", ["ngRoute", "ngMaterial", "ngAnimate",
    "ngMessages", "ngResource"]);

var pages = [{
  "title": "Queue",
  "url": "queue",
  "icon": "toc"
}, {
  "title": "Setup",
  "url": "setup",
  "icon": "settings"
}, {
  "title": "Log",
  "url": "log",
  "icon": "chat"
}];

shift.factory("sharedResources", function($resource) {
  var service = {};

  service.QueueItem = $resource("queue/:processId");

  service.Setup = $resource("channels/main/:id", {}, {
    "create": {
      method: "POST"
    },
    "save": {
      method: "PUT"
    },
    "remove": {
      method: "DELETE",
      isArray: true
    }
  });

  service.Log = $resource("logging");

  service.cache = {};

  service.putCache = function(key, value) {
    service.cache[key] = value;
  }

  service.getCache = function(key) {
    var cache = service.cache[key];
    delete service.cache[key];
    return cache;
  }

  return service;
});

shift.controller("MainController", function($scope, $resource, $location,
        $mdSidenav, sharedResources) {
  $scope.pages = pages;

  $scope.go = function(path) {
    $location.path(path);
    if (!$mdSidenav("main-nav").isLockedOpen()) {
      $mdSidenav("main-nav").close();
    }
    return false;
  };

  $scope.showNav = function() {
    $mdSidenav("main-nav").open();
  }

  $location.url("/queue");
});

shift.controller("QueueController", function($scope, $interval, $mdDialog,
        sharedResources) {
  $scope.queue = sharedResources.getCache("Queue");

  $scope.$on("$destroy", function(event) {
    $interval.cancel(refreshPromise);
    sharedResources.putCache("Queue", $scope.queue);
  });

  $scope.refresh = function() {
    sharedResources.QueueItem.query(function(data) {
      $scope.queue = data.reverse().splice(0, 100);
    });
  }

  $scope.showItem = function(ev, item) {
    $mdDialog.show({
      controller: "QueueItemController",
      resolve: {
        "item": function() {
          return item;
        }
      },
      templateUrl: 'queue-item.html',
      parent: angular.element(document.body),
      targetEvent: ev,
      openForm: ev.target,
      clickOutsideToClose: true,
      fullscreen: true
    });
  };

  var refreshPromise = $interval($scope.refresh, 2000);
});

shift.controller("QueueItemController", function($scope, $mdDialog, $interval,
        sharedResources, item) {

  $scope.item = item;
  $scope.live = true;

  $scope.$on("$destroy", function(event) {
    $interval.cancel(refreshPromise);
  });

  $scope.cancel = function() {
    $mdDialog.cancel();
  };

  $scope.refresh = function() {
    sharedResources.QueueItem.get($scope.item, function(data) {
      $scope.item = data;
    }, function() {
      $scope.live = false;
      $interval.cancel(refreshPromise);
    });
  };

  var refreshPromise = $interval($scope.refresh, 2000);
});

shift.controller("SetupController", function($scope, $mdDialog, $interval,
        sharedResources) {
  $scope.channels = sharedResources.getCache("Channels");

  $scope.$on("$destroy", function(event) {
    $interval.cancel(refreshPromise);
    sharedResources.putCache("Channels", $scope.channels);
  });

  $scope.newChannel = function(event) {
    var confirm = $mdDialog.prompt().title("New setup?").textContent(
            "Hot-folder name (hint: you can change it later).").placeholder(
            "Name").ariaLabel("Setup name").targetEvent(event).ok("OK").cancel(
            "Cancel");
    $mdDialog.show(confirm).then(function(result) {
      sharedResources.Setup.create({
        "name": result
      }, function(data) {
        $scope.$parent.go("setup/" + data.id + "/" + data.name);
      });
    });
  }

  $scope.editChannel = function(channel) {
    $scope.$parent.go("setup/" + channel.id + "/" + channel.name);
  }

  $scope.refresh = function() {
    sharedResources.Setup.query(function(data) {
      $scope.channels = data;
    });
  }

  var refreshPromise = $interval($scope.refresh, 2000);
});

shift.controller("SetupEditorController", function($scope, $mdDialog,
        $routeParams, $mdToast, sharedResources) {
  $scope.channel = {
    "id": $routeParams.id,
    "name": $routeParams.name
  };
  $scope.help = false;

  var dirtyform = function() {
    $scope.form.$setSubmitted();
    $scope.form.$setDirty();
  }

  $scope.newOutput = function() {
    var conf = $scope.channel.configuration;
    conf.outputs.push({
      'name': 'Output-' + (conf.outputs.length + 1),
      'locations': []
    });
    dirtyform();
  };

  $scope.deleteOutput = function(index) {
    var output = $scope.channel.configuration.outputs[index];
    $scope.channel.configuration.outputs.splice(index, 1);
    var toast = $mdToast.simple().textContent("Deleted").action("UNDO")
            .highlightAction(true);
    $mdToast.show(toast).then(function(response) {
      if (response == "ok") {
        $scope.channel.configuration.outputs.splice(index, 0, output);
      }
    });
    dirtyform();
  };

  $scope.newLocation = function(output) {
    output.locations.push({
      'type': 'FILE'
    });
    dirtyform();
  };

  $scope.deleteLocation = function(output, index) {
    var location = output.locations[index];
    output.locations.splice(index, 1);
    var toast = $mdToast.simple().textContent("Deleted").action("UNDO")
            .highlightAction(true);
    $mdToast.show(toast).then(function(response) {
      if (response == "ok") {
        output.locations.splice(index, 0, location);
      }
    });
    dirtyform();
  };

  $scope.updateChannel = function() {
    $mdToast.showSimple("Saving...");
    sharedResources.Setup.save($scope.channel, $scope.channel, function(data) {
      $scope.channel = data;
      var toast = $mdToast.simple().textContent("Saved").action("DONE")
              .highlightAction(true);
      $mdToast.show(toast).then(function(response) {
        if (response == "ok") {
          $scope.exit();
        }
      });
    });
  }

  $scope.exit = function() {
    $scope.$parent.go("setup");
  }

  $scope.deleteChannel = function() {
    var confirm = $mdDialog.confirm().title(
            "Delete " + $scope.channel.name + "?").textContent(
            "This cannot be undone.").ariaLabel('Delete').targetEvent(event)
            .ok("OK").cancel("Cancel");
    $mdDialog.show(confirm).then(function(result) {
      sharedResources.Setup.remove($scope.channel, function(data) {
        sharedResources.putCache("Channels", data);
        $scope.exit();
      });
    });
  }

  $scope.load = function() {
    sharedResources.Setup.get($scope.channel, function(data) {
      $scope.channel = data;
    });
  };

  $scope.load();

  $scope.varmatrix = [
      ["file.name", "The full input filename", "somefile.tif"],
      ["file.path", "The parent folder path", "/volumes/test/input/A/"],
      ["file.relpath","The parent folder path below the input folder","/A/"],
      ["file.fullpath","The entire file path","/volumes/test/somefile.pdf"],
      ["file.basename","File name minus the extension","somefile"],
      ["file.ext","File extension","pdf"],
      ["file.length","File length in bytes","32456"],
      ["match[1]","First match of the regular expressions that selected this output (used in locations only)",""],
      ["match[n]","The nth match of the regular expression","${match[2]}"],
      ["rand[1-9]","Random 1 to 9 digit number","${rand[3]}"],
      ["index[1-9]","A 1 to 9 digit index that incremements with each process","${index[8]}"],
      ["date.hour","2 digit hour (00-24)","22"],
      ["date.month","2 digit month (01-12).","04"],
      ["date.year","4 digit year","2016"],
      ["date.milli","3 digit millisecond","987"],
      ["date.day","2 digit day in the month","06"],
      ["date.decaminute","The 'tens' part of the minute","2"],
      ["date.full","The full date (same as '${date.year}${date.month}${date.day}')","20160409"],
      ["date.minute","2 digit minute in the hour","38"],
      ["date.second","2 digit second in the minute","44"]
  ];

});

shift.controller("LogController", function($scope, $interval, sharedResources) {
  $scope.logEntries = sharedResources.getCache("Logs");

  $scope.$on("$destroy", function(event) {
    $interval.cancel(refreshPromise);
    sharedResources.putCache("Logs", $scope.logEntries);
  });

  $scope.refresh = function() {
    sharedResources.Log.query(function(data) {
      $scope.logEntries = data;
    });
  }

  var refreshPromise = $interval($scope.refresh, 2000);
});

shift.config(function($routeProvider, $locationProvider) {
  for (var i = 0; i < pages.length; i++) {
    $routeProvider.when("/" + pages[i].url, {
      templateUrl: pages[i].url + ".html",
      controller: pages[i].title + "Controller"
    });
  }
  $routeProvider.when("/setup/:id/:name", {
    templateUrl: "setup-editor.html",
    controller: "SetupEditorController"
  });
});

shift.config(function($mdThemingProvider) {
  $mdThemingProvider.theme("default").primaryPalette("grey").accentPalette(
          "pink").warnPalette("orange");
});