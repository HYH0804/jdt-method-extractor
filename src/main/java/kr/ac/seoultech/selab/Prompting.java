package kr.ac.seoultech.selab;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class Prompting {
    Properties properties = new Properties();

    public Prompting() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("key.properties")) {
            // load a properties file from class path, inside static method
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getKey(String key) {
        return properties.getProperty(key);
    }

    public String getURL(){
        return properties.getProperty("api_url");
    }

    public String callAPI(String prompt){
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        String apiKey = getKey("hyun_api_key");
        String apiUrl = getURL();

        JsonObject requestBody = new JsonObject();
        // Set the model
        requestBody.addProperty("model", "gpt-4-turbo");

        // Create the messages array
        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);

        requestBody.add("messages", messages);
        requestBody.addProperty("max_tokens", 4096); // 프롬프트 토큰 최댓값


        String jsonBody = requestBody.toString();

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String responseBody = response.body().string();
            JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();

            JsonArray choices = responseJson.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                JsonObject messageObject = firstChoice.getAsJsonObject("message");
                return messageObject.get("content").getAsString();
            } else {
                throw new IOException("No text found in the response");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void updateCsvWithQuestionOrAnswer(String filePath, String value, int rowIndex, String columnName) {
        try (
                Reader reader = Files.newBufferedReader(Paths.get(filePath));
                CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader())
        ) {
            List<CSVRecord> records = csvParser.getRecords();
            List<String[]> updatedRecords = new ArrayList<>();
            int columnIndex = csvParser.getHeaderMap().get(columnName);

            for (int i = 0; i < records.size(); i++) {
                CSVRecord record = records.get(i);
                String[] newRow = new String[record.size()];

                for (int j = 0; j < record.size(); j++) {
                    newRow[j] = record.get(j);
                }

                if (i == rowIndex) {
                    newRow[columnIndex] = value;
                }

                updatedRecords.add(newRow);
            }

            try (
                    BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath));
                    CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(csvParser.getHeaderMap().keySet().toArray(new String[0])))
            ) {
                for (String[] record : updatedRecords) {
                    csvPrinter.printRecord((Object[]) record);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void printConsole(String str){
        System.out.println("====================API 호출 결과=====================");
        System.out.println(str);
    }
}
