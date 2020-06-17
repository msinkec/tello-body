package com.main.control;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Message;

import com.main.connectivity.TelloController;
import com.main.connectivity.VideoRecvThread;

import android.os.Handler;
import android.util.Log;


import java.io.IOException;

public class FeedbackControlLoop extends Thread {

    private Handler uiHandler;
    private TelloController tello;
    private VideoRecvThread videoRecvThread;
    private PoseEstimator poseEstimator;
    private BodyDetector bodyDetector;

    private int frameCntr = 0;

    private volatile boolean isRunning;

    public FeedbackControlLoop(Context context, Handler uiHandler, TelloController tello,
                               VideoRecvThread videoRecvThread) {
        this.uiHandler = uiHandler;
        this.tello = tello;
        this.videoRecvThread = videoRecvThread;
        this.poseEstimator = new PoseEstimator(context);

        bodyDetector = new BodyDetector();
    }

    private void displayFrame(Bitmap frame) {
        Message msg = new Message();
        msg.obj = frame;
        uiHandler.sendMessage(msg);
    }

    public void run() {
        Canvas canvas = new Canvas();
        canvas.drawColor(Color.TRANSPARENT);
        Paint pointPaint = new Paint();
        pointPaint.setColor(Color.RED);
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setAntiAlias(true);

        isRunning = true;
        while (isRunning) {
            // TODO update UI stats

            if (!videoRecvThread.isNewFrameReady()) {
                continue;
            }

            Bitmap frame = videoRecvThread.getLatestFrame();
            frameCntr++;

            if (frameCntr == 10) {

                // TODO search for human body
                //      if no body found -> execute rotate command
                //      else -> adjust rotation and position, so that the body will be approximately
                //              in the center of the frame, then detect gesture

                boolean foundBody = true;

                poseEstimator.setBuffer(frame);
                poseEstimator.predict();

                try {
                    if (!tello.isInAir()) {
                        if (poseEstimator.pointArray.length <= 0) {
                            continue;
                        }

                        if (!bodyDetector.containsBody(frame)) {
                            tello.rc(0, 0, 0, 30, false);
                            continue;
                        }

                        // Draw dots on Bitmap
                        canvas.setBitmap(frame);
                        for (int i = 0; i < 14; i++) {
                            canvas.drawCircle(poseEstimator.pointArray[0][i] * (TelloController.VIDEO_WIDTH / 96f),
                                              poseEstimator.pointArray[1][i] * (TelloController.VIDEO_HEIGHT / 96f),
                                    8, pointPaint);
                        }
                    }
                } catch (IOException e) {
                    Log.e("tello", e.getMessage());
                }

                displayFrame(frame);
                frameCntr = 0;
            }
        }
    }

    public void finish() {
        isRunning = false;
    }

}
