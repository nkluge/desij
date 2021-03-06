/**
 * Copyright 2004-2013 Mark Schaefer, Dominic Wist, Stanislavs Golubcovs
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

package net.strongdesign.stg;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.rmi.server.SkeletonNotFoundException;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import lpsolve.LpSolveException;

import net.strongdesign.balsa.hcexpressionparser.terms.STGCompositionOperations;
import net.strongdesign.desij.CLW;
import net.strongdesign.desij.DesiJ;
import net.strongdesign.desij.DesiJException;
import net.strongdesign.desij.decomposition.BasicDecomposition;
import net.strongdesign.desij.decomposition.DecompositionEvent;
import net.strongdesign.desij.decomposition.STGInOutParameter;
import net.strongdesign.desij.decomposition.partitioning.PartitionerCommonCauseSubnet;
import net.strongdesign.statesystem.StateSystem;
import net.strongdesign.stg.solvers.RedundantPlaceSolverLP;
import net.strongdesign.stg.solvers.RedundantPlaceStatistics;
import net.strongdesign.stg.traversal.Condition;
import net.strongdesign.stg.traversal.ConditionFactory;
import net.strongdesign.stg.traversal.NotCondition;
import net.strongdesign.util.FileSupport;
import net.strongdesign.util.HelperApplications;
import net.strongdesign.util.StreamGobbler;



public abstract class STGUtil {
	
//	IMPLEMENT more efficient, maybe calculate once when necessary and save the result?
	public static boolean isMarkedGraph(STG stg) {
		return stg.getPlaces(new NotCondition<Place>(ConditionFactory.MARKED_GRAPH_PLACE) ).isEmpty();
	}
	
	/**
	 * Contracts all dummy transitions of an STG.
	 * --> strongly inspired by the method contract() of AbstractDecomposition 
	 * uses Reordering as well
	 * @param stg The stg.
	 * @return The number of contracted dummies
	 * @throws STGException If a dummy cannot be contracted.
	 */
	public static int removeDummies(STG stg) throws STGException {
		if (CLW.instance.ORDER_DUMMY_TRANSITIONS.isEnabled())
			return contractDummies(new TransitionQueueWithTreeMap(stg), stg);
		else
			return contractDummies(new TransitionQueueWithArrayList(stg), stg);
	}
	
	
	public static void removeDeadTransitions(STG stg)
	{
		
		while (true) {
			
			HashSet<Transition> dead = new HashSet<Transition>();
			
			// gather dead transitions  
			for (Place p: stg.getPlaces()) {
				if (p.getMarking()>0) continue;
				if (p.getParents().size()>0) continue;
				
				// all postset transitions are dead
				for (Node n: p.getChildren()) {
					dead.add((Transition)n);
				}
			}
			
			if (dead.isEmpty()) break;
			
			for (Transition t: dead) {
				HashSet<Node> parents = new HashSet<Node>();
				parents.addAll(t.getParents());
				
				for(Node n: parents) {
					if (n.getChildren().size()==1) stg.removeNode(n);
				}
				stg.removeTransition(t);
			}
		}
		
		
	}
	
	private static int contractDummies(TransitionQueue queue, STG stg) throws STGException
	{
		int result = queue.size();
		int contractions = 0;
		int redDel_frequency = 10;
		while(true)
		{
			int nroPlaces = stg.getNumberOfPlaces();
			int nroTransitions = stg.getNumberOfTransitions();
			Transition actTransition = queue.pop();
			if(actTransition != null)
			{
				Collection<Place> places = stg.contract(actTransition);
				for(Place place : places)
					queue.registerAffectedNodes(place.getNeighbours());
				
				contractions++;
				
				DesiJ.logFile.debug("Contracted transition: " + actTransition.getString(Node.UNIQUE));
			}
			
			
			// when only safe contractions are enabled, there is no risk of place count explosion
			// so checking it often won't change much
			if (!CLW.instance.SAFE_CONTRACTIONS.isEnabled()) {
				
				if (
						   CLW.instance.CHECK_RED_OFTEN.isEnabled()
						|| actTransition == null
						|| contractions * redDel_frequency >= queue.getContractibleTransitionsCount()
						|| stg.getNumberOfPlaces() > CLW.instance.PLACE_INCREASE.getDoubleValue() * nroPlaces
					) {
						contractions = 0;
						NeighbourTrackingNodeRemover remover = new NeighbourTrackingNodeRemover(stg);
						redDel(stg, remover);
						queue.removeNodes(remover.getRemoved());
						
						Collection<Node> neighbours = remover.getNeighbours();
						queue.registerAffectedNodes(neighbours);
						for(Node neighbour : neighbours)
							queue.registerAffectedNodes(neighbour.getNeighbours());
					}
			}
			
			if(stg.getNumberOfPlaces() == nroPlaces && stg.getNumberOfTransitions() == nroTransitions)
				break;
		}
		return result-queue.size();
	}
	
	
	
	public static boolean isChannelName(String name) {
		
		if (name.charAt(0)!='a'&&name.charAt(0)!='r') return false;
		
		int len = name.length();
		
		for (int i=1;i<len;i++) {
			if (name.charAt(i)<'0'||name.charAt(i)>'9') return false;
		}
		
		return true;
	}
	
	
