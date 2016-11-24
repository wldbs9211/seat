package com.seat.sw_maestro.seat;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.utils.ConnectionSharingAdapter;

import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.PublishSubject;

/*
    BluetoothService는 Background에서 동작하며, 방석과 블루투스 관련 동작 및 패킷이 들어왔을 때 처리를 합니다.
    또한 사용자가 어디 화면을 보고 있는지 파악하며(state 관리), 이에 따라 방석에게 어떤 패킷을 보낼지 결정합니다.

    1. 블루투스 관련 동작
        1.1 자동으로 장비와 연결시도 (Auto connect)
            1.1.1 방석의 Bluetooth 장비가 바뀐 경우에는 아래의 변수를 수정합니다.
                    private static final String macAddress
                    private static final UUID characteristicUUID
        1.2 방석에게 데이터 요청 전송 (Write)
        1.3 방석로부터 받은 패킷을 처리하기 위해 BluetoothPacket 클래스에게 넘겨줌 (Read)

    2. State 관리
        2.1 State list
            2.1.1 방석상태 모드 - 방석의 연결상태 및 배터리 잔량을 표시합니다.
            2.1.2 실시간 모드 - 실시간으로 방석에게 빠르게 현재 자세를 요청합니다.
            2.1.3 일반 모드 - 방석에게 쌓인 데이터를 요청합니다.
        2.2 State 변경시기
            2.2.1 방석상태 모드 - 요약(Tab1)화면을 보는 경우 Service는 방석상태 모드로 진입합니다.
            2.2.2 실시간 모드 - 실시간(Tab3)화면을 보는 경우 Service는 실시간 모드로 진입합니다.
            2.2.3 일반 모드 - 위의 두 가지 상황 외에는 방석은 모두 일반모드로 동작합니다.
        2.3 State별 동작
            2.3.1 방석상태 모드 - Tab1 Fragment에 주기적으로 Bluetooth 연결상태을 보내줍니다.
            2.3.2 실시간 모드 - 방석에게 실시간 패킷을 주기적으로 전송합니다.
                             이후 답장으로 패킷을 받는다면 그것을 BluetoothPacket 클래스에게 넘겨줍니다.
                             BluetoothPacket 클래스에서 디코딩된 패킷의 정보를 이용하여 각 자세별 Centroid 객체끼리의 거리를 구합니다.
                             가장 짧은 거리를 갖는 Centroid를 현재 자세라 판단하고, Tab3 Fragment에 자세를 보내줍니다.
            2.3.3 일반 모드 - 방석에게 일반 모드 패킷을 주기적으로 전송합니다.
                            이후 과정은 자세를 판단하기까지 과정은 실시간 모드와 동일하며, 일반 모드에서는 Tab3 Fragment에 자세를 보내는 대신
                            DatabaseManager 클래스에게 넘겨주어 로컬 database에 그 시간대의 정확도와 시간을 저장합니다.
            2.3.4 위의 주기적으로 동작하는 것은 아래에서 다음과 같은 변수의 값을 변경함으로써 바꿀 수 있습니다.
                    private static final int commonModeInterval - 일반모드 실행주기
                    private static final int realTimeModeInterval - 실시간모드 실행주기
                    private static final int tab1ModeInterval - 방석연결상태 실행주기

    3. AlarmReceiver 등록
        매 정각 실행되는 AlarmReceiver 를 등록시켜 서버로 데이터를 전송하고, 사용자에게 notification 을 보내줍니다.

    4. Bluetooth battery 초기화
        연결이 끊어진 경우에는 요약(Tab1)에 보이는 배터리의 잔량을 초기화시킵니다.
*/

public class BluetoothService extends Service {
    private static final String TAG = "BluetoothService";             // 디버깅용 TAG 입니다.

    // Bluetooth low energy와 관련된 내용입니다.
    RxBleDevice device;                                               // 연결할 블루투스 디바이스의 정보를 담습니다.
    RxBleClient rxBleClient;                                          // BLE 사용을 위한 변수입니다. connect 및 read, write 등
    private Observable<RxBleConnection> connectionObservable;         // 블루투스 디바이스를 공유하기 위한 변수입니다.
    private static final String macAddress = "20:CD:39:7B:FC:5F";     // 기기를 연결을 위한 macAddress 입니다.
    private static final UUID characteristicUUID =
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");  // 해당 BLE 장비에서 통신을 위해 사용하는 UUID

