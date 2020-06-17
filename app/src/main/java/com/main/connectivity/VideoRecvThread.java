package com.main.connectivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import androidx.renderscript.*;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;


public class VideoRecvThread extends Thread {
    private boolean streamOn;
    private DatagramSocket src;

    private MediaCodec mediaCodec;

    private Bitmap outBitmap;
    private final Object outBitmapLock = new Object();
    private boolean newFrameReady;

    private RenderScript rs;
    private ScriptC_yuv420888 mYuv420;

    public VideoRecvThread(DatagramSocket src, Context appContext) {
        this.src = src;
        this.streamOn = false;
        this.newFrameReady = false;

        rs = RenderScript.create(appContext);
        mYuv420 = new ScriptC_yuv420888(rs);

        outBitmap = Bitmap.createBitmap(TelloController.VIDEO_WIDTH, TelloController.VIDEO_HEIGHT, Bitmap.Config.ARGB_8888);
    }

    private void YUV_420_888_toRGB_out(Image image, int width, int height){
        // Get the three image planes
        Image.Plane[] planes = image.getPlanes();

        // Get the three image planes
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] y = new byte[buffer.remaining()];
        buffer.get(y);

        buffer = planes[1].getBuffer();
        byte[] u = new byte[buffer.remaining()];
        buffer.get(u);

        buffer = planes[2].getBuffer();
        byte[] v = new byte[buffer.remaining()];
        buffer.get(v);

        // get the relevant RowStrides and PixelStrides
        // (we know from documentation that PixelStride is 1 for y)
        int yRowStride= planes[0].getRowStride();
        int uvRowStride= planes[1].getRowStride();  // we know from   documentation that RowStride is the same for u and v.
        int uvPixelStride= planes[1].getPixelStride();  // we know from   documentation that PixelStride is the same for u and v.

        // Y,U,V are defined as global allocations, the out-Allocation is the Bitmap.
        // Note also that uAlloc and vAlloc are 1-dimensional while yAlloc is 2-dimensional.
        Type.Builder typeUcharY = new Type.Builder(rs, Element.U8(rs));
        typeUcharY.setX(yRowStride).setY(height);
        Allocation yAlloc = Allocation.createTyped(rs, typeUcharY.create());
        yAlloc.copyFrom(y);
        mYuv420.set_ypsIn(yAlloc);

        Type.Builder typeUcharUV = new Type.Builder(rs, Element.U8(rs));
        // note that the size of the u's and v's are as follows:
        //      (  (width/2)*PixelStride + padding  ) * (height/2)
        // =    (RowStride                          ) * (height/2)
        // but I noted that on the S7 it is 1 less...
        typeUcharUV.setX(u.length);
        Allocation uAlloc = Allocation.createTyped(rs, typeUcharUV.create());
        uAlloc.copyFrom(u);
        mYuv420.set_uIn(uAlloc);

        Allocation vAlloc = Allocation.createTyped(rs, typeUcharUV.create());
        vAlloc.copyFrom(v);
        mYuv420.set_vIn(vAlloc);

        // handover parameters
        mYuv420.set_picWidth(width);
        mYuv420.set_uvRowStride (uvRowStride);
        mYuv420.set_uvPixelStride (uvPixelStride);

        Allocation outAlloc = Allocation.createFromBitmap(rs, outBitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);

        Script.LaunchOptions lo = new Script.LaunchOptions();
        lo.setX(0, width);  // by this we ignore the y’s padding zone, i.e. the right side of x between width and yRowStride
        lo.setY(0, height);

        mYuv420.forEach_doConvert(outAlloc,lo);
        synchronized (outBitmapLock) {
            outAlloc.copyTo(outBitmap);
            newFrameReady = true;
        }
    }

    public Bitmap getLatestFrame() {
        synchronized (outBitmapLock) {
            newFrameReady = false;
            Bitmap tmp = outBitmap.copy(outBitmap.getConfig(), true);
            return tmp;
        }
    }

    public boolean isNewFrameReady() {
        synchronized (outBitmapLock) {
            return newFrameReady;
        }
    }

    public void run() {
        this.streamOn = true;

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                TelloController.VIDEO_WIDTH, TelloController.VIDEO_HEIGHT);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);

        try {
            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(format, null, null, 0);
            mediaCodec.start();
        } catch (IOException e) {
            Log.e("tello", Log.getStackTraceString(e.getCause()));
        }

        ByteArrayOutputStream bs = new ByteArrayOutputStream(1000000);

        byte[] dpData = new byte[2048];
        DatagramPacket dp = new DatagramPacket(dpData, dpData.length);

        while (streamOn) {
            try {
                src.receive(dp);
                bs.write(dp.getData(), dp.getOffset(), dp.getLength());
            } catch (IOException e) {
                Log.e("tello", Log.getStackTraceString(e.getCause()));
            }

            if (dp.getLength() != 1460) {
                /* Input for codec */
                int inIndex = mediaCodec.dequeueInputBuffer(0);
                if (inIndex >= 0) {
                    ByteBuffer input = mediaCodec.getInputBuffer(inIndex);
                    input.put(bs.toByteArray());
                    mediaCodec.queueInputBuffer(inIndex, 0, bs.size(), 16, 0);
                }

                /* Output from codec */
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                if(outIndex >= 0) {
                    final Image output = mediaCodec.getOutputImage(outIndex);
                    YUV_420_888_toRGB_out(output, TelloController.VIDEO_WIDTH, TelloController.VIDEO_HEIGHT);
                    mediaCodec.releaseOutputBuffer(outIndex, false);
                }

                bs.reset();
            }

        }

        mediaCodec.stop();
        mediaCodec.release();
    }

    public void streamOff() {
        streamOn = false;
    }
}
