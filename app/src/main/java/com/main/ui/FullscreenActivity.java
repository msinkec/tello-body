package com.main.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.main.connectivity.TelloController;
import com.main.connectivity.VideoRecvThread;
import com.main.control.FeedbackControlLoop;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

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

    private Context context;

    private TelloController tello;
    private VideoRecvThread videoThread;
    private FeedbackControlLoop fcl;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("tello", "OpenCV loaded successfully");
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    private final View.OnClickListener mStartStopBtnOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
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

        if (!OpenCVLoader.initDebug()) {
            Log.d("tello", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d("tello", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
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
