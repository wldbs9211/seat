package com.seat.sw_maestro.seat;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.Calendar;

/**
 * Created by JiYun on 2016. 11. 6..
 */
public class BluetoothPacket {
    final static String TAG = "BluetoothPacket";

    final static int commonMode_int = 1;
    final static int realTimeMode_int = 2;

    final static byte startByte = (byte)0xFF;   // 시작 알림 바이트
    final static byte endByte = (byte)0xFE;     // 끝 알림 바이트
    //final static byte endByte = (byte)'!';     // 끝 알림 바이트
    final static byte commonLengthByte = (byte)0x08;    // 일반모드 길이
    final static byte realTimeLengthByte = (byte)0x02;  // 실시간모드 길이
    final static byte commonMode = (byte)0x01;  // 일반모드 뜻함
    final static byte realTimeMode = (byte)0x02;  // 실시간모드 뜻함

    // 타입 사이즈. (mode, date, value, position, length)
    final static private int typeSize = 5;

    // 데이터 타입별 내부 데이터의 개수 (1이면 아래 타입별 변수를 배열로 만들 필요가 없지)
    final static private int modeSize = 1;
    final static private int dateSize = 4;
    final static private int valueSize = 9;
    final static private int positionSize = 2;
    final static private int lengthSize = 1;

    // 데이터 타입별 변수
    private int mode_int;
    private int[] date_int = new int[dateSize]; // yy mm dd hh
    private int[] value_int = new int[valueSize];
    private double[] position_double = new double[positionSize];
    private int length_int;

    // 데이터 타입별 변수
    private String mode_string;
    private String[] date_string = new String[dateSize];
    private String[] value_string = new String[valueSize];
    private String[] position_string = new String[positionSize];
    private String length_string;

    /*  BLE 전용입니다.
    SPP와는 다르게 BLE는 데이터가 20byte 씩 끊어져서 들어옵니다.
    따라서 각각 들어온 패킷을 합치는 작업이 필요합니다.
     */
    boolean isPacketCompleted = false;  // 패킷이 완료되었는지 알려줍니다.
    String tempString = ""; // 각각 들어온 패킷을 이 변수에 저장합니다.

    // 생성자
    BluetoothPacket(){

    }

    int getMode(){
        return mode_int;
    }

    int[] getDate(){
        return date_int;
    }

    String getDataDate(){   // 리턴 예) String "20161102"
        return "20" + date_string[0] + date_string[1] + date_string[2];
    }

    String getDataHour(){   // 데이터의 시간 리턴
        return date_string[3];
    }

    int[] getValue(){
        return value_int;
    }

    double[] getPosition(){
        return position_double;
    }

    int getLength(){
        return length_int;
    }

    boolean getIsPacketCompleted() { return isPacketCompleted; }

