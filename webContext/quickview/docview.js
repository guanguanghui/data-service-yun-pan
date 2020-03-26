
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
		$("#vname").text(cleanFileName);
        var fileType = cleanFileName.substring(cleanFileName.lastIndexOf('\.'));
        var dlurl=(window.location.protocol+"//"+window.location.host+"/externalLinksController/downloadFileByKey/" + cleanFileName)+"?dkey="+result;
        $.ajax({
                    url: 'externalLinksController/getFilePreViewUrl.ajax',
                    type: 'POST',
                    dataType:'text',
                    data:{
                    	resourceUrl:dlurl
                    },
                    success:function(result){
                        if(result == 'NoAuth' || result == 'null'){
                            return;
                        }else{
                            var viewUrl = result;
                            window.location.replace(viewUrl);
                        }
                    },
                    error:function(){

                    }
        });

        },
    error:function(){}
});