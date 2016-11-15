package com.seat.sw_maestro.seat;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
    이 서비스에서는 주기적으로 방석으로 데이터를 보낸다. (실시간모드 or 일반모드)
    각 모드가 보내지는 경우는 다음과 같다.
    방석상태모드 - 사용자가 Tab1(방석연결상태)을 보고 있을 때
    실시간모드 - 사용자가 Tab3(실시간)을 보고 있을 때
    일반모드 - 그 외의 경우
    실시간모드는 방석에 실시간모드용 데이터 요청을 하며, 일반모드는 일반모드용 데이터 요청을 한다.
    실시간모드의 경우에는 방석의 데이터를 실시간으로 받으며, 일반모드에는 쌓인 데이터가 있는 경우라면 쌓인 데이터를 받고, 없는 경우라면 받은 당시 데이터 받음.
    받아온 데이터는 DB에 저장되어야하는데 일반모드의 경우에는 1(변경가능)분에 한 번씩 데이터를 요청하며, 그때마다 DB에 넣고 정확도와 시간을 재계산한다.
    정산의 내용은 다음과 같다.
    몇 번 데이터를 받았는지 카운트하여, 1시간동안 몇 분을 앉아있었는지 체크한다.
    그 카운트된 시점마다 정확도를 분석하여, 카운트 된 동안의 평균을 구한다. 점수도 구함.
    DB에 넣는다. 현재시간, 정확도, 점수, 현재 날짜.
*/

public class BluetoothService extends Service {

    //BLE
    RxBleDevice device;
    RxBleClient rxBleClient;
    private Observable<RxBleConnection> connectionObservable;
    private static final String macAddress = "F4:B8:5E:F0:57:E5";
    UUID characteristicUUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    // 자세별 코드
    private static final int standard = 0;  // 정자세
    private static final int leanLeft = 1;  // 왼쪽으로 쏠렸다.
    private static final int leanRight = 2;  // 오른쪽으로 쏠렸다.
    private static final int front = 3;  // 상체가 앞으로 쏠렸다
    private static final int back = 4;  // 상체가 뒤로 쏠렸다.
    private static final int hipFront = 5;  // 엉덩이를 앞으로 뺐다.
    private static final int crossRightLeg = 6;  // 오른쪽 다리를 왼쪽으로 꼬았다.
    private static final int crossLeftLeg = 7;  // 왼쪽 다리를 오른쪽으로 꼬았다.

    // 자세별 중심점 그룹
    Centroid centroid0; // 정자세
    Centroid centroid1; // 왼쪽
    Centroid centroid2; // 오른쪽
    Centroid centroid3; // 앞쪽
    Centroid centroid4; // 뒤쪽
    Centroid centroid5; // 엉덩이 앞쪽

    private static final String TAG = "BluetoothService";
    private static final String SeatName = "seat";    // 방석의 블루투스 이름을 입력한다.

    private static final int commonModeInterval = 10000; // 일반모드 실행주기
    private static final int realTimeModeInterval = 1000;   // 실시간모드 실행주기
    private static final int tab1ModeInterval = 3000;   // 방석연결상태 실행주기


    private Messenger mRemote;  // 서비스와 액티비티 간에 통신을 하기 위해서 쓰는 메신저
    Timer timer;    // 일정시간마다 일을 하기 위해서 .. 타이머
    BluetoothPacket bluetoothPacket; // 블루투스 패킷 관련

    int serviceState = 0;   // 서비스의 상태. 값은 아래를 보세요.
    private static final int STATE_COMMON = 0;  // 일반모드
    private static final int STATE_TAB1 = 1;    // Tab1을 보는 상태
    private static final int STATE_TAB3 = 2;    // Tab3를 보는 상태

