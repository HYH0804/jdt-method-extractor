package kr.ac.seoultech.selab;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

public class TopCounter {
    static int[] topCount = new int[3];

    //반환으로 matched 여부의 String값
    public static String doCount(String jsonString, String prompt,int iter) throws Exception{

        System.out.println("prompt   "+prompt);
        System.out.println("jsonString   "+jsonString);
        //String csvFile = "defects4j.csv";
        //String outputCsvFile = "prompt-googleSheet.csv";
        Map<String, Set<Integer>> devFixedJsonMap = parseDevFixedJson(jsonString,iter); //파싱 안되면 return null
        List<PromptDTO> promptDtoList = parsePromptJson(prompt,iter); //파싱 안되면 return null

        checkAndThrowParsingError(iter, devFixedJsonMap, promptDtoList); //여기서 예외 던지기

        System.out.println("devFixedJsonMap = " + devFixedJsonMap.toString());
        System.out.println("devFixedJsonMap = " + promptDtoList.toString());


        // Top-n 방식으로 3번만 반복
        for (int n = 1; n <= 3; n++) {
            boolean found = false;
            for (int i = 0; i < n && i < promptDtoList.size(); i++) {
                PromptDTO dto = promptDtoList.get(i);
                Set<Integer> faultyLines = devFixedJsonMap.get(dto.getClassName());
                if (faultyLines != null && faultyLines.contains(dto.getLine())) {
                    found = true;
                    break;  // 하나라도 일치하면 나머지 확인하지 않음
                }
            }
            if (found) {
                topCount[n - 1]++;
            }
        }

        System.out.println("top1 = " + topCount[0]+" top2 = " + topCount[1]+" top3 = " + topCount[2]);

        return matchedType(promptDtoList, devFixedJsonMap);


    }

    @NotNull
    private static String matchedType(List<PromptDTO> promptDtoList, Map<String, Set<Integer>> devFixedJsonMap) {
        boolean allMatched = true;
        boolean someMatched = false;

        // promptDtoList를 효율적으로 검색하기 위해 Set으로 변환
        Set<String> promptSet = new HashSet<>();
        for (PromptDTO prompt : promptDtoList) {
            promptSet.add(prompt.getClassName() + ":" + prompt.getLine());
        }

        for (Map.Entry<String, Set<Integer>> entry : devFixedJsonMap.entrySet()) {
            String className = entry.getKey();
            Set<Integer> lines = entry.getValue();

            for (Integer line : lines) {
                String combinedKey = className + ":" + line;
                if (promptSet.contains(combinedKey)) {
                    someMatched = true;
                } else {
                    allMatched = false;
                }
            }
        }

        if (allMatched && someMatched) {
            return "Matched";
        } else if (someMatched) {
            return "Partially matched";
        } else {
            return "Not matched";
        }
    }

    private static void checkAndThrowParsingError(int iter, Map<String, Set<Integer>> devFixedJsonMap, List<PromptDTO> promptDtoList) throws Exception {
        if(devFixedJsonMap ==null && promptDtoList ==null){
            throw new Exception("동시 DevFixed 파싱과 GPT 파싱 Error at "+(iter +2));
        }

        if(devFixedJsonMap ==null){
            throw new Exception("DevFixed 파싱 Error at "+(iter +2));
        }

        if(promptDtoList ==null){
            throw new Exception("GPT 파싱 Error at "+(iter +2));
        }
    }

    public static Map<String, Set<Integer>> parseDevFixedJson(String jsonString,int iter) {  //<클래스 이름 , set<해당 라인들>>
        Map<String, Set<Integer>> resultMap = new HashMap<>();
        try {
            JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();
            JsonArray devFixedArray = jsonObject.getAsJsonArray("devFixed");

            devFixedArray.forEach(element -> {
                JsonObject obj = element.getAsJsonObject();
                String className = obj.get("className").getAsString();
                JsonArray faultyLineArray = obj.getAsJsonArray("faultyLine");

                Set<Integer> faultyLines = new HashSet<>();
                faultyLineArray.forEach(line -> faultyLines.add(line.getAsInt()));

                resultMap.put(className, faultyLines);
            });
        } catch (JsonParseException e) {
            return null;
        }
        return resultMap;
    }

public static List<PromptDTO> parsePromptJson(String prompt,int iter) {
    ArrayList<PromptDTO> promptDtoList = new ArrayList<>();
    try {
        // JSON 문자열에서 올바른 JSON 객체 부분만 추출
        int jsonStartIndex = prompt.indexOf('{');
        if (jsonStartIndex == -1) {
            throw new JsonParseException("No JSON object could be decoded");
        }
        String jsonString = prompt.substring(jsonStartIndex);

        // JsonReader를 사용하여 lenient 모드로 설정
        JsonReader jsonReader = new JsonReader(new StringReader(jsonString));
        jsonReader.setLenient(true);

        // JsonParser를 사용하여 JSON 문자열을 JsonObject로 파싱
        JsonObject jsonObject = JsonParser.parseReader(jsonReader).getAsJsonObject();
        JsonArray faultLocArray = jsonObject.getAsJsonArray("faultLoc");

        // 각 요소를 순회하며 PromptDTO 리스트에 추가
        faultLocArray.forEach(element -> {
            JsonObject obj = element.getAsJsonObject();
            //String classFullName = obj.get("className").getAsString(); //googleSheet에선
            String classFullName = obj.get("ClassName").getAsString();
            String className = parseClassName(classFullName);
            int faultyLine = Integer.parseInt(obj.get("faultyLine").getAsString());
            promptDtoList.add(new PromptDTO(className, faultyLine));
        });
    } catch (Exception e) {
        System.out.println("iter에서 PromptJson 문제" + iter);
        System.err.println("Failed to parse prompt JSON: " + e.getMessage());
        return null;
    }
    return promptDtoList;
}





    public static String parseClassName(String classFullName) {
        if (classFullName == null || classFullName.isEmpty() || classFullName.contains("$")) {
            return "ClassName ErrorCustom";
        }
        String[] parts = classFullName.split("\\.");
        return parts[parts.length - 1];
    }
}
