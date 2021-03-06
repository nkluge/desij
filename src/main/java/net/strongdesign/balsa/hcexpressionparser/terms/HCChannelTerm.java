package net.strongdesign.balsa.hcexpressionparser.terms;

/**
 * Copyright 2012-2014 Stanislavs Golubcovs
 * 
 * This file is part of DesiJ.
 * 
 * DesiJ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * DesiJ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with DesiJ.  If not, see <http://www.gnu.org/licenses/>.
 */

import net.strongdesign.balsa.hcexpressionparser.terms.HCInfixOperator.Operation;

public class HCChannelTerm extends HCTerm {
	public String channel;
	
	protected int instanceNumber=0;

	public HCChannelTerm() {
	}
	
	/**
	 * This function should either return channel name with instance 
	 * or the corresponding channel number from a breeze file, 
	 * now only the first part is implemented
	 * 
	 * @return
	 */
	
	public String getChannelName() {
		String cnt="";
		if (instanceNumber>0) {
			cnt=""+instanceNumber;
		}
		return channel+cnt;
	}
	
	@Override
	public HCTerm expand(ExpansionType type, int scale, HCChannelSenseController sig, boolean oldChoice) {
		HCInfixOperator ret = new HCInfixOperator();
		ret.operation = Operation.SEQUENCE;
		
		
		HCTransitionTerm t1 = new HCTransitionTerm();
		t1.channel = channel;
		String dir = "+";
		if (type==ExpansionType.DOWN) dir = "-";
		
		t1.instanceNumber = instanceNumber;
		t1.direction = dir;
		t1.wire = "r";
		

		HCTransitionTerm t2 = new HCTransitionTerm();
		t2.channel = channel;
		t2.instanceNumber = instanceNumber;
		t2.direction = dir;
		t2.wire = "a";
		
		
		ret.components.add(t1);
		ret.components.add(t2);
		
		return ret;
	}

	@Override
	public String toString() {
		int cnt = instanceNumber;
		if (cnt>0) {
			return ""+channel+cnt;
		}
		
		return ""+channel;
	}

	@Override
	public void setInstanceNumber(int num, HCChannelSenseController sig) {
		if (sig.isScaled(channel))
			instanceNumber = num;
		else
			instanceNumber = 0;
	}

	

}