//	
//	private static HashSet<Integer> dummifyInternalChannelSignals(STG stg, HashSet<Integer> exceptionList) throws STGException {
//		
//		PartitionerCommonCauseSubnet partitioner = new PartitionerCommonCauseSubnet(stg);
//		
//		HashSet<Integer> ret = new HashSet<Integer>();
//		
//		if (exceptionList==null) exceptionList = new HashSet<Integer>();
//		
//		for (Integer sig: stg.getSignals()) {
//			
//			if (exceptionList.contains(sig)) continue;
//			if (stg.getSignature(sig)!=Signature.INTERNAL) continue;
//			
//			String channelName = stg.getSignalName(sig);
//			if (!isChannelName(channelName)) continue;
//			
//			HashSet<Transition> triggers = new HashSet<Transition>();
//			HashSet<Transition> triggered = new HashSet<Transition>();
//			Transition transition = null;
//			
//			// for a given signal, find its first transition
//			for (Transition t: stg.getTransitions(ConditionFactory.ALL_TRANSITIONS)) {
//				if (t.getLabel().getSignal().equals(sig)) {
//					transition=t;
//					break;
//				}
//			}
//			if (transition==null) continue;
//			
//			triggered.add(transition);
//			triggers.add(transition);
//			partitioner.gatherPartition(triggers, triggered);
//			
//			HashSet<Integer> triggerSigs = new HashSet<Integer>();
//			for (Transition t: triggers) {
//				if (t.getLabel().getSignal().equals(sig)) continue;
//				triggerSigs.add(t.getLabel().getSignal());
//			}
//			HashSet<Integer> triggeredSigs = new HashSet<Integer>();
//			for (Transition t: triggered) {
//				if (t.getLabel().getSignal().equals(sig)) continue;
//				triggeredSigs.add(t.getLabel().getSignal());
//			}
//			
//			if (triggeredSigs.size()+triggerSigs.size()>10) continue;
//			
//			
//			
//			stg.setSignature(sig, Signature.DUMMY);
//			ret.add(sig);
//		}
//		
//		return ret;
//	}

	
	/**
	 * Try to lambdarize insignificant channel signals (internal signals only)
	 * - follow the exception list (signals that are not to be lambdarized)
	 * - avoid growing large components
	 */
	public static HashSet<Integer> removeChannelsBreeze(STG stg, Set<Integer> exceptionList) throws STGException {
		PartitionerCommonCauseSubnet partitioner = new PartitionerCommonCauseSubnet(stg);
		HashSet<Integer> ret = new HashSet<Integer>();
		
		// try to remove existing dummies (if any)
		removeDummiesBreeze(stg, true);
		
		HashSet<Integer> signals = new HashSet<Integer>();
		signals.addAll(stg.getSignals());
		
		int size = signals.size();
		int cur = 0;
		
		for (Integer sig: signals) {
			cur++;
			
			if (exceptionList.contains(sig)) continue;
			if (stg.getSignature(sig)!=Signature.INTERNAL) continue;
			
			String channelName = stg.getSignalName(sig);
			
			// TODO: only lambdarize channel signals? 
			// if (!isChannelName(channelName)) continue;
			
			HashSet<Transition> triggers = new HashSet<Transition>();
			HashSet<Transition> triggered = new HashSet<Transition>();
			Transition transition = null;
			
			// for a given signal, find its first transition
			for (Transition t: stg.getTransitions(ConditionFactory.ALL_TRANSITIONS)) {
				if (t.getLabel().getSignal().equals(sig)) {
					transition=t;
					break;
				}
			}
			if (transition==null) continue;
			
			triggered.add(transition);
			triggers.add(transition);
			partitioner.gatherPartition(triggers, triggered);
			
			HashSet<Integer> triggerSigs = new HashSet<Integer>();
			for (Transition t: triggers) {
//				if (t.getLabel().getSignal().equals(sig)) continue;
				triggerSigs.add(t.getLabel().getSignal());
			}
			HashSet<Integer> triggeredSigs = new HashSet<Integer>();
			for (Transition t: triggered) {
//				if (t.getLabel().getSignal().equals(sig)) continue;
				triggeredSigs.add(t.getLabel().getSignal());
			}
			
			HashSet<Integer> common = new HashSet<Integer>();
			for (Integer signal: triggerSigs) {
				// add all internal signals from triggers
				if (stg.getSignature(signal)==Signature.INTERNAL)
					common.add(signal);
			}
			// only common signals are remaining
			common.retainAll(triggeredSigs);
			// do not contract problematic signals
			common.removeAll(exceptionList);
			
			if (triggeredSigs.size()+triggerSigs.size()-2*common.size()>8) continue;
			if (triggeredSigs.size()-common.size()>4) continue;
			if (triggerSigs.size()-common.size()>4) continue;
			
			
			// dummify all common signals, that occur in both triggers and triggered sets
			stg.setSignature(common, Signature.DUMMY);
			ret.addAll(common);
			
			STGUtil.relaxInjectiveSplitSharedPath2(stg);
			simpleDummyRemoval2(stg);
			tryZipUpAll(stg);
		}
		
		return ret;
	}
	
	/**
	 * Special type of removing dummies, 
	 * designed for working with STGs generated from breeze files
	 * @param stgParam
	 * @return
	 * @throws STGException
	 */
	public static void removeDummiesBreeze(STG stg, boolean relaxed, boolean recoverUncontracted) throws STGException {
		
		if (recoverUncontracted) {
			
			// create a list of problematic dummies
			HashSet<Integer> problematic = new HashSet<Integer>();
			
			boolean good = true;
			
			// find out problematic signals
			do {
				STG stgClone = stg.clone();
				// dummify all channel signals
				HashSet<Integer> dummified = removeChannelsBreeze(stgClone, problematic);
				
				good = true;
				
				// first, contract just to find the problematic dummies 
				removeDummiesBreeze(stgClone, true);
				
				for (Transition t: stgClone.getTransitions(ConditionFactory.IS_DUMMY)) {
					
					if (dummified.contains(t.getLabel().getSignal())) {
						problematic.add(t.getLabel().getSignal());
						good = false;
					}
				}
				
			} while (!good);
			
			// finally, remove dummies   
			removeChannelsBreeze(stg, problematic);
			removeDummiesBreeze(stg, true);
			
			// rename signals, which were left as problematic (so they don't look like channel names anymore)
//			HashMap<String, String> rename = new HashMap<String, String>();
//			for (Integer sig: problematic) {
//				String oldName = stg.getSignalName(sig);
//				rename.put(oldName, "c_"+oldName);
//			}
//			stg.renameSignals(rename);
			
			
		} else {
			removeDummiesBreeze(stg, relaxed);
		}
	}
	
	private static void removeDummiesBreeze(STG stg, boolean relaxed) throws STGException {
		
		int dum1, dum2;
		
		// do the redundancy check without the solver before the loop
		boolean lpsolve =  CLW.instance.USE_LP_SOLVE_FOR_IMPLICIT_PLACES.isEnabled();
		CLW.instance.USE_LP_SOLVE_FOR_IMPLICIT_PLACES.setEnabled(false);
		redDel(stg, new NeighbourTrackingNodeRemover(stg));
		CLW.instance.USE_LP_SOLVE_FOR_IMPLICIT_PLACES.setEnabled(lpsolve);
		
		STGUtil.simpleDummyRemoval2(stg);
		// initial relaxation - before any other operations
		
		if (relaxed)
			STGUtil.relaxInjectiveSplitSharedPath2(stg);
		
		//if (relaxed) return;
		
		while (true) {
			
			dum1=stg.getNumberOfDummies();
			//Contract dummies
			removeDummies(stg);
			dum2=stg.getNumberOfDummies();
			
//			System.out.println(" D"+dum1+"-> D"+dum2);
			
			Collection<Node> n = 
				redDel(stg, new NeighbourTrackingNodeRemover(stg));
			
			if (dum2==0) {
				// nothing more to contract, still try to do final basic improvements
				tryZipUpAll(stg);
				redDel(stg, new NeighbourTrackingNodeRemover(stg));
				return; 
			}
			
			
			if (!n.isEmpty()) continue;
			
			// if fails to do any relaxations, then return
			if (relaxed) {
				
				if (!STGUtil.relaxInjectiveSplitSharedPath2(stg)) {
//					System.out.println("relax1 failed");
					
					if (tryZipUpAll(stg)==0) {
//						System.out.println("merge failed");
						return;
					}
					
//					
//					if (!STGUtil.relaxInjectiveSplitMergePlaces(stg)) {
////						System.out.println("relax2 failed");
//						
//						if (tryZipUpAll(stg)==0) {
//							return;
//						}
//					}
				}
				
			} else return;
			
		}
		
	}
	
	// the following three (private) methods are dedicated to removeDummies()
	
	enum Reason {SYNTACTIC, OK}

	static Reason isContractable(STG stg, Transition transition) {
		
		if (stg.getSignature(transition.getLabel().getSignal()) != Signature.DUMMY) {
			DesiJ.logFile.debug("Contraction of " + transition.getString(Node.UNIQUE) + " is not possible because it is not a dummy");
			return Reason.SYNTACTIC;
		}

		if ( ! ConditionFactory.SECURE_CONTRACTION.fulfilled(transition)) {
			DesiJ.logFile.debug("Contraction of " + transition.getString(Node.UNIQUE) + " is not possible because it is not secure");
			return Reason.SYNTACTIC;
		}

		//TODO wird das doppelt geprueft ???
		if ( ConditionFactory.LOOP_NODE.fulfilled(transition)) {
			DesiJ.logFile.debug("Contraction of " + transition.getString(Node.UNIQUE) + " is not possible because it is a loop transition");
			return Reason.SYNTACTIC;
		}

		//TODO wird das doppelt geprueft ???
		if ( ConditionFactory.ARC_WEIGHT.fulfilled(transition)) {
			DesiJ.logFile.debug("Contraction of " + transition.getString(Node.UNIQUE) + " is not possible because it has no proper arc weights");
			return Reason.SYNTACTIC;
		}


		if (CLW.instance.SAFE_CONTRACTIONS.isEnabled()) {
			if (! ConditionFactory.SAFE_CONTRACTABLE.fulfilled(transition)) {
				if (CLW.instance.SAFE_CONTRACTIONS_UNFOLDING.isEnabled() && stg.getSize() <= CLW.instance.MAX_STG_SIZE_FOR_UNFOLDING.getIntValue()) {
					if (! new ConditionFactory.SafeContraction<Transition>(stg).fulfilled(transition)) {
						DesiJ.logFile.debug("Contraction of " + transition.getString(Node.UNIQUE) + " is not possible because it is dynamically unsafe");
						return Reason.SYNTACTIC;
					}
				}
				else {
					DesiJ.logFile.debug("Contraction of " + transition.getString(Node.UNIQUE) + " is not possible because it is structurally unsafe");
					return Reason.SYNTACTIC;
				}
			}
		}

		return Reason.OK;
	}
	
	
	public static Collection<Node> redDel(STG stg) {
		return redDel(stg, new DefaultNodeRemover(stg)); 
	}
	
	/**
	 * Delete redundant places and redundant transitions
	 * @param stg
	 * @param remover
	 * @return
	 */
	public static Collection<Node> redDel(STG stg, NodeRemover remover) {
		
		
		Collection<Node> result = new HashSet<Node>();

		if (CLW.instance.REMOVE_REDUNDANT_PLACES.isEnabled()) { 
			Collection<Place> r=STGUtil.removeRedundantPlaces(stg, remover);
			result.addAll(r);
			DesiJ.logFile.debug("Remove all redundant places: " + r.toString());
		}
		
		if (CLW.instance.REMOVE_REDUNDANT_TRANSITIONS.isEnabled()) { 
			Collection<Transition> r=STGUtil.removeRedundantTransitions(stg, remover);
			result.addAll(r);
			DesiJ.logFile.debug("Remove all redundant transitions: " + r.toString());
		}
		
		return result;
	}

	public static Set<Place> removeRedundantPlaces(STG stg, NodeRemover remover) {
		if (CLW.instance.RED_UNFOLDING.isEnabled() && (stg.getSize() <= CLW.instance.MAX_STG_SIZE_FOR_UNFOLDING.getIntValue()))
			return removeRedundantPlacesWithUnfolding(stg, false, remover);
		else
			return removeRedundantPlaces(stg, false, remover);
	}
	

	//THEO-XXX move generation of unfolding to constructor, is this correct? can a place become not redundant after del. of some other redundant place?
	public static Set<Place> removeRedundantPlacesWithUnfolding(STG stg, boolean repeat, NodeRemover remover) {
		File unfolding = null;

		try {
			//where the STG is saved
			File tmpSTG = File.createTempFile("desij", ".g");

			//where the unfolding is saved
			unfolding = File.createTempFile("desij", ".unf");

			//save the STG, generate the unfolding 
			FileSupport.saveToDisk(STGFile.convertToG(stg, false, true), tmpSTG.getCanonicalPath());

			Process punf = HelperApplications.startExternalTool(HelperApplications.PUNF,  
					" -m"+HelperApplications.SECTION_START+"="+HelperApplications.SECTION_END +
					HelperApplications.SECTION_START+unfolding.getCanonicalPath()+HelperApplications.SECTION_END + 
					" " + 
					HelperApplications.SECTION_START+tmpSTG.getCanonicalPath()+HelperApplications.SECTION_END );

			if (CLW.instance.PUNF_MPSAT_GOBBLE.isEnabled()) {
				StreamGobbler.createGobbler(punf.getInputStream(), "punf", System.out);
				StreamGobbler.createGobbler(punf.getErrorStream(), "punf", System.err);
			}

			punf.waitFor();
			punf.getErrorStream().close();
			punf.getInputStream().close();
			punf.getOutputStream().close();


		}
		catch (IOException e) {
			e.printStackTrace();
		} 
		catch (InterruptedException e) {
			e.printStackTrace();
		}


		Set<Place> removed = new HashSet<Place>();
		boolean found;

		do {			
			found = false;		

			for (Place place : new HashSet<Place>(stg.getPlaces()) ) {
				// boolean red = false;
				
				//no children, does not affect firing of any transition -> definitely redundant
				if (place.getChildren().size() == 0)	{
					found = true;
					remover.removePlace(place);
					removed.add(place);
					continue;
				}

				//is the single parent of a transition -> definitely not redundant
				boolean singleParent = false;
				for (Node children : place.getChildren()) {
					singleParent = singleParent || children.getParents().size() == 1;
				}

				if (singleParent) continue;


				StringBuilder cl = new StringBuilder();
//				try {
//					cl.append(HelperApplications.getApplicationPath("mpsat") + " -F -d ");				
//				} 
//				catch (IOException e) {
//					e.printStackTrace();
//				}

				cl.append("~" + place.getString(Node.UNIQUE) + "&(");

				for (Node child : place.getChildren()) {
					boolean addedEt = false;
					for (Node sibbling : child.getParents()) {
						if (sibbling == place) continue;
						cl.append(sibbling.getString(Node.UNIQUE) + "&");
						addedEt = true;
					}
					if (addedEt)
						cl.deleteCharAt(cl.length()-1);
					cl.append("|");
				}
				cl.deleteCharAt(cl.length()-1);
				cl.append(") ");

				try {
					File tmpOut= File.createTempFile("mpsat", ".out");
					cl.append(HelperApplications.SECTION_START+unfolding.getCanonicalPath()+HelperApplications.SECTION_END + 
							" " + 
							HelperApplications.SECTION_START+tmpOut.getCanonicalPath()+HelperApplications.SECTION_END );
					// Process exec = Runtime.getRuntime().exec(cl.toString());
					Process exec = HelperApplications.startExternalTool(HelperApplications.MPSAT, 
							" -F -d " + 
							cl.toString());

					if (CLW.instance.PUNF_MPSAT_GOBBLE.isEnabled()) {
						StreamGobbler.createGobbler(exec.getInputStream(), "mpsat", System.out);
						StreamGobbler.createGobbler(exec.getErrorStream(), "mpsat", System.err);
					}
					exec.waitFor();
					exec.getErrorStream().close();
					exec.getInputStream().close();
					exec.getOutputStream().close();

					String res = FileSupport.loadFileFromDisk(tmpOut.getCanonicalPath());
					if (res.startsWith("NO")) {
						found = true;
						remover.removePlace(place);
						removed.add(place);
					}

				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		} while (repeat && found);

		return removed;
	}


	static class GreatestPresetFirst implements Comparator<Place> {
		@Override
		public int compare(Place p1, Place p2) {
			// higher marking goes first
			
			int t = p2.getMarking()- p1.getMarking();
			if (t==0) {
				// now compare by preset sizes
				t = p2.getParents().size() - p1.getParents().size();
				if (t==0) {
					t = p2.getChildren().size() - p1.getChildren().size();
				}
			}
			
			return t;
		}
	}
	
	public static Set<Place> removeRedundantPlaces(STG stg, boolean repeat, NodeRemover remover) {
		
		Set<Place> result = new HashSet<Place>();
		
		if (!CLW.instance.REMOVE_REDUNDANT_PLACES.isEnabled()) return result;
		
		boolean found;
		
		// first, remove all redundant places that are quick to find
		Condition<Place> redPlace = ConditionFactory.getRedundantPlaceCondition(stg);
		
		if (true||!CLW.instance.USE_LP_SOLVE_FOR_IMPLICIT_PLACES.isEnabled()) 
		{
			do {
				found = false;
				int y=stg.getNumberOfPlaces();
				if (y>100000) throw new DesiJException("The STG has more than 100000 places."); //System.exit(1);			
				
				LinkedList<Place> places = new LinkedList<Place>();
				places.addAll(stg.getPlaces());
				Collections.sort(places, new STGUtil.GreatestPresetFirst());
				
				for (Place place : places ) {
					if (redPlace.fulfilled(place)) {
						RedundantPlaceStatistics.totalStructuralChecks++;
						found = true;
						remover.removePlace(place);
						result.add(place);
					}
				}
			} while (repeat && found);
		}
		
		
		if (CLW.instance.USE_LP_SOLVE_FOR_IMPLICIT_PLACES.isEnabled()) {
			long time = System.currentTimeMillis();
			
			Date start = new Date();
			
			int d=CLW.instance.IPLACE_LP_SOLVER_DEPTH.getIntValue();
			int foundNum = 0;
			
			
//			redPlace = ConditionFactory.getRedundantPlaceLPCondition(stg, d);
			
//			redPlace = ConditionFactory.getRedundantPlaceLP2Condition(stg, d);
			
//			Condition<Place> redPlace2 = ConditionFactory.getRedundantPlaceLP2Condition(stg, d);
			
			redPlace = ConditionFactory.getImplicitPlaceCondition(stg, d);
			
			RedundantPlaceSolverLP solver = new RedundantPlaceSolverLP();
			
			do {
				found = false;
				int i = 0;
				int pl = stg.getPlaces().size();
				
				LinkedList<Place> places = new LinkedList<Place>();
				places.addAll(stg.getPlaces());
				Collections.sort(places, new STGUtil.GreatestPresetFirst());
				
				for (Place place : places ){
					i++;
					
/*					if (d==0) {
						double tt = (System.currentTimeMillis()-(double)startTime)/1000;
						System.out.printf("LP solver for place %d/%d |pre|=%d |post|=%d total found:%d time elapsed:%0.2f s\n", i, pl, place.getParents().size(), place.getChildren().size(), foundNum, tt);
					} else
	*/				
					
					if ((System.currentTimeMillis()-time)>5000) {
						time=System.currentTimeMillis();
						
						System.out.printf("LP solver: place %d/%d |pre|=%d |post|=%d total found:%d time elapsed: "
								+(new Date().getTime() - start.getTime())/1000.0+" s\n", 
								i, pl, place.getParents().size(), place.getChildren().size(), foundNum);
					}
					
					
					boolean r1 = solver.isRedundant2(stg, place, d);
//					boolean r2 = solver.isRedundant2(stg, place, d);
					
//					boolean r1 = redPlace.fulfilled(place);
//					boolean r2 = redPlace2.fulfilled(place);
//					
//					if (r1&&!r2) {
//						System.out.print("ERROR! +  -\n");
//					}
//					
//					if (!r1&&r2) {
//						System.out.print("ERROR! -  +\n");
//					}
					
					
					if (r1) {
						found = true;
						remover.removePlace(place);
						result.add(place);
						foundNum ++;
					}
					
				}
			} while (false && repeat && found);
			
//			System.out.printf("LP solver found:%d\n", foundNum);
		}

		return result;
	}

	
	public static Set<Transition> removeRedundantTransitions(STG stg, NodeRemover remover) {
		Set<Transition> result = new HashSet<Transition>();
		
//		result.addAll(removeLoopOnlyTransitions(stg, remover));
//		result.addAll(DuplicateTransitionRemover.removeDuplicateTransitions(stg, remover));
//		
//		return result;
		Condition<Transition> redTransition = ConditionFactory.getRedundantTransitionCondition(stg);
		
		while (true) {
			
			java.util.List<Transition> t = stg.getTransitions(redTransition);
			if (t.size()==0) return result;
			remover.removeTransition(t.get(0));
			result.add(t.get(0));
		}
	}

	@SuppressWarnings("unused")
	private static Collection<? extends Transition> removeLoopOnlyTransitions(STG stg, NodeRemover remover) {
		Set<Transition> result = new HashSet<Transition>();
		for(Transition t : stg.getTransitions(ConditionFactory.LOOP_ONLY_TRANSITION))
		{
			result.add(t);
			remover.removeTransition(t);
		}
		return result;
	}


	/**
	 * Generates from a Collection of signals a set containing the both most popular
	 * signal edges of these signals.	 * 
	 * @param signals
	 * @return
	 */
	public static Set<SignalEdge> getEdges(Collection<Integer> signals) {
		Set<SignalEdge> result = new HashSet<SignalEdge>();

		for (Integer sig : signals) {
			result.add(new SignalEdge(sig, EdgeDirection.UP));
			result.add(new SignalEdge(sig, EdgeDirection.DOWN));
		}

		return result;
	}


	public static STG generateReachabilityGraph(STG stg) throws STGException  {
		STG result 								= new STG();

		for (Integer signal : stg.getSignals()) {
			result.setSignalName(signal, stg.getSignalName(signal));
			result.setSignature(signal, stg.getSignature(signal));
		}

		Map<Marking, Place> knownStates 		= new HashMap<Marking, Place>();
		Queue<Marking> toDoStates  				= new LinkedList<Marking>();
		Queue<Place> toDoPlaces	  				= new LinkedList<Place>();
		StateSystem<Marking, SignalEdge> sys 	= STGAdapterFactory.getStateSystemAdapter(stg);                

		Place startPlace = result.addPlace("p", 1);
		toDoStates.add(sys.getInitialState());
		toDoPlaces.add(startPlace);
		knownStates.put(sys.getInitialState(), startPlace);

		while ( !toDoStates.isEmpty() ) {
			Marking currentState = toDoStates.poll();
			Place currentPlace = toDoPlaces.poll();            

			for (SignalEdge event : sys.getEvents(currentState) ) {
				for (Marking newState : sys.getNextStates(currentState, event)) {
					Transition newTransition = result.addTransition(event);
					currentPlace.setChildValue(newTransition, 1);	

					Place targetPlace = knownStates.get(newState);

					if (targetPlace != null) 
						newTransition.setChildValue(targetPlace, 1);
					else {
						Place newPlace = result.addPlace("p", 0 ); 
						toDoStates.add(newState);
						toDoPlaces.add(newPlace);
						knownStates.put(newState, newPlace);
						newTransition.setChildValue(newPlace, 1);
					}
				}   
			}
		}
		return result;
	}

	public static int sizeOfReachabilityGraph(STG stg)   {
		Set<Marking> knownStates 				= new HashSet<Marking>();
		Queue<Marking> toDoStates				= new LinkedList<Marking>();

		StateSystem<Marking, SignalEdge> sys 	= STGAdapterFactory.getStateSystemAdapter(stg);                

		toDoStates.add(sys.getInitialState());
		knownStates.add(sys.getInitialState());

		int result = 0;

		while ( !toDoStates.isEmpty() ) {
			++result;
			if (result % 1000 ==0 )
				System.err.print(".");
			Marking currentState = toDoStates.poll();			

			for (SignalEdge event : sys.getEvents(currentState) ) {
				for (Marking newState : sys.getNextStates(currentState, event)) {
					if (knownStates.contains(newState))
						continue;

					toDoStates.add(newState);
					knownStates.add(newState);
				}
			}
		}

		return result;
	}
	
	/**
	 * Checks if transitions have the same signals and directions 
	 * @param t1
	 * @param t2
	 * @return
	 */
	public static boolean sameTransitions(Transition t1, Transition t2, boolean dummiesAreSame) {
		
		if (dummiesAreSame) {
			if (t1.getSTG().getSignature(t1.getLabel().getSignal())==Signature.DUMMY&&
				t2.getSTG().getSignature(t2.getLabel().getSignal())==Signature.DUMMY) return true;
		}
		
		if (t1.getSTG().getSignature(t1.getLabel().getSignal())==Signature.DUMMY) return false;
		if (t2.getSTG().getSignature(t2.getLabel().getSignal())==Signature.DUMMY) return false;
		
		STG stg1 = t1.getSTG();
		STG stg2 = t2.getSTG();
		
		if (stg1==stg2) {
			return (t1.getLabel().getSignal().equals(t2.getLabel().getSignal())&&
					t1.getLabel().getDirection()==t2.getLabel().getDirection());
		} else {
			return (stg1.getSignalName(t1.getLabel().getSignal()).equals(
					stg2.getSignalName(t2.getLabel().getSignal()))
					&&
					t1.getLabel().getDirection()==t2.getLabel().getDirection());
		}
	}
	
	
	/**
	 * Returns true, if both nodes have identical postsets
	 * @param n1
	 * @param n2
	 * @return
	 */
	public static boolean samePostsets(Node n1, Node n2) {
		
		if (n1.getChildren().size()!=n2.getChildren().size()) return false;
		
		if (!n1.getChildren().containsAll(n2.getChildren())) return false;
		if (!n2.getChildren().containsAll(n1.getChildren())) return false;
		
		for (Node test : n1.getChildren())
			if (n1.getChildValue(test) != n2.getChildValue(test))
				return false;
		
		return true;
	}
	
	/**
	 * only applies injective labelling for a given transition
	 * @param stg
	 * @param tran
	 */
	public static void enforceInjectiveLabellingExperimental(STG stg, Transition tran) {
		LinkedList<Transition> merge = new LinkedList<Transition>();
		
		// collect all transitions that are to be merged, but ignore transitions with self-loops
		Collection<Transition> allt = stg.getTransitions(ConditionFactory.ALL_TRANSITIONS);
		
		for (Transition t: allt) {
			if (sameTransitions(tran, t, false)) {
				merge.add(t);
			}
		}
		
		if (merge.size()<2) return;
		
		
		// find common preset and post-set places (the ones that are shared among all transitions in merge
		Set<Node> common_pre = new HashSet<Node>();
		Set<Node> common_post = new HashSet<Node>();
		
		boolean first=true;
		
		for (Transition t: merge) {
			for (Node p: t.getParents()) {
				if (t.getParentValue(p)!=1) return;
				if (t.getChildValue(p)!=0) return;
				if (((Place)p).getMarking()!=0) return;
			}
			
			for (Node p: t.getChildren()) {
				if (t.getChildValue(p)!=1) return;
				if (t.getParentValue(p)!=0) return;
				if (((Place)p).getMarking()!=0) return;
			}
			
			if (first) {
				first=false;
				common_pre.addAll(t.getParents());
				common_post.addAll(t.getChildren());
			}
			
			common_pre.retainAll(t.getParents());
			common_post.retainAll(t.getChildren());
		}
		
		
		LinkedList<Place> pfrom = new LinkedList<Place>();
		LinkedList<Place> pto   = new LinkedList<Place>();
		
		// there should be exactly one place in pre-set and one place in post-set, which is 
		// a marked graph place
		// 
		for (Transition t: merge) {
			Set<Node> pre = new HashSet<Node>();
			pre.addAll(t.getParents());
			pre.removeAll(common_pre);
			
			Set<Node> post = new HashSet<Node>();
			post.addAll(t.getChildren());
			post.removeAll(common_post);
			
			if (pre.size()!=1) return;
			if (post.size()!=1) return;
			
			Place p1 = (Place)pre.iterator().next();
			Place p2 = (Place)post.iterator().next();
			
			if (!ConditionFactory.MARKED_GRAPH_PLACE.fulfilled(p1)) return;
			if (!ConditionFactory.MARKED_GRAPH_PLACE.fulfilled(p2)) return;
			
			
			// create place pair
			pfrom.add(p1);
			pto.add(p2);
		}
		
		
		// create arcs from presets to postsets
		LinkedList<Place> checkRedun  = new LinkedList<Place>();
		for (Node p: common_pre) {
			checkRedun.add((Place)p);
		}
		
		for (Node p: common_post) {
			checkRedun.add((Place)p);
		}

		
		for (int i=0;i<pfrom.size();i++) {
			Transition t1 = (Transition)pfrom.get(i).getParents().iterator().next();
			Transition t2 = (Transition)pto.get(i).getChildren().iterator().next();
			Place p = stg.addPlace("p", 0);
			checkRedun.add(p);
			
			t1.setChildValue(p, 1);
			p.setChildValue(t2, 1);
		}
		
		// all connect to the main places (indexed 0)
		for (int i=1;i<pfrom.size();i++) {
			Transition t1 = (Transition)pfrom.get(i).getParents().iterator().next();
			Transition t2 = (Transition)pto.get(i).getChildren().iterator().next();
			
			Place p = pfrom.get(0);
			t1.setChildValue(p, 1);
			
			p = pto.get(0);
			p.setChildValue(t2, 1);
		}
		
		
		// remove all merged nodes, apart from the ones indexed 0
		for (int i=1;i<pfrom.size();i++) {
			Node n = pfrom.get(i);
			Node c = n.getChildren().iterator().next();
			Node n2= pto.get(i);
			stg.removeNode(n);
			stg.removeNode(c);
			stg.removeNode(n2);
		}
		

		// launch the redundancy check
		removeRedundantPlaces(stg);
	}
		
	
	
	public static boolean isSamePostset(Node node1, Node node2) {
		
		Set<Node> post1 = node1.getChildren();
		Set<Node> post2 = node2.getChildren();
		
		
		// postsets are different because they have different size
		if (post1.size()!=post2.size()) return false;
		
		Set<Node> test = new HashSet<Node>();
		
		// if the intersection of the sets is different from post1, they are different
		// no need to check the other way around, because their element count is the same
		test.addAll(post1);
		if (test.retainAll(post2)) return false;
		
		// check also that the arcs have the same weight
		for (Node n: test) {
			if (node1.getChildValue(n)!=node2.getChildValue(n)) return false;
		}
		
		return true;
	}
	
	/**
	 * Special type of injective labelling, it tries to go backwards from a merge-place
	 * and merge together preset transitions
	 * @param stg
	 * @param tran
	 */
	public static boolean tryZipUp(STG stg, Place place, NodeRemover remover) {
		
		// check that the presets of merged transitions are not dummies
		// WARNING: when variable is false, this may be in conflict with the merge-place splitting operation  
		boolean checkDummyPreset=false;
		
		if (place.getParents().size()<=1) return false;
		
		LinkedList<Node> trans = new LinkedList<Node>();
		
		trans.addAll(place.getParents());
		
		boolean zippedSomething = false;
		
		for (Node t1: trans) {
			
			// there must be no dummy transitions in the immediate preset transitions
			// (unless we are about to merge dummy transitions)
			
			boolean foundDummy1 = false;
			// if both transitions are dummy, do not avoid their merging based on pre-set transitions
			if (checkDummyPreset&&t1.getSTG().getSignature(((Transition)t1).getLabel().getSignal())!=Signature.DUMMY)
				for (Node tmpPlace: t1.getParents()) {
					for (Node tt: tmpPlace.getParents()) {
						if (stg.getSignature(((Transition)tt).getLabel().getSignal())==Signature.DUMMY) {
							foundDummy1 = true;
							break;
						}
					}
				}
			
			if (foundDummy1) continue;
			
	t2loop:	for (Node t2: trans) {
				if (t1==t2) continue;
				
				
				if (!sameTransitions((Transition)t1, (Transition)t2, true)) continue;
				
				// there must be no dummy transitions in the immediate preset transitions
				// to avoid conflicts with the merge place splitting
				// however, splitting dummy transitions is not allowed, so we don't care about presets, when t1 and t2 are dummies 
				
				boolean foundDummy2 = false;
				
				// if both transitions are dummy, do not avoid their merging based on pre-set transitions
				if (checkDummyPreset&&t2.getSTG().getSignature(((Transition)t2).getLabel().getSignal())!=Signature.DUMMY)
					for (Node tmpPlace: t2.getParents()) {
						for (Node tt: tmpPlace.getParents()) {
							if (stg.getSignature(((Transition)tt).getLabel().getSignal())==Signature.DUMMY) {
								foundDummy2 = true;
								break;
							}
						}
					}
				
				if (foundDummy2) continue;
				
				
				// both transitions must have the same post-set
				if (!isSamePostset(t1, t2)) continue;
				
				
				// find common preset places
				HashSet<Node> common = new HashSet<Node>();
				common.addAll(t1.getParents());
				common.retainAll(t2.getParents());
				
				// each common preset place has to have the same weight towards t1 and t2
				for (Node p: common) {
					if (p.getChildValue(t1)!=p.getChildValue(t2))
						continue t2loop;
				}
				
				// find non-common preset places
				HashSet<Node> t1par = new HashSet<Node>();
				HashSet<Node> t2par = new HashSet<Node>();
				t1par.addAll(t1.getParents());
				t2par.addAll(t2.getParents());
				
				t1par.removeAll(common);
				t2par.removeAll(common);
				
				if (t1par.size()!=1||t2par.size()!=1) continue;
				
				Place p1 = (Place)t1par.iterator().next();
				Place p2 = (Place)t2par.iterator().next();
				
				if (p1==p2) continue;
				
				// W(p_{1},t_{1})=W(p_{2},t_{2})=1
				if (t1.getParentValue(p1)!=1) continue;
				if (t1.getParentValue(p1)!=t2.getParentValue(p2)) continue;
				
				
				if (place.getParentValue(t1)!=place.getParentValue(t2)) continue;
				
				// TODO: allow common postset for p1, p2?
				// |\post{p_1}|=|\post{p_2}|=1
				 
				if (p1.getChildren().size()!=1||p2.getChildren().size()!=1) continue;
				
				// presets of p1 and p2 must be disjoint
				Set<Node> test = new HashSet<Node>();
				test.addAll(p1.getParents());
				test.retainAll(p2.getParents());
				if (!test.isEmpty()) continue;
				
				// set the sum of tokens
				int toks = p1.getMarking()+p2.getMarking();
				p1.setMarking(toks);
				
				// now simply copy all arcs *p2->p2 to p1
				for (Node t: p2.getParents()) {
					int val = p2.getParentValue(t);
					p1.setParentValue(t, val);
				}
				
				if (remover!=null) {
					remover.removePlace(p2);
					remover.removeTransition((Transition)t2);
				}
				
				stg.removePlace(p2);
				stg.removeTransition((Transition)t2);
				
				zippedSomething = true;
				
				tryZipUp(stg, p1, remover);
				
			}
		}
		
		return zippedSomething;
	}
	
	public static int tryZipUpAll(STG stg) {
		int ret = 0;
		
		// for each merge place
		HashSet<Place> places = new HashSet<Place>();
		
		places.addAll(stg.getPlaces());
		
		for (Place place: places) {
			if (tryZipUp(stg, place, null)) ret++;
		}
		return ret;
	}
		
	
	
	/**
	 * Special type of injective labelling, it tries to go forwards from a split-place
	 * This operation may hide some details of an STG and change it's behaviour!
	 */
	public static boolean tryZipDown(STG stg, Place place) {
		
		
		if (place.getChildren().size()<=1) return false;
		LinkedList<Node> trans = new LinkedList<Node>();
		
		trans.addAll(place.getChildren());
		
		for (Node t1: trans) {
			for (Node t2: trans) {
				if (t1==t2) continue;
				if (!sameTransitions((Transition)t1, (Transition)t2, false)) continue;
				
				if (t1.getChildren().size()!=1||t2.getChildren().size()!=1) continue;
				
				Place p1 = (Place)t1.getChildren().iterator().next();
				Place p2 = (Place)t2.getChildren().iterator().next();
				
				if (p1==p2) continue;
				
				if (place.getChildValue(t1)!=place.getChildValue(t2)) continue;
				if (t1.getChildValue(p1)!=t2.getChildValue(p2)) continue;
				
				if (p1.getParents().size()!=1||p2.getParents().size()!=1) continue;
				
				// postsets of p1 and p2 must be disjoint
				Set<Node> test = new HashSet<Node>();
				test.addAll(p1.getChildren());
				test.retainAll(p2.getChildren());
				if (!test.isEmpty()) continue;
				
				// now simply copy all connections from *p2 to p2 in p1
				int toks = p1.getMarking()+p2.getMarking();
				p1.setMarking(toks);
				
				for (Node t: p2.getChildren()) {
					int val = p2.getChildValue(t);
					p1.setChildValue(t, val);
				}
				
				stg.removePlace(p2);
				stg.removeTransition((Transition)t2);
				
				tryZipDown(stg, p1);
			}
		}
		
		return true;
	}
	
	
	/**
	 * For a given transition it tries to merge all transitions 
	 * with the same signal direction into one instance
	 * It transforms the stg passed as an argument 
	 * @param stg, tran
	 * @return
	 */
	public static void enforceInjectiveLabelling(STG stg, Transition tran) {
		
		LinkedList<Transition> merge = new LinkedList<Transition>();
		
		// collect all transitions that are to be merged, but ignore transitions with self-loops
		Collection<Transition> allt = stg.getTransitions(ConditionFactory.ALL_TRANSITIONS);
		
		for (Transition t: allt) {
			if (sameTransitions(tran, t, false)) {
				merge.add(t);
			}
		}
		
		if (merge.size()<2) return;
		
		// there should be exactly one pre-place and one post-place for each of the transitions
		// with no tokens, those must be MG places
		for (Transition t: merge) {
			
			if (t.getParents().size()!=1) 
				return;
			
			if (t.getChildren().size()!=1) 
				return;
			
			Place p1 = (Place)t.getParents().iterator().next();
			Place p2 = (Place)t.getChildren().iterator().next();
			
			if (p1.getMarking()!=0) 
				return;
			
			if (p2.getMarking()!=0) 
				return;
			
			if (!ConditionFactory.MARKED_GRAPH_PLACE.fulfilled(p1))
				return;
			if (!ConditionFactory.MARKED_GRAPH_PLACE.fulfilled(p2)) 
				return;
		}
		
		LinkedList<Place> pfrom = new LinkedList<Place>();
		LinkedList<Place> pto   = new LinkedList<Place>();
		
		// create place pairs
		for (Transition t: merge) {
			pfrom.add((Place)t.getParents().iterator().next());
			pto.add((Place)t.getChildren().iterator().next());
		}
		
		// try expand up
		boolean success = true;
		while (success) {
			Transition mt = (Transition)pfrom.get(0).getParents().iterator().next();
			// check if can move up
			for (Place p: pfrom) {
				Transition t = (Transition)p.getParents().iterator().next();
				if (!sameTransitions(mt, t, false))  success=false;
				if (t.getParents().size()!=1) success=false;
				if (t.getChildren().size()!=1) success=false;
				
				Place pp = (Place)t.getParents().iterator().next();
				if (pto.contains(pp))   success=false;
				if (pp.getMarking()!=0) success=false;
				if (!ConditionFactory.MARKED_GRAPH_PLACE.fulfilled(pp)) success=false;
			}
			// move up
			if (success) {
				LinkedList<Place> np = new LinkedList<Place>();
				for (Place p: pfrom)
					np.add((Place)p.getParents().iterator().next().getParents().iterator().next());
				
				pfrom=np;
			}
		}
		
		// try expand down
		success = true;
		while (success) {
			Transition mt = (Transition)pto.get(0).getChildren().iterator().next();
			// check if can move down
			for (Place p: pto) {
				Transition t = (Transition)p.getChildren().iterator().next();
				if (!sameTransitions(mt, t, false))   success=false;
				if (t.getParents().size()!=1) success=false;
				if (t.getChildren().size()!=1) success=false;
				
				Place pp = (Place)t.getChildren().iterator().next();
				if (pfrom.contains(pp)) success=false;
				if (pp.getMarking()!=0) success=false;
				if (!ConditionFactory.MARKED_GRAPH_PLACE.fulfilled(pp)) success=false;
			}
			
			// move down
			if (success) {
				LinkedList<Place> np = new LinkedList<Place>();
				for (Place p: pto)
					np.add((Place)p.getChildren().iterator().next().getChildren().iterator().next());
				
				pto=np;
			}
		}
		
		// create arcs from presets to postsets
		LinkedList<Place> checkRedun  = new LinkedList<Place>();
		for (int i=0;i<pfrom.size();i++) {
			Transition t1 = (Transition)pfrom.get(i).getParents().iterator().next();
			Transition t2 = (Transition)pto.get(i).getChildren().iterator().next();
			Place p = stg.addPlace("p", 0);
			checkRedun.add(p);
			
			t1.setChildValue(p, 1);
			p.setChildValue(t2, 1);
		}
		
		// all connect to the main places (indexed 0)
		for (int i=1;i<pfrom.size();i++) {
			Transition t1 = (Transition)pfrom.get(i).getParents().iterator().next();
			Transition t2 = (Transition)pto.get(i).getChildren().iterator().next();
			
			Place p = pfrom.get(0);
			t1.setChildValue(p, 1);
			
			p = pto.get(0);
			p.setChildValue(t2, 1);
		}
		

		// remove all merged nodes, apart from the ones indexed 0
		for (int i=1;i<pfrom.size();i++) {
			Node n = pfrom.get(i);
			Node n2= pto.get(i);
			while (n!=n2) {
				n=n.getChildren().iterator().next();
				stg.removeNode(n.getParents().iterator().next());
			}
			stg.removeNode(n2);
		}
		

		// remove redundant places among the created ones
		Condition<Place> redPlace = ConditionFactory.getRedundantPlaceCondition(stg);
		for (Place p: checkRedun) {
			if (redPlace.fulfilled(p))
				stg.removePlace(p);
		}
		
	}
	
	
	static class SmallestPresetFirst implements Comparator<Transition> {

		@Override
		public int compare(Transition t1, Transition t2) {
			int t = t1.getParents().size() - t2.getParents().size();
			if (t==0) {
				t = t1.getChildren().size() - t2.getChildren().size();
			}
			return t;
		}
	}
	
	
	public static void reduceSTG(STG stg) {
		if (stg==null) return;


		STGInOutParameter componentParameter = new STGInOutParameter(
				stg);
		BasicDecomposition deco = new BasicDecomposition(
				"basic", stg);
		
		try {
			deco.reduce(componentParameter);
			

		} catch (STGException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Tries to enforce injective labelling to all transitions
	 * @param stg
	 */
	public static void enforceInjectiveLabelling(STG stg) {
		
		// try to enforce injectivity on all merge places
		HashSet<Place> places = new HashSet<Place>();
		
		
		places.addAll(stg.getPlaces());
		for (Place p: places) {
			if (p.getParents().size()<=1) continue;
			tryZipUp(stg, p, null);
		}
		
		for (Place p: places) {
			if (p.getChildren().size()<=1) continue;
			tryZipDown(stg, p);
		}
		
		
		// first, find all transitions that we want to try to enforce
		HashSet< Entry<Integer, EdgeDirection> > entries = new HashSet< Entry<Integer, EdgeDirection> >();
		HashSet<Transition> enforce = new HashSet<Transition>();
		
		Collection<Transition> ct = stg.getTransitions(ConditionFactory.ALL_TRANSITIONS);
		for (Transition t: ct) {
			Entry<Integer, EdgeDirection> en = 
				new AbstractMap.SimpleEntry<Integer, EdgeDirection>(t.getLabel().getSignal(), t.getLabel().getDirection());
			
			if (entries.contains(en) ) {
				enforce.add(t);
			} else {
				entries.add(en);
			}
			
		}
		
		// sort entries, smallest sizes go first
		LinkedList<Transition> enforce_list = new LinkedList<Transition>();
		enforce_list.addAll(enforce);
		Collections.sort(enforce_list, new SmallestPresetFirst());
		
		// 
		for (Transition t: enforce) {
			enforceInjectiveLabelling(stg, t);
		}
	}
	
	
	/**
	 * 
	 * @param fromp	- place, where the main path starts
	 * @param top	- place, where the main path ends
	 * @param p1	- place, where the copied transitions should go   
	 * @param p2	- the last place of the copied path
	 */
	private static void copyPath(STG stg, Place fromp, Place top, Place p1, Place p2) {
		
		while (fromp!=top) {
			Transition ft = (Transition)fromp.getChildren().iterator().next();
			Transition t = stg.addTransition(ft.getLabel());
			
			if (ConditionFactory.IS_DUMMY.fulfilled(ft)) {
				System.out.println("DUMMY coppied!");
			}
			
			p1.setChildValue(t, 1);
			
			// also add arc from each pre-place, which is not "fromp",
			for (Node p: ft.getParents()) {
				if (p==fromp) continue;
				p.setChildValue(t, p.getChildValue(ft));
			}
			
			fromp = (Place)ft.getChildren().iterator().next();
			
			if (fromp!=top) {
				
				Place np = stg.addPlace("p", 0);
				
				t.setChildValue(np, 1);
				p1 = np;
			} else {
				
				t.setChildValue(p2, 1);
				p1=p2;
			}
			
			
		}
	}
	
//	/**
//	 * Function relaxes injective labelling by splitting shared paths 
//	 * @param stg - the stg to work on
//	 */
//	public static boolean relaxInjectiveSplitSharedPath(STG stg) {
//		boolean ret = false;
//		
//		simpleDummyRemoval2(stg);
//		
//		Collection<Place> places = new HashSet<Place>();
//		places.addAll(stg.getPlaces());
//		
//		
//		// do the optimisation that is opposite to enforcing injective labelling
//		for (Place place: places) {
//			
//			// the primitive case of the shared path optimisation
//			//if (true||CLW.instance.SHARED_SHORTCUT_PLACE.isEnabled()) 
//			{
//				if (place.getMarking()==0&&place.getParents().size()>1&&place.getChildren().size()==1) {
//					Place place2 = place;
//					
//					boolean failed = false;
//					
//					while (!failed&&place2.getChildren().size()==1) {
//						
//						if (place2.getMarking()>0) failed=true;
//						
//						Transition t = (Transition)place2.getChildren().iterator().next();
//						
//						if (place2.getChildValue(t)!=1) failed=true;
//						
//						// do not allow dummy transitions on the path
//						if (ConditionFactory.IS_DUMMY.fulfilled(t)) { failed=true; break; }
//						
//						if (t.getParents().size()!=1||t.getChildren().size()!=1) {
//							failed=true;
//							break;
//						} else {
//							Place np = (Place)t.getChildren().iterator().next();
//							// do not allow self-loop transitions
//							if (np!=place2)
//								place2 = (Place)t.getChildren().iterator().next();
//							else
//								failed=true;
//							
//							if (t.getChildValue(place2)!=1) failed=true;
//
//						}
//						
//						// do not allow places with many parent transitions (apart from the first place)
//						if (place2.getParents().size()>1) failed=true;
//					}
//					
//					Set<Node> parents  = new HashSet<Node>();
//					parents.addAll(place.getParents());
//					
//					Set<Node> children = new HashSet<Node>();
//					children.addAll(place2.getChildren());
//					
//					Set<Node> test = new HashSet<Node>();
//					test.addAll(parents);
//					test.addAll(children);
//					
//					Map<Transition, Place> tp = new HashMap<Transition, Place>();
//					Map<Place, Transition> pt = new HashMap<Place, Transition>();
//					
//					
//					if (parents.size()!=children.size()) failed=true;
//					
//					// check pre-sets and post-sets do not intersect 
//					if (!failed&&parents.size()+children.size()==test.size()) {
//						
//						for (Node t1: place.getParents()) {
//							
//							if (t1.getChildren().size()!=2) continue;
//							if (t1.getChildValue(place)!=1) continue;
//							
//							Iterator<Node> it = t1.getChildren().iterator();
//							Place p = (Place) it.next();
//							if (p==place) p = (Place) it.next();
//							
//							// for now only very primitive cases are considered
//							if (p.getMarking()>0) continue;
//							
//							if (!ConditionFactory.MARKED_GRAPH_PLACE.fulfilled(p)) continue;
//							
//							Transition t2 = (Transition) p.getChildren().iterator().next();
//							if (place2.getChildValue(t2)!=1) continue;
//							
//							
//							if (parents.contains(t1)&&children.contains(t2)) {
//								tp.put((Transition)t1,p);
//								pt.put(p, t2);
//								
//								parents.remove(t1);
//								children.remove(t2);
//							}
//						}
//					}
//					
//					if (parents.size()!=0) failed=true;
//					if (children.size()!=0) failed=true;
//					
//					
//					if (!failed) {
//						// 
//						
//
//						for (Entry<Transition, Place> e: tp.entrySet()) {
//							
//							Transition t1 = e.getKey();
//							Transition t2 = pt.get(e.getValue());
//							
//							if (
//								stg.getSignature(t1.getLabel().getSignal())==Signature.DUMMY
//								||
//								stg.getSignature(t2.getLabel().getSignal())==Signature.DUMMY
//								)
//							{
//								RedundantPlaceStatistics.totalSharedPathSplits++;
//								
//								// we do have some changes
//								ret=true;
//								
//								if (place.getParents().size()>1) {
//									
//									Place p1 = stg.addPlace("p", 0);
//									Place p2 = stg.addPlace("p", 0);
//									
//									t1.setChildValue(p1, 1);
//									p2.setChildValue(t2, 1);
//									
//									t1.setChildValue(place, 0);
//									place2.setChildValue(t2, 0);
//									
//									copyPath(stg, place, place2, p1, p2);
//									
//								}
//								
//								stg.removePlace(e.getValue());
//							}
//						}
//					}
//				}
//			}
//			
//		}
//		
//		return ret;
//	}

	
	public static boolean isSimplePath(Place p1, Place p2) {
		if (p1==p2) return false;
		if (p1.getMarking()>0) return false;
		if (p2.getMarking()>0) return false;
		
		if (p1.getChildren().size()>1) return false;
		
		
		Node n = p1.getChildren().iterator().next();
		if (p1.getChildValue(n)!=1) return false;
		
		while (n!=p2) {
			if (n.getChildren().size()!=1)
				return false;
			
			if (n.getParents().size()!=1) 
				return false;
			
			if (n instanceof Place) {
				Place p=(Place)n;
				if (p.getMarking()>0) return false;
				
			} else {
				Transition t=(Transition)n;
				//if (ConditionFactory.IS_DUMMY.fulfilled(t))	return false;
			}
			
			Node n2 = n.getChildren().iterator().next();
			if (n.getChildValue(n2)!=1) return false;
			n=n2;
		}
		
		return true;
	}
	
	public static boolean relaxInjectiveSplitSharedPath2(STG stg) {
		boolean ret = false;
		
		//simpleDummyRemoval2(stg);
		
		// just make sure the dummy count doesn't increase
		int d = stg.getTransitions(ConditionFactory.IS_DUMMY).size();
			
		Collection<Place> places = new HashSet<Place>();
		places.addAll(stg.getPlaces());
		
		boolean iteration_found = true; 
		
		while (iteration_found) {
			iteration_found=false;
			
			for (Place p1: places) {
				if (p1.getParents().size()<2) continue;
				if (p1.getChildren().size()!=1) continue;
				
				
				// find a simple path
				Node n = p1.getChildren().iterator().next();
				while (n!=null&&n!=p1&&n.getChildren().size()==1) 
					n=n.getChildren().iterator().next();
				
				Place p2 = null;
				
				if (n instanceof Place) {
					p2=(Place)n;
				} else continue;
				
				if (!isSimplePath(p1, p2)) continue;
				
				
				Set<Node> parents  = new HashSet<Node>();
				parents.addAll(p1.getParents());
				
				Set<Node> children = new HashSet<Node>();
				children.addAll(p2.getChildren());
				
				Set<Node> test = new HashSet<Node>();
				test.addAll(parents);
				test.addAll(children);
				
				if (parents.size()!=children.size()) continue;
				
				if (parents.size()+children.size()!=test.size()) continue;

				
				Map<Transition, Place> tp = new HashMap<Transition, Place>();
				Map<Place, Transition> pt = new HashMap<Place, Transition>();
				
				boolean failed=false;
				
				// for each transition in |*p1| do check it is a legitimate path user
				for (Node t: p1.getParents()) {
					if (t.getChildren().size()!=2) {
						failed=true;
						break;
					}
					
					Place p=null;
					
					for (Node pp: t.getChildren()) {
						if (pp!=p1) 
							p=(Place)pp;
					}
					
					if (!ConditionFactory.MARKED_GRAPH_PLACE.fulfilled(p)) failed=true;
					
					if (p.getMarking()!=0) failed=true;
					
					if (p.getChildren().size()!=1) failed=true;
					
					Transition t2 = (Transition)p.getChildren().iterator().next();
					
					if (!p2.getChildren().contains(t2)) failed=true; 
					
					if (!failed) {
						tp.put((Transition)t, (Place)p);
						pt.put((Place)p, (Transition)t2);
					}
					
					if (failed) break;
				}
				
				if (failed) continue;
				
				// now we are sure we have the shared path split
				for (Node t1: parents) {
					ret=true;
					iteration_found=true;
					
					
					
					Place p = tp.get(t1);
					Transition t2 = pt.get(p);
					stg.removePlace(p);
					RedundantPlaceStatistics.totalSharedPathSplits++;
					
					if (p1.getParents().size()>1) {
						Place np1 = stg.addPlace("p", 0);
						Place np2 = stg.addPlace("p", 0);
						copyPath(stg, p1, p2, np1, np2);
						t1.setChildValue(p1, 0);
						p2.setChildValue(t2, 0);
						t1.setChildValue(np1, 1);
						np2.setChildValue(t2, 1);
					}
					
					simpleDummyRemoval2(stg, (Transition)t1);
					simpleDummyRemoval2(stg, t2);
				}
			}
			
		}
		
		//System.out.println("Total path splits:" +RedundantPlaceStatistics.totalSharedPathSplits);
		
		return ret;
	}
	
	/**
	 * Function splits merge places to allow more contractions
	 * 
	 * @param stg - the stg to work on
	 * returns true if it did change something
	 */
	public static boolean relaxInjectiveSplitMergePlaces(STG stg) {
		
		boolean ret = false;
		
		// for each dummy transition, that has no loops and has a choice place in its preset
		for (Transition tran: stg.getTransitions(ConditionFactory.IS_DUMMY)) {
			
			if (ConditionFactory.LOOP_NODE.fulfilled(tran)) continue;
			
			boolean hasConflictPrePlace = false;
			boolean hasMergePostPlace = false;
			boolean failed = false;
			// does it have a conflict pre-place?
			for (Node pre: tran.getParents()) {
				if (pre.getChildren().size()>1) {
					hasConflictPrePlace=true;
					break;
				}
			}
			
			HashSet<Node> tt = new HashSet<Node>();
			
			// does it have a merge post-place?
			for (Node post: tran.getChildren()) {
				if (post.getParents().size()>1) {
					hasMergePostPlace = true;
					
					// all connections around the place must have weight 1
					for (Node par: post.getParents()) {
						if (post.getParentValue(par)>1) 
							failed=true;
					}
					
					for (Node chil: post.getChildren()) {
						if (post.getChildValue(chil)>1) 
							failed=true;
					}
					
				}
				
				// check that for each t: | *t intersected with tran* | < 2
				HashSet<Node> t = new HashSet<Node>();
				t.addAll(tt);
				t.retainAll(post.getChildren());
				
				if (t.isEmpty()) {
					tt.addAll(post.getChildren());
				} else {
					failed=true;
					break;
				}
				
			}
			
			
			HashSet<Node> postPlaces = new HashSet<Node>();
			postPlaces.addAll(tran.getChildren());
			
			
			// if both conditions occur, then it is not a reducible place, 
			if (!hasConflictPrePlace || !hasMergePostPlace) continue;
			
			// additional rule: only allow relaxation if no dummy is about to be split
			for (Node post: postPlaces) {
				if (post.getParents().size()>1) {
					for (Node t: post.getChildren()) {
						if (ConditionFactory.IS_DUMMY.fulfilled((Transition)t)) {
							failed=true;
							break;
						}
					}
				}
			}
			
			if (failed) continue;
			
			// conditions are good, apply relaxation for each of the tran*
			RedundantPlaceStatistics.totalMergePlaceSplits++;

			for (Node post: postPlaces) {
				
				if (post.getParents().size()>1) {
					
					ret=true; // we do have some changes
					
					// create a new place p with zero marking
					Place p  = stg.addPlace("p", 0);
					
					// switch connection tran->post to tran->p
					tran.setChildValue(post, 0);
					tran.setChildValue(p, 1);
					
					
					HashSet<Node> postTransitions = new HashSet<Node>();
					postTransitions.addAll(post.getChildren());
					
					// duplicate each of the post* transitions 
					for (Node postT: postTransitions) {
						
						// copy transition and all its connections
						Transition t = (Transition)postT;
						Transition dt = stg.addTransition(t.getLabel());
						
						for (Node pp: t.getParents())
							dt.setParentValue(pp, t.getParentValue(pp));
						
						for (Node pp: t.getChildren())
							dt.setChildValue(pp, t.getChildValue(pp));
						
						// switch connection post->dt to p->dt
						post.setChildValue(dt, 0);
						p.setChildValue(dt,1);
					}
					
				}
			}
			
		}
		
		System.out.println("Total splits:"+RedundantPlaceStatistics.totalMergePlaceSplits+ " new Transition count:"+stg.getNumberOfTransitions());
		
		return ret;
	}
	
	private static void collectNodes(Node node, int depth, Set<Node> collector) {
		if (depth==0) return;
		
		for (Node n: node.getParents()) {
			collector.add(n);
			collectNodes(n, depth-1, collector); 
		}
		
		for (Node n: node.getChildren()) {
			collector.add(n);
			collectNodes(n, depth-1, collector); 
		}
	}
			 
	/**
	 * Returns an STG showing structure surrounding a given node up to a given depth
	 * @param stg
	 * @param node
	 * @param depth
	 * @return new stg
	 */
	public static STG getNodeSurrounding(STG old_stg, Node node, int depth) {
		
		STG stg = new STG();
		
		// collect all nodes that are to be copied
		Set<Node> toCopy = new HashSet<Node>();
		toCopy.add(node);
		collectNodes(node, depth, toCopy);
		
		// produce the new stg with nodes from the collection
		Map<Node,Node> newNodes = new HashMap<Node,Node>();
		
		for (Node n: toCopy) {
			if (n instanceof Transition) {
				// its a transition
				Transition t = (Transition)n;
				
				// create the copied transition
				String name = old_stg.getSignalName(t.getLabel().getSignal());
				
				Integer newNum = stg.getSignalNumber(name); // get or create the signal
				
				Signature s = old_stg.getSignature(t.getLabel().getSignal());
				stg.setSignature(newNum, s);
				
				SignalEdge se = new SignalEdge(newNum, t.getLabel().getDirection());
				
				Transition newTransition = stg.addTransition(se, t.getIdentifier());
				
				newNodes.put(t, newTransition);
				
			} else {
				// its a place
				Place p = (Place)n;
				Place np = stg.addPlace(p.getLabel(), p.getMarking());
				newNodes.put(p, np);
			}
		}
		
		// produce same connections
		for (Node n1: toCopy) {
			for (Node n2: n1.getChildren()) {
				if (n1==n2) continue;
				newNodes.get(n1).setChildValue(newNodes.get(n2), n1.getChildValue(n2));
			}
		}
		
		return stg;
	}
	
	
	public static STG synchronousProduct(LinkedList<STG> stgs, boolean removeRedPlaces) {
		STG ret = null;
		if (stgs.size()==1) return stgs.get(0);
		
		// just make a stub for places
		HashSet<Place> p = new HashSet<Place>();
		ret = new STG();
		for (STG stg: stgs) {
			ret = STGCompositionOperations.synchronousProduct(ret, p, p, stg, p, p, removeRedPlaces, p, p);
		}
		return ret;
	}
	
	// method overloading because of NodeRemover idea
	
	public static Collection<Place> removeRedundantPlaces(STG stg) {
		return removeRedundantPlaces(stg, new DefaultNodeRemover(stg));
	}

	public static Collection<Place> removeRedundantPlaces(STG stg, boolean repeat) {
		return removeRedundantPlaces(stg, repeat, new DefaultNodeRemover(stg));
	}

	public static Collection<Transition> removeRedundantTransitions(STG stg) {
		return removeRedundantTransitions(stg, new DefaultNodeRemover(stg));
	}

	/**
	 * Removes - if possible - internal signals (encoding signals) 
	 * by preserving the CSC property.
	 * 
	 * @param stg --> must satisfy CSC
	 * @return number of removed internal signals
	 * return -1 --> impossible: either CSC wasn't satisfied initially or LP approximates not good enough
	 * @throws STGException 
	 */
	public static int removeInternalSignals(STG stg) throws STGException {
		// Implement Josep's algorithm for calculation of CSC support:
		
		// First, consider all internal signals to be unnecessary 
		// --> merely the external signals matter
		Set<Integer> unnecessaryInternals = new HashSet<Integer>(stg.getSignals(Signature.INTERNAL));
		Set<Integer> necessarySignals = new HashSet<Integer>(stg.getSignals());
		necessarySignals.removeAll(unnecessaryInternals);
		
		int returnValue = unnecessaryInternals.size();
		
		// initialize CSCChecking --> pick the right strategy here
//		ICSCCheckLPStrategy lpCSCCheck = CSCCheckerLPSolvePreCaching.
//				getCSCCheckerLPSolvePreCaching(stg); // singleton: uses the lpsolve java wrapper directly
		ICSCCheckLPStrategy lpCSCCheck = new CSCCheckerLPSimple(stg); // inefficient, but more transparent implementation
		
		try {
			while (!lpCSCCheck.execute(necessarySignals)) { // while CSC is not satisfied
				Set<Integer> newEncodingSignals = 
						lpCSCCheck.getUnbalancedSignals(necessarySignals);
				if (newEncodingSignals.isEmpty())
					return -1;
				else {
					necessarySignals.add(newEncodingSignals.iterator().next());
					--returnValue; // decrement the count of removable signals
				}
			}
		} catch (LpSolveException e) {
			e.printStackTrace();
		}
		
		unnecessaryInternals.removeAll(necessarySignals);
		
		stg.setSignature(unnecessaryInternals, Signature.DUMMY);
		removeDummies(stg);
		
		// are there un-removable signals (i.e. structurally un-removable)
		Set<Integer> dummiesLeft = stg.getSignals(Signature.DUMMY);
		dummiesLeft.retainAll(unnecessaryInternals);
		if (!dummiesLeft.isEmpty()) {
			returnValue -= dummiesLeft.size();
			stg.setSignature(dummiesLeft, Signature.INTERNAL);
		}
						
		return returnValue;
	}
	
	
	/**
	 * 1. creates the Cartesian product of the given two sets of places,
	 * 2. creates appropriate transition arcs
	 * 3. sets appropriate token counts
	 * 4. removes old arcs and old places form the STG
	 */
	static public Set<Place> cartesianProductBinding(STG stg, Set<Place> inPlaces, Set<Place> outPlaces) {
		
		Place newPlace;
		
		Set<Place> toDelete = new HashSet<Place>();
		Set<Place> toReturn = new HashSet<Place>();
		
		for (Place p1 : inPlaces) {
			for (Place p2: outPlaces) {
				int m1 = p1.getMarking(); 
				int m2 = p2.getMarking();
				newPlace = stg.addPlace("p", m1+m2);
				toReturn.add(newPlace);

				// now copy arcs
				for (Node n : p1.getParents()) {
					newPlace.setParentValue(n, p1.getParentValue(n));
				}
				for (Node n : p1.getChildren()) {
					newPlace.setChildValue(n, p1.getChildValue(n));
				}
				for (Node n : p2.getParents()) {
					newPlace.setParentValue(n, p2.getParentValue(n));
				}
				for (Node n : p2.getChildren()) {
					newPlace.setChildValue(n, p2.getChildValue(n));
				}
				
				// mark places, which will be removed from the STG
				toDelete.add(p1);
				toDelete.add(p2);
			}
		}
		
		// now remove all the marked places
		for (Place p: toDelete) {
			stg.removePlace(p);
		}
		
		return toReturn;
	}
	
//
//	/**
//	 * very quick simplification, removes simple dummy transitions
//	 * @param stg
//	 */
//	public static void simpleDummyRemoval(STG stg) {
//		// for each empty MG place check if it is the only post-set and the only preset for its parent and child,
//		// then merge pre-set and post-set transitions, if at least one of them is dummy
//		
//		Collection<Place> places = stg.getPlaces(ConditionFactory.MARKED_GRAPH_PLACE);
//		
//		for (Place p: places) {
//			if (p.getMarking()>0) continue;
//			
//			Transition t1 = (Transition)p.getParents().iterator().next();
//			Transition t2 = (Transition)p.getChildren().iterator().next();
//			
//			if (t1.getChildren().size()>1) continue;
//			if (t2.getParents().size()>1) continue;
//			
//			if (ConditionFactory.IS_DUMMY.fulfilled(t2)) {
//				// child is dummy
//				for (Node t2p: t2.getChildren()) {
//					t1.setChildValue(t2p, t2.getChildValue(t2p));
//				}
//				
//				stg.removePlace(p);
//				stg.removeTransition(t2);
//				continue;
//			} else {
//				if (ConditionFactory.IS_DUMMY.fulfilled(t1)) {
//					// parent is dummy
//					for (Node pt1: t1.getParents()) {
//						t2.setParentValue(pt1, t1.getParentValue(pt1));
//					}
//					
//					stg.removePlace(p);
//					stg.removeTransition(t1);
//					continue;
//				}
//			}
//		}
//	}
	
	
	
	public static void simpleDummyRemoval2(STG stg, Transition t) {
		
		if (!ConditionFactory.IS_DUMMY.fulfilled(t)) return;
		
		if (t.getParents().size()!=1)
			return;
		
		if (t.getChildren().size()!=1) 
			return;
		
		Place p1 = (Place)t.getParents().iterator().next();
		Place p2 = (Place)t.getChildren().iterator().next();
		
		if (p1.getChildren().size()!=1)
			return;
		
		if (p2.getParents().size()!=1) 
			return;
		
		if (p1.getChildValue(t)!=1) 
			return;
		if (t.getChildValue(p2)!=1) 
			return;
		
		int tokens = p1.getMarking()+p2.getMarking();
		
		for (Node tt: p2.getChildren()) {
			int v = p2.getChildValue(tt);
			p1.setChildValue(tt, v);
		}
		
		p1.setMarking(tokens);
		
		stg.removeTransition(t);
		stg.removePlace(p2);
	}
	
	public static void simpleDummyRemoval2(STG stg) {
		// for each dummy transition with 
		
		Collection<Transition> transitions = stg.getTransitions(ConditionFactory.IS_DUMMY);
		
		for (Transition t: transitions) {
			simpleDummyRemoval2(stg, t);
		}
		
	}

	public static void makeSignalsExclusive(STG stg,
			HashSet<Integer> exclusiveSignals) {
		
		// 1. create a new place with a token
		Place p = stg.addPlace("p_ex", 1);
		
		// 2. outgoing arcs to each of the + transitions
		// 3. incoming arcs from each of the - transitions
		for (Transition t: stg.getTransitions(ConditionFactory.ALL_TRANSITIONS)) {
			if (exclusiveSignals.contains(t.getLabel().getSignal())) {
				
				if (t.getLabel().getDirection() == EdgeDirection.UP) {
					p.setChildValue(t, 1);
					
				} else if (t.getLabel().getDirection() == EdgeDirection.DOWN) {
					
					t.setChildValue(p, 1);
				}
				
			}
		}
		
		
		// TODO Auto-generated method stub
		
	}
	
	
}
