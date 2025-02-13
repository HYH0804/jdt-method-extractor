# 주의사항 (2024년 9월 이후)
해당 코드는 dataSet이 학과 교수님에게 종속되어 있기에 해당 dataSet 없이는 재현이 불가능합니다.
데이터셋에 쓰였던 Defects4j와 Apache 라이브러리의 버그 일부분으로 재현하였습니다.


해당 버그들을 기준으로 FuseFL의 프롬프트 엔지니어링에 대한 자세한 프롬프트 작성 리스트와 버그 리스트는 아래와 같습니다.

Apache NPE without Doc

https://docs.google.com/spreadsheets/d/1I8zgQZGNfzMPWr2KOR9mdYy5YOUQUqyJqVYSzDWpHx0/edit?gid=693887254#gid=693887254

Defects4J without Doc

https://docs.google.com/spreadsheets/d/1M5chrpiTGADJ-UcDQhX7RS89KE0QbZiOFY6lkFPJk68/edit?gid=2061759473#gid=2061759473

Apache NPE with Doc

https://docs.google.com/spreadsheets/d/1Tckxr_n1vZxgBgzIGSBqd3EyKu1VDwJpvnkb82E2k9I/edit?gid=871552259#gid=871552259

Defects4J with Doc

https://docs.google.com/spreadsheets/d/13BzSnbLt9M3qf6tlBnOXf6AuWRTI8Wsl4RhXw08N6F8/edit?gid=1296486114#gid=1296486114

## 종합 결과

