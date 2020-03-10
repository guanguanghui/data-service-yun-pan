import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

public class JsonTest {

    private static void getAllDepartmentTree(JSONArray nodes, JSONArray targetTreeArr){
        if (nodes == null){
            return;
        }
        if(targetTreeArr == null){
            targetTreeArr = new JSONArray();
        }
        for (int i=0;i<nodes.size();i++){
            JSONObject department = (JSONObject) nodes.get(i);
            JSONObject newDepartment = new JSONObject();

            // 添加部门节点的 text，href，tags
            newDepartment.put("text",department.getString("name"));
            newDepartment.put("href",department.getString("id"));
            JSONArray tags = new JSONArray();
            tags.add(0,""+department.getLong("deptTeacherNum"));
            newDepartment.put("tags",tags);

            // 添加组织节点的负责人
            JSONArray userOutputDtos = department.getJSONArray("userOutputDtos");
            JSONArray newUserOutputDtos = new JSONArray();
            for(int k=0;k<userOutputDtos.size();k++){
                JSONObject userOutputDto = (JSONObject) userOutputDtos.get(k);
                JSONObject newUserOutputDto = new JSONObject();
                newUserOutputDto.put("text",userOutputDto.getString("userName") + " [" + userOutputDto.getString("duty") + "]") ;
                newUserOutputDto.put("href",userOutputDto.getString("userId"));
                JSONArray userTags = new JSONArray();
                userTags.add(0,"0");
                newUserOutputDto.put("tags",userTags);
                newUserOutputDtos.add(k,newUserOutputDto);
            }

            // 添加子部门
            JSONArray sunDepartments = department.getJSONArray("sunDepartment");
            newDepartment.put("nodes",newUserOutputDtos);
            targetTreeArr.add(newDepartment);
            //targetTreeArr.set(i,newDepartment);
            getAllDepartmentTree(sunDepartments,newUserOutputDtos);

//            for(int j=0;j<sunDepartments.size();j++ ){
//                JSONObject sunDepartment = (JSONObject) sunDepartments.get(i);
//                JSONObject newSunDepartment = new JSONObject();
//                newSunDepartment.put("text",sunDepartment.getString("userName"));
//                newSunDepartment.put("href",sunDepartment.getString("userId"));
//                JSONArray userTags = new JSONArray();
//                userTags.add(0,"0");
//                newSunDepartment.put("tags",userTags);
//                sunDepartments.add(j,newSunDepartment);
//            }
        }
    }


    public static void main(String[] args) {
        String body = "[ {\n" +
                "    \"id\" : \"2b5377f9-be35-4444-bce2-445e2dfa55a1\",\n" +
                "    \"name\" : \"四川省龙泉中学\",\n" +
                "    \"sunDepartment\" : [ {\n" +
                "      \"id\" : \"6e9ab2ec-2613-4187-af0d-0be4f1a940c8\",\n" +
                "      \"name\" : \"学生处\",\n" +
                "      \"sunDepartment\" : [ ],\n" +
                "      \"userOutputDtos\" : [ {\n" +
                "        \"userId\" : \"56b067ac-e5c7-41ae-9712-e0ddbf5e3ff8\",\n" +
                "        \"userName\" : \"何林海\",\n" +
                "        \"duty\" : \"校长\"\n" +
                "      }, {\n" +
                "        \"userId\" : \"ec8fe529-90f2-4092-af7b-d9d9fae16ece\",\n" +
                "        \"userName\" : \"毛一苇\",\n" +
                "        \"duty\" : \"年级组长\"\n" +
                "      } ],\n" +
                "      \"deptTeacherNum\" : 2\n" +
                "    }, {\n" +
                "      \"id\" : \"999b34ff-a71d-49fa-a127-d388acc74d7e\",\n" +
                "      \"name\" : \"教务处\",\n" +
                "      \"sunDepartment\" : [ {\n" +
                "        \"id\" : \"4cb51d73-2577-4a2f-bc48-939d01353a1e\",\n" +
                "        \"name\" : \"督导室\",\n" +
                "        \"sunDepartment\" : [ ],\n" +
                "        \"userOutputDtos\" : [ ],\n" +
                "        \"deptTeacherNum\" : 0\n" +
                "      } ],\n" +
                "      \"userOutputDtos\" : [ ],\n" +
                "      \"deptTeacherNum\" : 0\n" +
                "    }, {\n" +
                "      \"id\" : \"eb34c92c-7f38-4469-b4b0-31825f680b9a\",\n" +
                "      \"name\" : \"高一年级组\",\n" +
                "      \"sunDepartment\" : [ ],\n" +
                "      \"userOutputDtos\" : [ {\n" +
                "        \"userId\" : \"1cf2ac20-8141-46fc-bc31-c4a73117b145\",\n" +
                "        \"userName\" : \"蒋芳媛\",\n" +
                "        \"duty\" : \"副校长\"\n" +
                "      }, {\n" +
                "        \"userId\" : \"038be11a-6a33-4f0f-ad0c-570f892f21d7\",\n" +
                "        \"userName\" : \"莫云\",\n" +
                "        \"duty\" : \"学科教师\"\n" +
                "      }, {\n" +
                "        \"userId\" : \"56b067ac-e5c7-41ae-9712-e0ddbf5e3ff8\",\n" +
                "        \"userName\" : \"何林海\",\n" +
                "        \"duty\" : \"校长\"\n" +
                "      }, {\n" +
                "        \"userId\" : \"f153337a-ccfc-40c6-9075-9c7ea51ec10b\",\n" +
                "        \"userName\" : \"邹鸿跃\",\n" +
                "        \"duty\" : \"学科教师\"\n" +
                "      } ],\n" +
                "      \"deptTeacherNum\" : 4\n" +
                "    } ],\n" +
                "    \"userOutputDtos\" : [ {\n" +
                "      \"userId\" : \"18bf99cf-2972-405a-9ce0-a790a6df0693\",\n" +
                "      \"userName\" : \"邹晶\",\n" +
                "      \"duty\" : \"副校长\"\n" +
                "    } ],\n" +
                "    \"deptTeacherNum\" : 1\n" +
                "  } ]";
        JSONArray nodes = JSON.parseArray(body);
        JSONArray targetTreeArr = new JSONArray();

        getAllDepartmentTree(nodes, targetTreeArr);

        System.out.println(targetTreeArr.toJSONString());
    }
}
