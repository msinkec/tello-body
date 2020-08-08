package com.main.control;

import android.graphics.Bitmap;
import android.util.Log;

import com.main.connectivity.TelloController;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.HOGDescriptor;

public class BodyDetector {

    private HOGDescriptor hog;
    private Mat mMat;
    private Size winStride, padding;
    private double scale;

    public BodyDetector() {
        // SVM model for DETECTING bodies (not estimating pose)
        hog = new HOGDescriptor();
        //hog.setSVMDetector(HOGDescriptor.getDaimlerPeopleDetector());
        hog.setSVMDetector(HOGDescriptor.getDefaultPeopleDetector());

        mMat = new Mat(TelloController.VIDEO_WIDTH, TelloController.VIDEO_HEIGHT,  CvType.CV_8UC3);
        winStride = new Size(8, 8);
        padding = new Size(32, 32);
        scale = 1.05;
    }

    public boolean containsBody(Bitmap bitmap) {
        // Convert Bitmap to Mat
        Bitmap bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        //Utils.bitmapToMat(bmp32, mMat);
        Mat tmp = new Mat();
        Utils.bitmapToMat(bmp32, tmp);
        Imgproc.cvtColor(tmp, mMat, Imgproc.COLOR_BGRA2BGR);
        Utils.bitmapToMat(bitmap, tmp);

        MatOfPoint res = new MatOfPoint();
        MatOfDouble weights = new MatOfDouble();
        hog.detect(mMat, res, weights, 0, winStride, padding);

        return !res.empty();
    }
}
