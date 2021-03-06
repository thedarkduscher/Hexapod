package com.philipp_mandler.hexapod.server;


import com.philipp_mandler.hexapod.hexapod.JoystickType;
import com.philipp_mandler.hexapod.hexapod.Vec2;
import com.philipp_mandler.hexapod.hexapod.Vec3;
import com.philipp_mandler.hexapod.hexapod.WalkingGait;
import com.philipp_mandler.hexapod.hexapod.net.JoystickPackage;
import com.philipp_mandler.hexapod.hexapod.net.NetPackage;
import com.philipp_mandler.hexapod.hexapod.net.NotificationPackage;
import com.philipp_mandler.hexapod.hexapod.net.RotationPackage;

public class MobilityModule extends Module implements NetworkingEventListener {

	private Vec2 m_speed = new Vec2();
	private double m_rotSpeed = 0.0;
	private Leg[] m_legs;
	private Vec2[] m_endPositions = new Vec2[6];
	private double m_speedFactor;
	private Vec3 m_rotation = new Vec3();
	private Vec3 m_rotationGoal = new Vec3();
	private Vec3 m_groundRotation = new Vec3();
	private Vec2 m_centerOffset = new Vec2();
	private boolean m_tilt = false;
	private boolean m_leveling = false;
	private boolean m_errorDemo = false;
	private ButtonGroup m_buttonGroup;

	private boolean m_groundAdaption = false;

	private LegLoadReader m_loadReader;

	private double[] m_loadOffsets = new double[6];
	private double m_preferredHeight = 100;
	private double m_preferredHeightGoal = 100;

	private LegUpdater m_legUpdater;

	private int[] m_caseStepRipple = {5, 2, 3, 6, 1, 4};
	private int[] m_caseStepTripod = {1, 3, 3, 1, 1, 3};
	private int[] m_caseStepWave = {1, 3, 5, 7, 9, 11};

	private WalkingGait m_walkingGait = WalkingGait.Ripple;

	private WalkingGait m_switchGait = null;

	private int m_mode = 3; // 0: lifting, 1: dropping, 2: lifted, 3: dropped

	private Vec2[] m_defaultPositions = new Vec2[] {
			new Vec2(-180, 310),  //200
			new Vec2(180, 310),
			new Vec2(-260, 0), // 300
			new Vec2(260, 0),
			new Vec2(-180, -310),
			new Vec2(180, -310)
	};

	private Vec3[] m_currentWalkPositions = new Vec3[6];

	private Time m_stepTime = new Time();

	private TimeTracker m_timeTracker;
	private boolean m_slowmode = false;


