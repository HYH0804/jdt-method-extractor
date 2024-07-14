package kr.ac.seoultech.selab;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JDTMethodExtractor {

    public static void main(String[] args) throws IOException {
        PathAssembler pathAssembler = new PathAssembler();
        ClassLoader classLoader = JDTMethodExtractor.class.getClassLoader();
        Prompting prompting = new Prompting();
        String csvFilePath = "src/main/resources/result.csv"; // CSV 파일 경로
        int rowIndex = 0;

        for (String path : pathAssembler.defects4j) { //여기만 바꿔끼우기
            List<StringBuilder> templateArgsList = new ArrayList<>();
            Map<String, String> pathMap = pathAssembler.assembler(path);

            String jsonFilePath = pathMap.get("jsonFilePath");
            String sourceRootPath = pathMap.get("sourceRootPath");
            String testRootPath = pathMap.get("testRootPath");
            String stackTracesPath = pathMap.get("stackTraces");

            StringBuilder faultyCode = new StringBuilder();
            StringBuilder taskDescription = new StringBuilder();
            StringBuilder testCode = new StringBuilder();
            StringBuilder testFailedLine = new StringBuilder();
            StringBuilder stackTraces = new StringBuilder(readStackTraces(classLoader, stackTracesPath));

            templateArgsList.add(faultyCode);
            templateArgsList.add(taskDescription);
            templateArgsList.add(testCode);
            templateArgsList.add(testFailedLine);
            templateArgsList.add(stackTraces);

            System.out.println(prompting.getKey("hyun_api_key"));

            // JSON 파일을 파싱하여 타겟 메소드 정보를 가져옵니다.
            List<TestDTO> targetTest = TraceParser.parseJson(jsonFilePath); //<methodName , <source, Line>>

            // FuseFL
            targetTest.forEach((testDTO) -> {
                List<SourceDTO> targetSource = testDTO.getSource();
                // ClassLoader를 사용하여 소스 코드 루트 경로를 절대 경로로 변환합니다
                targetSource.forEach((sourceDTO) -> {
                    faultyCode.append(absolutePath(sourceDTO, classLoader, sourceRootPath, 0));
                    taskDescription.append(absolutePath(sourceDTO, classLoader, sourceRootPath, 1));
                });
                testCode.append(absolutePath(testDTO, classLoader, testRootPath, 0));
                testFailedLine.append(absolutePath(testDTO, classLoader, testRootPath, 2));

            });
            try {
                //템플릿 매핑값 출력
                String templateResult = fuseTemplateAssemlber("FuseTemplate.txt",classLoader, templateArgsList);
                //System.out.println(templateResult);
                Prompting.updateCsvWithQuestionOrAnswer(csvFilePath, templateResult, rowIndex++,"Question");

                //API 호출 결과 출력
                //String templateResult = fuseTemplateAssemlber("FuseTemplate.txt", classLoader, templateArgsList);
                //String templateAns = prompting.callAPI(templateResult);
                //prompting.printConsole(prompting.callAPI(templateResult));


                //Csv 출력
                //Prompting.updateCsvWithQuestionOrAnswer(csvFilePath, templateAns, rowIndex++,"Answer");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // LLM in FL, Wu et. al
            targetTest.forEach((testDTO) -> {
                List<SourceDTO> targetSource = testDTO.getSource();
                // ClassLoader를 사용하여 소스 코드 루트 경로를 절대 경로로 변환합니다
                targetSource.forEach((sourceDTO) -> {
                    try {
                        System.out.println("Prompt1 ========================================================================");
                        System.out.println(prompt1Assemlber("Prompt1Template.txt", classLoader, absolutePath(sourceDTO, classLoader, sourceRootPath, 0)));
                        if (!readStackTraces(classLoader, stackTracesPath).equals("Stack Traces Path Wrong")) {
                            System.out.println("Prompt2 ------------------------------------------------------------------------");
                            System.out.println(prompt2Assemlber("Prompt2Template.txt", classLoader, readStackTraces(classLoader, stackTracesPath), absolutePath(testDTO, classLoader, testRootPath, 0)));
                        }
                        System.out.println("================================================================================");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            });
        }
    }

    //index == 0 >> 코드 추출
    //index == 1 >> 주석 추출
    //index == 2 >> 라인만 추출
    private static String absolutePath(SourceDTO sourceDTO, ClassLoader classLoader, String sourceRootPath, int index) { //Source 출력
        System.out.println("sourceRootPath: "+sourceRootPath);
        File rootDir = new File(classLoader.getResource(sourceRootPath).getFile());

//        System.out.println("rootDir: " + rootDir.getPath());
        String absoluteSourceRootPath = rootDir.getAbsolutePath();

        String sourceClassPath = sourceDTO.getSourceClass().replace('.', File.separatorChar) + ".java";
        // $ 문자가 있을 경우 해당 문자 앞까지만 사용
        int dollarIndex = sourceClassPath.indexOf('$');
        if (dollarIndex != -1) {
            sourceClassPath = sourceClassPath.substring(0, dollarIndex) + ".java";
        }

        String filePath = Paths.get(absoluteSourceRootPath, sourceClassPath).toString();
        System.out.println("=======================================" +sourceDTO.getSourceLine().toString());
        //System.out.println("Processing file: " + filePath);
        try {
            String source = new String(Files.readAllBytes(Paths.get(filePath)));
            if(index==0) {//Description
                return extractMethodCode(source, sourceDTO.getSourceClass(), sourceDTO.getSourceMethod(), sourceDTO.getSourceLine());
            }
            else if(index==1){
                return extractMethodJavadoc(source, sourceDTO.getSourceClass(), sourceDTO.getSourceMethod(), sourceDTO.getSourceLine());
            }
            else{
                return extractMethodCode(source, sourceDTO.getSourceClass(), sourceDTO.getSourceMethod(), sourceDTO.getSourceLine());
            }
        } catch (IOException e) {
            String errorMessage = new String("파일을 읽는 동안 오류가 발생했습니다: " + filePath);
            e.printStackTrace();
            return errorMessage;
        }
    }

    //index == 0 >> 코드 추출
    //index == 1 >> 주석 추출
    //index == 2 >> 라인만 추출
    private static String absolutePath(TestDTO testDTO, ClassLoader classLoader, String testRootPath, int index) { //Test 출력
        File rootDir = new File(classLoader.getResource(testRootPath).getFile());

//        System.out.println("rootDir: " + rootDir.getPath());
        String absoluteSourceRootPath = rootDir.getAbsolutePath();

        String filePath = Paths.get(absoluteSourceRootPath, testDTO.getTestClass().replace('.', File.separatorChar) + ".java").toString();
//        System.out.println("Processing file: " + filePath);
        try {
            String source = new String(Files.readAllBytes(Paths.get(filePath)));
            if (index == 0) {
                return extractMethodCode(source, testDTO.getTestClass(), testDTO.getTestMethod(), testDTO.getTestLine());
            } else if (index == 1) {
                return extractMethodJavadoc(source, testDTO.getTestClass(), testDTO.getTestMethod(), testDTO.getTestLine());
            }
            else{
                return extractMethodLine(source, testDTO.getTestClass(), testDTO.getTestMethod(), testDTO.getTestLine().get(0));
            }
        } catch (IOException e) {
            String errorMessage = new String("파일을 읽는 동안 오류가 발생했습니다: " + filePath);
            e.printStackTrace();
            return errorMessage;
        }
    }


//    private static String extractMethodCode(String source, String className, String methodName, int lineNumber) {
//        StringBuilder methodCode = new StringBuilder();
//        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
//        parser.setSource(source.toCharArray());
//        parser.setKind(ASTParser.K_COMPILATION_UNIT);
//
//        // 파싱 옵션 설정
//        Map<String, String> options = JavaCore.getOptions();
//        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
//        parser.setCompilerOptions(options);
//
//        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
//
//
//        cu.accept(new ASTVisitor() {
//            @Override
//            public boolean visit(MethodDeclaration node) {
//                if (node.getName().getIdentifier().equals(methodName)) {
//                    int startLine = cu.getLineNumber(node.getStartPosition());
//                    int endLine = cu.getLineNumber(node.getStartPosition() + node.getLength());
//                    if (startLine <= lineNumber && lineNumber <= endLine) {
//                        methodCode.append("ClassName: " + className + " ,Start Line: " + startLine + " ,End Line: " + endLine + "Error Line number: "+lineNumber +"\n");
//                        methodCode.append(node.toString());
//                        methodCode.append("\n");
//                    }
//                }
//                return super.visit(node);
//            }
//        });
//        return methodCode.toString();
//    }
//
//    public static String extractMethodLine(String source, String className, String methodName, int lineNumber) {
//        StringBuilder methodLine = new StringBuilder();
//        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
//        parser.setSource(source.toCharArray());
//        parser.setKind(ASTParser.K_COMPILATION_UNIT);
//
//        // 파싱 옵션 설정
//        Map<String, String> options = JavaCore.getOptions();
//        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
//        parser.setCompilerOptions(options);
//
//        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
//        cu.accept(new ASTVisitor() {
//            @Override
//            public boolean visit(MethodDeclaration node) {
//                if (node.getName().getIdentifier().equals(methodName)) {
//                    int startLine = cu.getLineNumber(node.getStartPosition());
//                    int endLine = cu.getLineNumber(node.getStartPosition() + node.getLength());
//                    if (startLine <= lineNumber && lineNumber <= endLine) {
//                        // Get the specific line from the source code
//                        int startOffset = cu.getPosition(lineNumber, 0);
//                        int endOffset = startOffset;
//                        for (int i = startOffset; i < source.length(); i++) {
//                            if (source.charAt(i) == '\n') {
//                                endOffset = i;
//                                break;
//                            }
//                        }
//                        if (startOffset >= 0 && endOffset >= 0 && endOffset > startOffset) {
//                            methodLine.append("className: " + className +", Statement:");
//                            methodLine.append("generate a \'NullPointerException\' in line "+lineNumber);
//                            methodLine.append(source, startOffset, endOffset).append("\n");
//                        } else {
//                            methodLine.append("Line number out of bounds or empty line.\n");
//                        }
//                    }
//                }
//                return super.visit(node);
//            }
//        });
//
//        return methodLine.toString();
//    }
//
//    public static String extractMethodJavadoc(String source, String className, String methodName, int lineNumber) { //해당 코드 주석
//        StringBuilder javadocCode = new StringBuilder();
//
//        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
//        parser.setSource(source.toCharArray());
//        parser.setKind(ASTParser.K_COMPILATION_UNIT);
//
//        // 파싱 옵션 설정
//        Map<String, String> options = JavaCore.getOptions();
//        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
//        parser.setCompilerOptions(options);
//
//        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
//        cu.accept(new ASTVisitor() {
//            @Override
//            public boolean visit(MethodDeclaration node) {
//                if (node.getName().getIdentifier().equals(methodName)) {
//                    int startLine = cu.getLineNumber(node.getStartPosition());
//                    int endLine = cu.getLineNumber(node.getStartPosition() + node.getLength());
//                    if (startLine <= lineNumber && lineNumber <= endLine) {
//                        Javadoc javadoc = node.getJavadoc();
//                        if (javadoc != null) {
//                            javadocCode.append("className: "+ className+ "\n"+javadoc.toString()).append("\n");
//                        } else {
//                            javadocCode.append("No Javadoc found.\n");
//                        }
//                    }
//                }
//                return super.visit(node);
//            }
//        });
//
//        return javadocCode.toString();
//    }

    private static String extractMethodCode(String source, String className, String methodName, List<Integer> lineNumber) {
        StringBuilder methodCode = new StringBuilder();
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);

        // 파싱 옵션 설정
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
        parser.setCompilerOptions(options);

        CompilationUnit cu = (CompilationUnit) parser.createAST(null);

        cu.accept(new ASTVisitor() {
            private String currentClassName = "";

            @Override
            public boolean visit(TypeDeclaration node) {
                // 클래스 선언을 방문할 때마다 현재 클래스 이름을 업데이트
                if (currentClassName.isEmpty()) {
                    currentClassName = node.getName().getIdentifier();
                } else {
                    currentClassName += "$" + node.getName().getIdentifier();
                }
                return super.visit(node);
            }

            @Override
            public void endVisit(TypeDeclaration node) {
                // 클래스 선언이 끝날 때 현재 클래스 이름을 부모 클래스 이름으로 되돌림
                int dollarIndex = currentClassName.lastIndexOf('$');
                if (dollarIndex != -1) {
                    currentClassName = currentClassName.substring(0, dollarIndex);
                } else {
                    currentClassName = "";
                }
                super.endVisit(node);
            }

            @Override
            public boolean visit(MethodDeclaration node) {
                String currentMethodName = node.getName().getIdentifier();
                String simpleClassName = getSimpleClassName(className);

                boolean isConstructor = methodName.equals("<init>") && currentMethodName.equals(simpleClassName);
                if (currentMethodName.equals(methodName) || isConstructor) {
                    int startLine = cu.getLineNumber(node.getStartPosition());
                    int endLine = cu.getLineNumber(node.getStartPosition() + node.getLength());
                    if (startLine <= lineNumber.get(0) && lineNumber.get(0) <= endLine) {
                        methodCode.append("ClassName: " + className + " ,Start Line: " + startLine + " ,End Line: " + endLine + " Error Line number: " + lineNumber.toString() + "\n");

                        methodCode.append(node.toString());
                        methodCode.append("\n");
                    }
                }
                return super.visit(node);
            }
        });
        return methodCode.toString();
    }


    public static String extractMethodLine(String source, String className, String methodName, int lineNumber) {
        StringBuilder methodLine = new StringBuilder();
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);

        // 파싱 옵션 설정
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
        parser.setCompilerOptions(options);

        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        cu.accept(new ASTVisitor() {
            private String currentClassName = "";

            @Override
            public boolean visit(TypeDeclaration node) {
                // 클래스 선언을 방문할 때마다 현재 클래스 이름을 업데이트
                if (currentClassName.isEmpty()) {
                    currentClassName = node.getName().getIdentifier();
                } else {
                    currentClassName += "$" + node.getName().getIdentifier();
                }
                return super.visit(node);
            }

            @Override
            public void endVisit(TypeDeclaration node) {
                // 클래스 선언이 끝날 때 현재 클래스 이름을 부모 클래스 이름으로 되돌림
                int dollarIndex = currentClassName.lastIndexOf('$');
                if (dollarIndex != -1) {
                    currentClassName = currentClassName.substring(0, dollarIndex);
                } else {
                    currentClassName = "";
                }
                super.endVisit(node);
            }

            @Override
            public boolean visit(MethodDeclaration node) {
                String currentMethodName = node.getName().getIdentifier();
                String simpleClassName = getSimpleClassName(className);

                boolean isConstructor = methodName.equals("<init>") && currentMethodName.equals(simpleClassName);
                if (currentMethodName.equals(methodName) || isConstructor) {
                    int startLine = cu.getLineNumber(node.getStartPosition());
                    int endLine = cu.getLineNumber(node.getStartPosition() + node.getLength());
                    if (startLine <= lineNumber && lineNumber <= endLine) {
                        // Get the specific line from the source code
                        int startOffset = cu.getPosition(lineNumber, 0);
                        int endOffset = startOffset;
                        for (int i = startOffset; i < source.length(); i++) {
                            if (source.charAt(i) == '\n') {
                                endOffset = i;
                                break;
                            }
                        }
                        if (startOffset >= 0 && endOffset >= 0 && endOffset > startOffset) {
                            methodLine.append("className: " + currentClassName + ", Statement: ");
                            methodLine.append("generate a 'NullPointerException' in line " + lineNumber + "\n");

                            methodLine.append(source, startOffset, endOffset).append("\n");
                        } else {
                            methodLine.append("Line number out of bounds or empty line.\n");
                        }
                    }
                }
                return super.visit(node);
            }
        });

        return methodLine.toString();
    }

    public static String extractMethodJavadoc(String source, String className, String methodName, List<Integer> lineNumber) {
        StringBuilder javadocCode = new StringBuilder();

        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);

        // 파싱 옵션 설정
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
        parser.setCompilerOptions(options);

        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        cu.accept(new ASTVisitor() {
            private String currentClassName = "";

            @Override
            public boolean visit(TypeDeclaration node) {
                // 클래스 선언을 방문할 때마다 현재 클래스 이름을 업데이트
                if (currentClassName.isEmpty()) {
                    currentClassName = node.getName().getIdentifier();
                } else {
                    currentClassName += "$" + node.getName().getIdentifier();
                }
                return super.visit(node);
            }

            @Override
            public void endVisit(TypeDeclaration node) {
                // 클래스 선언이 끝날 때 현재 클래스 이름을 부모 클래스 이름으로 되돌림
                int dollarIndex = currentClassName.lastIndexOf('$');
                if (dollarIndex != -1) {
                    currentClassName = currentClassName.substring(0, dollarIndex);
                } else {
                    currentClassName = "";
                }
                super.endVisit(node);
            }

            @Override
            public boolean visit(MethodDeclaration node) {
                String currentMethodName = node.getName().getIdentifier();
                String simpleClassName = getSimpleClassName(className);

                boolean isConstructor = methodName.equals("<init>") && currentMethodName.equals(simpleClassName);
                if (currentMethodName.equals(methodName) || isConstructor) {
                    int startLine = cu.getLineNumber(node.getStartPosition());
                    int endLine = cu.getLineNumber(node.getStartPosition() + node.getLength());
                    if (startLine <= lineNumber.get(0) && lineNumber.get(0) <= endLine) {
                        Javadoc javadoc = node.getJavadoc();
                        if (javadoc != null) {
                            javadocCode.append("className: " + currentClassName + ", methodName: "+ methodName + "\n" + javadoc.toString()).append("\n");

                        } else {
                            javadocCode.append("No Javadoc found.\n");
                        }
                    }
                }
                return super.visit(node);
            }
        });

        return javadocCode.toString();
    }


    private static String getSimpleClassName(String className) {
        int dollarIndex = className.lastIndexOf('$');
        return (dollarIndex != -1) ? className.substring(dollarIndex + 1) : className;
    }



    public static String readStackTraces(ClassLoader classLoader, String path) throws IOException {
        URL resource = classLoader.getResource(path); //defect4J에서 Stack Traces 저장 파일 없어서 파일 없을때 예외처리
        if (resource != null) {
            File rootDir = new File(resource.getFile());
            String absoluteStackTracesRootPath = rootDir.getAbsolutePath();
            String stackTraces = new String(Files.readAllBytes(Paths.get(absoluteStackTracesRootPath)));
            return stackTraces;
        } else {
            return "Stack Traces Path Wrong";
        }

    }


    public static boolean isContainTest(String str) {
        return str.contains("Test");
    }

    public static String fuseTemplateAssemlber(String templatePath, ClassLoader classLoader, List<StringBuilder> list) throws IOException {
        File rootDir = new File(classLoader.getResource(templatePath).getFile());
        String absoluteTemplatePath = rootDir.getAbsolutePath();
        String template = new String(Files.readAllBytes(Paths.get(absoluteTemplatePath)));
        return String.format(template,
                list.get(0).toString(),
                list.get(1).toString(),
                list.get(3).toString(),
                list.get(2).toString(),
                list.get(4).toString());
    }

    public static String prompt1Assemlber(String templatePath, ClassLoader classLoader, String faultyCode) throws IOException {
        File rootDir = new File(classLoader.getResource(templatePath).getFile());
        String absoluteTemplatePath = rootDir.getAbsolutePath();
        String template = new String(Files.readAllBytes(Paths.get(absoluteTemplatePath)));
        return String.format(template, faultyCode);

    }

    public static String prompt2Assemlber(String templatePath, ClassLoader classLoader, String errorMsg, String testCode) throws IOException {
        File rootDir = new File(classLoader.getResource(templatePath).getFile());
        String absoluteTemplatePath = rootDir.getAbsolutePath();
        String template = new String(Files.readAllBytes(Paths.get(absoluteTemplatePath)));
        return String.format(template, errorMsg, testCode);
    }
}