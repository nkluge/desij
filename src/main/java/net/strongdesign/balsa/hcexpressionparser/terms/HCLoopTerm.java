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

import java.util.HashSet;
import java.util.Set;

import net.strongdesign.balsa.hcexpressionparser.terms.HCInfixOperator.Operation;
import net.strongdesign.stg.EdgeDirection;
import net.strongdesign.stg.Place;
import net.strongdesign.stg.STG;
import net.strongdesign.stg.STGUtil;
import net.strongdesign.stg.SignalEdge;
import net.strongdesign.stg.Signature;
import net.strongdesign.stg.Transition;

public class HCLoopTerm extends HCTerm implements HCSTGGenerator {
	public HCTerm component;

	@Override
	public HCTerm expand(ExpansionType type, int scale, HCChannelSenseController sig, boolean oldChoice) throws Exception  {
		if (type==ExpansionType.UP) {
			HCInfixOperator ret = new HCInfixOperator();
			HCTerm up = component.expand(ExpansionType.UP, scale, sig, oldChoice);
			HCTerm down = component.expand(ExpansionType.DOWN, scale, sig, oldChoice);
			if (up!=null) ret.components.add(up);
			if (down!=null) ret.components.add(down);
			
			ret.operation = Operation.SEQUENCE;
			
			HCLoopTerm lp = new HCLoopTerm();
			lp.component = ret;
			
			return lp; // return the expanded loop component
		}
		
		return null;
	}

	@Override
	public String toString() {
		
		return "#("+component.toString()+")";
	}

	@Override
	public void setInstanceNumber(int num, HCChannelSenseController sig) {
		component.setInstanceNumber(num, sig);
	}

	@Override
	public Object clone() {
		HCLoopTerm ret = (HCLoopTerm)super.clone();
		ret.component = (HCTerm)component.clone();
		return ret;
	}

	@Override
	public void generateSTGold(STG stg, HCChannelSenseController sig, Place inPlace, Place outPlace) {
		
		HCSTGGenerator hc = (HCSTGGenerator)component;
		
		Place p = stg.addPlace("p", 0);
		
		int num=stg.getSignalNumber("loop");
		
		Transition t1 = stg.addTransition(
				new SignalEdge(
						num, 
						EdgeDirection.DONT_CARE
						)
				);
		
		inPlace.setChildValue(t1, 1);
		t1.setChildValue(p, 1);
		
		stg.setSignature(num, Signature.DUMMY);
		
		
		
		hc.generateSTGold(stg, sig, p, p);
	}
	
	@Override
	public STG generateSTG(HCChannelSenseController sig, Set<Place> inPlaces, Set<Place> outPlaces, boolean enforce, boolean solveCSC) {
		
		HCSTGGenerator hc = (HCSTGGenerator)component;
		
		Set<Place> inP = new HashSet<Place>();
		Set<Place> outP = new HashSet<Place>();
		
		STG stg = hc.generateSTG(sig, inP, outP, enforce, solveCSC);
		
		inPlaces.addAll(STGUtil.cartesianProductBinding(stg, inP, outP));
		
		outPlaces.addAll(inPlaces);
		
		return stg;
	}
}