	public MobilityModule() {

		super.setName("mobility");

		// create time trackers
		m_timeTracker = Main.getTimeManager().createTracker("mobility");

		m_buttonGroup = new ButtonGroup(getName(), "Mobility Module");
		m_buttonGroup.addButton(new Button("lift", "Lift", getName() + " lift"));
		m_buttonGroup.addButton(new Button("drop", "Drop", getName() + " drop"));
		m_buttonGroup.addButton(new Button("gait-ripple", "Ripple Gait", getName() + " gait ripple"));
		m_buttonGroup.addButton(new Button("gait-tripod", "Tripod Gait", getName() + " gait tripod"));
		m_buttonGroup.addButton(new Button("gait-wave", "Wave Gait", getName() + " gait wave"));
		m_buttonGroup.addButton(new Button("toggle-tilt", "Tilt", getName() + " toggle-tilt"));
		m_buttonGroup.addButton(new Button("toggle-groundadaption", "Ground Adaption", getName() + " toggle-groundadaption"));
		m_buttonGroup.addButton(new Button("toggle-leveling", "Leveling", getName() + " toggle-leveling"));
		m_buttonGroup.addButton(new Button("height-up", "Body +", getName() + " height up"));
		m_buttonGroup.addButton(new Button("height-down", "Body -", getName() + " height down"));
		m_buttonGroup.addButton(new Button("toggle-slowmode", "Slow Mode", getName() + " toggle-slowmode"));
		m_buttonGroup.addButton(new Button("toggle-loss", "Error Demo", getName() + " toggle-loss"));
		m_buttonGroup.addButton(new Button("move-center-y+", "Move COW Y+", getName() + " move-center-y+"));
		m_buttonGroup.addButton(new Button("move-center-y-", "Move COW Y-", getName() + " move-center-y-"));
		m_buttonGroup.addButton(new Button("move-center-x+", "Move COW X+", getName() + " move-center-x+"));
		m_buttonGroup.addButton(new Button("move-center-x-", "Move COW X-", getName() + " move-center-x-"));
		m_buttonGroup.addButton(new Button("move-center-res", "Reset COW", getName() + " move-center-res"));


		m_legUpdater = new LegUpdater();


		ActuatorManager acs = Main.getActuatorManager();

		m_legs = new Leg[6];

		m_legs[1] = new Leg(1, Data.upperLeg, Data.lowerLeg, new Vec2(90, 210), -1.0122f + Math.PI, acs.getLegServo(1, 0), acs.getLegServo(1, 1), acs.getLegServo(1, 2), true);
		m_legs[3] = new Leg(3, Data.upperLeg, Data.lowerLeg, new Vec2(130, 0), Math.PI, acs.getLegServo(3, 0), acs.getLegServo(3, 1), acs.getLegServo(3, 2), true);
		m_legs[5] = new Leg(5, Data.upperLeg, Data.lowerLeg, new Vec2(90, -210), 1.0122f + Math.PI, acs.getLegServo(5, 0), acs.getLegServo(5, 1), acs.getLegServo(5, 2), true);


		m_legs[0] = new Leg(0, Data.upperLeg, Data.lowerLeg, new Vec2(-90, 210), -1.0122f, acs.getLegServo(0, 0), acs.getLegServo(0, 1), acs.getLegServo(0, 2), false);
		m_legs[2] = new Leg(2, Data.upperLeg, Data.lowerLeg, new Vec2(-130, 0), 0, acs.getLegServo(2, 0), acs.getLegServo(2, 1), acs.getLegServo(2, 2), false);
		m_legs[4] = new Leg(4, Data.upperLeg, Data.lowerLeg, new Vec2(-90, -210), 1.0122f, acs.getLegServo(4, 0), acs.getLegServo(4, 1), acs.getLegServo(4, 2), false);


		for(Leg leg : m_legs) {
			m_legUpdater.addLeg(leg);
		}

		m_loadReader = new LegLoadReader(m_legs);

	}

	@Override
	public void onStart() {

		for(int i = 0; i < 6; i++) {
			m_legs[i].setGoalPosition(new Vec3(m_defaultPositions[i], 20));
			m_currentWalkPositions[i] = new Vec3(m_defaultPositions[i], 20);
			m_endPositions[i] = new Vec2(m_defaultPositions[i]);
			m_loadOffsets[i] = 0.0;
		}

		m_speedFactor = 0.2;

		m_legUpdater.start();

		m_groundRotation = new Vec3();

		Main.getNetworking().addEventListener(this);
		Main.getNetworking().addButtonGroup(m_buttonGroup);

		m_loadReader.start();
	}

	@Override
	public void onStop() {
		Main.getNetworking().removeButtonGroup(m_buttonGroup);
		Main.getNetworking().removeEventListener(this);
		m_legUpdater.stop();
		m_loadReader.stop();
	}

