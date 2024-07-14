package kr.ac.seoultech.selab;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class TraceParser {

    public static List<TestDTO> parseJson(String jsonFilePath) {
        //TestDTO 객체 만들고
        List<TestDTO> target = new ArrayList<>();

        try (Reader reader = new InputStreamReader(
                TraceParser.class.getClassLoader().getResourceAsStream(jsonFilePath))) {
            if (reader == null) {
                throw new IllegalArgumentException("JSON 파일을 찾을 수 없습니다: " + jsonFilePath);
            }
            JsonElement jsonElement = JsonParser.parseReader(reader);
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            JsonArray tracesArray = jsonObject.getAsJsonArray("npe.traces"); // [ { test.class test.method traces [] } , { test.class test.method traces [] } ... ]

            for (JsonElement traceElement : tracesArray) {
                JsonObject traceObject = traceElement.getAsJsonObject();

                //test.class 와 test.method test.source로 TestDTO 만들고 put 해줌.
                String testClassName = traceObject.get("test.class").getAsString();
                String testMethodName = traceObject.get("test.method").getAsString();
                TestDTO testDTO = new TestDTO(testClassName, testMethodName);
                target.add(testDTO);

                JsonArray traceDetails = traceObject.getAsJsonArray("traces");             // [ {class method line is_target}. {class method line is_target} , ... ]


                for (JsonElement detailElement : traceDetails) {
                    JsonObject detailObject = detailElement.getAsJsonObject();
                    boolean isTarget = detailObject.get("is_target").getAsBoolean();
                    if (isTarget) {
                        String className = detailObject.get("class").getAsString();
                        String methodName = detailObject.get("method").getAsString();
                        int lineNumber = detailObject.get("line").getAsInt();
                        //className에 "Test"있는지 확인하고
                        if (JDTMethodExtractor.isContainTest(className)) {
                            //"Test" 있으면 기존 TestDTO에 line 필드 추가 (npe.trace.json에 각각 객체에는 Test 클래스가 하나씩밖에 없음)
                            testDTO.addTestLine(lineNumber);
                        }
                        else {
                            //"Test" 없으면 TestDTO의 source에 기존 class와 method 동시에 겹치는게 있는지 확인하고
                            if (testDTO.isDuplicate(className, methodName)) {
                                //있다면 SourceDTO 안의 sourceLine 리스트에 해당 라인값만 추가
                                SourceDTO sourceDTO = testDTO.findSourceDTO(className, methodName);
                                //sourceDTO.getSourceLine().get(lineNumber);
                                sourceDTO.addSourceLine(lineNumber);
                            } else {
                                //없다면 source 필드에 list.add(new SourceDTO(class,method,line))
                                testDTO.getSource().add(new SourceDTO(className, methodName, lineNumber));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return target;
    }
}