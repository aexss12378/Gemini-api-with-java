package com.example.exam_system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.JSONArray;
import org.json.JSONObject;

public class GoogleApiExample {
    public static void main(String[] args) {
        String pdfPath = "C:\\Users\\user\\OneDrive - mail.nuk.edu.tw\\桌面\\2.pdf"; // 替換為您的 PDF 文件路徑
        String displayName = "TEXT"; // 可替換為文件顯示名稱
        String baseUrl = "https://generativelanguage.googleapis.com/v1beta/models";
        String modelName = "gemini-1.5-flash";
        String apiPath = ":generateContent";
        String apiKey = "";      // 替換為您的 Google API 金鑰

        try {
            // 計算文件大小
            Path pdfFile = Paths.get(pdfPath);
            long numBytes = Files.size(pdfFile);

            // 創建一個HttpClient實例
            HttpClient client = HttpClient.newHttpClient();
            // 構建HTTP POST請求
            HttpRequest initRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/upload/v1beta/files?key=" + apiKey))
                    .header("X-Goog-Upload-Protocol", "resumable")
                    .header("X-Goog-Upload-Command", "start")
                    .header("X-Goog-Upload-Header-Content-Length", String.valueOf(numBytes))
                    .header("X-Goog-Upload-Header-Content-Type", "application/pdf")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"file\": {\"display_name\": \"" + displayName + "\"}}"))
                    .build();
            
            // 發送initRequest並接收伺服器回應，將結果以字串存儲在initResponse中        
            HttpResponse<String> initResponse = client.send(initRequest, HttpResponse.BodyHandlers.ofString());
            if (initResponse.statusCode() != 200) {
                throw new RuntimeException("Failed to initialize upload: " + initResponse.body());
            }

            // 從伺服器回應中獲取上傳URL，伺服器說OK(回傳這個header)才可以開始上傳文件
            String uploadUrl = initResponse.headers().firstValue("X-Goog-Upload-URL")
                    .orElseThrow(() -> new RuntimeException("Upload URL not found"));

            // 上傳文件 (去掉 Content-Length 標頭)
            HttpRequest uploadRequest = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("X-Goog-Upload-Offset", "0")
                .header("X-Goog-Upload-Command", "upload, finalize")
                .POST(HttpRequest.BodyPublishers.ofFile(pdfFile)) // 直接使用 BodyPublishers.ofFile
                .build();

            HttpResponse<String> uploadResponse = client.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
            if (uploadResponse.statusCode() != 200) {
            throw new RuntimeException("Failed to upload file: " + uploadResponse.body());
            }

            String fileUri = new JSONObject(uploadResponse.body())
                    .getJSONObject("file")
                    .getString("uri");

            // 發送生成內容請求
            String completeUrl = baseUrl + "/" + modelName + apiPath + "?key=" + apiKey;
            HttpRequest contentRequest = HttpRequest.newBuilder()
                    .uri(URI.create(completeUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"contents\": [{\"parts\": [" +
                                    "{\"text\": \"Help me to split the question of this pdf file. Give me every question and its choices&index,and can you return it using this JSON schema:\"}, " +
                                    "{\"file_data\": {\"mime_type\": \"application/pdf\", \"file_uri\": \"" + fileUri + "\"}}" +
                                    "]}]}"))
                    .build();

            HttpResponse<String> contentResponse = client.send(contentRequest, HttpResponse.BodyHandlers.ofString());
            if (contentResponse.statusCode() != 200) {
                throw new RuntimeException("Failed to generate content: " + contentResponse.body());
            }

            // 處理回應
            JSONObject responseJson = new JSONObject(contentResponse.body());
            JSONArray candidates = responseJson.getJSONArray("candidates");
            for (int i = 0; i < candidates.length(); i++) {
                JSONObject candidate = candidates.getJSONObject(i);
                JSONArray parts = candidate.getJSONObject("content").getJSONArray("parts");
                for (int j = 0; j < parts.length(); j++) {
                    System.out.println(parts.getJSONObject(j).getString("text"));
                }
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } catch (RuntimeException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