	@Override
	public void tick(long tick, Time elapsedTime) {

		m_timeTracker.startTracking(tick);

		if(m_mode == 0) { // lifting
			int readyLegs = 0;
			for(Leg leg : m_legs) {
				double moveDist = ((leg.getGoalPosition().getZ() + m_preferredHeight) / 2 + 10) * elapsedTime.getSeconds();
				if(leg.getGoalPosition().getZ() - moveDist > -m_preferredHeight)
					leg.transform(new Vec3(0, 0, -moveDist));
				else {
					leg.getGoalPosition().setZ(-m_preferredHeight);
					readyLegs++;
				}
			}
			if(readyLegs > 5)
				m_mode = 2;
		}
		else if(m_mode == 1) { // dropping
			int readyLegs = 0;

			double goalHeight = -10;
			for(Leg leg : m_legs) {

				double moveDist = ((leg.getGoalPosition().getZ() + goalHeight) / 2 - 10) * elapsedTime.getSeconds();
				if(leg.getGoalPosition().getZ() + moveDist < -goalHeight)
					leg.transform(new Vec3(0, 0, -moveDist));
				else {
					leg.getGoalPosition().setZ(-goalHeight);
					readyLegs++;
				}
			}
			if(readyLegs > 5)
				m_mode = 4;
		}
		else if(m_mode == 2) { // lifted

			TimeTrackerAction action;

			if(m_leveling) {
				action = m_timeTracker.trackAction("level");

				Vec3 raw = Main.getSensorManager().getLevel();

				Vec3 gravity = new Vec3(raw.getZ(), raw.getX(), 0);


				m_groundRotation.add(new Vec3(-gravity.getX() * elapsedTime.getSeconds() * 0.2, gravity.getY() * elapsedTime.getSeconds() * 0.2, 0));

				action.stopTracking();
			}

			action = m_timeTracker.trackAction("prepare walking");


			double duration = 800 / 6 / m_speedFactor; // duration per case, 800ms per step

			Vec2 speed = new Vec2(m_speed);
			speed.multiply(150 * m_speedFactor);

			double speedR = m_rotSpeed / 2 * m_speedFactor;

			double stepHeight = 40;

			if(m_groundAdaption)
				stepHeight = 60;

			if(m_switchGait != null) {
				m_speed = new Vec2();
				speedR = 0;
			}

			boolean idle = false;

			if(speed.getLength() < 0.05 && Math.abs(speedR) < 0.01) {

				idle = true;

				for(int legID = 0; legID < 6; legID++) {
					if(new Vec2(m_currentWalkPositions[legID].getX(), m_currentWalkPositions[legID].getY()).sub(m_defaultPositions[legID]).getLength() > 1) {
						idle = false;
						break;
					}
				}

			}

			if(m_switchGait != null && idle) {
				m_walkingGait = m_switchGait;
				m_switchGait = null;
			}

			action.stopTracking();

			if(m_groundAdaption) {

				action = m_timeTracker.trackAction("get leg loads");

				double sumLoad = 0;

				int loads[] = m_loadReader.getLoads();
				double weightedLoads[] = new double[6];

				for(int i = 0; i < 6; i++) {
					// check if legs are touching ground
					if(shellLegAdapt(i) || idle) {
						double load = loads[i];
						if(m_legs[i].isRightSided()) load = -load;
						Vec2 pos2d = new Vec2(m_legs[i].getRelativeGoalPosition().getX(), m_legs[i].getRelativeGoalPosition().getY());
						weightedLoads[i] = load / pos2d.getLength();
						sumLoad += load;
					}
				}

				for(int i = 0; i < 6; i++) {
					// check if legs are touching ground
					if(shellLegAdapt(i) || idle) {
						double loadError = (sumLoad / 5) - weightedLoads[i];
						m_loadOffsets[i] -= loadError * elapsedTime.getSeconds() * 80;
					}
				}


				action.stopTracking();

				action = m_timeTracker.trackAction("height correction");

				double loadOffsetSum = 0;

				for(int i = 0; i < 6; i++) {
					loadOffsetSum += m_loadOffsets[i];
				}


				for(int i = 0; i < 6; i++) {
					m_loadOffsets[i] -= loadOffsetSum / 6;
				}


				for(int i = 0; i < 6; i++) {
					if(!(shellLegAdapt(i) || idle)) {
						m_loadOffsets[i] = 0;
					}
				}


				Vec3 sum = new Vec3();
				Vec3 sumQuad = new Vec3();
				double sumXY = 0.0;
				double sumXZ = 0.0;
				double sumYZ = 0.0;

				for(int i = 0; i < 6; i++) {
					Vec3 pos = new Vec3(m_currentWalkPositions[i].getX() + 500, m_currentWalkPositions[i].getY() + 400, m_loadOffsets[i]);
					sum.add(pos);
					sumQuad.add(pos.pow(2));
					sumXY += pos.getX() * pos.getY();
					sumXZ += pos.getX() * pos.getZ();
					sumYZ += pos.getY() * pos.getZ();
				}

				Vec3 o = new Vec3(sumQuad.getX() / sum.getX(), sumXY / sum.getX(), sumXZ / sum.getX());

				if(sum.getX() == 0)
					o = new Vec3();

				Vec3 p1 = new Vec3(sumXY / sum.getY(), sumQuad.getY() / sum.getY(), sumYZ / sum.getY());

				if(sum.getY() == 0)
					p1 = new Vec3();

				Vec3 p2 = new Vec3(sum.getX() / 6, sum.getY() / 6, sum.getZ() / 6);

				Plane plane = new Plane(o, p1, p2);

				for(int i = 0; i < 6; i++) {

					double val = plane.getZ(new Vec2(m_currentWalkPositions[i].getX() + 500, m_currentWalkPositions[i].getY() + 400));
					if(!Double.isNaN(val))
						m_loadOffsets[i] -= val;

				}

				action.stopTracking();
			}


			action = m_timeTracker.trackAction("sequencing");

			if(m_walkingGait == WalkingGait.Ripple) {
				// TODO: adjustments
			}
			else if(m_walkingGait == WalkingGait.Tripod) {
				speed.multiply(3);
				speedR *= 1;
			}
			if(m_walkingGait == WalkingGait.Wave) {
				speed.multiply(0.3);
				speedR *= 0.3;
			}



			for(int legID = 0; legID < 6; legID++) {

				Leg leg = m_legs[legID];
				Vec3 pos = new Vec3(m_currentWalkPositions[legID]);


				if(!idle) {

					if(m_walkingGait == WalkingGait.Ripple) {
						handleRippleGait(legID, pos, duration, elapsedTime, speed, speedR, stepHeight);
					}
					else if(m_walkingGait == WalkingGait.Tripod) {
						handleTripodGait(legID, pos, duration, elapsedTime, speed, speedR, stepHeight);
					}
					else if(m_walkingGait == WalkingGait.Wave) {
						handleWaveGait(legID, pos, duration, elapsedTime, speed, speedR, stepHeight);
					}

				}
				else {
					//pos.setZ(pos.getZ() - (pos.getZ() * elapsedTime.getSeconds() * 0.0001));
					pos.setZ(0);
				}

				m_currentWalkPositions[legID] = new Vec3(pos);

				m_preferredHeight = (m_preferredHeightGoal - m_preferredHeight) * elapsedTime.getSeconds() / 2 + m_preferredHeight;
				if(idle) pos.setZ(0);

				m_rotation.setX((m_rotationGoal.getX() - m_rotation.getX()) * elapsedTime.getSeconds() + m_rotation.getX());
				m_rotation.setY((m_rotationGoal.getY() - m_rotation.getY()) * elapsedTime.getSeconds() + m_rotation.getY());
				pos.rotate(m_rotation);

				m_preferredHeight = (m_preferredHeightGoal - m_preferredHeight) * elapsedTime.getSeconds() / 2 + m_preferredHeight;


				if(m_groundAdaption) {
					pos.add(new Vec3(0, 0, m_loadOffsets[legID]));
				}

				if(m_leveling) {
					pos.rotate(m_groundRotation);
				}

				Vec3 tmp = pos.sum(new Vec3(m_centerOffset.getX(), m_centerOffset.getY(), -m_preferredHeight));
				if(m_errorDemo && legID == 3) tmp.setZ(0);
				leg.setGoalPosition(tmp);

			}

			action.stopTracking();

			action = m_timeTracker.trackAction("step calculation");

			if(!idle) {
				if (m_stepTime.getMilliseconds() < duration) m_stepTime = Time.fromNanoseconds(m_stepTime.getNanoseconds() + elapsedTime.getNanoseconds());
				else {
					double factor = 0.8;

					if(m_slowmode)
						factor = 0.2;

					if(m_speed.getLength() < Math.abs(m_rotSpeed)) {
							m_speedFactor = Math.abs(m_rotSpeed) * factor + 0.2;
					}
					else {
						m_speedFactor = m_speed.getLength() * factor + 0.2;
					}
					m_stepTime = Time.fromNanoseconds(0);
				}
			}

			action.stopTracking();
		}

		m_timeTracker.stopTracking();

	}

