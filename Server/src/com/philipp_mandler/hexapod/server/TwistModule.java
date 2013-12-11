package com.philipp_mandler.hexapod.server;

import com.philipp_mandler.hexapod.hexapod.*;

public class TwistModule extends Module {

	private LegGroup m_legGroup;

	private double m_height = -100;

	private double m_heightOffset = 0;

	private LegUpdater m_legUpdater;

	private Vec3 m_rotation = new Vec3();

	private boolean m_lifted = false;


	public TwistModule() {

		setName("twist");

		ActuatorManager acs = Main.getActuatorManager();

		Leg[] m_legs = new Leg[6];
		m_legs[1] = new Leg(1, Data.upperLeg, Data.lowerLeg, new Vec2(90, 210), -1.0122f + Math.PI, acs.getLegServo(1, 0), acs.getLegServo(1, 1), acs.getLegServo(1, 2), true);
		m_legs[3] = new Leg(3, Data.upperLeg, Data.lowerLeg, new Vec2(130, 0), Math.PI, acs.getLegServo(3, 0), acs.getLegServo(3, 1), acs.getLegServo(3, 2), true);
		m_legs[5] = new Leg(5, Data.upperLeg, Data.lowerLeg, new Vec2(90, -210), 1.0122f + Math.PI, acs.getLegServo(5, 0), acs.getLegServo(5, 1), acs.getLegServo(5, 2), true);


		m_legs[0] = new Leg(0, Data.upperLeg, Data.lowerLeg, new Vec2(-90, 210), -1.0122f, acs.getLegServo(0, 0), acs.getLegServo(0, 1), acs.getLegServo(0, 2), false);
		m_legs[2] = new Leg(2, Data.upperLeg, Data.lowerLeg, new Vec2(-130, 0), 0, acs.getLegServo(2, 0), acs.getLegServo(2, 1), acs.getLegServo(2, 2), false);
		m_legs[4] = new Leg(4, Data.upperLeg, Data.lowerLeg, new Vec2(-90, -210), 1.0122f, acs.getLegServo(4, 0), acs.getLegServo(4, 1), acs.getLegServo(4, 2), false);


		//LegGroup triangle1 = new LegGroup(new Leg[] {m_legs[0], m_legs[3], m_legs[4]}, new Vec2[] {new Vec2(-200, 310), new Vec2(300, 0), new Vec2(-200, -310)});
		//LegGroup triangle2 = new LegGroup(new Leg[] {m_legs[1], m_legs[2], m_legs[5]}, new Vec2[] {new Vec2(200, 310), new Vec2(-300, 0), new Vec2(200, -310)});

		m_legGroup = new LegGroup(new Leg[] {m_legs[0], m_legs[3], m_legs[4], m_legs[1], m_legs[2], m_legs[5]}, new Vec2[] {new Vec2(-180, 320), new Vec2(280, 0), new Vec2(-180, -320), new Vec2(180, 320), new Vec2(-280, 0), new Vec2(180, -320)});


		m_legGroup.setTranslation(new Vec3(0, 0, 20));

		m_legGroup.moveLegs();

		m_legUpdater = new LegUpdater();

		for(Leg leg : m_legs) {
			m_legUpdater.addLeg(leg);
		}

	}

	@Override
	protected void onStart() {
		m_legUpdater.start();
	}

	@Override
	protected void onStop() {
		m_legUpdater.stop();
	}

	@Override
	public void tick(Time elapsedTime) {
		if(!m_lifted) {
			if(m_legGroup.getTranslation().getZ() > m_height) {
				m_legGroup.translate(new Vec3(0, 0, - elapsedTime.getSeconds() * 30));
			}
			else {
				m_lifted = true;
			}
		}
		else {
			m_legGroup.setTranslation(new Vec3(0, 0, m_height - m_heightOffset));
			m_legGroup.setRotation(m_rotation);
		}
		m_legGroup.moveLegs();
	}

	@Override
	public void onDataReceived(ClientWorker client, NetPackage pack) {
		if(pack instanceof RotationPackage) {
			RotationPackage rotPack = (RotationPackage)pack;

			Vec3 rawRot = rotPack.getValue();

			m_rotation.setX((rawRot.getX() / 9.81 * (Math.PI / 2)) / 2.0);
			m_rotation.setY(-((rawRot.getY() / 9.81 * (Math.PI / 2)) / 2));

		}
		else if(pack instanceof JoystickPackage) {
			JoystickPackage joyPack = (JoystickPackage)pack;

			if(joyPack.getType() == JoystickType.Direction) {
				m_heightOffset = joyPack.getData().getY() * 70;
			}
			else if(joyPack.getType() == JoystickType.Rotation) {
				m_rotation.setZ(joyPack.getData().getX() * 0.2);
			}
		}
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
}