![image](https://github.com/user-attachments/assets/17e27a7c-47e3-4951-9b19-a9dd7c2ca97a)

기존의 FuseFL 논문에서 더 나아가서 버그가 나는 지점에서 추가로 주석도 포함하여 프롬프트를 구성하였습니다.

-----------------------------------------------------------------------------------------------------------



# jdt-method-extractor

## 참고 
Eclipse JDT (Java Development Tools) 를 이용하여 런타임때 Abstract Syntax Tree(AST)에서 각각을 파싱하여 프롬프트 구성 자동화를 만들었습니다.
FuseFL 기반의 자동화 프로그램입니다.(https://arxiv.org/abs/2403.10507)

## 논문 리뷰 발표자료

[lab_paperReview1.pptx](https://github.com/user-attachments/files/18783662/lab_paperReview1.pptx)

[lab_paperReview2.pptx](https://github.com/user-attachments/files/18783661/lab_paperReview2.pptx)

## 실행방법

제가 뽑아낸 결과들은  fusefl/result 디렉토리 안에, 코드를 돌려보기 위해서 필요한 resources들은 fuse/flresources 디렉토리 안에 구조 맞춰서 넣어놨습니다.

github: https://github.com/HYH0804/jdt-method-extractor

main branch : hyun

# Warning

현재 각각의 gpt 응답 json 구조 안에 이스케이프 \ 가 있다면 제대로 이스케이프가 되지 않아 DoMatched를 실행할때 파싱에러가 발생합니다. 따라서 JDTMethodExtractor로 gpt 응답을 뽑고 난 후 각 디렉토리에서 gpt 응답 csv의 이스케이프 부분을 모두 지우시고 DoMatched를 돌려야합니다. 
DoMatched에서 devFixed_new.csv로 gpt와 비교할때 버그 이름으로 답안 json과 gpt 의 답 행을 매핑하는 것이 아닌 단순 순서로 비교하고 있어서 두 csv파일의 행 순서를 동일한 버그가 될 수 있게 잘 맞춰야합니다.

# How to Start
gitHub에서 코드 클론 이후 ide로 열고
서버 FuseFL/resources 디렉토리 하위파일들을 모두 프로젝트 resources 디렉토리 아래에 넣으시면 됩니다.
jar가 아닌 ide 내에서 JDTMethodExtractor 클래스와 DoMatched 클래스의 main메서드를 각각 실행시켜 주세요

JDTMethodExtractor 수행 이후 작성된 csv 파일들 내부에 이스케이프 관련 \ 을 수동으로 지워주셔야 DoMatched에서 json을 파싱할때 정상적으로 동작합니다.

추가 확장시에는 아래의 절차를 따릅니다.
1) devFixed_new.csv에 추가할려는 버그의 답을 json 구조로 만들어야합니다.
```
{"devFixed": [{"className": "CategoryPlot", "faultyLine": [2166, 2448]}, {"className": "XYPlot", "faultyLine": [2293, 2529]}]}
```
2) PathAssembler 클래스에서 추가할려는 버그에 따라 알맞는 위치에 Apache , Defects4j 리스트에 추가해줍니다.
3) DoMatched의 main 메서드 실행으로 Matched 여부를 판단할때는 각 버그의 행 순서가 매우 중요하므로 순서를 맞춰주셔야 합니다. 


# Explanation
jar가 아닌 ide 내에서 JDTMethodExtractor 클래스와 DoMatched 클래스의 main메서드를 각각 실행시켜 주세요
2개의 클래스 모두 main 메서드로 따로 실행시키셔야 합니다.

 key.properties의 hyun_api_key 값으로 api key 주시면 됩니다. 


JDTMethodExtractor는 npe.traces.json을 기준으로 isTarget==true를 기준으로  FuseFL 프롬프트를 만들고 각각의 프롬프트에 대하여 gpt 응답을 받아옵니다.
DoMatched는 JDTMethodExtractor를 통하여 얻은 gpt 응답 json을 기준으로 파싱하여 정답과 비교하여 Matched 여부를 판단합니다.

서버 디렉토리 상으로는 APACHE 버그들은 같은 lang 안에 lang_npe_1 ~ lang_npe_14까지 , collections도 마찬가지로 디렉토리 아래에 세부 버그들인 collections-io_npe_1~collections-io_npe_19까지 있는 형태로 존재하지만
프로젝트 resources 디렉토리 안에 각 버그들이 개별로 따로 존재해야합니다.
Apache의 lang 같은 경우 lang 디렉토리 전체를 두는 것이 아닌 lang 디렉토리 안의 모든 버그들을 각각 분리하여 프로젝트의 resources에 넣어야합니다. 나머지 버그들도 마찬가지입니다.
Defects4j 버그들은 서버의 subjects/defects4j 경로에서 개별 버그들이 각각 큰 디렉토리로 묶여있지않고 이미 나뉘어져있기에 그대로 프로젝트 resources 하위에 넣으시면 됩니다.

Matched 여부는 ```resources/defects4j_devFixed_new.csv``` , ```resources/googleSheet_devFixed_new.csv``` 의 json 형식으로 된 버그의 답을 보고 판단하게 됩니다.

마지막으로 Defects4j와 Apache 버그들을 따로 뽑고있어서 소스코드 내부에 하드코딩 된 부분들을 바꿔 데이터셋을 바꿔줘야합니다.
JDTMethodExtractor에서는 27,28,32 라인을 바꿔주셔야 합니다.

 27,28번째 라인에서 csvFilePath 경로를 각 버그 실행때에 맞게 주석처리 해주시고 
32번째 라인에서 pathAssembler.defects4j , pathAssembler.apache 도 각 버그에 맞게 바꿔주세요. 

마찬가지로 DoMatched의 main메서드 실행때에도 17,18,19번째 메서드의 파라미터를 BugType.APACHE 혹은 BugType.DEFECTS4J 로 바꿔주셔야 합니다.



# Project Directory Structure

 resources 하위의 NPE_try 가  gpt-4o 의 모델에 대한 응답 디렉토리입니다.
 defects4j_try 도 마찬가지입니다.

각각의 gpt 응답과 결과는 
NPE_try/Doc 혹은 defects4j_try/Doc 내부에 저장됩니다. 
이러한 Doc 디렉토리 내부에는
```{버그이름} with Doc - TryN .csv``` 와
```Matched_tryN.csv``` 로 각 회차(N)에 맞게 들어갑니다.

기존 제가 뽑아낸 것들은 ```fusefl/result/NPE_try/Doc``` , ```fusefl/result/defects4j_try/Doc``` 에 위치해있습니다.