	@Override
	public void onDataReceived(ClientWorker client, NetPackage pack) {
		if(pack instanceof JoystickPackage) {
			JoystickPackage joyPack = (JoystickPackage)pack;
			if(joyPack.getType() == JoystickType.Direction) {
				m_speed.set(joyPack.getData().getX(), joyPack.getData().getY());
			}
			else if(joyPack.getType() == JoystickType.Rotation) {
				m_rotSpeed = joyPack.getData().getX();
			}
		}
		else if(pack instanceof RotationPackage) {
			RotationPackage rotPack = (RotationPackage)pack;

			Vec3 rawRot = rotPack.getValue();

			if(m_tilt) {
				m_rotationGoal.setX(Math.max(Math.min(rawRot.getX() / 3, 0.25), -0.25));
				m_rotationGoal.setY(Math.max(Math.min(-rawRot.getY() / 3, 0.25), -0.25));
			}
			else {
				m_rotationGoal.setX(0);
				m_rotationGoal.setY(0);
			}

		}
	}

	@Override
	public void onCmdReceived(ClientWorker client, String[] cmd) {
		if(cmd.length > 1) {
			if(cmd[0].toLowerCase().equals(getName())) {
				if(cmd[1].toLowerCase().equals("speed")) {
					if(cmd.length > 2) {
						try {
							m_speed.setY(Double.valueOf(cmd[2]));
							DebugHelper.log("Walking speed set to " + m_speed.getY() + ".");
						}
						catch(NumberFormatException e) {
							DebugHelper.log("The last parameter is no valid number.");
						}
					}
				}
				else if(cmd[1].toLowerCase().equals("speedx")) {
					if(cmd.length > 2) {
						try {
							m_speed.setX(Double.valueOf(cmd[2]));
							DebugHelper.log("Walking speed x set to " + m_speed.getX() + ".");
						}
						catch(NumberFormatException e) {
							DebugHelper.log("The last parameter is no valid number.");
						}
					}
				}
				else if(cmd[1].toLowerCase().equals("tilt")) {
					if(cmd.length > 2) {
						if(cmd[2].toLowerCase().equals("on")) {
							m_tilt = true;
							Main.getNetworking().broadcast(new NotificationPackage("Tilting activated."));
						}
						else if(cmd[2].toLowerCase().equals("off")) {
							m_tilt = false;
							Main.getNetworking().broadcast(new NotificationPackage("Tilting deactivated."));
						}
					}
				}
				else if(cmd[1].toLowerCase().equals("toggle-tilt")) {
					if(!m_groundAdaption) {
						m_tilt = !m_tilt;
						if(m_tilt) Main.getNetworking().broadcast(new NotificationPackage("Tilting activated."));
						else Main.getNetworking().broadcast(new NotificationPackage("Tilting deactivated."));
						if(!m_tilt) m_rotationGoal = new Vec3();
					}
					else Main.getNetworking().broadcast(new NotificationPackage("Error: Ground Adaption enabled."));
				}
				else if(cmd[1].toLowerCase().equals("toggle-groundadaption")) {
					m_tilt = false;
					for(int i = 0; i < 6; i++) {
						m_loadOffsets[i] =  0.0;
					}

					m_groundAdaption = !m_groundAdaption;
					if(m_groundAdaption) Main.getNetworking().broadcast(new NotificationPackage("Adaption activated."));
					else Main.getNetworking().broadcast(new NotificationPackage("Adaption deactivated."));

				}
				else if(cmd[1].toLowerCase().equals("toggle-leveling")) {
					if(Main.getSensorManager().getKinect() != null) {
						m_tilt = false;

						m_groundRotation.set(0, 0, 0);

						m_leveling = !m_leveling;
						if(m_leveling) Main.getNetworking().broadcast(new NotificationPackage("Leveling activated."));
						else Main.getNetworking().broadcast(new NotificationPackage("Leveling deactivated."));
					}
					else {
						Main.getNetworking().broadcast(new NotificationPackage("Missing Kinect."));
					}
				}
				else if(cmd[1].toLowerCase().equals("drop")) {
					drop();
					Main.getNetworking().broadcast(new NotificationPackage("Dropping..."));
				}
				else if(cmd[1].toLowerCase().equals("lift")) {
					lift();
					Main.getNetworking().broadcast(new NotificationPackage("Lifting..."));
				}
				else if(cmd[1].toLowerCase().equals("gait")) {
					if(cmd.length > 2) {
						if(cmd[2].toLowerCase().equals("ripple")) {
							if(m_walkingGait != WalkingGait.Ripple) m_switchGait = WalkingGait.Ripple;
							Main.getNetworking().broadcast(new NotificationPackage("Switching to ripple gait..."));
						}
						else if(cmd[2].toLowerCase().equals("tripod")) {
							if(m_walkingGait != WalkingGait.Tripod) m_switchGait = WalkingGait.Tripod;
							Main.getNetworking().broadcast(new NotificationPackage("Switching to tripod gait..."));
						}
						else if(cmd[2].toLowerCase().equals("wave")) {
							if(m_walkingGait != WalkingGait.Wave) m_switchGait = WalkingGait.Wave;
							Main.getNetworking().broadcast(new NotificationPackage("Switching to wave gait..."));
						}
					}
				}
				else if(cmd[1].toLowerCase().equals("height")) {
					if(cmd.length > 2) {
						if(cmd[2].toLowerCase().equals("up")) {
							if(m_preferredHeight <= 170) {
								m_preferredHeightGoal += 10;
							}
							else {
								Main.getNetworking().broadcast(new NotificationPackage("Maximum reached (" + (Math.round(m_preferredHeightGoal) / 10) + "cm)"));
							}
						}
						else if(cmd[2].toLowerCase().equals("down")) {
							if(m_preferredHeight >= 40) {
								m_preferredHeightGoal -= 10;
							}
							else {
								Main.getNetworking().broadcast(new NotificationPackage("Minimum reached (" + (Math.round(m_preferredHeightGoal) / 10) + "cm)"));
							}
						}
					}
				}
				else if(cmd[1].toLowerCase().equals("toggle-slowmode")) {
					m_slowmode = !m_slowmode;
					if(m_slowmode) Main.getNetworking().broadcast(new NotificationPackage("Slow Mode activated."));
					else Main.getNetworking().broadcast(new NotificationPackage("Slow Mode deactivated."));
				}
				else if(cmd[1].toLowerCase().equals("toggle-loss")) {
					m_errorDemo = !m_errorDemo;
					if(m_errorDemo) Main.getNetworking().broadcast(new NotificationPackage("Error demo activated."));
					else Main.getNetworking().broadcast(new NotificationPackage("Error demo deactivated."));
				}
				else if(cmd[1].toLowerCase().equals("move-center-y+")) {
					m_centerOffset.add(new Vec2(0, 5));
				}
				else if(cmd[1].toLowerCase().equals("move-center-y-")) {
					m_centerOffset.add(new Vec2(0, -5));
				}
				else if(cmd[1].toLowerCase().equals("move-center-x+")) {
					m_centerOffset.add(new Vec2(5, 0));
				}
				else if(cmd[1].toLowerCase().equals("move-center-x-")) {
					m_centerOffset.add(new Vec2(-5, 0));
				}
				else if(cmd[1].toLowerCase().equals("move-center-res")) {
					m_centerOffset.set(0,0);
				}
			}
		}
	}