    // 블루투스 패킷을 decode하기 위한 클래스입니다.
    private BluetoothPacket bluetoothPacket;

    // 방석과 통신을 할 주기(ms 단위입니다. 예) 1초 = 1000)
    private static final int commonModeInterval = 180 * 1000;         // 일반모드 실행주기, 3분
    private static final int realTimeModeInterval = 2 * 1000;         // 실시간모드 실행주기, 2초
    private static final int tab1ModeInterval = 3 * 1000;             // 방석연결상태 실행주기, 3초

    // 자세별 코드
    private static final int standard = 0;                            // 정자세
    private static final int leanLeft = 1;                            // 왼쪽으로 쏠렸습니다.
    private static final int leanRight = 2;                           // 오른쪽으로 쏠렸습니다.
    private static final int front = 3;                               // 상체가 앞으로 쏠렸습니다.
    private static final int hipFront = 4;                            // 엉덩이를 앞으로 뺐습니다.
    private static final int crossRightLeg = 5;                       // 오른쪽 다리를 왼쪽으로 꼬았습니다.
    private static final int crossLeftLeg = 6;                        // 왼쪽 다리를 오른쪽으로 꼬았습니다.

    /*
        왼쪽 혹은 오른쪽으로 쏠린 자세인 경우에 다리가 꼬은건가 판별하기 위한 변수입니다.
        자세를 추측하는 과정에서 왼쪽으로 쏠린 경우에는 오른쪽 앞부분의 셀 크기를 비교합니다.
        그 후 오른쪽 앞 부분의 셀 크기가 아래의 threshold_right 값보다 작다면 다리를 꼬은 것으로 판정합니다.
        오른쪽으로 쏠린 경우에는 왼쪽 앞부분의 셀 크기를 threshold_left와 비교합니다.
     */
    private static final int threshold_left = 20;
    private static final int threshold_right = 20;

    // 최종 학습 후 자세별 군집의 중심좌표입니다.
    private static final Centroid centroid_standard = new Centroid(-0.4,-1.6);  // 정자세
    private static final Centroid centroid_leanLeft = new Centroid(-1.4,-0.8);      // 왼쪽으로 쏠렸습니다.
    private static final Centroid centroid_leanRight = new Centroid(1,0);    // 오른쪽으로 쏠렸습니다.
    private static final Centroid centroid_front = new Centroid(-0.7,-1);     // 앞으로 쏠렸습니다.
    private static final Centroid centroid_hipFront = new Centroid(0, 1);     // 엉덩이 앞으로 뺐습니다.

    // Service의 동작 상태를 변경하기 위한 부분입니다.
    private Messenger mRemote;                   // Service <-> Activity 간에 통신을 하기 위한 Messenger입니다.
    private Timer timer;                         // 주기적으로 작업을 실행하기 위한 Timer입니다.
    private int serviceState = 0;                // 서비스의 동작 상태를 저장하고 있는 변수입니다.
    private static final int STATE_COMMON = 0;   // 일반 모드
    private static final int STATE_TAB1 = 1;     // Tab1을 보는 상태 (방석상태 모드)
    private static final int STATE_TAB3 = 2;     // Tab3를 보는 상태 (실시간 모드)

    // 방석의 연결유무 상태입니다.
    private static final String BLUETOOTH_CONNECTED = "1";
    private static final String BLUETOOTH_NOT_CONNECTED = "0";

    // 생성자입니다.
    public BluetoothService(){

    }