    void decodePacket(byte[] inputPacket) {
        Log.d(TAG, "패킷이 들어왔다.");
        Log.d(TAG, "내용 : " + byteToString(inputPacket));

        // 마지막 조각인지 검사한다.
        boolean isLastChunk = (inputPacket[inputPacket.length - 1] == endByte);
        if(!isLastChunk) isPacketCompleted = false; // 마지막 조각 여부를 이 변수에 저장한다.

        // 들어온 패킷을 붙입니다.
        tempString = tempString + byteToString(inputPacket);

        if(isLastChunk){    // 마지막 조각인 경우에는 데이터를 파싱처리합니다.
            if(tempString.charAt(1) == '1' || tempString.charAt(1) == '2'){   // 패킷이 정상인지 검사합니다. 모드가 정상인지 검사하자.
                // StartByte와 EndByte를 0으로 치환합니다.
                StringBuilder builder = new StringBuilder(tempString);
                builder.setCharAt(0, '0');
                builder.setCharAt(tempString.length()-1, '0');
                tempString = builder.toString();
                Log.d(TAG, "최종 String 내용 : " + tempString);

                // Type별(모드~날짜~벨류~좌표~길이 들어옴)로 ~를 기준으로 짜른다.
                String[] splitedIntoPart;
                splitedIntoPart = tempString.split("~");

                // 그 안에 데이터는 ',' 기준으로 또 나눈다.
                mode_string = splitedIntoPart[0];
                mode_int = StringToInt(mode_string);

                //Log.d(TAG, "모드 값 : " + mode_int);
                switch(mode_int) {
                    // 일반모드 패킷을 분석합니다.
                    case commonMode_int :
                        Log.d(TAG, "일반모드 패킷 분석");

                        // 날짜를 세부적으로 나눕니다.
                        date_string = splitedIntoPart[1].split(",");
                        for(int i = 0; i < date_string.length; i++){
                            date_int[i] = StringToInt(date_string[i]);
                        }

                        // 센서 값을 세부적으로 나눕니다.
                        value_string = splitedIntoPart[2].split(",");
                        for(int i = 0; i < value_string.length; i++){
                            value_int[i] = StringToInt(value_string[i]);
                        }

                        // 자세 좌표를 세부적으로 나눕니다.
                        position_string = splitedIntoPart[3].split(",");
                        for(int i = 0; i < position_string.length; i++){
                            // 양수일 경우 처리합니다.
                            if(position_string[i].charAt(0) == '+'){
                                //Log.d(TAG, "양수");
                                position_string[i] = position_string[i].replace('+', '0');
                                //Log.d(TAG, "+ 없애기 테스트 : " + position_string[i]);
                                int positionTemp_int = StringToInt(position_string[i]);
                                position_double[i] = (double)positionTemp_int/100;
                            }
                            // 음수일 경우를 처리합니다.
                            else if(position_string[i].charAt(0) == '-') {
                                //Log.d(TAG, "음수");
                                position_string[i] = position_string[i].replace('-', '0');
                                //Log.d(TAG, "- 없애기 테스트 : " + position_string[i]);
                                int positionTemp_int = StringToInt(position_string[i]);
                                position_double[i] = ((double)positionTemp_int/100) * -1;
                            }
                        }

                        // 길이를 얻습니다.
                        length_string = splitedIntoPart[4];
                        length_int = StringToInt(length_string);
                        length_int = length_int / 10;   // 10으로 나눈 이유는 end byte를 0으로 바꿔서 실제 길이에 10이 곱해진다. 따라서 나눈다.

                        // 테스트 출력
                        //Log.d(TAG, "모드 : " + mode_string);
                        Log.d(TAG, "모드 : " + mode_int);

                        Log.d(TAG, "날짜 차례대로 연월일시간");
                        for (int i = 0; i < date_string.length; i++) {
                            //Log.d(TAG, date_string[i]);
                            Log.d(TAG, "" + date_int[i]);
                        }

                        Log.d(TAG, "셀값 차례로 1~9번 셀 값을 출력합니다.");
                        for (int i = 0; i < value_string.length; i++) {
                            //Log.d(TAG, value_string[i]);
                            Log.d(TAG, "" + value_int[i]);
                        }

                        // Log.d(TAG, "좌표값 : " + position_string[0] + " , " + position_string[1]);
                        Log.d(TAG, "좌표값 : " + position_double[0] + " , " + position_double[1]);

                        //Log.d(TAG, "길이 값 : " + length_string);
                        Log.d(TAG, "길이 값 : " + length_int);
                        break;

                    // 실시간모드 패킷을 분석합니다.
                    case realTimeMode_int :
                        Log.d(TAG, "실시간모드 패킷 분석");

                        value_string = splitedIntoPart[1].split(",");
                        for(int i = 0; i < value_string.length; i++){
                            value_int[i] = StringToInt(value_string[i]);
                        }

                        position_string = splitedIntoPart[2].split(",");
                        for(int i = 0; i < position_string.length; i++){
                            if(position_string[i].charAt(0) == '+'){
                                //Log.d(TAG, "양수");
                                position_string[i] = position_string[i].replace('+', '0');
                                //Log.d(TAG, "+ 없애기 테스트 : " + position_string[i]);
                                int positionTemp_int = StringToInt(position_string[i]);
                                position_double[i] = (double)positionTemp_int/100;

                            }else if(position_string[i].charAt(0) == '-') {
                                //Log.d(TAG, "음수");
                                position_string[i] = position_string[i].replace('-', '0');
                                //Log.d(TAG, "- 없애기 테스트 : " + position_string[i]);
                                int positionTemp_int = StringToInt(position_string[i]);
                                position_double[i] = ((double)positionTemp_int/100) * -1;
                            }
                        }

                        length_string = splitedIntoPart[3];
                        length_int = StringToInt(length_string);
                        length_int = length_int / 10;   // 10으로 나눈 이유는 end byte를 0으로 바꿔서 실제 길이에 10이 곱해진다. 따라서 나눔


                        // 테스트 출력
                        //Log.d(TAG, "모드 : " + mode_string);
                        Log.d(TAG, "모드 : " + mode_int);

                        Log.d(TAG, "셀값 ");
                        for (int i = 0; i < value_string.length; i++) {
                            //Log.d(TAG, value_string[i]);
                            Log.d(TAG, "" + value_int[i]);
                        }

                        // Log.d(TAG, "좌표값 : " + position_string[0] + " , " + position_string[1]);
                        Log.d(TAG, "좌표값 : " + position_double[0] + " , " + position_double[1]);

                        //Log.d(TAG, "길이 값 : " + length_string);
                        Log.d(TAG, "길이 값 : " + length_int);
                        break;
                }
            }else{
                Log.d(TAG, "버리는 패킷임");
            }

            tempString = "";    // 쌓아왔던 String을 초기화시킵니다.
            isPacketCompleted = true;   // 패킷이 해석이 완료됬다고 저장합니다.
        }
    }

