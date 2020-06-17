package com.main.control;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Size;
import org.opencv.objdetect.HOGDescriptor;

public class BodyDetector {

    private HOGDescriptor hog;
    private Mat mMat;
    private Size winStride, padding;
    private double scale;

    public BodyDetector() {
        // SVM model for DETECTING bodies (not estimating pose)
        hog = new HOGDescriptor();
        hog.setSVMDetector(HOGDescriptor.getDaimlerPeopleDetector());

        mMat = new Mat();
        winStride = new Size(8, 8);
        padding = new Size(32, 32);
        scale = 1.05;
    }

    public boolean containsBody(Bitmap bitmap) {
        // Convert Bitmap to Mat
        Bitmap bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Utils.bitmapToMat(bmp32, mMat);

        MatOfPoint res = new MatOfPoint();
        hog.detect(mMat, res, null, 0, winStride, padding);

        return !res.empty();
    }
}
