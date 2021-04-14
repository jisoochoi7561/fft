package com.zeroindexed.piedpiper;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.casualcoding.reedsolomon.EncoderDecoder;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;


public class MainActivity extends AppCompatActivity implements ToneThread.ToneCallback { // AcitivityBody(?) ==> AppCompatActivity로 변경
    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int FEC_BYTES = 4;
    private final int MY_PERMISSIONS_RECORD_AUDIO = 1;

    EditText text;
    View play_tone;
    View listen_tone;
    ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        text = (EditText) findViewById(R.id.text);
        play_tone = findViewById(R.id.play_tone);
        listen_tone = findViewById(R.id.listen_tone);
        progress = (ProgressBar) findViewById(R.id.progress);
        play_tone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = text.getText().toString();
                byte[] payload = new byte[0];
                payload = message.getBytes(Charset.forName("UTF-8"));

                EncoderDecoder encoder = new EncoderDecoder();
                final byte[] fec_payload;
                try {
                    fec_payload = encoder.encodeData(payload, FEC_BYTES);
                } catch (EncoderDecoder.DataTooLargeException e) {
                    return;
                }

                ByteArrayInputStream bis = new ByteArrayInputStream(fec_payload);

                play_tone.setEnabled(false);
                ToneThread.ToneIterator tone = new BitstreamToneGenerator(bis, 7);
                new ToneThread(tone, MainActivity.this).start();
            }
        });

        listen_tone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, "Listen Start!", Toast.LENGTH_SHORT).show();
                try {
                    requestAudioPermissions();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        });
    }

    public void receiveMessage() throws InterruptedException {

        Thread receive = new Thread(new Runnable() {

            Listentone recv_tone = new Listentone();

            @Override
            public void run() {
                recv_tone.Listen_main();
            }
        });
        Toast.makeText(MainActivity.this, "ThreadStart!", Toast.LENGTH_SHORT).show();

        receive.start();

    }

    private void requestAudioPermissions() throws InterruptedException {
        if (ContextCompat.checkSelfPermission(this,  // checkSelfPermission 오류 ==> build.gradle (Module: app)의 dependencies 수정
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            //When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,  // shouldShowRequestPermissionRationale 오류 ==> build.gradle (Module: app)의 dependencies 수정
                    Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "Please grant permissions to record audio", Toast.LENGTH_LONG).show();

                //Give user option to still opt-in the permissions
                ActivityCompat.requestPermissions(this,  // requestPermissions 오류 ==> build.gradle (Module: app)의 dependencies 수정
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);

            } else {
                // Show user dialog to grant permission to record audio
                ActivityCompat.requestPermissions(this,  // requestPermissions 오류 ==> build.gradle (Module: app)의 dependencies 수정
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);
            }
        }
        //If permission is granted, then go ahead recording audio
        else if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {

            //Go ahead with recording audio now
            Toast.makeText(MainActivity.this, "ListenOn Class In Right Away", Toast.LENGTH_SHORT).show();
            receiveMessage();
        }

    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_RECORD_AUDIO: {
                // 사용권한에 대한 콜백을 받음
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! (권한 동의 버튼 선택)
                    try { // 동의했을 경우 다음의 함수로
                        receiveMessage();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    // 사용자가 권한 동의를 하지 않음(권한 동의안함 버튼 선택)
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "Permissions Denied to record audio", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    @Override
    public void onProgress(int current, int total) {
        progress.setMax(total);
        progress.setProgress(current);
    }

    @Override
    public void onDone() {
        play_tone.setEnabled(true);
        progress.setProgress(0);
    }
}
