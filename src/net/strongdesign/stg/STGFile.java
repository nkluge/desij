/**
 * Copyright 2004,2005,2006,2007,2008,2009,2010,2011 Mark Schaefer, Dominic Wist
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

/*
 * Created on 05.10.2004
 *
 */
package net.strongdesign.stg;

import java.awt.Point;
import java.io.StringReader;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.strongdesign.desij.CLW;
import net.strongdesign.desij.Messages;
import net.strongdesign.stg.parser.GParser;
import net.strongdesign.stg.parser.ParseException;
import net.strongdesign.stg.traversal.CollectorFactory;
import net.strongdesign.stg.traversal.Condition;
import net.strongdesign.stg.traversal.ConditionFactory;

/**
 * This class is for saving and loading STGs. Until now only the .g format is
 * supported as used e.g. by Petrify
 * 
 * check for parsing of freaky files
 * 
 * @author Mark Schaefer
 *  
 */

public abstract class STGFile {
	
	
	private static final String signalPrefix = "_";
	
	public static String convertToDot(STG stg, Collection<Node> nodes, String l) {
		
		//result contains the content of the file to be written at the and of the method
		StringBuilder result = new StringBuilder();
		
		//ehre, wem ehre gebuehrt :-)
		result.append("/*Generated by DesiJ "+new Date() + "*/\n");
		result.append("/*(c) 2004-2007 by Mark Schaefer, University of Augsburg, www.uni-augsburg.de */\n");
		result.append("/*Number of places: " + stg.getNumberOfPlaces() + "  Number of Transitions: " + stg.getNumberOfTransitions() +"*/\n\n");
		
		//now the *.g stuff
		result.append(	"\ndigraph {\nsize=\"7,10\"\n"+"" +
						"center=true\n"+"" +
						"labelloc=bottom\n"+
						"concentrate=false\n"
					);
		
		result.append("label=\"" + l + "\\nGenerated by DesiJ - "+new Date()+"\"\n");
		
		for (Place place : stg.getPlaces(ConditionFactory.ALL_PLACES  )) {
			if (place.getMarking()>0 || !ConditionFactory.MARKED_GRAPH_PLACE.fulfilled(place) || nodes.contains(place)) {
				String marking = place.getMarking()>0?""+place.getMarking():"";
				if (nodes.contains(place))
					result.append("\""+place.getIdentifier() +"\" [shape=circle, peripheries=2, label=\""+ marking +"\" ]\n");
				else
					result.append("\""+place.getIdentifier() +"\" [shape=circle, label=\""+ marking +"\" ]\n");
				
				for (Node child : place.getChildren()) {
					result.append("\""+place.getIdentifier() +"\"");
					if (place.getChildValue(child)>1) {
						int w = place.getChildValue(child);
						result.append(" -> \"" + child.getIdentifier() + "\" [style=\"setlinewidth("+w+")\",label=\""+w+"\"]\n");
					}
					else
						result.append(" -> \"" + child.getIdentifier() +"\" \n");
				}
				result.append("\n"); 
			}
		}
		for (Transition transition : stg.getTransitions(ConditionFactory.ALL_TRANSITIONS)  )	{
			String style=null;
			String label=transition.getString(Node.UNIQUE);
			
			Signature sig = stg.getSignature(transition.getLabel().getSignal()); 
			if (sig==Signature.DUMMY) { 
				style="height=0,fontsize=10,";
				label=transition.getString(Node.UNIQUE);
			}
			if (sig==Signature.INPUT)
				style="height=0.25,style=bold,color=red,";
			if (sig==Signature.OUTPUT)
				style="height=0.25,color=blue,";
			if (sig==Signature.INTERNAL)
				style="height=0.25,color=green,style=rounded,";
			
			if (nodes.contains(transition))
				result.append("\""+transition.getIdentifier() +
						"\"  ["+style+"shape=rectangle,peripheries=2,label=\"" +label+    "\"]\n ");
			else
				result.append("\""+transition.getIdentifier() +
					"\"  ["+style+"shape=rectangle,label=\"" +label+    "\"]\n ");
			
			for (Node child : transition.getChildren()) {
				result.append("\""+transition.getIdentifier()+"\"" );
				if (ConditionFactory.MARKED_GRAPH_PLACE.fulfilled((Place)child) && ((Place)child).getMarking()==0 && !nodes.contains(child))
					if ( transition.getChildValue(child.getChildren().iterator().next())>1 )
						result.append(" -> \"" + child.getChildren().iterator().next().getIdentifier() + 
								"\" [label=\""+transition.getChildValue(child.getChildren().iterator().next())+"\"]\n");
					else
						result.append(" -> \"" + child.getChildren().iterator().next().getIdentifier()+"\"" );
				
				else
					if (transition.getChildValue(child)>1) {
						int w = transition.getChildValue(child);
						result.append(" -> \"" + child.getIdentifier() + 
								"\" [style=\"setlinewidth("+w+")\",label=\""+transition.getChildValue(child)+"\"]\n");
					}
						
					else
						result.append(" -> \"" + child.getIdentifier() + "\" \n");
				
				
				result.append("\n"); 
			}
			
		}
		
		result.append(" }"); 
		
		return result.toString();
		
	}
	

