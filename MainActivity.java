package com.example.yourapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.Manifest; // 권한 관련 클래스 임포트
import android.content.pm.PackageManager; // 권한 체크를 위한 클래스 임포트

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public class MainActivity extends AppCompatActivity {
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    // 권한 요청 코드 정의
    private static final int PERMISSION_REQUEST_CODE = 100;
    private Handler handler;
    private TextView textView;
    private String siteUrl = "https://kwangju.pythonanywhere.com/"; // 서버 URL
    private JSONObject postJson;
    private String imageUrl = null;
    private Bitmap bmImg = null;
    private CloadImage taskDownload;
    private static final int PICK_IMAGE_REQUEST = 1;  // 이미지 선택 요청 코드
    private ImageView imageView;
    private Uri selectedImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 권한 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 권한이 승인되었으면 이미지 선택 가능
                imageView = findViewById(R.id.imageView);
                textView = findViewById(R.id.textView);
                onClickUpload(imageView);
            } else {
                Toast.makeText(this, "권한이 거부되었습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void onClickDownload(View v) {

        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }
        taskDownload = new CloadImage();
        taskDownload.execute(siteUrl + "/api_root/Post/");
        Toast.makeText(getApplicationContext(), "Download", Toast.LENGTH_LONG).show();

    }

    public void onClickUpload(View v) {
        // 이미지 선택을 위한 Intent
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    // 이미지를 선택하고 나서 호출되는 메서드
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri selectedImage = data.getData();
            // 이미지 선택 후 서버로 업로드하는 작업 수행
            new PutPost().execute(selectedImage);
        }
    }



    private class PutPost extends AsyncTask<Uri, Void, String> {

        @Override
        protected String doInBackground(Uri... params) {
            Uri imageUri = params[0];
            HttpURLConnection connection = null;
            DataOutputStream outputStream = null;
            InputStream inputStream = null;

            String lineEnd = "\r\n";
            String twoHyphens = "--";
            String boundary = "*****";
            String result = "Error";

            try {
                // 선택된 이미지 파일을 File로 변환
                String filePath = getPathFromURI(imageUri);
                File file = new File(filePath);

                // 서버 URL 설정 (Django 서버의 업로드 URL)
                URL url = new URL("https://kwangju.pythonanywhere.com/upload/"); // 서버 URL을 입력하세요.
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setUseCaches(false);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Connection", "Keep-Alive");
                connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

                outputStream = new DataOutputStream(connection.getOutputStream());

                // 파일 추가
                outputStream.writeBytes(twoHyphens + boundary + lineEnd);
                outputStream.writeBytes("Content-Disposition: form-data; name=\"file\";filename=\"" + file.getName() + "\"" + lineEnd);
                outputStream.writeBytes(lineEnd);

                FileInputStream fileInputStream = new FileInputStream(file);
                int bytesAvailable = fileInputStream.available();
                int maxBufferSize = 1024;
                int bufferSize = Math.min(bytesAvailable, maxBufferSize);
                byte[] buffer = new byte[bufferSize];
                int bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                while (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead);
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                }

                outputStream.writeBytes(lineEnd);
                outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                // 서버 응답 읽기
                inputStream = connection.getInputStream();
                int responseCode = connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    result = "Upload successful";
                } else {
                    result = "Upload failed";
                }

                fileInputStream.close();
                outputStream.flush();
                outputStream.close();

            } catch (Exception e) {
                e.printStackTrace();
                result = "Error: " + e.getMessage();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            // 업로드 완료 후 결과 처리
            Toast.makeText(getApplicationContext(), result, Toast.LENGTH_SHORT).show();
        }

        // URI를 실제 파일 경로로 변환하는 메소드
        private String getPathFromURI(Uri uri) {
            String[] projection = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(projection[0]);
            return cursor.getString(columnIndex);
        }
    }

    private class CloadImage extends AsyncTask<String, Integer, List<Bitmap>> {

        @Override
        protected List<Bitmap> doInBackground(String... urls) {
            List<Bitmap> bitmapList = new ArrayList<>();
            HttpURLConnection conn = null;
            InputStream is = null;

            try {
                // API URL
                String apiUrl = urls[0];  // 첫 번째 URL 파라미터
                String token = "bf46b8f9337d1d27b4ef2511514c798be1a954b8"; // 인증 토큰

                // API 요청
                URL urlAPI = new URL(apiUrl);
                conn = (HttpURLConnection) urlAPI.openConnection();
                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);  // 연결 타임아웃
                conn.setReadTimeout(3000);  // 읽기 타임아웃

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // 응답 데이터를 읽어오기
                    is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }

                    // JSON 파싱
                    String strJson = result.toString();
                    JSONArray aryJson = new JSONArray(strJson);

                    // JSON 배열에서 이미지 URL을 하나씩 추출
                    for (int i = 0; i < aryJson.length(); i++) {
                        JSONObject postJson = aryJson.getJSONObject(i);
                        String imageUrl = postJson.getString("image");  // 이미지 URL 추출

                        if (!imageUrl.isEmpty()) {
                            // 이미지 URL에서 Bitmap 다운로드
                            HttpURLConnection imageConn = null;
                            InputStream imgStream = null;
                            try {
                                URL myImageUrl = new URL(imageUrl);
                                imageConn = (HttpURLConnection) myImageUrl.openConnection();
                                imgStream = imageConn.getInputStream();
                                Bitmap imageBitmap = BitmapFactory.decodeStream(imgStream);
                                bitmapList.add(imageBitmap);  // Bitmap을 리스트에 추가
                            } catch (IOException e) {
                                e.printStackTrace();  // 이미지 다운로드 오류 처리
                            } finally {
                                if (imgStream != null) {
                                    imgStream.close();  // InputStream 닫기
                                }
                                if (imageConn != null) {
                                    imageConn.disconnect();  // 연결 해제
                                }
                            }
                        }
                    }
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();  // 오류 처리
            } finally {
                if (is != null) {
                    try {
                        is.close();  // InputStream 닫기
                    } catch (IOException e) {
                        e.printStackTrace();  // 오류 처리
                    }
                }
                if (conn != null) {
                    conn.disconnect();  // 연결 해제
                }
            }
            return bitmapList;  // 이미지 리스트 반환
        }

        @Override
        protected void onPostExecute(List<Bitmap> images) {
            // TextView가 null이 아닌지 확인 (null 방지)
            if (textView != null) {
                // 이미지 리스트가 비어있을 경우
                if (images.isEmpty()) {
                    textView.setText("불러올 이미지를 찾을 수 없습니다.");
                } else {
                    textView.setText("이미지 로드 성공!");
                }
            }

            // RecyclerView가 null이 아닌지 확인
            RecyclerView recyclerView = findViewById(R.id.recyclerView);
            if (recyclerView != null) {
                // ImageAdapter를 사용해 이미지를 RecyclerView에 표시
                ImageAdapter adapter = new ImageAdapter(images);
                recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));  // 수직 스크롤 레이아웃 매니저
                recyclerView.setAdapter(adapter);  // 어댑터 설정
            }
        }
    }
}