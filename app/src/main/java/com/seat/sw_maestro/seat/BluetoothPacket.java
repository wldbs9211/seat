package com.seat.sw_maestro.seat;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.Calendar;

/**
 * Created by JiYun on 2016. 11. 6..
 */
public class BluetoothPacket {
    final static String TAG = "BluetoothPacket";

    final static byte startByte = (byte)0xFF;   // 시작 알림 바이트
    final static byte endByte = (byte)0xFE;     // 끝 알림 바이트
    final static byte commonLengthByte = (byte)0x07;    // 일반모드 길이
    final static byte realTimeLengthByte = (byte)0x02;  // 실시간모드 길이
    final static byte commonMode = (byte)0x01;  // 일반모드 뜻함
    final static byte realTimeMode = (byte)0x02;  // 실시간모드 뜻함

    // 타입 사이즈. (mode, date, value, position, length)
    final static int typeSize = 5;

    // 데이터 타입별 내부 데이터의 개수 (1이면 아래 타입별 변수를 배열로 만들 필요가 없지)
    final static int modeSize = 1;
    final static int dateSize = 4;
    final static int valueSize = 9;
    final static int positionSize = 2;
    final static int lengthSize = 1;

    // 데이터 타입별 변수
    int mode;
    int[] date = new int[dateSize];
    int[] value = new int[valueSize];
    int[] position = new int[positionSize];

    // 데이터 타입별 변수 temp
    String mode2;
    String[] date2 = new String[dateSize];
    String[] value2 = new String[valueSize];
    String[] position2 = new String[positionSize];

    BluetoothPacket(){

    }

    void decodePacket(byte[] inputPacket){
        Log.d(TAG, "패킷을 이제 디코딩한다.");

        if((inputPacket[0] == startByte) && (inputPacket[inputPacket.length-1] == endByte)){    // 정상 패킷 검사 1(Start,End 검사)

            // Byte를 String으로 일단 바꿔줍니다.
            String packetToString = "";
            try{
                packetToString = new String(inputPacket, "UTF-8");
                Log.d(TAG, "packetToString : " + packetToString);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            // Type별(모드~날짜~벨류~좌표~길이 들어옴)로 ~를 기준으로 짜른다.
            String[] splitedIntoPart;
            splitedIntoPart = packetToString.split("~");

        }else{
            Log.d(TAG, "버리는 패킷임");
        }
    }

    byte[] makeCommonModePacket(){  // 일반모드용 요청 패킷
        /* 패킷의 구성
        | Start(0xFF) | Mode(0x01) | YY MM DD hh mm | Length(0x07) | End(0xFE) |
        1, 1, 5, 1, 1 바이트씩 총 9Byte 크기의 패킷
         */

        Calendar calendar = Calendar.getInstance(); // 현재시간

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

        byte[] commonModePacket = new byte[9];

        commonModePacket[0] = startByte;
        commonModePacket[1] = commonMode;
        commonModePacket[2] = (byte)year;
        commonModePacket[3] = (byte)month;
        commonModePacket[4] = (byte)day;
        commonModePacket[5] = (byte)hour;
        commonModePacket[6] = (byte)minute;
        commonModePacket[7] = commonLengthByte;
        commonModePacket[8] = endByte;

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
}
