package com.seat.sw_maestro.seat;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    public static Activity LoginActivity;

    EditText editTextID;
    EditText editTextPassword;
    Button buttonLogin;
    String result;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        LoginActivity = this;

        Log.d(TAG, "LoginActivity");

        editTextID = (EditText)findViewById(R.id.editTextID);
        editTextPassword = (EditText)findViewById(R.id.editTextPassword);
        buttonLogin = (Button) findViewById(R.id.buttonLogin);

        buttonLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "로그인 버튼이 눌림");

                String id;
                String password;

                id = editTextID.getText().toString();
                password = editTextPassword.getText().toString();

                Log.d(TAG, "입력한 아이디 : " + id);
                Log.d(TAG, "입력한 패스워드 : " + password);


                // 나중에 서버가 종료되었을 때 서버 없이도 데모를 보여줄 수 있도록 만드는 admin 계정이다.
                if(id.equals("admin") && password.equals("admin")){
                    // 로그인 정보를 저장하기 위한 sharedPreferences
                    SharedPreferences prefs = getSharedPreferences("UserStatus", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();

                    editor.putString("isLoggedIn", "true"); // 로그인 상태 true로
                    editor.putString("UserNumber", "admin"); // UserNumber 세팅
                    editor.commit();

                    startActivity(new Intent(getApplicationContext(), Tutorial1Activity.class));  // 다음으로 이동
                    finish();
                }
                // admin 계정 관련 부분 끝

                String[] params = new String[1];
                params[0] = id;

                HTTPManager httpManager = new HTTPManager();
                try {
                    result = httpManager.useAPI(10, params);  // 특정 계정 조회를 하는 API를 불러온다.
                }catch (java.lang.NullPointerException e){
                    Log.e(TAG, "네트워크가 꺼져서 서버로 못 보냄, 혹은 서버가 꺼져있음.");
                    Toast.makeText(getApplicationContext(), "네트워크 상태를 확인해주세요. 혹은 서버 관리자에게 문의하세요.", Toast.LENGTH_LONG).show();
                    return ;
                }
                Log.d(TAG, "계정조회 결과 : " + result);   // 이미 입력한 아이디 있으면 계정정보 리턴, 없으면 -1

                if (result.equals("-1")) {   // 아이디 없음.
                    Toast.makeText(getApplicationContext(), "아이디 혹은 비밀번호를 확인해주세요.", Toast.LENGTH_LONG).show();
                } else {    // 그게 아니면 비밀번호 체크하겠지
                    try {
                        JSONArray jsonArray = new JSONArray(result);    // 서버로부터 json 배열 받고
                        JSONObject jsonObject = jsonArray.getJSONObject(0);   // 리턴은 하나니까 이거 하나만 오브젝트로..

                        String encoded_password = jsonObject.getString("Password");    // 값 중에서 패스워드 추출
                        Log.d(TAG, "서버로 부터 받은 비번 : " + encoded_password);
                        String decoded_password = new String(Base64.decode(encoded_password, Base64.DEFAULT)); // 복호화
                        Log.d(TAG, "복호화 : " + decoded_password);

                        if(password.equals(decoded_password)){  // 로그인 성공
                            //Toast.makeText(getApplicationContext(), "Success!", Toast.LENGTH_LONG).show();

                            // 로그인 정보를 저장하기 위한 sharedPreferences
                            SharedPreferences prefs = getSharedPreferences("UserStatus", MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();

                            editor.putString("isLoggedIn", "true"); // 로그인 상태 true로
                            editor.putString("UserNumber", jsonObject.getString("UserNumber")); // UserNumber 세팅
                            editor.commit();

                            startActivity(new Intent(getApplicationContext(), Tutorial1Activity.class));  // 다음으로 이동
                        }
                        else{   // 로그인 실패
                            Toast.makeText(getApplicationContext(), "아이디 혹은 비밀번호를 확인해주세요.", Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
}
