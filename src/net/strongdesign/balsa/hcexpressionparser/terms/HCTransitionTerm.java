package net.strongdesign.balsa.hcexpressionparser.terms;

import java.util.Set;

import net.strongdesign.stg.EdgeDirection;
import net.strongdesign.stg.Place;
import net.strongdesign.stg.STG;
import net.strongdesign.stg.SignalEdge;
import net.strongdesign.stg.Signature;
import net.strongdesign.stg.Transition;

public class HCTransitionTerm extends HCChannelTerm implements HCSTGGenerator {
	public String wire;
	public String direction;
	
	@Override
	public HCTerm expand(ExpansionType type, int scale, HCChannelSenseController sig, boolean oldChoice) {
		if (type==ExpansionType.UP) return this;
		return null;
	}

	@Override
	public String toString() {
		return wire+getChannelName()+direction;
	}
	
	@Override
	public void generateSTGold(STG stg, HCChannelSenseController sig, Place inPlace, Place outPlace) {
		
		Integer signal = stg.getSignalNumber(wire+getChannelName());
		Signature sg = Signature.INPUT;
		
		if (wire.equals("r")&&sig.isActive(channel)||
			wire.equals("a")&&!sig.isActive(channel)
				) {
			sg = Signature.OUTPUT;
		}
				
		stg.setSignature(signal, sg);
		
		EdgeDirection ed = EdgeDirection.DONT_CARE;
		if (direction.equals("+")) ed=EdgeDirection.UP;
		if (direction.equals("-")) ed=EdgeDirection.DOWN;
		
		SignalEdge se = new SignalEdge(signal, ed);
		
		Transition t = stg.addTransition(se);
		inPlace.setChildValue(t, 1);
		t.setChildValue(outPlace, 1);
		
	}

	
	@Override
	public void generateSTG(STG stg, HCChannelSenseController sig, Set<Place> inPlaces, Set<Place> outPlaces) {
		
		Integer signal = stg.getSignalNumber(wire+getChannelName());
		Signature sg = Signature.INPUT;
		
		if (wire.equals("r")&&sig.isActive(channel)||
			wire.equals("a")&&!sig.isActive(channel)
				) {
			sg = Signature.OUTPUT;
		}
				
		stg.setSignature(signal, sg);
		
		EdgeDirection ed = EdgeDirection.DONT_CARE;
		if (direction.equals("+")) ed=EdgeDirection.UP;
		if (direction.equals("-")) ed=EdgeDirection.DOWN;
		
		SignalEdge se = new SignalEdge(signal, ed);
		
		Transition t = stg.addTransition(se);
		
		// the function creates the input and the output places
		// The caller should make use of it, and change the STG if needed 
		
		Place inp = stg.addPlace("p", 0);
		Place outp = stg.addPlace("p", 0);
		inp.setChildValue(t, 1);
		t.setChildValue(outp, 1);
		inPlaces.add(inp);
		outPlaces.add(outp);
	}
}