    // 생성자
    public BluetoothService(){

    }

    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "서비스가 bind 됨");
        return new Messenger(new RemoteHandler()).getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // TODO Auto-generated method stub
        Log.d(TAG,"서비스가 unbind 됨");
        return super.onUnbind(intent);
    }

    // what 값에 따라서 액티비티에서 받았을 때 처리가 달라진다.
    // 현재 Service -> Tab1 간에 통신은  what 값 0을 사용 (방석의 상태)
    // 현재 Service -> Tab3 간에 통신은  what 값 1을 사용 (자세의 결과)
    public void remoteSendMessage_Tab1(String data) {    // 액티비티로 메시지 전달. 방석의 연결 유무 상태를 나타내주기 위해 사용
        if (mRemote != null) {
            Message msg = new Message();
            msg.what = 0;
            msg.obj = data; // 오브젝트로 String data를 보낸다.
            try {
                mRemote.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void remoteSendMessage_Tab3(String data) {    // 액티비티로 메시지 전달. 자세의 결과
        if (mRemote != null) {
            Message msg = new Message();
            msg.what = 1;
            msg.obj = data; // 오브젝트로 String data를 보낸다.
            try {
                mRemote.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }


    // Service handler 추가  액티비티 -> 서비스로 받아오는 경우.
    private class RemoteHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {

            // 일반모드
            TimerTask timerTask_Common = new TimerTask() {
                public void run() { // 일반모드에서 어떻게 동작하는지
                    Log.d(TAG, "일반모드 요청 패킷 전송");
                    if (isBluetoothConnected()) {
                        connectionObservable
                                .flatMap(rxBleConnection -> rxBleConnection.writeCharacteristic(characteristicUUID, bluetoothPacket.makeCommonModePacket()))
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(bytes -> {
                                    Log.d(TAG, "일반모드 전송 성공");
                                }, throwable -> {
                                    Log.d(TAG, "일반모드 전송 실패" + throwable);
                                });
                    }
                }
            };

            // 탭 1과 연결된 경우 동작을 여기다 둔다.
            // Service -> Tab1 보냄
            // 주기적으로 방석의 상태를 보내주는 일을 한다. 이것을 안하면 바뀌는 순간에만 텍스트뷰를 바꾸니... 바뀐 순간에 그 화면을 안보면 안바꿔짐
            TimerTask timerTask_Tab1 = new TimerTask() {
                public void run() {
                    Log.d(TAG, "방석의 연결상태 전송");
                    Log.d(TAG, "연결 상태 : " + device.getConnectionState());
                    if (isBluetoothConnected()) {
                        remoteSendMessage_Tab1("1"); // 연결되었다고 보내자.
                    }else {
                        remoteSendMessage_Tab1("0");
                    }
                }
            };

            // 탭 3과 연결된 경우 동작을 여기다 둔다.
            // Service -> Tab3 보냄
            // 자세의 결과를 보내준다.
            TimerTask timerTask_Tab3 = new TimerTask() {
                public void run() {
                    Log.d(TAG, "실시간모드 요청 패킷 전송");
                    if (isBluetoothConnected()) {
                        connectionObservable
                                .flatMap(rxBleConnection -> rxBleConnection.writeCharacteristic(characteristicUUID, bluetoothPacket.makeRealTimeModePacket()))
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(bytes -> {
                                    Log.d(TAG, "실시간모드 전송 성공");
                                }, throwable -> {
                                    Log.d(TAG, "실시간모드 전송 실패" + throwable);
                                });
                    }
                }
            };

            switch (msg.what) {
                case 0 :    // Tab1을 보는 경우.
                    Log.d(TAG, "서비스와 탭1이 연결되었다.");
                    serviceState = STATE_TAB1;
                    mRemote = (Messenger) msg.obj;

                    if(timer != null){  // 기존 타이머에 등록됬던 것 다 삭제
                        timer.cancel();
                        timer = null;
                    }

                    timer = new Timer();
                    timer.schedule(timerTask_Tab1, 1000, tab1ModeInterval);  // Tab1전용 task(방석 연결상태)를 1초 후 2초마다 실행
                    break;

                case 1 :    // Tab3를 보는 경우
                    Log.d(TAG, "서비스와 탭3이 연결되었다.");
                    serviceState = STATE_TAB3;
                    mRemote = (Messenger) msg.obj;

                    if(timer != null){  // 기존 타이머에 등록됬던 것 다 삭제
                        timer.cancel();
                        timer = null;
                    }

                    timer = new Timer();
                    timer.schedule(timerTask_Tab3, 1000, realTimeModeInterval); // Tab3전용 task(방석에 실시간 데이터 요청)를 1초 후 1초마다 실행
                    break;

                case 2 :    // Tab1이 화면에서 사라짐
                    Log.d(TAG, "Tab1에서 끝났다 신호 보냄.");
                    // Tab1에서 끝났다는 것은, Tab1이 화면에서 사라짐. -> 홈으로 갔거나, Tab3를 본다...
                    // 타이머를 없애려면..

                    // 만약 Tab3를 보는 경우라면 serviceState가 Tab3로 먼저 바뀐다.
                    // 그래서 아래서 Tab1과 비교하는 것이다. 그대로 Tab1인 경우에는 홈화면으로 간 것이니까.
                    // 비교를 안하면 Tab3 새로운 타이머가 등록되었는데 그것을 삭제해버림.

                    if((timer != null) && (serviceState == STATE_TAB1)){  // 기존 타이머에 등록됬던 것 다 삭제
                        timer.cancel();
                        timer = null;

                        // 여기로 들어온 경우에는 홈 화면을 보는 것이다. 타이머 일반모드 실행시켜야함.
                        // 서비스의 동작을 일반모드로 변경
                        timer = new Timer();
                        timer.schedule(timerTask_Common, 1000, commonModeInterval);   // 일반모드 실행
                        serviceState = STATE_COMMON;
                    }
                    break;

                case 3 :    // Tab3가 화면에서 사라짐
                    Log.d(TAG, "Tab3에서 끝났다 신호 보냄.");
                    // Tab1에서 끝났다는 것은, Tab1이 화면에서 사라짐. -> 홈으로 갔거나, Tab3를 본다...
                    // 타이머를 없애려면..
                    if((timer != null) && (serviceState == STATE_TAB3)){  // 기존 타이머에 등록됬던 것 다 삭제
                        timer.cancel();
                        timer = null;

                        // 여기로 들어온 경우에는 홈 화면을 보는 것이다. 타이머 일반모드 실행시켜야함.
                        // 서비스의 동작을 일반모드로 변경
                        timer = new Timer();
                        timer.schedule(timerTask_Common, 1000, commonModeInterval);   // 일반모드 실행
                        serviceState = STATE_COMMON;
                    }
                    break;

                case 4: // 테스트 앱을 실행시켰다는 메시지. 서비스를 중단해야함.
                    Log.d(TAG, "서비스를 중단합니다.");
                    stopService();
                    break;

                default :
                    Log.d(TAG, "등록되지 않은 곳에서 메시지가 옴");
                    break;
            }
        }
    }

    public void stopService(){
        // 블루투스 찾는 것 중지
        connectionObservable = null;

        // 타이머테스크 중지
        if(timer != null){
            timer.cancel();
            timer = null;
        }

        stopSelf(); // 이 이후에는 프로세스 종료되도 서비스가 다시 살아나지 않음.

        // 앱(액티비티) 죽이기 (SettingActivity와 TabActivity)
        SettingActivity settingActivity = (SettingActivity)SettingActivity.SettingActivity;
        TabActivity tabActivity = (TabActivity) TabActivity.TabActivity;
        if(settingActivity != null) settingActivity.finish();
        if(tabActivity != null) tabActivity.finish();

        android.os.Process.killProcess(android.os.Process.myPid()); // 서비스 완전 종료

    }

    @Override
    public void onCreate() {
        Log.d(TAG,"서비스가 시작되었습니다.");

        centroid0 = new Centroid(0.79255102,-1.647755102); // 정자세
        centroid1 = new Centroid(-2.389793814,-0.439484536);   // 왼쪽
        centroid2 = new Centroid(1.8,-3.3);    // 오른쪽
        centroid3 = new Centroid(0.194646465,-1.044747475); // 앞으로 쏠
        centroid4 = new Centroid(0.922626263,-1.468989899); // 뒤로 쏠
        centroid5 = new Centroid(0.2966,4.2344); // 엉덩이 앞으로 뺌

        bluetoothPacket = new BluetoothPacket();    // 블루투스 패킷을 디코딩하기 위한 클래스

        // BLE 관련입니다.
        rxBleClient = RxBleClient.create(getApplicationContext());
        device = rxBleClient.getBleDevice(macAddress);  // 디바이스를 얻어옵니다.
        PublishSubject<Void> disconnectTriggerSubject = PublishSubject.create();

        // 디바이스의 연결을 공유하는 connectionObservable. 이것을 활용해서 나중에 읽기 쓰기를 합니다.
        connectionObservable = device
                .establishConnection(getApplicationContext(), true) // 오른쪽 boolean 인자는 자동연결 관련입니다.
                .observeOn(AndroidSchedulers.mainThread())
                .takeUntil(disconnectTriggerSubject)
                .compose(new ConnectionSharingAdapter());

        setBluetoothConnect();  // 블루투스 연결을 실제로 시작
        setBluetoothRead();     // 블루투스 읽기를 시작

        // 서비스가 생성될 때는 TimerTask를 일반모드로 생성한다.
        // 위에 핸들러에도 있는데 여기다 또 만드는 이유는 서비스가 죽었다가 자동으로 살아나는 경우에도 실행될 수 있도록..(안하면 앱을 들어갔다 나와야 실행된다.)
        // 일반모드
        TimerTask timerTask_Common = new TimerTask() {
            public void run() { // 일반모드에서 어떻게 동작하는지
                Log.d(TAG, "일반모드 요청 패킷 전송");

                if (isBluetoothConnected()) {
                    connectionObservable
                            .flatMap(rxBleConnection -> rxBleConnection.writeCharacteristic(characteristicUUID, bluetoothPacket.makeCommonModePacket()))
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(bytes -> {
                                Log.d(TAG, "일반모드 전송 성공");
                            }, throwable -> {
                                Log.d(TAG, "일반모드 전송 실패" + throwable);
                            });
                }
            }
        };

        timer = new Timer();
        timer.schedule(timerTask_Common, 1000, commonModeInterval);   // 일반모드 실행
        serviceState = STATE_COMMON;

        setAlarm(); // 서비스가 실행될 때 알람을 실행한다. 1시간마다 실행하며 값을 정산해서 서버로 보내는 부분

        // Register for broadcasts on BluetoothAdapter state change
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"서비스가 종료되었습니다.");
        super.onDestroy();
    }

    public void setAlarm(){
        Log.d(TAG, "알람이 설정");
        Calendar calendar = Calendar.getInstance();
        Log.d(TAG, "지금 시간 : " + calendar.getTime());

        //테스트용
        //calendar.add(Calendar.HOUR, +8);
        //Log.d(TAG, "내가 지정한 시간 : " + calendar.getTime());

        calendar.add(Calendar.HOUR, + 1); // 서비스가 시작된 시간으로부터 1시간 뒤(다음 정각)
        calendar.set(Calendar.MINUTE, 0); // 다음 정각 0분에
        calendar.set(Calendar.SECOND, 30); // 초는 30초로 (혹시모를 오류.. 더 빨리 실행해버리면 꼬이니까)
        Log.d(TAG, "언제 알림이 시작될 것인가? : " + calendar.getTime());

        AlarmManager alarm = (AlarmManager) this. getSystemService(Context.ALARM_SERVICE);   // 알람 매니저
        Intent intent = new Intent(getApplication(), AlarmReceiver.class);  // 알람 리시버로 인텐트
        PendingIntent sender = PendingIntent.getBroadcast(getApplication(), 0, intent, 0);

        //alarm.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), 1 * 1 * 30 * 1000, sender);
        // 위는 테스트용, 인자는 시간타입, 언제 실행할 건가?, 간격은?, 인텐트. 5초마다 AlarmReceiver 실행.

        alarm.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), 1 * 60 * 60 * 1000, sender);
        // 이게 진짜 실전용 1시간마다 동작하는 알람.

        // 24 * 60 * 60 * 1000 -> 하루 24시간, 60분, 60초
    }

    public int getMinIndex(double[] input){
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
        디코딩된 패킷을 이용해서 이제 그 후 작업을 한다.// 디코딩된 패킷을 이용해서 이제 그 후 작업을 한다.
        일반모드라면 디코딩된 패킷의 값을 이용하여 자세를 추측하고 디비에 넣는다.
        실시간모드라면 자세를 추측하고 Tab3에 자세의 결과를 보내준다.
     */
    public void processPacket(BluetoothPacket decodedPacket){
        if(decodedPacket.getMode() == 1){
            Log.d(TAG, "일반모드 처리");
            int guessedPosition = guessPosition(decodedPacket.getValue(),decodedPacket.getPosition());  // 자세추측
            int accuracy = makeScore(guessedPosition);  // 추측된 자세의 정확도를 얻음

            // 디비에 넣기 위해
            DatabaseManager databaseManager = new DatabaseManager(getApplicationContext());
            databaseManager.insertData(decodedPacket.getDataHour(),1,accuracy,decodedPacket.getDataDate());

            Log.d("databaseTest", "데이터베이스에 다음과 같은 값을 넣었습니다.");
            Log.d("databaseTest", "타임라인 : " + decodedPacket.getDataHour());
            Log.d("databaseTest", "앉은시간 추가 : " + 1);
            Log.d("databaseTest", "정확도 : " + accuracy);
            Log.d("databaseTest", "날짜 : " + decodedPacket.getDataDate());

        } else if(decodedPacket.getMode() == 2){
            Log.d(TAG, "실시간모드 처리");
            int guessedPosition = guessPosition(decodedPacket.getValue(),decodedPacket.getPosition());

            if (isBluetoothConnected())
                remoteSendMessage_Tab3(String.valueOf(guessedPosition)); // 자세의 결과를 Tab3로 보냄
            else
                remoteSendMessage_Tab3("-1");   // 블루투스 연결이 안되어있음
        }
    }

    /*
    input : 9개의 셀에서 나온 값, x.y좌표
    output : 추측해서 나온 자세코드
    동작 : 9개의 cell 값과 2개의 x,y 좌표를 이용해서 자세를 추측한 후 자세에 맞는 번호를 리턴한다.
     */
    public int guessPosition(int[] cellValue, double[] positionValue){

        double[] positionProbability = new double[6];
        positionProbability[standard] = centroid0.getDistance(positionValue[0],positionValue[1]);  // 정자세
        positionProbability[leanLeft] = centroid1.getDistance(positionValue[0],positionValue[1]);  // 왼쪽
        positionProbability[leanRight] = centroid2.getDistance(positionValue[0],positionValue[1]);  // 오른쪽
        positionProbability[front] = centroid3.getDistance(positionValue[0],positionValue[1]);  // 상체 앞으로
        positionProbability[back] = centroid4.getDistance(positionValue[0],positionValue[1]);  // 상체 뒤로
        positionProbability[hipFront] = centroid5.getDistance(positionValue[0],positionValue[1]);  // 엉덩이 앞으로

        Log.d(TAG,"정자세" + positionProbability[standard]);
        Log.d(TAG,"왼쪽" + positionProbability[leanLeft]);
        Log.d(TAG,"오른쪽" + positionProbability[leanRight]);
        Log.d(TAG,"상체앞" + positionProbability[front]);
        Log.d(TAG,"상체뒤" + positionProbability[back]);
        Log.d(TAG,"엉덩이 앞" + positionProbability[hipFront]);

        int positionResult = getMinIndex(positionProbability);

        if(positionResult == leanLeft && cellValue[2] <= 10){   // 왼쪽으로 쏠린 자세인데, 오른쪽 앞 부분이 안눌렸다 -> 오른쪽 다리를 꼬았음
            return crossRightLeg;
        }else if(positionResult == leanRight && cellValue[0] <= 10){ // 오른쪽으로 쏠린 자세인데, 왼쪽 앞 부분이 비었다 -> 왼쪽 다리를 꼬았음
            return crossLeftLeg;
        }else {
            return positionResult;
        }
    }

    /*
    input : 자세코드
    output : 자세점수
    동작 : 자세를 입력받아서 그에 맞는 자세별 점수를 리턴한다.
     */
    public int makeScore(int position){
        int score = 0;
        switch(position){
            case standard : score = 100; break;
            case leanLeft : score = 80; break;
            case leanRight: score = 80; break;
            case front : score = 80; break;
            case back : score = 80; break;
            case hipFront : score = 40; break;
            case crossRightLeg : score = 30; break;
            case crossLeftLeg : score = 30; break;
        }
        return score;
    }

    public boolean isBluetoothConnected(){
        return device.getConnectionState() == RxBleConnection.RxBleConnectionState.CONNECTED;
    }

    public void setBluetoothConnect(){
        connectionObservable.subscribe(
                characteristicValue -> {
                    // Read characteristic value.
                    Log.d(TAG, "블루투스가 연결되었다.");
                },
                throwable -> {
                    // Handle an error here.
                    Log.d(TAG, "블루투스가 연결 실패. 내용 : " + throwable);
                    Log.d(TAG, "재연결을 시도합니다.");
                    setBluetoothConnect();
                }
        );
    }

    public void setBluetoothRead(){
        // read 와 관련된 부분입니다. 리스너라고 생각하세요.
        connectionObservable.flatMap(rxBleConnection -> rxBleConnection.setupNotification(characteristicUUID))
                .doOnNext(notificationObservable -> {
                    // Notification has been set up
                    // 리스너가 세팅된 후 동작을 여기에 적습니다.
                })
                .flatMap(notificationObservable -> notificationObservable) // <-- Notification has been set up, now observe value changes.
                .subscribe(
                        bytes -> {
                            // Given characteristic has been changes, here is the value.// Given characteristic has been changes, here is the value.
                            Log.d(TAG, "값이 들어왔습니다.");
                            bluetoothPacket.decodePacket(bytes); // 패킷을 디코딩한다.
                            processPacket(bluetoothPacket); // 디코딩한 패킷 처리
                        },
                        throwable -> {
                            Log.d(TAG, "read에 예외가 발생했습니다. 내용 : " + throwable);
                            Log.d(TAG, "Read 재연결을 시도합니다.");
                            setBluetoothRead();
                        }
                );
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "블루투스 꺼져있음");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d(TAG, "블루투스 끔");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "블루투스 켜져있음");
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d(TAG, "블루투스 킴");

                        break;
                }
            }
        }
    };
}