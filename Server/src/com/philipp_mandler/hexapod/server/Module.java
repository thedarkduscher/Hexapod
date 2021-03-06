package com.philipp_mandler.hexapod.server;

public abstract class Module implements NetworkingEventListener {

	private String m_name;
	private boolean m_running = false;

	public String getName() {
		return m_name;
	}

	protected void setName(String name) {
		m_name = name;
	}

	protected abstract void onStart();
	protected abstract void onStop();

	public boolean isRunning() {
		return m_running;
	}

	public void start() {
		onStart();
		m_running = true;
	}

	public void stop() {
		m_running = false;
		onStop();
	}

	public abstract void tick(long tick, Time elapsedTime);
}
