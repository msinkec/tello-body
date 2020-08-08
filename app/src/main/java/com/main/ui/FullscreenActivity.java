package com.main.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StrictMode;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.main.connectivity.TelloController;
import com.main.connectivity.VideoRecvThread;
import com.main.control.FeedbackControlLoop;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity {

    private final Handler mHideHandler = new Handler();

    private View mContentView;
    private View mControlsView;
    private Button mStartStopBtn;
    private ImageView mImageView;

    private FloatingActionButton mPoseBtnOne, mPoseBtnTwo, mPoseBtnThree, mPoseBtnFour,
            mPoseBtnFive, mPoseBtnSix, mAscentBtn, mDescentBtn;

    private Context context;

    private TelloController tello;
    private VideoRecvThread videoThread;
    private FeedbackControlLoop fcl;

    static {
        if(!OpenCVLoader.initDebug()) {
            Log.i("tello", "Unable to load OpenCV");
        } else {
            Log.i("tello", "OpenCV loaded");
        }
    }

    private final View.OnClickListener mStartStopBtnOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            v.setEnabled(false);
            try {
                if (!tello.isInAir()) {
                    tello.takeoff();
                } else {
                    tello.land();
                }

                if (tello.isInAir()) {
                    mStartStopBtn.setText("LAND");
                } else {
                    mStartStopBtn.setText("TAKEOFF");
                }
            } catch (IOException e) {
                Log.e("tello", e.getMessage());
            }
            v.setEnabled(true);
        }
    };

    private final View.OnTouchListener mFlightControlTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if(event.getAction() == MotionEvent.ACTION_DOWN) {
                v.performClick();
                switch (v.getId()) {
                    case R.id.ascent_button:
                        fcl.addToRcState(0,0,40, 0);
                        break;
                    case R.id.descent_button:
                        fcl.addToRcState(0,0,-40, 0);
                        break;
                }

            }

            if(event.getAction() == MotionEvent.ACTION_UP) {
                switch (v.getId()) {
                    case R.id.ascent_button:
                        fcl.addToRcState(0,0,-40, 0);
                        break;
                    case R.id.descent_button:
                        fcl.addToRcState(0,0,+40, 0);
                        break;
                }
            }

            return false;
        }
    };

    private final View.OnClickListener mPoseButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int label = 0;

            switch (v.getId()) {
                case R.id.pose_button_1:
                    label = 1;
                    break;
                case R.id.pose_button_2:
                    label = 2;
                    break;
                case R.id.pose_button_3:
                    label = 3;
                    break;
                case R.id.pose_button_4:
                    label = 4;
                    break;
                case R.id.pose_button_5:
                    label = 5;
                    break;
                case R.id.pose_button_6:
                    label = 6;
                    break;
                case R.id.pose_button_7:
                    label = 6;
                    break;
            }

            float[][] poseData = fcl.getLatestPose();

            File path = context.getExternalFilesDir(null);
            File file = new File(path, "pose-data.csv");
            Log.i("TESTT", file.getAbsolutePath());
            try {
                FileWriter fw = new FileWriter(file, true);
                try {
                    for (int i = 0; i < 14; i++) {
                        fw.write(String.valueOf((int) poseData[0][i]));
                        fw.write(",");
                        fw.write(String.valueOf((int) poseData[1][i]));
                        fw.write(",");
                    }
                    fw.write(String.valueOf(label));
                    fw.write("\n");
                } finally {
                    fw.flush();
                    fw.close();
                }
            } catch (FileNotFoundException e) {
                Log.e("tello", e.getMessage());
            } catch (IOException e) {
                Log.e("tello", e.getMessage());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        this.context = getApplicationContext();

        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);
        mImageView = findViewById(R.id.imageView);
        mStartStopBtn = findViewById(R.id.start_stop_button);

        mPoseBtnOne = findViewById(R.id.pose_button_1);
        mPoseBtnTwo = findViewById(R.id.pose_button_2);
        mPoseBtnThree = findViewById(R.id.pose_button_3);
        mPoseBtnFour = findViewById(R.id.pose_button_4);
        mPoseBtnFive = findViewById(R.id.pose_button_5);
        mPoseBtnSix = findViewById(R.id.pose_button_6);
        mAscentBtn = findViewById(R.id.ascent_button);
        mDescentBtn = findViewById(R.id.descent_button);

        mPoseBtnOne.setOnClickListener(mPoseButtonClickListener);
        mPoseBtnTwo.setOnClickListener(mPoseButtonClickListener);
        mPoseBtnThree.setOnClickListener(mPoseButtonClickListener);
        mPoseBtnFour.setOnClickListener(mPoseButtonClickListener);
        mPoseBtnFive.setOnClickListener(mPoseButtonClickListener);
        mPoseBtnSix.setOnClickListener(mPoseButtonClickListener);

        mAscentBtn.setOnTouchListener(mFlightControlTouchListener);
        mDescentBtn.setOnTouchListener(mFlightControlTouchListener);

        mStartStopBtn.setText("TAKEOFF");
        mStartStopBtn.setOnClickListener(mStartStopBtnOnClickListener);

        // TODO: dynamically check for connection
        try {
            InetAddress telloInetAdress = InetAddress.getByName("192.168.10.1");
            tello = new TelloController(telloInetAdress, 8889);
            videoThread = tello.connectWithVideo(context);

            Handler uiHandler = new Handler(Looper.getMainLooper()) {
                /*
                 * handleMessage() defines the operations to perform when
                 * the Handler receives a new Message to process.
                 */
                @Override
                public void handleMessage(Message inputMessage) {
                    mImageView.setImageBitmap((Bitmap) inputMessage.obj);
                }
            };

            fcl = new FeedbackControlLoop(context, uiHandler, tello, videoThread);
            fcl.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Hide status and navigation bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop feedback control loop
        fcl.finish();

        // Disconnect from Tello. This will also land it, if it's still in air.
        try {
            tello.disconnect();
        } catch (IOException e) {
            Log.e("tello", e.getMessage());
        }
    }
}