    /*  SPP 전용입니당.
    void decodePacket(byte[] inputPacket){
        Log.d(TAG, "패킷을 이제 디코딩한다.");

        // 테스트 출력
        String tempPacketToString = "";
        try{
            tempPacketToString = new String(inputPacket, "UTF-8");
            Log.d(TAG, "packetToString : " + tempPacketToString);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }


        if((inputPacket[0] == startByte) && (inputPacket[inputPacket.length-1] == endByte)){    // 정상 패킷 검사 1(Start,End 검사)
            // start와 end 부분은 버린다. 공백으로 바꾼다.
            inputPacket[0] = 0x30;
            inputPacket[inputPacket.length-1] = 0x30;

            // Byte를 String으로 일단 바꿔줍니다.
            String packetToString = "";
            try{
                packetToString = new String(inputPacket, "UTF-8");
                //Log.d(TAG, "packetToString : " + packetToString);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            // Type별(모드~날짜~벨류~좌표~길이 들어옴)로 ~를 기준으로 짜른다.
            String[] splitedIntoPart;
            splitedIntoPart = packetToString.split("~");

            // 그 안에 데이터는 ',' 기준으로 또 나눈다.
            mode_string = splitedIntoPart[0];
            mode_int = StringToInt(mode_string);

            //Log.d(TAG, "모드 값 : " + mode_int);
            switch(mode_int) {
                case commonMode_int :
                    Log.d(TAG, "일반모드 패킷 분석");

                    date_string = splitedIntoPart[1].split(",");
                    for(int i = 0; i < date_string.length; i++){
                        date_int[i] = StringToInt(date_string[i]);
                    }

                    value_string = splitedIntoPart[2].split(",");
                    for(int i = 0; i < value_string.length; i++){
                        value_int[i] = StringToInt(value_string[i]);
                    }

                    position_string = splitedIntoPart[3].split(",");
                    for(int i = 0; i < position_string.length; i++){
                        if(position_string[i].charAt(0) == '+'){
                            //Log.d(TAG, "양수");
                            position_string[i] = position_string[i].replace('+', '0');
                            //Log.d(TAG, "+ 없애기 테스트 : " + position_string[i]);
                            int positionTemp_int = StringToInt(position_string[i]);
                            position_double[i] = positionTemp_int/100;

                        }else if(position_string[i].charAt(0) == '-') {
                            //Log.d(TAG, "음수");
                            position_string[i] = position_string[i].replace('-', '0');
                            //Log.d(TAG, "- 없애기 테스트 : " + position_string[i]);
                            int positionTemp_int = StringToInt(position_string[i]);
                            position_double[i] = positionTemp_int/100 * -1;
                        }
                    }

                    length_string = splitedIntoPart[4];
                    length_int = StringToInt(length_string);
                    length_int = length_int / 10;   // 10으로 나눈 이유는 end byte를 0으로 바꿔서 실제 길이에 10이 곱해진다. 따라서 나눔



                    // 테스트 출력
                    //Log.d(TAG, "모드 : " + mode_string);
                    Log.d(TAG, "모드 : " + mode_int);

                    Log.d(TAG, "날짜 차례대로 연월일시간");
                    for (int i = 0; i < date_string.length; i++) {
                        //Log.d(TAG, date_string[i]);
                        Log.d(TAG, "" + date_int[i]);
                    }

                    Log.d(TAG, "셀값 ");
                    for (int i = 0; i < value_string.length; i++) {
                        //Log.d(TAG, value_string[i]);
                        Log.d(TAG, "" + value_int[i]);
                    }

                    // Log.d(TAG, "좌표값 : " + position_string[0] + " , " + position_string[1]);
                    Log.d(TAG, "좌표값 : " + position_double[0] + " , " + position_double[1]);

                    //Log.d(TAG, "길이 값 : " + length_string);
                    Log.d(TAG, "길이 값 : " + length_int);


                    break;

                case realTimeMode_int :
                    Log.d(TAG, "실시간모드 패킷 분석");

                    value_string = splitedIntoPart[1].split(",");
                    for(int i = 0; i < value_string.length; i++){
                        value_int[i] = StringToInt(value_string[i]);
                    }

                    position_string = splitedIntoPart[2].split(",");
                    for(int i = 0; i < position_string.length; i++){
                        if(position_string[i].charAt(0) == '+'){
                            //Log.d(TAG, "양수");
                            position_string[i] = position_string[i].replace('+', '0');
                            //Log.d(TAG, "+ 없애기 테스트 : " + position_string[i]);
                            int positionTemp_int = StringToInt(position_string[i]);
                            position_double[i] = positionTemp_int/100;

                        }else if(position_string[i].charAt(0) == '-') {
                            //Log.d(TAG, "음수");
                            position_string[i] = position_string[i].replace('-', '0');
                            //Log.d(TAG, "- 없애기 테스트 : " + position_string[i]);
                            int positionTemp_int = StringToInt(position_string[i]);
                            position_double[i] = positionTemp_int/100 * -1;
                        }
                    }

                    length_string = splitedIntoPart[3];
                    length_int = StringToInt(length_string);
                    length_int = length_int / 10;   // 10으로 나눈 이유는 end byte를 0으로 바꿔서 실제 길이에 10이 곱해진다. 따라서 나눔


                    // 테스트 출력
                    //Log.d(TAG, "모드 : " + mode_string);
                    Log.d(TAG, "모드 : " + mode_int);

                    Log.d(TAG, "셀값 ");
                    for (int i = 0; i < value_string.length; i++) {
                        //Log.d(TAG, value_string[i]);
                        Log.d(TAG, "" + value_int[i]);
                    }

                    // Log.d(TAG, "좌표값 : " + position_string[0] + " , " + position_string[1]);
                    Log.d(TAG, "좌표값 : " + position_double[0] + " , " + position_double[1]);

                    //Log.d(TAG, "길이 값 : " + length_string);
                    Log.d(TAG, "길이 값 : " + length_int);


                    break;
            }

        }else{
            Log.d(TAG, "버리는 패킷임");
        }
    }
    */


