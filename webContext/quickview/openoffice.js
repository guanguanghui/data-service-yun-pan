$(function() {
	window.onresize = function(){
		showCloseBtn();
    }
	var fileId = getFileIdAndFileName()[0];
	var fileName = decodeURIComponent(getFileIdAndFileName()[1]);
    $.ajax({
    	url:'externalLinksController/getDownloadKey.ajax',
    	type:'POST',
    	dataType:'text',
    	data:{
    		fId:fileId
    	},
    	success:function(result){
    		// 获取链接
    		var cleanFileName = fileName.replace(/\'/g,'');
            var fileType = cleanFileName.substring(cleanFileName.lastIndexOf('\.') + 1);
            var dlurl=(window.location.protocol+"//"+window.location.host+"/externalLinksController/downloadFileByKey/file")+"?dkey="+result;
            var config = {
                "document": {
                    "fileType": fileType,
                    "key": fileId,
                    "title": cleanFileName,
                    "url": dlurl
                },
                "documentType": "text",
            	"editorConfig": {
                    "mode": "view"
                }
            };

            var docEditor = new DocsAPI.DocEditor("placeholder", config);
    	},
    	error:function(){

    	}
    });
});
// 获取URL上的视频id参数，它必须是第一个参数。
function getFileIdAndFileName() {
	var url = location.search;
	var result=new Array();
	if (url.indexOf("?") != -1) {
		var str = url.substr(1);
		var strArr = str.split("&");
		var fileIdStrs = strArr[0].split("=");
		var fileNameStrs = strArr[1].split("=");
        result.push(fileIdStrs[1],fileNameStrs[1])
	}
	return result;
}

function showCloseBtn(){
	var win = $(window).width();
    if(win < 450){
    		$("#closeBtn").addClass("hidden");
    }else{
    		$("#closeBtn").removeClass("hidden");
    }
}
