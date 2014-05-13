package com.philipp_mandler.hexapod.server;


import com.philipp_mandler.hexapod.hexapod.net.NetPackage;
import com.philipp_mandler.hexapod.hexapod.orientation.BooleanMapManager;
import org.openkinect.freenect.*;

import java.nio.ByteBuffer;

public class VisionModule extends Module implements DepthHandler {

	private SingleServo m_servoRotate = Main.getActuatorManager().getKinectServo(0);
	private SingleServo m_servoTilt = Main.getActuatorManager().getKinectServo(1);

	private Device m_kinect;

	private KinectWorker m_kinectWorker;

	private VideoStreamer m_videoStreamer;

	private double m_rotation = 0;

	private TimeTracker m_timeTracker;


	public VisionModule() {
		super.setName("vision");

		m_timeTracker = Main.getTimeManager().createTracker("vision");
	}

	@Override
	protected void onStart() {
		if(m_servoRotate.isConnected()) m_servoRotate.setGoalPosition(Math.PI + 0.2 + m_rotation);
		if(m_servoTilt.isConnected()) m_servoTilt.setGoalPosition(Math.PI);

		m_kinect = Main.getSensorManager().getKinect();

		m_kinectWorker = new KinectWorker();
		m_kinectWorker.start();

		m_videoStreamer = new VideoStreamer();

		if(m_kinect != null) {
			m_kinect.setLed(LedStatus.GREEN);
			m_kinect.setDepthFormat(DepthFormat.D11BIT);
			m_kinect.startDepth(this);
			m_kinect.setVideoFormat(VideoFormat.RGB);
			m_kinect.startVideo(m_videoStreamer);
		}
	}

	@Override
	protected void onStop() {
		m_kinectWorker.end();

		if(m_kinect != null) {
			m_kinect.setLed(LedStatus.BLINK_GREEN);
			m_kinect.stopDepth();
			m_kinect.close();
			m_kinect.stopVideo();
			m_kinect = null;
		}
	}

	@Override
	public void tick(long tick, Time elapsedTime) {
		m_timeTracker.startTracking(tick);
		m_videoStreamer.tick(elapsedTime);
		m_timeTracker.stopTracking();
	}

	@Override
	public void onDataReceived(ClientWorker client, NetPackage pack) {

	}

	@Override
	public void onCmdReceived(ClientWorker client, String[] cmd) {

	}

	@Override
	public void onClientDisconnected(ClientWorker client) {

	}

	@Override
	public void onClientConnected(ClientWorker client) {

	}

	public BooleanMapManager getObstacleMap() {
		return m_kinectWorker.getObstacleMap();
	}

	@Override
	public void onFrameReceived(FrameMode frameMode, ByteBuffer byteBuffer, int i) {
		if(m_kinectWorker != null)
			m_kinectWorker.setKinectData(byteBuffer);
	}

	public double getRotation() {
		return m_rotation;
	}

	public void setRoation(double rot) {
		m_rotation = rot;
		if(m_servoRotate.isConnected()) m_servoRotate.setGoalPosition(Math.PI + 0.2 + m_rotation);
	}
}