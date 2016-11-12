package com.seat.sw_maestro.seat;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class Tutorial4Activity extends AppCompatActivity {

    int count = 0;
    static final int maxList = 7;

    Button buttonBack;
    Button buttonNext;

    ImageView imageViewPosition;    // 자세 이미지
    TextView textViewTitle;         // 자세 타이틀
    TextView textViewDescription;   // 자세 설명

    static final String[] TitleList = {"정자세입니다.",
                          "왼쪽으로 기울었습니다.",
                          "오른쪽으로 기울었습니다.",
                          "앞으로 기울었습니다.",
                          "뒤로 기울었습니다.",
                          "엉덩이를 앞으로 빼셨습니다.",
                          "왼쪽 다리를 꼬셨습니다.",
                          "오른쪽 다리를 꼬셨습니다."};

    static final String[] DescriptionList = {"이 자세를 유지하세요!",
                                "좌우 균형을 맞추세요!",
                                "좌우 균형을 맞추세요!",
                                "자세를 바로잡으세요!",
                                "자세를 바로잡으세요!",
                                "허리에 몹시 안 좋아요!",
                                "몸에 엄청 안 좋아요!",
                                "몸에 엄청 안 좋아요!"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tutorial4);

        imageViewPosition = (ImageView)findViewById(R.id.imageViewPosition);
        textViewTitle = (TextView)findViewById(R.id.textViewTitle);
        textViewDescription = (TextView)findViewById(R.id.textViewDescription);

        buttonBack = (Button) findViewById(R.id.buttonBack);
        buttonBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                beforeUpdate();
            }
        });

        buttonNext = (Button) findViewById(R.id.buttonNext);
        buttonNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextUpdate();
            }
        });
    }

    void nextUpdate(){
        if(count == maxList){
            finish();
        }else{
            count ++;
            textViewTitle.setText(TitleList[count]);
            textViewDescription.setText(DescriptionList[count]);
            imageupdate();
        }
    }

    void beforeUpdate(){
        if(count == 0){
            finish();
        }else{
            count --;
            textViewTitle.setText(TitleList[count]);
            textViewDescription.setText(DescriptionList[count]);
            imageupdate();
        }
    }

    void imageupdate(){
        switch (count){
            case 0 :
                imageViewPosition.setImageDrawable(getResources().getDrawable(R.drawable.status_realtime_good));
                break;
            case 1 :
                imageViewPosition.setImageDrawable(getResources().getDrawable(R.drawable.status_realtime_left));
                break;
            case 2 :
                imageViewPosition.setImageDrawable(getResources().getDrawable(R.drawable.status_realtime_right));
                break;
            case 3 :
                imageViewPosition.setImageDrawable(getResources().getDrawable(R.drawable.status_realtime_front));
                break;
            case 4 :
                imageViewPosition.setImageDrawable(getResources().getDrawable(R.drawable.status_realtime_back));
                break;
            case 5 :
                //imageViewPosition.setImageDrawable(getResources().getDrawable(R.drawable.status_realtime_hipfront));
                break;
            case 6 :
                //imageViewPosition.setImageDrawable(getResources().getDrawable(R.drawable.status_realtime_good));
                break;
            case 7 :
                //imageViewPosition.setImageDrawable(getResources().getDrawable(R.drawable.status_realtime_good));
                break;
        }
    }
}