	@Override
	public void onClientDisconnected(ClientWorker client) {

	}

	@Override
	public void onClientConnected(ClientWorker client) {

	}

	public void setWalkingSpeed(Vec2 speed) {
		m_speed = speed;
	}

	public Vec2 getWalkingSpeed() {
		return m_speed;
	}

	public void setRotationSpeed(double speed) {
		if(speed > 1) m_rotSpeed = 1;
		else if(speed < -1) m_rotSpeed = -1;
		else m_rotSpeed = speed;
	}

	public double getRotationSpeed() {
		return m_rotSpeed;
	}

	public boolean lifted() {
		return (m_mode == 2);
	}

	public void lift() {
		if(m_mode != 2) m_mode = 0;
	}

	public void drop() {
		if(m_mode != 3) m_mode = 1;
	}

	private boolean shellLegAdapt(int legID) {
		if(m_walkingGait == WalkingGait.Ripple) {
			if(m_caseStepRipple[legID] != 1) {
				return true;
			}
		}
		else if(m_walkingGait == WalkingGait.Tripod) {
			if(m_caseStepTripod[legID] != 1) {
				return true;
			}
		}
		else if(m_walkingGait == WalkingGait.Wave) {
			if(m_caseStepWave[legID] != 1) {
				return true;
			}
		}
		return false;
	}

