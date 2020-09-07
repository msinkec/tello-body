package com.main.control;

import android.content.Context;
import android.util.Log;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class GestureClassifier {

    private Module model;
    private float[] floatBuffer;

    public GestureClassifier(Context context) {
        floatBuffer = new float[28];

        model = Module.load(assetFilePath(context, "gesture-classifier.pt.1"));
    }

    private static String assetFilePath(Context context, String assetName) {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            Log.e("tello", "Error process asset " + assetName + " to file path");
        }
        return null;
    }


    public void setBuffer(float[][] coords) {
        int j = 0;
        for (int i = 0; i < 14; i++) {
            floatBuffer[j++] = coords[0][i] / 95.0f;
            floatBuffer[j++] = coords[1][i] / 95.0f;
        }
    }

    public int predict() {
        Tensor input = Tensor.fromBlob(floatBuffer, new long[]{1, 28});

        Tensor outputTensor = model.forward(IValue.from(input)).toTensor();
        float[] scores = outputTensor.getDataAsFloatArray();

        float max = 0.0f;
        int label = 0;
        for (int i = 0; i < 7; i++) {
            float val = scores[i];
            if (val > max) {
                max = val;
                label = i;
            }
        }

        return label;
    }
}