	/**
	 * Converts a *.g file to the internal representation. For this, a JavaCC generated parser is used.
	 * @param file
	 * @return
	 * @throws ParseException
	 * @throws STGException 
	 */
	
	public static STG convertToSTG(String file, boolean withCoordinates) throws  ParseException, STGException {
		GParser parser = new GParser(new StringReader(file));
		
		STG result = parser.STG();//
		result.clearUndoStack();
		return result;
	}
	
	
	public static String  convertToG(STG stg) {
		return convertToG(stg, true, false);
	}
	
	public static String convertToG(STG stg, boolean withSignalNames) {
		return convertToG(stg, withSignalNames, false);
	}
	
	public static String convertToG(STG stg, boolean withSignalNames, boolean implicitPlaces) {
		
		boolean implicit = implicitPlaces || CLW.instance.SAVE_ALL_PLACES.isEnabled();
		
		
		if (CLW.instance.REMOVE_REDUNDANT_BEFORE_SAVE.isEnabled()) {
			STGUtil.removeRedundantPlaces(stg, true);
		}
		
		//result contains the file to be written at the end of the method
		StringBuilder result = new StringBuilder();
		
		
		//collect all implicit places which have to be made explicit
		// initial comments
		result.append(Messages.getString("STGFile.stg_start_comment")+new Date() + "\n"); 
		result.append("#Number of places: " + stg.getNumberOfPlaces() + "  Number of Transitions: " + stg.getNumberOfTransitions() +"\n\n");
		
		Map<Node, String> savedNames = new HashMap<Node, String>();
		Set<String> used = new HashSet<String>();
		
		Condition<Place> mgPlace = ConditionFactory.MARKED_GRAPH_PLACE;
		
		// first, name all transitions
		
		for (Transition transition : stg.getTransitions(ConditionFactory.ALL_TRANSITIONS)  ) {
			Signature sig = stg.getSignature(transition.getIdentifier());
			if (sig== Signature.ANY) continue; // this transition type is not supported at the moment
			
			if (sig== Signature.DUMMY) {
				int id=2;
				String name=transition.getString(0);
				if (!withSignalNames) name  = signalPrefix;
				if (used.contains(name)) {
					while (used.contains(name+"_"+id)) {
						id++;
					}
					name=name+"_"+id;
				}
				
				savedNames.put(transition, name);
				used.add(name);
			} else {
				int id=2;
				String name=transition.getString(0);
				if (!withSignalNames) name  = signalPrefix + transition.getLabel();
				if (used.contains(name)) {
					while (used.contains(name+"/"+id)) {
						id++;
					}
					name=name+"/"+id;
				}
				savedNames.put(transition, name);
				used.add(name);
			}
		}
		

		// now name all the explicit places
		for (Place place : stg.getPlaces(ConditionFactory.ALL_PLACES)) {
			if (implicit||!mgPlace.fulfilled(place)) {
				// use explicit naming
				int id=2;
				String name=place.getString(0);
				if (used.contains(name)) {
					while (used.contains(name+"_"+id)) {
						id++;
					}
					name=name+"_"+id;
				}
				savedNames.put(place, name);
				used.add(name);
			}
		}
		
		
		//now the *.g stuff
		
		//the different signals	
		for (Signature signature : Signature.values()) {
			if (signature == Signature.ANY) continue;
			
			Set<Integer> signals = new HashSet<Integer>(stg.collectFromTransitions(
					ConditionFactory.getSignatureOfCondition(signature), 
					CollectorFactory.getSignalCollector()));

			if (! signals.isEmpty()) {
				result.append(signature.getGFormatName() + " ");
				
				for (Integer signal : signals) {
					if (withSignalNames) {
						result.append(stg.getSignalName(signal));
					}
					else {
						result.append(signalPrefix + signal);
					}
					
					result.append(" ");
				}
				
				result.append("\n"); 
			}
		}
		
		// now the dummy signals (and ANY is also treated as dummy)
//		{
//			Set<Integer> dummies = new HashSet<Integer>(stg.collectFromTransitions(
//					ConditionFactory.getSignatureOfCondition(Signature.DUMMY), 
//					CollectorFactory.getSignalCollector()));
//			Set<Integer> any = new HashSet<Integer>(stg.collectFromTransitions(
//					ConditionFactory.getSignatureOfCondition(Signature.ANY), 
//					CollectorFactory.getSignalCollector()));
//			if (!dummies.isEmpty()||!any.isEmpty()) {
//				result.append(".dummies");
//				for (Integer signal : dummies) {
//					result.append(" ");
//					if (withSignalNames) {
//						result.append(stg.getSignalName(signal));
//					} else {
//						result.append(signalPrefix + signal);
//					}
//				}
//				for (Integer signal : any) {
//					result.append(" ");
//					if (withSignalNames) {
//						result.append(stg.getSignalName(signal));
//					} else {
//						result.append(signalPrefix + signal);
//					}
//				}
//				
//			}
//		}
		
		
		
		result.append("\n.graph\n");
		
		for (Place place : stg.getPlaces(ConditionFactory.ALL_PLACES  )) { 
			if (!place.hasChildren()) continue;
			if (savedNames.containsKey(place) )
			{	
				result.append(savedNames.get(place));
				
				for (Node child : place.getChildren()) {
					result.append(" ");
					if (savedNames.containsKey(child)) {
						result.append(savedNames.get(child));
						if (place.getChildValue(child)>1)
							 result.append("("+place.getChildValue(child)+")");
					}
						
				}
				
				result.append("\n"); 
			}
		}
		
		for (Transition transition : stg.getTransitions(ConditionFactory.ALL_TRANSITIONS)  ) {
			if (!transition.hasChildren()) continue;
			
			result.append(savedNames.get(transition));
			

			for (Node child : transition.getChildren()) {
				if (savedNames.containsKey(child)) {
					result.append(" " + savedNames.get(child));
					if (transition.getChildValue(child)>1)
						result.append("("+transition.getChildValue(child)+")");
				} else {
					Transition t = (Transition) child.getChildren().iterator().next();
					result.append(" "+savedNames.get(t));
				}
			}
			
			result.append("\n"); 
		}
		
		
		//capacity removed due to new project csc aware decomposition
	/*	result.append("\n\n.capacity ");
		for (Place place : stg.getPlaces(ConditionFactory.getAll())  ) 
			if (implicit || !ConditionFactory.getMarkedGraphPlaceCondition().fulfilled(place) || explicit.contains(place))
				result.append(" " + place.getString(Node.UNIQUE) + "=128 " ); 
			else
				result.append(" <" + place.getParents().iterator().next().getString(Node.UNIQUE) + "," + 
						place.getChildren().iterator().next().getString(Node.UNIQUE) +">" + "=128 "); 
		
	*/
		result.append("\n\n.marking {"); 
		
		
		for (Place place : stg.getPlaces(ConditionFactory.ALL_PLACES)  ) 
			if (place.getMarking()>0) {
				String marking = place.getMarking()==1?"":"="+place.getMarking();
				
				if (savedNames.containsKey(place)) {
					result.append(" " + place.getString(0)  ); 
				} else {
					result.append(" <" );
					result.append( 	
							savedNames.get(place.getParents().iterator().next()) + "," +
							savedNames.get(place.getChildren().iterator().next()));
					result.append( ">");
				}
				result.append(marking);
			}
		
		
		result.append(" }\n.end\n");
		
		if (stg.isWithCoordinates()) {
			// now save coordinates
			
			result.append(" \n.coordinates\n");
			
			// all transitions first
			for (Map.Entry<Node, Point> en: stg.getCoordinates().entrySet()) {
				if (!(en.getKey() instanceof Transition)) continue;
				
				Transition transition = (Transition)en.getKey();
				
				Point p = stg.getCoordinates(en.getKey());
				
				if (p!=null) {
					result.append(savedNames.get(transition));
					result.append(" "+p.x+" "+p.y+"\n");
				}
			}
			
			// all places
			for (Place place : stg.getPlaces(ConditionFactory.ALL_PLACES)  ) {
				
				Point p = stg.getCoordinates(place);
				
				if (p!=null) {
					
					if (savedNames.containsKey(place))
						result.append( savedNames.get(place)); 
					else {
						result.append("<" );
						result.append( 	
							savedNames.get(place.getParents().iterator().next()) + "," + 
							savedNames.get(place.getChildren().iterator().next()));
						
						result.append( ">");
					}
					
					result.append(" "+p.x+" "+p.y+"\n");
				}
				
			}
			result.append(" \n.coordinates_end\n");
		}
		return result.toString();
		
		
	}
	
	
	
}