	private void handleRippleGait(int legID, Vec3 pos, double duration, Time elapsedTime, Vec2 speed, double speedR, double stepHeight) {

		Vec2 initPos = m_defaultPositions[legID];

		switch(m_caseStepRipple[legID]) {

			case 1: //forward raise

				pos.setX(((m_endPositions[legID].getX() * (duration * 2 - m_stepTime.getMilliseconds())) + (initPos.getX() * m_stepTime.getMilliseconds())) / (duration * 2));
				pos.setY(((m_endPositions[legID].getY() * (duration * 2 - m_stepTime.getMilliseconds())) + (initPos.getY() * m_stepTime.getMilliseconds())) / (duration * 2));

				pos.setZ(Math.sin((m_stepTime.getMilliseconds() / duration) * (Math.PI / 2)) * stepHeight);

				break;

			case 2: // forward lower

				pos.setX(((m_endPositions[legID].getX() * (duration * 2 - (m_stepTime.getMilliseconds() + duration))) + (initPos.getX() * (m_stepTime.getMilliseconds() + duration))) / (duration * 2));
				pos.setY(((m_endPositions[legID].getY() * (duration * 2 - (m_stepTime.getMilliseconds() + duration))) + (initPos.getY() * (m_stepTime.getMilliseconds() + duration))) / (duration * 2));

				pos.setZ((Math.cos(((m_stepTime.getMilliseconds() / duration) * Math.PI)) + 1) * (stepHeight / 2));

				break;

			case 3:
			case 4:
			case 5:
			case 6:

				// pull back slowly

				pos.add(new Vec3(-speed.getX() * elapsedTime.getSeconds(), -speed.getY() * elapsedTime.getSeconds(), 0));
				pos.setZ(0);
				pos.rotate(new Vec3(0, 0, speedR * elapsedTime.getSeconds()));

				break;

		}

		if(m_stepTime.getMilliseconds() >= duration) {
			if(m_caseStepRipple[legID] >= 6) {
				m_caseStepRipple[legID] = 1;
			}
			else {
				m_endPositions[legID] = new Vec2(pos.getX(), pos.getY());
				m_caseStepRipple[legID]++;
			}
		}
	}

