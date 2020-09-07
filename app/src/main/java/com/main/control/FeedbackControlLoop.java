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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;

public class FeedbackControlLoop extends Thread {

    private Context context;

    private Handler uiHandler;
    private TelloController tello;
    private VideoRecvThread videoRecvThread;
    private PoseEstimator poseEstimator;
    private BodyDetector bodyDetector;
    private GestureClassifier gestureClassifier;

    private int frameCntr = 0;

    private volatile boolean isRunning;

    private final Object latestResultsLock = new Object();
    private float[][]  latestPose;
    private int latestGestureLabel;

    private final Object rcStateLock = new Object();
    private int[] baseRcState = new int[4];
    private int[] rcStateDelta = new int[4];

    private Canvas canvas;
    private Paint drawPaint;

    private int[] posePointColors = {
            Color.parseColor("#33cc33"),
            Color.parseColor("#00ffff"),
            Color.parseColor("#ff3300"),
            Color.parseColor("#ff0066"),
            Color.parseColor("#cc00cc"),
            Color.parseColor("#9900cc"),
            Color.parseColor("#ffff00"),
            Color.parseColor("#996633"),
            Color.parseColor("#ff99ff"),
            Color.parseColor("#006600"),
            Color.parseColor("#ff6600"),
            Color.parseColor("#993366"),
            Color.parseColor("#800000"),
            Color.parseColor("#009999"),
    };

    public FeedbackControlLoop(Context context, Handler uiHandler, TelloController tello,
                               VideoRecvThread videoRecvThread) {
        this.uiHandler = uiHandler;
        this.tello = tello;
        this.videoRecvThread = videoRecvThread;
        this.context = context;

        // Models
        bodyDetector = new BodyDetector();
        poseEstimator = new PoseEstimator(context);
        gestureClassifier = new GestureClassifier(context);

        canvas = new Canvas();
        canvas.drawColor(Color.TRANSPARENT);
        drawPaint = new Paint();
        drawPaint.setColor(Color.RED);
        drawPaint.setStyle(Paint.Style.FILL);
        drawPaint.setAntiAlias(true);
        drawPaint.setTextSize(40);
    }

    private void displayFrame(Bitmap frame) {
        Message msg = new Message();
        msg.obj = frame;
        uiHandler.sendMessage(msg);
    }

    private void drawResults(Bitmap frame) {
        canvas.setBitmap(frame);
        for (int i = 0; i < 14; i++) {
            drawPaint.setColor(posePointColors[i]);
            canvas.drawCircle(poseEstimator.pointArray[0][i] * (TelloController.VIDEO_WIDTH / 96f),
                    poseEstimator.pointArray[1][i] * (TelloController.VIDEO_HEIGHT / 96f),
                    8, drawPaint);
        }

        drawPaint.setColor(Color.RED);
        canvas.drawText(String.valueOf(latestGestureLabel),15,35, drawPaint);
    }

