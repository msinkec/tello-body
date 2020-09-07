package com.main.control;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import com.xiaomi.mace.JniMaceUtils;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.nio.FloatBuffer;

public class PoseEstimator {

    private FloatBuffer floatBuffer;
    private int[] intValues;
    protected float[][] pointArray;
    private Mat mMat;

    public PoseEstimator(Context context) {
        int lengthValues = 192 * 192 * 3;
        float[] floatValues = new float[lengthValues];
        floatBuffer = FloatBuffer.wrap(floatValues, 0, lengthValues);
        intValues = new int[192 * 192];

        String storagePath = context.getFilesDir().getAbsolutePath() + File.separator + "mace";
        File file = new File(storagePath);
        if (!file.exists()) {
            try {
                file.mkdir();
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }

        int result = JniMaceUtils.maceMobilenetCreateGPUContext(storagePath);

        int ompNumThreads = 2;
        int cpuAffinityPolicy = 1;
        int gpuPerfHint = 3;
        int gpuPriorityHint = 3;

        result = JniMaceUtils.maceMobilenetCreateEngine(
                ompNumThreads, cpuAffinityPolicy,
                gpuPerfHint, gpuPriorityHint,
                "cpm_v1", "GPU");
    }

    public void setBuffer(Bitmap img) {
        floatBuffer.rewind();

        // Resize bitmap to fit input scale of the model.
        img = Bitmap.createScaledBitmap(img, 192, 192, false);

        img.getPixels(intValues, 0, img.getWidth(), 0, 0, img.getWidth(), img.getHeight());
        int pixel = 0;
        long startTime = SystemClock.uptimeMillis();
        for (int i = 0; i < 192; ++i) {
            for (int j = 0; j < 192; ++j) {
                final int val = intValues[pixel++];
                floatBuffer.put(val & 0xFF);
                floatBuffer.put((val >> 8) & 0xFF);
                floatBuffer.put((val >> 16) & 0xFF);
            }
        }
        long endTime = SystemClock.uptimeMillis();
        Log.d("pose-estimation", "Timecost to put frame values into ByteBuffer: " + Long.toString(endTime - startTime));
    }

    public void predict() {
        float[] result = JniMaceUtils.maceMobilenetClassify(floatBuffer.array());

        // TODO: check if OpenCV was initialized

        if (pointArray == null)
            pointArray = new float[2][14];

        if (mMat == null)
            mMat = new Mat(96, 96, CvType.CV_32F);

        float[] tempArray = new float[96 * 96];
        float[] outTempArray = new float[96 * 96];

        long st = System.currentTimeMillis();

        for (int i = 0; i < 14; i++) {
            int index = 0;
            for (int x = 0; x < 96; x++) {
                for (int y = 0; y < 96; y++) {
                    tempArray[index] = result[x * 96 * 14 + y * 14 + i];
                    index++;
                }
            }

            mMat.put(0, 0, tempArray);
            Imgproc.GaussianBlur(mMat, mMat, new Size(5, 5), 0, 0);
            mMat.get(0, 0, outTempArray);

            float maxX = 0, maxY = 0;
            float max = 0;

            for (int x = 0; x < 96; x++) {
                for (int y = 0; y < 96; y++) {
                    float center = get(x, y, outTempArray);

                    if (center >= 0.01) {

                        if (center > max) {
                            max = center;
                            maxX = x;
                            maxY = y;
                        }
                    }
                }
            }

            if (max == 0) {
                pointArray = new float[2][14];
                return;
            }

            pointArray[0][i] = maxY;
            pointArray[1][i] = maxX;
        }

        Log.i("post_processing", "" + (System.currentTimeMillis() - st));
    }

    private float get(int x, int y, float[] arr) {
        if (x < 0 || y < 0 || x >= 96 || y >= 96)
            return -1;
        return arr[x * 96 + y];
    }

}