	private void handleTripodGait(int legID, Vec3 pos, double duration, Time elapsedTime, Vec2 speed, double speedR, double stepHeight) {

		Vec2 initPos = m_defaultPositions[legID];

		switch(m_caseStepTripod[legID]) {

			case 1: //forward raise

				pos.setX(((m_endPositions[legID].getX() * (duration * 2 - m_stepTime.getMilliseconds())) + (initPos.getX() * m_stepTime.getMilliseconds())) / (duration * 2));
				pos.setY(((m_endPositions[legID].getY() * (duration * 2 - m_stepTime.getMilliseconds())) + (initPos.getY() * m_stepTime.getMilliseconds())) / (duration * 2));

				pos.setZ(Math.sin((m_stepTime.getMilliseconds() / duration) * (Math.PI / 2)) * stepHeight);

				break;

			case 2: // forward lower

				pos.setX(((m_endPositions[legID].getX() * (duration * 2 - (m_stepTime.getMilliseconds() + duration))) + (initPos.getX() * (m_stepTime.getMilliseconds() + duration))) / (duration * 2));
				pos.setY(((m_endPositions[legID].getY() * (duration * 2 - (m_stepTime.getMilliseconds() + duration))) + (initPos.getY() * (m_stepTime.getMilliseconds() + duration))) / (duration * 2));

				pos.setZ((Math.cos(((m_stepTime.getMilliseconds() / duration) * Math.PI)) + 1) * (stepHeight / 2));

				break;

			case 3:
			case 4:

			// pull back slowly

				pos.add(new Vec3(-speed.getX() * elapsedTime.getSeconds(), -speed.getY() * elapsedTime.getSeconds(), 0));
				pos.setZ(0);
				pos.rotate(new Vec3(0, 0, speedR * elapsedTime.getSeconds()));

				break;

		}

		if(m_stepTime.getMilliseconds() >= duration) {
			if(m_caseStepTripod[legID] >= 4) {
				m_caseStepTripod[legID] = 1;
			}
			else {
				m_endPositions[legID] = new Vec2(pos.getX(), pos.getY());
				m_caseStepTripod[legID]++;
			}
		}

	}


