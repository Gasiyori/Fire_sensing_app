package com.example.firesensing;

// 라이브러리 import
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

// 메인 동작, 앱 실행 시 시작되는 코드 부분
public class MainActivity extends Activity {
    private WebView mWebView; //mWebView 인스턴스 생성
    private TextView textView1; //현재 URL을 출력할 TextView객체 생성 1
    private TextView textView2; //현재 URL을 출력할 TextView객체 생성 2

    // twilio의 SID, TOKEN 입력
    public static final String ACCOUNT_SID = "Your real SID";
    public static final String AUTH_TOKEN = "Your real TOKEN";

    // 앱 실행 시 생성되는 객체들
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 서버에서 온도/상태 데이터를 갖고오기 위한 서브 스레드 생성
        backThread1 thread1 = new backThread1();
        thread1.setDaemon(true); // 메인 앱 종료 시 서브 스레드도 동시에 종료하도록 설정
        thread1.start();

        backThread2 thread2 = new backThread2();
        thread2.setDaemon(true);
        thread2.start();

        // SID, 토큰을 통한 설정 초기화
        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);

        // 레이아웃에 선언된 Webview에 대한 인스턴스 전달
        mWebView = (WebView) findViewById(R.id.wv_motion);
        // 웹뷰에서 자바스크립트실행가능
        mWebView.getSettings().setJavaScriptEnabled(true);

        // IP 주소 및 포트 번호를 저장하고 웹뷰의 Url로 지정
        final EditText etEdit = new EditText(this);
        AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
        dialog.setTitle("주소 및 포트 번호 입력");
        dialog.setMessage("IP 주소와 포트 번호를 입력해주세요. \n ex) 192.168.0.100:8081");
        dialog.setView(etEdit);
        // OK 버튼 이벤트
        dialog.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String inputValue = etEdit.getText().toString();
                Toast.makeText(MainActivity.this, inputValue, Toast.LENGTH_SHORT).show();
                mWebView.loadUrl(inputValue);
            }
        });
        // Cancel 버튼 이벤트
        dialog.setNegativeButton("Cancel",new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        dialog.show();

        // WebViewClient 지정
        mWebView.setWebViewClient(new WebViewClientClass());
    }

    // 버튼 클릭 시 실행되는 함수
    public void msg_send(View v){
        // 전송 알림
        Toast.makeText(getApplicationContext(), "메시지 전송됨", Toast.LENGTH_LONG).show();
        // 함수 실행 시 msg 함수를 스레드로 실행
        send_msg msg = new send_msg();
        msg.setDaemon(true);
        msg.start();
    }

    // 화재 상태 서버로부터 갖고오는 동작
    class backThread1 extends Thread{
        public void run() {
            String resultText = "[NULL]"; // 초기 문자열은 공백
            while(true) {
                // activity_main에 존재하는 상태 텍스트뷰와 연동
                textView1 = (TextView)findViewById(R.id.temp_1);
                try { // 서버에서 값을 받아오기 위한 HTTP 요청
                    OkHttpClient client = new OkHttpClient().newBuilder()
                            .build();
                    Request request = new Request.Builder()
                            .url("http://203.253.128.177:7579/Mobius/Fire_sensing/Temp/la")
                            .method("GET", null)
                            .addHeader("Accept", "application/json")
                            .addHeader("X-M2M-RI", "12345")
                            .addHeader("X-M2M-Origin", "SOrigin")
                            .build();
                    Response response = client.newCall(request).execute();

                    // 응답으로 받아온 값을 임시 저장, 문자열에서 온도값만 추출하여 저장
                    String temp = response.body().string();
                    String temp2[] = temp.split("\"");
                    resultText = temp2[39] + "°C";
                } catch (Exception e) { // 에러 발생시 출력
                    System.err.println(e.toString());
                }
                textView1.setText(resultText); // 받아온 온도값으로 텍스트뷰 값 변경
            }
        }
    }

    // 위와 동일한 방식의 동작
    class backThread2 extends Thread{
        public void run() {
            String resultText = "[NULL]";
            while(true) {
                textView2 = (TextView)findViewById(R.id.stat_1);
                try {
                    OkHttpClient client = new OkHttpClient().newBuilder()
                            .build();
                    Request request = new Request.Builder()
                            .url("http://203.253.128.177:7579/Mobius/Fire_sensing/Fire/la")
                            .method("GET", null)
                            .addHeader("Accept", "application/json")
                            .addHeader("X-M2M-RI", "12345")
                            .addHeader("X-M2M-Origin", "SOrigin")
                            .build();
                    Response response = client.newCall(request).execute();
                    String temp = response.body().string();
                    String temp2[] = temp.split("\"");
                    resultText = temp2[39];
                } catch (Exception e) {
                    System.err.println(e.toString());
                }
                textView2.setText(resultText);
            }
        }
    }

    // 버튼 누르면 실행되는 스레드
    // 근본적인 부분은 위와 비슷한 형태
    class send_msg extends Thread {
        public void run() {
            try {
                OkHttpClient client = new OkHttpClient().newBuilder() // HTTP 클라이언트 객체 생성
                        .build();
                MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded"); //x-www-form-urlencoded 형식으로 데이터 전송
                // To에는 수신자 번호, From에는 Twilio에서 생성한 가상 번호, Body에는 메시지를 넣으면 됨
                // <---->까지 내용 바꾸면 됨
                RequestBody body = RequestBody.create(mediaType, "To=+<--receiver number-->&From=+<--virtual number-->&Body=화재가 감지되었습니다!");
                // 실제 요청 부분. url과 방식 등을 지정
                // Account SID부분 수정 필요
                Request request = new Request.Builder()
                        .url("https://api.twilio.com/2010-04-01/Accounts/<--Account SID-->/Messages")
                        .method("POST", body)
                        .addHeader("Authorization", "Basic QUM1MDdlYWRmNDY0ZTg5ODQ4ZmI0ZDcxZjk4NTAwMmM2NTpkZDdiNjVlYTE0N2I4YzIxYmYzNjRlM2FhZGRmOWQ2MA==")
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .build();
                Response response = client.newCall(request).execute(); // 실행
            } catch (Exception e) {
                System.err.println(e.toString());
            }
        }
    }

    // 위에서 설정된대로 웹뷰(스트리밍) 실행
    private class WebViewClientClass extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }
    }
}