    /*
        입력 : X
        리턴 : X
        동작 : 요약(Tab1)에서 보여줄 배터리의 잔량을 초기화합니다.
              SharedPreference를 통해서 Tab1과 공유하며, -1은 battery 잔량을 알 수 없다는 의미입니다.
    */
    private void resetBattery(){
        SharedPreferences prefs = getSharedPreferences("battery", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("battery", -1);
        editor.commit();
    }

    public void onStart(Intent intent, int startId) {
        Log.d(TAG, "서비스가 시작되었습니다.");
        super.onStart(intent, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "서비스가 bind 되었습니다.");
        return new Messenger(new RemoteHandler()).getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // TODO Auto-generated method stub
        Log.d(TAG,"서비스가 unbind 되었습니다.");
        return super.onUnbind(intent);
    }


    /* 메세지의 what 값은 Service <-> Activity 통신 간에 어디로 보냈는지 구분하기 위한 값입니다.
        Service <-> Tab1 간에 통신은  what 값 0을 사용 (방석상태 모드)
        입력 : String 형 data
        리턴 : X
        동작 : Tab1으로 입력한 String 형 data가 담긴 메시지를 전달합니다. data는 방석상태입니다.
    */
    private void remoteSendMessage_Tab1(String data) {
        if (mRemote != null) {
            Message msg = new Message();
            msg.what = 0;
            msg.obj = data;
            try {
                mRemote.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /* 메세지의 what 값은 Service <-> Activity 통신 간에 어디로 보냈는지 구분하기 위한 값입니다.
        Service <-> Tab3 간에 통신은  what 값 1을 사용 (실시간 모드)
        입력 : String 형 data
        리턴 : X
        동작 : Tab3으로 입력한 String 형 data가 담긴 메시지를 전달합니다. data는 자세의 결과입니다.
    */
    private void remoteSendMessage_Tab3(String data) {
        if (mRemote != null) {
            Message msg = new Message();
            msg.what = 1;
            msg.obj = data;
            try {
                mRemote.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }


    /*
        Handler 입니다. Activity -> Service 로 메시지가 오는 경우 동작을 어떻게 할지 결정합니다.
        Common(일반모드), Tab1(방석상태 모드), Tab3(실시간 모드) 별로 동작을 결정하는 TimerTask가 3개 존재합니다.
        이후 switch - case 문에서 Activity -> Service로 메시지가 오는 경우 상황에 맞게 TimerTask를 변경합니다.

        Activity -> Service 메신저가 오는 경우 상황
          what - 0 : 사용자가 Tab1(방석상태)를 보고 있습니다.
          what - 1 : 사용자가 Tab3(실시간)를 보고 있습니다.
          what - 2 : 사용자가 Tab1에서 벗어났습니다.
          what - 3 : 사용자가 Tab3에서 벗어났습니다.
          what - 4 : 사용자가 환경설정에서 개발자용 Test Application을 실행하였습니다.
     */
    private class RemoteHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {

            /*
                일반모드의 동작입니다.
                방석과 블루투스가 연결되어있다면 방석에게 일반모드 패킷을 전송합니다.
            */
            TimerTask timerTask_Common = new TimerTask() {
                public void run() {
                    Log.d(TAG, "일반모드 TimerTask가 동작합니다.");
                    if (isBluetoothConnected()) {
                        connectionObservable
                                .flatMap(rxBleConnection -> rxBleConnection.writeCharacteristic(characteristicUUID, bluetoothPacket.makeCommonModePacket()))
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(bytes -> {
                                    Log.d(TAG, "일반모드 패킷 전송을 성공하였습니다.");
                                }, throwable -> {
                                    Log.d(TAG, "일반모드 패킷 전송을 실패하였습니다. 이유 : " + throwable);
                                });
                    }
                }
            };

            /*
                방석상태모드의 동작입니다.
                Tab1으로 방석의 연결상태를 보내줍니다.
             */
            TimerTask timerTask_Tab1 = new TimerTask() {
                public void run() {
                    Log.d(TAG, "방석의 연결상태를 전송합니다.");
                    if (isBluetoothConnected()) {
                        remoteSendMessage_Tab1(BLUETOOTH_CONNECTED);
                    }else {
                        remoteSendMessage_Tab1(BLUETOOTH_NOT_CONNECTED);
                    }
                }
            };

            /*
                실시간모드의 동작입니다.
                방석과 블루투스가 연결되어있다면 방석에게 실시간모드 패킷을 전송합니다.
            */
            TimerTask timerTask_Tab3 = new TimerTask() {
                public void run() {
                    Log.d(TAG, "실시간모드 TimerTask가 동작합니다.");
                    if (isBluetoothConnected()) {
                        connectionObservable
                                .flatMap(rxBleConnection -> rxBleConnection.writeCharacteristic(characteristicUUID, bluetoothPacket.makeRealTimeModePacket()))
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(bytes -> {
                                    Log.d(TAG, "실시간모드 패킷 전송을 성공하였습니다.");
                                }, throwable -> {
                                    Log.d(TAG, "실시간모드 패킷 전송을 실패했습니다. 이유 : " + throwable);
                                });
                    }
                }
            };

            /*
                Activity -> Service 메신저가 도착한 경우의 처리입니다.
             */
            switch (msg.what) {

                /*
                    Tab1을 보는 경우입니다.
                    serviceState 를 변경하고, TimerTask로 방석상태 모드를 실행합니다.
                */
                case 0 :
                    Log.d(TAG, "서비스와 Tab1이 연결되었습니다.");
                    serviceState = STATE_TAB1;
                    mRemote = (Messenger) msg.obj;

                    if(timer != null){  // 기존 타이머를 중지합니다.
                        timer.cancel();
                        timer = null;
                    }

                    timer = new Timer();
                    timer.schedule(timerTask_Tab1, 1000, tab1ModeInterval);  // 방석상태 전용 TimerTask를 수행합니다.
                    break;

                 /*
                    Tab3을 보는 경우입니다.
                    serviceState 를 변경하고, TimerTask로 실시간 모드를 실행합니다.
                */
                case 1 :
                    Log.d(TAG, "서비스와 Tab3가 연결되었습니다.");
                    serviceState = STATE_TAB3;
                    mRemote = (Messenger) msg.obj;

                    if(timer != null){   // 기존 타이머를 중지합니다.
                        timer.cancel();
                        timer = null;
                    }

                    timer = new Timer();
                    timer.schedule(timerTask_Tab3, 1000, realTimeModeInterval); // 실시간 전용 TimerTask를 수행합니다.
                    break;

                /*
                    사용자가 Tab1에서 벗어났습니다.
                    TimerTask를 common mode로 변경합니다.
                */
                case 2 :
                    Log.d(TAG, "Tab1에서 종료 신호를 보냈습니다.");
                    if((timer != null) && (serviceState == STATE_TAB1)){   // TimerTask를 교체합니다.
                        timer.cancel();
                        timer = null;

                        timer = new Timer();
                        timer.schedule(timerTask_Common, 1000, commonModeInterval);   // 일반모드 실행
                        serviceState = STATE_COMMON;
                    }
                    break;

                /*
                    사용자가 Tab3에서 벗어났습니다.
                    TimerTask를 common mode로 변경합니다.
                */
                case 3 :    // Tab3가 화면에서 사라짐
                    Log.d(TAG, "Tab3에서 종료 신호를 보냈습니다.");
                    if((timer != null) && (serviceState == STATE_TAB3)){   // TimerTask를 교체합니다.
                        timer.cancel();
                        timer = null;

                        timer = new Timer();
                        timer.schedule(timerTask_Common, 1000, commonModeInterval);   // 일반모드 실행
                        serviceState = STATE_COMMON;
                    }
                    break;

                /*
                    설정에서 개발자 전용 Application 을 켰습니다.
                    블루투스 간섭을 막기 위해서 Service를 중지합니다.
                 */
                case 4:
                    Log.d(TAG, "개발자 Application이 실행됩니다.");
                    stopService();
                    break;

                default :
                    Log.d(TAG, "등록되지 않은 곳에서 메시지가 왔습니다.");
                    break;
            }
        }
    }

    /*
        입력 : X
        리턴 : X
        동작 : 개발자 Application이 실행될 때 실행하는 함수입니다.
              블루투스 연결 간섭을 막기 위해 이 Service를 완전히 종료합니다.
              블루투스 자동 연결, TimerTask, Activity를 종료합니다.
     */
    private void stopService(){
        // 블루투스 찾는 것 중지
        connectionObservable = null;

        // 타이머테스크 중지
        if(timer != null){
            timer.cancel();
            timer = null;
        }

        stopSelf(); // 이 이후에는 프로세스 종료되도 서비스가 다시 살아나지 않습니다.

        // 개발자 Application 실행 후에 자연스러움을 위해 기존에 켜져있던 Activity를 종료시킵니다.
        SettingActivity settingActivity = (SettingActivity)SettingActivity.SettingActivity;
        TabActivity tabActivity = (TabActivity) TabActivity.TabActivity;
        if(settingActivity != null) settingActivity.finish();
        if(tabActivity != null) tabActivity.finish();

        android.os.Process.killProcess(android.os.Process.myPid()); // 서비스 완전 종료
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "서비스가 시작되었습니다.");
        Log.d(TAG, "블루투스 연결할 mac adress : " + macAddress);

        // 블루투스 패킷을 decode 하기 위한 클래스
        bluetoothPacket = new BluetoothPacket();

        // BLE 관련입니다.
        rxBleClient = RxBleClient.create(getApplicationContext());
        device = rxBleClient.getBleDevice(macAddress);              // mac adress를 통해서 연결할 device를 지정합니다.
        PublishSubject<Void> disconnectTriggerSubject = PublishSubject.create();

        // device의 연결을 공유하는 connectionObservable. 이것을 활용해서 읽기, 쓰기를 공유합니다.
        connectionObservable = device
                .establishConnection(getApplicationContext(), true) // 오른쪽 boolean 인자는 자동연결 관련입니다.
                .observeOn(AndroidSchedulers.mainThread())
                .takeUntil(disconnectTriggerSubject)
                .compose(new ConnectionSharingAdapter());

        setBluetoothConnect();  // 블루투스 연결을 실제로 시작
        setBluetoothRead();     // 블루투스 읽기를 시작

        /*
                처음 서비스가 실행될 때에는 일반모드로 TimerTask를 동작시킵니다.
                일반모드의 동작입니다.
                방석과 블루투스가 연결되어있다면 방석에게 일반모드 패킷을 전송합니다.
        */
        TimerTask timerTask_Common = new TimerTask() {
            public void run() {
                Log.d(TAG, "일반모드 TimerTask가 동작합니다.");
                if (isBluetoothConnected()) {
                    connectionObservable
                            .flatMap(rxBleConnection -> rxBleConnection.writeCharacteristic(characteristicUUID, bluetoothPacket.makeCommonModePacket()))
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(bytes -> {
                                Log.d(TAG, "일반모드 패킷 전송을 성공하였습니다.");
                            }, throwable -> {
                                Log.d(TAG, "일반모드 패킷 전송을 실패하였습니다. 이유 : " + throwable);
                            });
                }
            }
        };

        timer = new Timer();
        timer.schedule(timerTask_Common, 1000, commonModeInterval);   // 일반모드를 실행합니다.
        serviceState = STATE_COMMON;                                  // 서비스의 상태를 일반모드로 바꿉니다.

        setAlarm();                                                   // 1시간마다 동작할 Alarm을 설정합니다.

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"서비스가 종료되었습니다.");
        resetBattery();
        super.onDestroy();
    }

    /*
        입력 : X
        리턴 : X
        동작 : 매 정각 울리는 Alarm을 설정합니다.
              알람의 역할은 매 정시에 사용자에게 최근 1시간 동안의 notification을 띄워주며, 서버에게 데이터 전송을 시도합니다.
              구체적인 내용은 AlarmReceiver 클래스에서 확인할 수 있습니다.
     */
    private void setAlarm(){
        Log.d(TAG, "알람이 설정되었습니다.");
        Calendar calendar = Calendar.getInstance();
        Log.d(TAG, "현재 시간 : " + calendar.getTime());

        //테스트용입니다.
        //calendar.add(Calendar.HOUR, +8);
        //Log.d(TAG, "내가 지정한 시간 : " + calendar.getTime());

        calendar.add(Calendar.HOUR, + 1);   // 시간을 Service가 시작된 시간으로부터 1시간 뒤로 설정합니다. (다음 정각)
        calendar.set(Calendar.MINUTE, 0);   // 분은 0분으로 설정합니다.
        calendar.set(Calendar.SECOND, 30);  // 초는 30초로 설정합니다. 이유는 혹시나 더 일찍 실행되어 notification이 잘못 표기되는 것을 방지
        Log.d(TAG, "다음 알림이 울릴 시각 : " + calendar.getTime());

        AlarmManager alarm = (AlarmManager) this. getSystemService(Context.ALARM_SERVICE);   // AlarmManager 생성
        Intent intent = new Intent(getApplication(), AlarmReceiver.class);  // AlarmReceiver 클래스에 Intent 설정
        PendingIntent sender = PendingIntent.getBroadcast(getApplication(), 0, intent, 0);  // PendingIntent 설정

        //alarm.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), 1 * 1 * 30 * 1000, sender);
        // 위는 테스트용, 인자는 시간타입, 언제 실행할 건가?, 간격은?, 인텐트. 5초마다 AlarmReceiver 실행.

        // 1시간마다 반복하도록 설정합니다. , 시간관련 인자 설명 : 24 * 60 * 60 * 1000 -> 하루 24시간, 60분, 60초
        alarm.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), 1 * 60 * 60 * 1000, sender);
    }

    /*
        입력 : double[] 형 input
        리턴 : int형 minIndex
        동작 : 입력으로 들어온 배열에서 가장 작은 값을 갖는 부분의 Index를 리턴한다.
     */
    private int getMinIndex(double[] input){
        int minIndex = 0;
        double min = 999999;

        for(int i = 0;i < input.length; i++){
            if(input[i] < min){
                min = input[i];
                minIndex = i;
            }
        }
        return minIndex;
    }

    /*
        입력 : BluetoothPacket 형 decodedPacket
        리턴 : X
        동작 : decoding 된 패킷을 이용해서 그 후 작업을 한다.
              1. 일반모드라면 디코딩된 패킷의 값을 이용하여 자세를 추측하고, Database 에 시간과 정확도를 추가합니다.
              2. 실시간모드라면 자세를 추측하고, Tab3(실시간 탭)에 자세의 결과를 전송합니다.

              자세를 추측하는 것은 아래의 guessPosition 함수를 사용합니다.
     */
    private void processPacket(BluetoothPacket decodedPacket){

        if(decodedPacket.getMode() == 1){   // 패킷의 모드를 검사합니다.
            Log.d(TAG, "일반모드 패킷을 처리합니다.");
            int guessedPosition = guessPosition(decodedPacket.getValue(),decodedPacket.getPosition());  // 자세를 추측합니다.
            int accuracy = makeScore(guessedPosition);  // 추측된 자세의 정확도를 얻습니다.

            // DatabaseManager 클래스를 이용하여 database 에 값을 추가합니다.
            DatabaseManager databaseManager = new DatabaseManager(getApplicationContext());
            databaseManager.insertData(decodedPacket.getDataHour(),1,accuracy,decodedPacket.getDataDate());

            // 요약(Tab1)에 보이는 배터리 잔량을 갱신합니다.
            SharedPreferences prefs = getSharedPreferences("battery", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("battery", bluetoothPacket.getBattery());
            editor.commit();
        } else if(decodedPacket.getMode() == 2){
            Log.d(TAG, "실시간모드 패킷을 처리합니다.");
            int guessedPosition = guessPosition(decodedPacket.getValue(),decodedPacket.getPosition()); // 자세를 추측합니다.
            if (isBluetoothConnected())
                remoteSendMessage_Tab3(String.valueOf(guessedPosition)); // 자세의 결과를 Tab3로 보냅니다.
            else
                remoteSendMessage_Tab3(BLUETOOTH_NOT_CONNECTED);   // 블루투스가 연결이 되지 않은 경우.
        }
    }

    /*
        입력 : int[]형 cellValue(9개의 셀에서 나온 값), double[]형 positionValue(x , y 좌표)
        리턴 : 추측해서 나온 자세코드
        동작 : 9개의 cell 값과 2개의 x,y 좌표를 이용해서 자세를 추측한 후 자세에 맞는 번호를 리턴한다.
              과정은 각 자세별 Centroid 로부터 거리를 측정하고 가장 짧은 거리의 Centroid를 선택한다.
     */
    private int guessPosition(int[] cellValue, double[] positionValue){

        double[] positionProbability = new double[5];
        positionProbability[standard] = centroid_standard.getDistance(positionValue[0],positionValue[1]);    // 정자세
        positionProbability[leanLeft] = centroid_leanLeft.getDistance(positionValue[0],positionValue[1]);    // 왼쪽으로 쏠렸습니다.
        positionProbability[leanRight] = centroid_leanRight.getDistance(positionValue[0],positionValue[1]);  // 오른쪽으로 쏠렸습니다
        positionProbability[front] = centroid_front.getDistance(positionValue[0],positionValue[1]);          // 앞으로 쏠렸습니다.
        positionProbability[hipFront] = centroid_hipFront.getDistance(positionValue[0],positionValue[1]);    // 엉덩이가 앞으로 갔습니다.

        int positionResult = getMinIndex(positionProbability);

        if(positionResult == leanLeft && cellValue[2] <= threshold_right){   // 왼쪽으로 쏠린 자세인데, 오른쪽 앞 부분이 안눌렸다 -> 오른쪽 다리를 꼬았음
            return crossRightLeg;
        }else if(positionResult == leanRight && cellValue[0] <= threshold_left){ // 오른쪽으로 쏠린 자세인데, 왼쪽 앞 부분이 비었다 -> 왼쪽 다리를 꼬았음
            return crossLeftLeg;
        }else {
            return positionResult;
        }
    }

    /*
        입력 : int형 posture(자세)
        리턴 : int형 score(해당 자세의 자세점수)
        동작 : 입력으로 들어온 자세에 해당하는 점수를 리턴합니다.
     */
    private int makeScore(int posture){
        int score = 0;
        switch(posture){
            case standard : score = 100; break;
            case leanLeft : score = 80; break;
            case leanRight: score = 80; break;
            case front : score = 80; break;
            case hipFront : score = 40; break;
            case crossRightLeg : score = 30; break;
            case crossLeftLeg : score = 30; break;
        }
        return score;
    }

    /*
        입력 : X
        리턴 : boolean 형의 블루투스 연결상태를 리턴합니다. (true -> 블루투스 연결상태, false -> 블루투스 연결X 상태)
        동작 : 블루투스의 현재 연결상태를 리턴합니다.
     */
    private boolean isBluetoothConnected(){
        return device.getConnectionState() == RxBleConnection.RxBleConnectionState.CONNECTED;
    }

    private void setBluetoothConnect(){
        connectionObservable.subscribe(
                characteristicValue -> {
                    // Read characteristic value.
                    Log.d(TAG, "블루투스가 연결되었다.");
                },
                throwable -> {
                    // Handle an error here.
                    Log.d(TAG, "블루투스가 연결 실패. 내용 : " + throwable);
                    Log.d(TAG, "재연결을 시도합니다.");
                    resetBattery();
                    setBluetoothConnect();
                }
        );
    }

    /*
        입력 : X
        리턴 : X
        동작 : 블루투스로부터 Read를 수행합니다.
              패킷이 들어온 경우 우선 BluetoothPacket 클래스를 통해서 패킷을 해석합니다.
              BLE의 경우에는 20Byte 씩 패킷이 끊어져서 들어오기 때문에 여러 개의 chunk 를 하나로 합치는 과정이 필요합니다.
              이후 패킷이 완성되었음을 확인하면 패킷의 좌표가 (-2, -2)인지 확인합니다. 이 좌표는 방석에서 idle 상태를 의미합니다.
              (-2, -2)가 아니라면 처리해야할 패킷이기 때문에 processPacket 함수를 통해서 패킷의 모드에 맞춰서 처리하는 동작을 수행합니다.

              예외상황의 경우에는 Read를 재시도하며, 재시도 전에 배터리 상황을 리셋하는 과정을 거칩니다.
     */
    private void setBluetoothRead(){
        connectionObservable.flatMap(rxBleConnection -> rxBleConnection.setupNotification(characteristicUUID))
                .doOnNext(notificationObservable -> {
                    // 리스너가 세팅된 후 동작을 여기에 적습니다.
                })
                .flatMap(notificationObservable -> notificationObservable)
                .subscribe(
                        bytes -> {
                            Log.d(TAG, "방석으로부터 값이 들어왔습니다.");
                            bluetoothPacket.decodePacket(bytes);            // 패킷을 디코딩한다.
                            if(bluetoothPacket.getIsPacketCompleted()) {    // 20Byte로 잘려 들어온 패킷을 합치는 과정이 완성되었다면

                                // 좌표가 -2,-2가 아닌 경우에만 패킷을 처리합니다. (방석에서 idle 상태일 때 좌표로 -2, -2를 보냅니다.)
                                if(bluetoothPacket.getPosition()[0] != -2 && bluetoothPacket.getPosition()[1] != -2) {
                                    Log.d(TAG, "패킷을 처리합니다.");
                                    processPacket(bluetoothPacket); // 디코딩한 패킷 처리합니다.
                                }
                            }
                        },
                        throwable -> {
                            Log.d(TAG, "read 에 예외가 발생했습니다. 내용 : " + throwable);
                            Log.d(TAG, "Read 재연결을 시도합니다.");
                            resetBattery();     // 재연결 전에 방석의 배터리 상황을 리셋합니다.
                            bluetoothPacket = null;
                            bluetoothPacket = new BluetoothPacket();
                            setBluetoothRead(); // 재귀적으로 다시 호출합니다.
                        }
                );
    }
}