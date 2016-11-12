package com.seat.sw_maestro.seat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class SettingActivity extends PreferenceActivity {

    private static final String TAG = "SettingActivity";
    private static final String testAppName = "kr.wonjun.somatest";
    private Messenger mRemote;  // 블루투스 서비스로부터 받아오는 메시지. 실시간 자세를 받아오기 위해서

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.setting);
        Log.d(TAG, "SettingActivity");

        Toast.makeText(getApplicationContext(), "일부 설정은 앱을 재시작하면 적용됩니다.", Toast.LENGTH_LONG).show();

        // 그래프 표시 관련
        ListPreference listPreference = (ListPreference) findPreference("prefGraphList");
        setOnPreferenceChange(listPreference);  // 값이 바뀌면.. 리스너 등록

        // 테스트 앱 실행 관련
        Preference openTestApp = (Preference) findPreference("prefOpenTestApp");
        openTestApp.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(testAppName);
                if (launchIntent != null) {
                    startActivity(launchIntent);//null pointer check in case package name was not found

                    // 테스트 앱을 실행시키고, 서비스에게는 서비스를 중단하라고 메시지를 보낸다.
                    Intent serviceIntent = new Intent(getApplicationContext(), BluetoothService.class);
                    getApplication().bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);


                }else{
                    Toast.makeText(getApplicationContext(), "테스트 앱이 존재하지 않습니다.", Toast.LENGTH_LONG).show();
                }
                return false;
            }
        });
    }

    // 요거는 리스너고, 바뀔때마다 프리퍼런스 키와 바뀐 값을 표시
    private Preference.OnPreferenceChangeListener onPreferenceChangeListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            //Log.d(TAG, "preference key : " + preference.getKey());
            //Log.d(TAG, "value : " + newValue);

            // 환경설정 정보를 저장하기 위한 sharedPreferences
            SharedPreferences prefs = getSharedPreferences("SettingStatus", MODE_PRIVATE);  // UserStatus 아닌 것 주의!!
            SharedPreferences.Editor editor = prefs.edit();

            editor.putString(preference.getKey(), newValue.toString()); // 바뀐 키와 값을 쉐어드 프리퍼런스에 저장.
            editor.commit();

            return true;
        }
    };

    // 이거는 위에서 만든 리스너를 등록해주는 것. 인자로 어떤 프리퍼런스인지만 넣어주면 된다.
    private void setOnPreferenceChange(Preference mPreference) {
        mPreference.setOnPreferenceChangeListener(onPreferenceChangeListener);

        onPreferenceChangeListener.onPreferenceChange(mPreference,
                PreferenceManager.getDefaultSharedPreferences(mPreference.getContext()).getString(mPreference.getKey(), ""));
    }

    /*
    이 아래 부분은 테스트 앱을 정상적으로 실행하기 위한 부분입니다.
    서비스에서 방석과 블루투스를 백그라운드로 연결하고 있기 때문에 테스트 앱에서 중복으로 블루투스를 사용할 수 없습니다.
    따라서 테스트 앱을 실행시키는 버튼을 누른다면 서비스를 중지하는 부분을 위해 만들었습니다.
    서비스에서 액티비티로부터 오는 리스너를 참고하시면 됩니다. 테스트 앱을 실행시켰다는 것의 what 값은 '4' 입니다.
     */
    private ServiceConnection mConnection = new ServiceConnection() {   // 서비스와 핸들러를 연결해주는 부분
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mRemote = new Messenger(service);   // service 하고 연결될때

            if (mRemote != null) {  // Activity handler를 service에 전달하기
                Message msg = new Message();    // 새로운 메시지를 만들고
                msg.what = 4;   // 서비스한테 종료하라고 메시지
                msg.obj = new Messenger(new RemoteHandler());   // 액티비티의 핸들러를 전달한다.
                try {
                    mRemote.send(msg);  // 전달
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mRemote = null; // service 하고 연결이 끊길때
        }
    };

    private class RemoteHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }
}