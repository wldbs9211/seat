package com.seat.sw_maestro.seat;

/**
 * Created by JiYun on 2016. 11. 6..
 */
public class BluetoothPacket {
    byte start;
    byte mode;
    byte length;
    byte[] realTime = new byte[6];
    short[] value;
    byte[] crc;
    byte end;

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

}
