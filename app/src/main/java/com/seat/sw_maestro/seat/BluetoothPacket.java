package com.seat.sw_maestro.seat;

import android.util.Log;

import java.util.Calendar;

/**
 * Created by JiYun on 2016. 11. 6..
 */
public class BluetoothPacket {
    final static String TAG = "BluetoothPacket";

    byte start;
    byte mode;
    byte length;
    byte[] realTime = new byte[6];
    short[] value;
    byte[] crc;
    byte end;

    BluetoothPacket(){

    }

    BluetoothPacket(byte[] data) {
        start = data[0];
        mode = data[1];
        length = data[2];

        switch (mode) {
            case 1: // 일반모드의 패킷
                for (int i = 0; i < 6; i++) {
                    realTime[i] = data[i + 3];
                }

                for (int i = 0; i < 9; i++) {
                    value[i] = data[i*2 + 10];
                }

                for (int i = 0; i < 4; i++) {
                    crc[i] = data[i + 35];
                }
                end = data[39];
                break;

            case 2: // 실시간 모드의 패킷

        }
    }


    int getStart(){
        return (int)start;
    }

    int getMode(){
        return (int)mode;
    }

    int getLength(){
        return (int)length;
    }

    int getEnd(){
        return (int)end;
    }

    byte[] getRealTime(){
        return realTime;
    }

    short[] getValue(){
        return value;
    }

    /*
        String Message="ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String Number="1234567890";
        String HanGul="ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎ";

        byte[] TotalByteMessage= new byte[Message.length() + Number.length() + HanGul.length()];

        TotalByteMessage = (Message + Number + HanGul).getBytes();

        System.out.println(TotalByteMessage);

        String byteToString = new String(TotalByteMessage,0,TotalByteMessage.length);

        System.out.println(byteToString);
     */

    Calendar calendar = Calendar.getInstance(); // 현재시간



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

        commonModePacket[0] = (byte)0xFF;
        commonModePacket[1] = (byte)0x01;
        commonModePacket[2] = (byte)year;
        commonModePacket[3] = (byte)month;
        commonModePacket[4] = (byte)day;
        commonModePacket[5] = (byte)hour;
        commonModePacket[6] = (byte)minute;
        commonModePacket[7] = (byte)0x07;
        commonModePacket[8] = (byte)0xFE;

        Log.d(TAG, "일반모드 패킷 생성");
        return commonModePacket;
    }

    byte[] makeRealTimeModePacket(){  // 실시간모드용 요청 패킷
        /* 패킷의 구성
        | Start(0xFF) | Mode(0x02) | Length(0x02) | End(0xFE) |
        1, 1, 1, 1 바이트씩 총 4Byte 크기의 패킷
         */

        byte[] commonModePacket = new byte[4];

        commonModePacket[0] = (byte)0xFF;
        commonModePacket[1] = (byte)0x02;
        commonModePacket[2] = (byte)0x02;
        commonModePacket[3] = (byte)0xFE;

        Log.d(TAG, "실시간모드 패킷 생성");
        return commonModePacket;
    }
}
