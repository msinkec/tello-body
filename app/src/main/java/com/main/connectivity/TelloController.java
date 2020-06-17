package com.main.connectivity;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class TelloController {

    /**
     * Tello always listens for commands on UDP port 8889,
     * and sends video stream to port 11111 on the receiver.
     */
    public static final int TELLO_CONTROL_PORT = 8889;
    public static final int VIDEO_PORT = 11111;
    public static final int COMMAND_TIMEOUT = 2000;
    public static final int VIDEO_WIDTH = 960;
    public static final int VIDEO_HEIGHT = 720;

    private DatagramSocket controlSock, videoSock;
    private InetAddress telloAddr;

    private VideoRecvThread videoRecvThread;

    private boolean inAir;
    private boolean videoMode;

    public TelloController(InetAddress telloAddr, int localPort) throws SocketException {
        this.telloAddr = telloAddr;
        inAir = false;
        videoMode = false;

        controlSock = new DatagramSocket(localPort);
        videoSock = new DatagramSocket(VIDEO_PORT);

        // Set response timeout for commands.
        controlSock.setSoTimeout(COMMAND_TIMEOUT);
    }

    public void disconnect() throws IOException {
        if (isInAir()) {
            land();
        }
        if (videoRecvThread != null) {
            videoRecvThread.streamOff();
        }
        if (videoMode) {
            sendControlCommand("streamoff", true);
        }
    }

    public VideoRecvThread connectWithVideo(Context appContext) throws IOException {
        // Enable command mode on Tello.
        sendControlCommand("command", true);

        // Wait for a second to make sure Tello entered command mode, then send command for
        // enabling the camera video stream.
        sendControlCommand("streamon", 1000, true);

        // Start video receive thread.
        videoRecvThread = new VideoRecvThread(videoSock, appContext);
        videoRecvThread.start();

        return videoRecvThread;
    }

    public boolean isInAir() throws IOException {
        //String resp = sendControlCommand("time?");
        //return !resp.equals("0s");

        return inAir;
    }

    public String takeoff() throws IOException {
        String resp = sendControlCommand("takeoff", true);
        if (resp.equals("ok")) {
            inAir = true;
        }
        return resp;
    }

    public String land() throws IOException {
        String resp = sendControlCommand("land", true);
        if (resp.equals("ok")) {
            inAir = false;
        }
        return resp;
    }

    public String rotateCW(int degrees, boolean waitForResp) throws IOException {
        degrees *= 10;
        return sendControlCommand("cw " + String.valueOf(degrees), waitForResp);
    }

    public String rotateCCW(int degrees, boolean waitForRes) throws IOException {
        degrees *= 10;
        return sendControlCommand("ccw " + String.valueOf(degrees), waitForRes);
    }

    public String rc(int lr, int fb, int ud, int yaw, boolean waitForResp) throws IOException {
        // All values must be between -100 and 100
        StringBuilder sb = new StringBuilder();
        sb.append("rc ");
        sb.append(String.valueOf(lr));
        sb.append(" ");
        sb.append(String.valueOf(fb));
        sb.append(" ");
        sb.append(String.valueOf(ud));
        sb.append(" ");
        sb.append(String.valueOf(yaw));
        return sendControlCommand(sb.toString(), waitForResp);
    }

    private String sendControlCommand(String command, boolean waitForResp) throws IOException {
        return sendControlCommand(command, 0, waitForResp);
    }

    private String sendControlCommand(String command, long commandDelay, boolean waitForResp) throws IOException {
        try {
            Thread.sleep(commandDelay);
        } catch (InterruptedException e) {
            Log.e("tello", Log.getStackTraceString(e.getCause()));
        }

        Log.i("tello", "Sending command: " + command);

        DatagramPacket dp = new DatagramPacket(
                command.getBytes(), command.length(), telloAddr, TELLO_CONTROL_PORT);
        controlSock.send(dp);

        if (!waitForResp) {
            return null;
        }

        // Wait for response until timeout.
        byte[] respData = new byte[1024];
        DatagramPacket resp = new DatagramPacket(respData, respData.length);
        String rcvd = null;
        try {
            controlSock.receive(resp);
            rcvd = new String(resp.getData(), 0, resp.getLength());
            Log.i("tello", "Response: " + rcvd);
        } catch (SocketTimeoutException e) {
            Log.e("tello", "Response timeout.");
            return "timeout";
        }

        return rcvd;
    }

}
