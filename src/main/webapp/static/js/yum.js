$(function() {
  $('img.rpmInfo').cluetip({
    activation: 'click',
    sticky: true,
    closePosition: 'title',
    arrows: true,
    width: 750
  });

  $('img.repoInfo').cluetip({
    activation: 'click',
    sticky: true,
    closePosition: 'title',
    arrows: true,
    width: 750,
    ajaxCache: false,
    onShow: function () {
      window.yum.repoName = $('#yumRepoName').attr("name");
      yum.renderScheduledSwitch();
      yum.renderTags();
      yum.renderRpmsSlider();
    }
  });

  $('img.virtualRepoInfo').cluetip({
    activation: 'click',
    sticky: true,
    closePosition: 'title',
    arrows: true,
    width: 750,
    ajaxCache: false,
    onActivate: function () {
      if (!yum.repos) {
        yum.loadStaticRepos();
      }
    },
    onShow: function () {
      $('#externalSwitch').switchbutton().change(function () {
        if ($(this).prop('checked')) {
          $('#targetRepos').hide();
          $('#targetUrl').show();
        } else {
          $('#targetUrl').hide();
          $('#targetRepos').show();
        }
        $('#saveButton').show();
      });
      var targetRepos = $('#targetRepos');
      var targetSelect = targetRepos.get(0);
      var currentTarget = $('#currentTarget').val();
      var currentIsInList = false;
      for (var i = 0; i < yum.repos.length; i++) {
        var entry = yum.repos[i];
        var option = new Option(entry.name, 'static/' + entry.name);
        if (entry.name === currentTarget) {
          option.selected = true;
          currentIsInList = true;
        }
        targetSelect.options[i] = option;
      }
      if (!currentIsInList) {
        var defaultOption = new Option(currentTarget + ' (not existing)', 'static/' + currentTarget);
        defaultOption.selected = true;
        targetSelect.options[targetSelect.options.length] = defaultOption;
      }
    }
  });
});

window.yum = {

  setContextBase : function(contextBase) {
    yum.contextBase = contextBase;
  },

  setStaticRepoType : function(reponame, type) {
    yum.setRepoProperty(reponame, 'type', type);
  },

  setMaxKeepRpms : function(reponame, value) {
    yum.setRepoProperty(reponame, 'maxKeepRpms', value);
  },

  setMaxDaysRpms : function(reponame, value) {
    yum.setRepoProperty(reponame, 'maxDaysRpms', value);
  },

  setRepoProperty : function(reponame, property, value) {
    $.ajax({
      type : 'PUT',
      cache : false,
      url : reponame + '/' + property,
      contentType: 'application/json',
      data : JSON.stringify(value),
      success: function () {
      },
      error: function (xhr, status) {
        alert('Saving failed : ' + status);
      }
    });
  },

  loadStaticRepos : function() {
    $.ajax('../', {
      dataType: 'json',
      async : false,
      success: function (data) {
        yum.repos = data.items;
      }
    });
  },

  renderScheduledSwitch: function() {
      $('.scheduledSwitch').switchbutton().change(function () {
          var value = $(this).prop("checked") ? 'SCHEDULED' : 'STATIC';
          var reponame = $(this).attr("name");
          yum.setStaticRepoType(reponame, value);
        });
  },

  renderRpmsSlider: function() {
      var keepRpmsSlider = $('#maxKeepRpmsSlider');
      var keepRpmsValue = $('#maxKeepRpmsValue');
      keepRpmsSlider.slider({
        min: 0,
        max: 10,
        value: keepRpmsValue.text(),
        slide: function( event, ui ) {
            var value = ui.value;
            if ( value === 0){
                value = "ALL";
            }
            keepRpmsValue.text(value);
          yum.setMaxKeepRpms(keepRpmsValue.attr('name'), ui.value);
        }
      });
      if ( keepRpmsValue.text() === 0){
          keepRpmsValue.text("ALL");
      }

      var daysRpmsSlider = $('#maxDaysRpmsSlider');
      var daysRpmsValue = $('#maxDaysRpmsValue');
      daysRpmsSlider.slider({
        min: 0,
        max: 30,
        value: daysRpmsValue.text(),
        slide: function( event, ui ) {
            var value = ui.value;
            if ( value == 0){
                value = "NEVER";
            }
            daysRpmsValue.text(value);
          yum.setMaxDaysRpms(daysRpmsValue.attr('name'), ui.value);
        }
      });
      if ( daysRpmsValue.text() == 0){
          daysRpmsValue.text("NEVER");
      }
  },

  renderTags: function() {
	  var tags = [];
	  $(".tag").each(function() {
		  tags.push({name: $(this).text()});
	  });

    $("#tagsInput").tokenInput([], {
      allowCreation: true,
      createTokenText: "Create new tag",
      onAdd: function() {},
      onDelete: function() {},
      theme: "facebook",
      prePopulate: tags,
      hintText: "add a tag"
    });
  },

  resetTags: function() {
    var tags = $("#tagsInput").tokenInput("get");
    yum.deleteAllTags();
    yum.saveTags(tags);
    window.location.reload();
  },
  
  deleteAllTags: function() {
	$.ajax({
	    type : 'DELETE',
	    async: false,
	    cache : false,
	    url : yum.repoName + '/tags',
	    error: function (xhr, status) {
	      alert('Resetting tags failed : ' + status);
	    }
	});
  },

  deleteRPM: function(target,repoPath,rpmHref) {
      $.ajax({
          type : 'DELETE',
          async: false,
          cache : false,
          url : repoPath+"/"+rpmHref,
          success: function () {
              $('#'+target).remove();
          },
          error: function (xhr, status) {
              alert('deleting file failed : ' + status);
          }
      });
    },
  deleteObsoleteRPMs: function(targetRepo,sourceRepo) {
    $.ajax({
      type : 'DELETE',
      async: false,
      cache : false,
      url : '?targetRepo='+targetRepo+'&sourceRepo='+sourceRepo,
      success: function () {
        alert('Asynchronous deleting of obsolete RPMs successfully triggered. Deletion may take some time due to delete volume limits to prevent mongodb replica locks. Reload page to see progress.');
      },
      error: function (xhr, status) {
        alert('deleting file failed : ' + status);
      }
    });
  },

  propagateRPM: function(target,sourcePath,targetRepoName) {
    $.ajax({
        type : 'POST',
        async: false,
        cache : false,
        url : yum.contextBase+'propagation',
        data : {source:sourcePath, destination:targetRepoName},
        success: function () {
            $('#'+target).remove();
        },
        error: function (xhr, status) {
            alert('propagating RPM failed : ' + status);
        }
    });
  },


    saveTags : function(tags) {
	$(tags).each(function() {
		var newTag = this.name;
		$.ajax({
		    type : 'POST',
		    async: false,
		    cache : false,
		    url : yum.repoName + '/tags',
		    data : {tag: newTag},
		    success: function () {},
		    error: function (xhr, status) {
		      alert('Saving failed : ' + status);
		    }
		});
	});
  },

  saveVirtualRepo : function(name) {
    var external = $('#externalSwitch').prop('checked');
    var target;
    if (external) {
      target = $('#targetUrl').val();
    } else {
      target = $('#targetRepos').val();
    }

    $('#saveButton').hide();
    $('#saveSpinner').show();

    $.ajax({
      type : 'POST',
      cache : false,
      url : '',
      data : {name : name, destination: target},
      success: function () {
        $('#saveSpinner').hide();
      },
      error: function (xhr, status) {
        $('#saveSpinner').hide();
        alert('Saving failed : ' + status);
        $('#saveButton').show();
      }
    });
  }
};