    byte[] makeCommonModePacket(){  // 일반모드용 요청 패킷
        /* 패킷의 구성
        | Start(0xFF) | Mode(0x01) | YY MM DD hh mm ss | Length(0x08) | End(0xFE) |
        1, 1, 6, 1, 1 바이트씩 총 10Byte 크기의 패킷
         */

        Calendar calendar = Calendar.getInstance(); // 현재시간

        // 시간 테스트 꼭 지울것
        // calendar.add(Calendar.MINUTE, +48);

        int year = calendar.get(Calendar.YEAR);
        year = year - 2000; // 2016년 -> 16만 보낸다

        int month = calendar.get(Calendar.MONTH);
        month = month + 1;  // 캘린더는 월이 0부터 시작. 1 더해줌

        int day = calendar.get(Calendar.DATE);

        int hour = calendar.get(Calendar.HOUR);
        if(calendar.get(Calendar.AM_PM) == 1){  // PM인 경우에는 시간을 12를 더해준다. 1은 PM
            hour = hour + 12;
        }

        int minute = calendar.get(Calendar.MINUTE);

        int second = calendar.get(Calendar.SECOND);

        byte[] commonModePacket = new byte[10];

        commonModePacket[0] = startByte;
        commonModePacket[1] = commonMode;
        commonModePacket[2] = (byte)year;
        commonModePacket[3] = (byte)month;
        commonModePacket[4] = (byte)day;
        commonModePacket[5] = (byte)hour;
        commonModePacket[6] = (byte)minute;
        commonModePacket[7] = (byte)second;
        commonModePacket[8] = commonLengthByte;
        commonModePacket[9] = endByte;

        Log.d(TAG, "일반모드 패킷 생성");
        return commonModePacket;
    }

    byte[] makeRealTimeModePacket(){  // 실시간모드용 요청 패킷
        /* 패킷의 구성
        | Start(0xFF) | Mode(0x02) | Length(0x02) | End(0xFE) |
        1, 1, 1, 1 바이트씩 총 4Byte 크기의 패킷
         */

        byte[] commonModePacket = new byte[4];

        commonModePacket[0] = startByte;
        commonModePacket[1] = realTimeMode;
        commonModePacket[2] = realTimeLengthByte;
        commonModePacket[3] = endByte;

        Log.d(TAG, "실시간모드 패킷 생성");
        return commonModePacket;
    }

    int StringToInt(String stringData){
        return Integer.parseInt(stringData);
    }

    String byteToString(byte[] byteData){
        String stringData = "";
        try{
            stringData = new String(byteData, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return stringData;
    }
}
