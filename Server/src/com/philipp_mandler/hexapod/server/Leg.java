package com.philipp_mandler.hexapod.server;

import com.philipp_mandler.hexapod.hexapod.Vec2;
import com.philipp_mandler.hexapod.hexapod.Vec3;

public class Leg {
	
	private SingleServo[] m_servos;
	private volatile Vec3 m_goalPosition;
	private Vec2 m_position;
	private double m_upperLeg;
	private double m_lowerLeg;
	private int m_legID;
	private double m_angle;
	private boolean m_rightSide;
	
	public Leg(int legID, double upperLegLength, double lowerLegLength, Vec2 position, double angle, SingleServo servo1, SingleServo servo2, SingleServo servo3, boolean rightSide) {
		m_legID = legID;
		
		m_servos = new SingleServo[3];
		
		m_upperLeg = upperLegLength;
		m_lowerLeg = lowerLegLength;
		
		m_servos[0] = servo1;
		m_servos[1] = servo2;
		m_servos[2] = servo3;

		// ping each servo
		for(SingleServo servo : m_servos) {
			if(!servo.ping())
				DebugHelper.log("Servo (ID: " + servo.getID() + ") from Leg (ID: " + legID + ") couldn't be found.", Log.WARNING);
		}
		
		m_goalPosition = null;
		
		m_position = position;
		m_angle = angle;
		m_rightSide = rightSide;
	}
	
	public void setGoalPosition(Vec3 pos) {
		m_goalPosition = pos;
	}
	
	public Vec3 getGoalPosition() {
		return m_goalPosition;
	}

	public Vec3 getRelativeGoalPosition() {
		return new Vec3(m_goalPosition.getX() - m_position.getX(), m_goalPosition.getY() - m_position.getY(), m_goalPosition.getZ());
	}
	
	public void setTorqueEnabled(boolean enable) {
		m_servos[0].setTorqueEnabled(enable);
		m_servos[1].setTorqueEnabled(enable);
		m_servos[2].setTorqueEnabled(enable);
	}
	
	private void moveLegToPosition(Vec3 goal) {
		moveLegToRelativePosition(new Vec3(goal.getX() - m_position.getX(), goal.getY() - m_position.getY(), goal.getZ()));
	}
	
	private void moveLegToRelativePosition(Vec3 goal) {

		// INVERSE KINEMATICS


		Vec3 tmpGoal = goal.sum(new Vec3(0, 0, Data.servoPosOffsetZ));

		// calculate s2

		double a = m_upperLeg;
		double b = m_lowerLeg;
		double c = tmpGoal.getLength();

		double gamma = Math.acos((Math.pow(c, 2) - Math.pow(a, 2) - Math.pow(b, 2)) / (-2 * a * b));


		double s2;

		if(m_rightSide) {
			s2 = gamma;
		}
		else {
			s2 = 2 * Math.PI - gamma;
		}


		// calculate s0

		Vec2 topGoal = new Vec2(tmpGoal.getX(), tmpGoal.getY());

		double s0;

		if(m_rightSide) {
			s0 = Math.asin(topGoal.getY() / topGoal.getLength()) + m_angle;
		}
		else {
			s0 = Math.PI - Math.asin(topGoal.getY() / topGoal.getLength()) - m_angle;
		}



		// calculate s1

		double beta = Math.acos((Math.pow(b, 2) - Math.pow(c, 2) - Math.pow(a, 2)) / (-2 * c * a));

		double s1;

		if(m_rightSide) {
			s1 = Math.atan2(topGoal.getLength(), tmpGoal.getZ());
			s1 = s1 - beta + Math.PI / 2;
		}
		else {
			s1 = Math.atan2(-topGoal.getLength(), tmpGoal.getZ());
			s1 = s1 + beta + Math.PI * 1.5;
		}

		// set servo positions
		if(!(Double.isNaN(s0) || Double.isNaN(s1) || Double.isNaN(s2) || Double.isInfinite(s0) || Double.isInfinite(s1) || Double.isInfinite(s2))) {
			m_servos[0].setGoalPosition(s0);
			m_servos[1].setGoalPosition(s1);
			m_servos[2].setGoalPosition(s2);
		}

	}

	public void transform(Vec3 transformation) {
		// transform goal position
		m_goalPosition.add(transformation);
	}
	
	public void setLegID(int id) {
		m_legID = id;
	}
	
	public int getLegID() {
		return m_legID;
	}
	
	public void updateServos() {
		// calculate inverse kinematics
		if(m_goalPosition != null)
			moveLegToPosition(m_goalPosition);	
	}

	public boolean isRightSided() {
		return m_rightSide;
	}
	
	public SingleServo[] getServos() {
		return m_servos;
	}

	public SingleServo getServo(int i) {
		return m_servos[i];
	}
}
