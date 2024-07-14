package kr.ac.seoultech.selab;

import java.util.ArrayList;
import java.util.List;

public class TestDTO {
    private String testClass; //이건 같게 나올수도 있는데
    private String testMethod; //이건 다 다르게 나옴
    private List<Integer> testLine = new ArrayList<>();


    //MultiValuedMap<String, List<SourceDTO>> source;
    List<SourceDTO> source = new ArrayList<>();

    public TestDTO(String testClass, String testMethod) {
        this.testClass = testClass;
        this.testMethod = testMethod;
    }

    public String getTestClass() {
        return testClass;
    }

    public void setTestClass(String testClass) {
        this.testClass = testClass;
    }

    public String getTestMethod() {
        return testMethod;
    }

    public void setTestMethod(String testMethod) {
        this.testMethod = testMethod;
    }

    public List<Integer> getTestLine() {
        return testLine;
    }


    public void addTestLine(int testLine) {
        this.testLine.add(testLine);
    }


    public List<SourceDTO> getSource() {
        return source;
    }

    public void setSource(List<SourceDTO> source) {
        this.source = source;
    }

    public boolean isDuplicate(String className, String methodName) {
        for (SourceDTO temp : source) {
            if (temp.getSourceClass().equals(className) && temp.getSourceMethod().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    public SourceDTO findSourceDTO(String className, String methodName) {
        for (SourceDTO temp : source) {
            if (temp.getSourceClass().equals(className) && temp.getSourceMethod().equals(methodName)) {
                return temp;
            }
        }
        return null;
    }

}