	private void handleWaveGait(int legID, Vec3 pos, double duration, Time elapsedTime, Vec2 speed, double speedR, double stepHeight) {

		Vec2 initPos = m_defaultPositions[legID];

		switch(m_caseStepWave[legID]) {

			case 1: //forward raise

				pos.setX(((m_endPositions[legID].getX() * (duration * 2 - m_stepTime.getMilliseconds())) + (initPos.getX() * m_stepTime.getMilliseconds())) / (duration * 2));
				pos.setY(((m_endPositions[legID].getY() * (duration * 2 - m_stepTime.getMilliseconds())) + (initPos.getY() * m_stepTime.getMilliseconds())) / (duration * 2));

				pos.setZ(Math.sin((m_stepTime.getMilliseconds() / duration) * (Math.PI / 2)) * stepHeight);

				break;

			case 2: // forward lower

				pos.setX(((m_endPositions[legID].getX() * (duration * 2 - (m_stepTime.getMilliseconds() + duration))) + (initPos.getX() * (m_stepTime.getMilliseconds() + duration))) / (duration * 2));
				pos.setY(((m_endPositions[legID].getY() * (duration * 2 - (m_stepTime.getMilliseconds() + duration))) + (initPos.getY() * (m_stepTime.getMilliseconds() + duration))) / (duration * 2));

				pos.setZ((Math.cos(((m_stepTime.getMilliseconds() / duration) * Math.PI)) + 1) * (stepHeight / 2));

				break;

			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
			case 11:
			case 12:

			// pull back slowly

				pos.add(new Vec3(-speed.getX() * elapsedTime.getSeconds(), -speed.getY() * elapsedTime.getSeconds(), 0));
				pos.setZ(0);
				pos.rotate(new Vec3(0, 0, speedR * elapsedTime.getSeconds()));

				break;

		}

		if(m_stepTime.getMilliseconds() >= duration) {
			if(m_caseStepWave[legID] >= 12) {
				m_caseStepWave[legID] = 1;
			}
			else {
				m_endPositions[legID] = new Vec2(pos.getX(), pos.getY());
				m_caseStepWave[legID]++;
			}
		}

	}

}