    public void run() {

        int lastLabel = 0;
        int labelCounter = 0;

        isRunning = true;
        while (isRunning) {
            if (!videoRecvThread.isNewFrameReady()) {
                continue;
            }

            Bitmap frame = videoRecvThread.getLatestFrame();
            if (frame == null) continue;

            frameCntr++;
            if (frameCntr == 1) {

                try {
                    if (tello.isInAir()) {

                        //if (bodyDetector.containsBody(frame)) {
                        if (true) {
                            poseEstimator.setBuffer(frame);
                            poseEstimator.predict();

                            // Try adjusting view, so that the person will be in the center of the frame
                            adjustView(poseEstimator.pointArray);

                            // Classify gesture
                            gestureClassifier.setBuffer(poseEstimator.pointArray);
                            int label = gestureClassifier.predict();


                            // To mitigate false classifications try to adjust label, if something obvious doesn't match up.
                            label = manuallyAdjustLabel(label, poseEstimator.pointArray);

                            // Change rc state according to label, if this label was predicted for at least a few frames
                            if (label == lastLabel) {
                                if (labelCounter >= 3) {
                                    File path = this.context.getExternalFilesDir(null);
                                    File file = new File(path, "classifications.csv");
                                    try {
                                        FileWriter fw = new FileWriter(file, true);
                                        try {
                                            fw.write(Calendar.getInstance().getTime().toString());
                                            fw.write(" ");
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
                                    updateRcFromLabel(label);
                                }
                                labelCounter++;
                            } else {
                                lastLabel = label;
                                labelCounter = 0;
                            }

                            // Save latest pose data for other threads to retrieve.
                            synchronized (latestResultsLock) {
                                latestPose = poseEstimator.pointArray.clone();
                                latestGestureLabel = label;
                            }

                            // Draw results on Bitmap
                            drawResults(frame);
                        } else {
                            // If the frame doesn't contain a body, keep rotating.
                            //rcStateDelta[3] = 10;
                        }

                        // Execute current rc state.
                        synchronized (rcStateLock) {
                            tello.rc(baseRcState[0] + rcStateDelta[0],
                                    baseRcState[1] + rcStateDelta[1],
                                    baseRcState[2] + rcStateDelta[2],
                                    baseRcState[3] + rcStateDelta[3], false);
                        }
                        resetRcStateDelta();

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

    public float[][] getLatestPose() {
        synchronized (latestResultsLock) {
            return latestPose.clone();
        }
    }

    public void addToRcState(int lr, int fb, int ud, int yaw) {
        synchronized (rcStateLock) {
            baseRcState[0] += lr;
            baseRcState[1] += fb;
            baseRcState[2] += ud;
            baseRcState[3] += yaw;
        }
    }

    public void setRcState(int lr, int fb, int ud, int yaw) {
        synchronized (rcStateLock) {
            baseRcState[0] = lr;
            baseRcState[1] = fb;
            baseRcState[2] = ud;
            baseRcState[3] = yaw;
        }
    }

    public int[] getRcState() {
        synchronized (rcStateLock) {
            return baseRcState.clone();
        }
    }

    private void addToRcStateDelta(int lr, int fb, int ud, int yaw) {
        rcStateDelta[0] += lr;
        rcStateDelta[1] += fb;
        rcStateDelta[2] += ud;
        rcStateDelta[3] += yaw;
    }

    private void resetRcStateDelta() {
        rcStateDelta[0] = 0;
        rcStateDelta[1] = 0;
        rcStateDelta[2] = 0;
        rcStateDelta[3] = 0;
    }

    private void adjustView(float[][] pose) {
        if (isAllZero(pose))
            return;

        boolean overXRange = false;
        boolean underXRange = false;
        int minX = 14;
        int maxX = 81;
        for (int i = 0; i < 14; i++) {
            float x = pose[0][i];
            if (x > maxX) {
                overXRange = true;
            } else if (x < minX) {
                underXRange = true;
            }
        }

        if (overXRange && !underXRange) {
            addToRcStateDelta(0,0, 0, 60);
        } else if (underXRange && !overXRange) {
            addToRcStateDelta(0,0, 0, -60);
        }
    }

    private boolean isAllZero(float[][] pose) {
        for (int i = 0; i < 14; i++) {
            if (pose[0][i] != 0.0 || pose[1][i] != 0.0)
                return false;
        }
        return true;
    }

    private int manuallyAdjustLabel(int label, float[][] pose) {
        // If every element of the pose array is 0, that means no pose was actually estimated, hence we assign label 0
        if (isAllZero(pose))
            return 0;

        int newLabel = label;
        switch (label) {
            case 5:
                // The model sometimes misclassifies 4 as 5.
                float rElbowX = pose[0][3];
                float rPalmX = pose[0][4];

                if (rPalmX <= rElbowX) {
                    // if this is true, the label cannot be 5 and is likely 4
                    newLabel = 4;
                }
                break;
            case 6:
                float lElbowX = pose[0][6];
                float lPalmX = pose[0][7];
                if (lPalmX >= lElbowX) {
                    // if this is true, the label cannot be 6 and is likely 4
                    newLabel = 4;
                }
                break;
        }
        return newLabel;
    }

    private void updateRcFromLabel(int label) {
        switch (label) {
            case 0:
                break;
            case 1:
                addToRcStateDelta(0,50, 0, 0);
                break;
            case 2:
                addToRcStateDelta(0,-50, 0, 0);
                break;
            case 3:
                addToRcStateDelta(0,0, 40, 0);
                break;
            case 4:
                addToRcStateDelta(0,0, -40, 0);
                break;
            case 5:
                addToRcStateDelta(40, 0, 0, 0);
                break;
            case 6:
                addToRcStateDelta(-40,0, 0, 0);
                break;
        }
    }
}
