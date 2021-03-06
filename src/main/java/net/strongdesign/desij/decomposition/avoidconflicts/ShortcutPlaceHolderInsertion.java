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

package net.strongdesign.desij.decomposition.avoidconflicts;

import java.util.Collection;
import java.util.Map;

import net.strongdesign.stg.Node;
import net.strongdesign.stg.Place;
import net.strongdesign.stg.STG;
import net.strongdesign.stg.STGException;
import net.strongdesign.stg.Signature;
import net.strongdesign.stg.Transition;

/**
 * @author Dominic Wist
 * Concrete Implementation of shortcut placeholder insertion such that the 
 * irreducible CSC conflict will be avoided
 */
public class ShortcutPlaceHolderInsertion extends PlaceHolderInsertion {
	
	private Transition delayer = null;
	private boolean shiftDelayer;
	private int potentialMarking;

	/**
	 * @param stg
	 * @param components
	 * @param nodeInfos
	 */
	public ShortcutPlaceHolderInsertion(STG stg, Collection<STG> components,
			Map<Node, NodeProperty> nodeInfos) {
		super(stg, components, nodeInfos);
	}

	/* (non-Javadoc)
	 * @see net.strongdesign.desij.decomposition.avoidconflicts.PlaceHolderInsertion#execute(net.strongdesign.stg.Transition, net.strongdesign.stg.Transition)
	 * Do not call before initializeTraversal!
	 * 
	 * Searching for a simple MG-path from t_entry to t_exit containing a non-input transition as delay transition
	 */
	@Override
	public boolean execute(Transition t_en, Transition t_ex)
			throws STGException {
		boolean status = false;
		if (!initialized) {
			return status;
		}
		
		// adapted depth-first search
		status = dfs(t_en);
		
		// complete the TraversalResult structure
		if (status) {
			myTraversalResult.prePlaceHolder.add(t_en);
			myTraversalResult.postPlaceHolder.add(this.delayer);
		}
		
		initialized = false;
		if (!status)
			return false;
		// insert placeholder according to myTraversalResult
		// doPlaceHolderInsertion(myTraversalResult, tTransInsertionCount++);
		return status;
	}
	
// ********************* private area ***************************************
	
	/****
	 * Starting a depth-first search.
	 * The Initialization has already been done.
	 * @param startVertex - Starting point for the search
	 * @return - True if the search finds a simple path to the exit transtion
	 * containing a non-input delay transition and False otherwise
	 */

	private boolean dfs(Transition startVertex) {
		// most of initialization already happened
		this.delayer = null;
		this.shiftDelayer = true;
		this.potentialMarking = 0;
		
		// mark startVertex as visited
		additionalNodeInfos.get(startVertex).isVisited = true;
		
		for (Node place: startVertex.getChildren()) 
			if (dfs_visit(place) && this.delayer != null)
				return true;
		return false;
	}
	
	/****
	 * Function that will be called recursively during the DFS
	 * In comparision to a standard DFS we remove the visiting marking of a node before returning the 
	 * recursive call --> Thus, we avoid repeating vertices and find simple paths only
	 * @param node - the currently considered node
	 * @return - Termination conditions:
	 * 1. False if the transition was already discovered
	 * 2. False if a Non-MG-place between t_entry and t_delay is discoverd or a Reverse-Conflict-Place between t_delay and t_exit
	 * 3. False if a relevant transition of the critical component is discovered --> so we have to deal with a much smaller graph only
	 * 4. True if the exit transition is found
	 */
	private boolean dfs_visit(Node node) {
				
		// check for MG-place -- only supporting stuff
		// it is not necessary to mark MG- or pure conflict-places as visited  
		// because there is at most one possiblity to enter them --> thus a cycle is impossible
		if (node instanceof Place) {
			Transition formerDelayer = this.delayer; // for reconstruction
			boolean formerShiftDelayer = this.shiftDelayer; // for reconstruction
			int formerPotentialMarking = 0; // for reconstruction
			
			if (this.delayer == null) { // we are between t_entry and t_d now
				if (!checkPlaceForMG((Place)node)) return false;
				myTraversalResult.marking += ((Place)node).getMarking();
			}
			else {
				if ( !checkPlaceForMG((Place)node) ) this.shiftDelayer = false;
				if (this.shiftDelayer) this.potentialMarking += ((Place)node).getMarking();
				if (!checkPlaceForNoMergeAndNoMarking((Place)node)) {
					if (this.shiftDelayer) {
						formerDelayer = this.delayer;
						this.delayer = null;
						myTraversalResult.marking += this.potentialMarking;
						formerPotentialMarking = this.potentialMarking; // for recovery
						this.potentialMarking = 0;
					}
					else {
						if (this.shiftDelayer) this.potentialMarking -= ((Place)node).getMarking();
						if (formerShiftDelayer) this.shiftDelayer = true;
						return false; // we are between t_d and t_exit now
					}
				}
			}
			
			for (Node trans: node.getChildren())
				if (dfs_visit(trans)) {
					if (this.delayer == null)
						this.delayer = formerDelayer; // backtrack until the last saved real delayer
					return true;
				}
			
			// Reconstruction of the former situation
			if ( (this.delayer == null) && (formerDelayer == null) ) // we are between t_entry and t_d during the backtracking
				myTraversalResult.marking -= ((Place)node).getMarking();
			else {
				if (this.shiftDelayer) {
					if (this.delayer == null) {
						this.delayer = formerDelayer;
						this.potentialMarking = formerPotentialMarking;
						myTraversalResult.marking -= this.potentialMarking;	
					}
					this.potentialMarking -= ((Place)node).getMarking();
				}
				else
					if (formerShiftDelayer) this.shiftDelayer = true;
			}
			return false;
		}
		
		// major task of dfs_visit
		else if (node instanceof Transition) {
			if (additionalNodeInfos.get(node).isVisited) return false; // vertex was already discovered
			
			// ** additional termination conditions **
			if (additionalNodeInfos.get(node).isExitTransition) {
				if ( this.delayer == null && stg.getSignature( ((Transition)node).getLabel().getSignal()) != Signature.INPUT )
					this.delayer = (Transition)node;
				return true;
			}
			// never traverse a transition of the critical component --> coping with complexity
			if (additionalNodeInfos.get(node).isRelevant || additionalNodeInfos.get(node).isEntryTransition)
				return false;
			// **
			
			additionalNodeInfos.get(node).isVisited = true; // vertex has just been discovered
			if ( this.delayer == null && stg.getSignature( ((Transition)node).getLabel().getSignal() ) != Signature.INPUT )
				this.delayer = (Transition)node; // find a delayer as early as possible
			
			for (Node place: node.getChildren())
				if (dfs_visit(place)) {
					additionalNodeInfos.get(node).isVisited = false; // necessary to find simple paths only
					return true;
				}
			additionalNodeInfos.get(node).isVisited = false; // necessary to find simple paths only
			if (this.delayer == node) this.delayer = null; // necassary to roll back the marking in myTraversalResult
			return false;
					
		}
		
		return false; // unreachable
	}
	
	
	/***
	 * Checks whether a place is a MG-place and has at least on children 
	 * @param place
	 * @return true if it is an MG-place and false otherwise
	 */
	private boolean checkPlaceForMG(Place place) {
		if (place.getParents().size() > 1 || place.getChildren().size() != 1) 
			return false;
		else
			return true;
	}
	
	/****
	 * Checks whether a place has no merge structure or reverse conflict resp.
	 * and has at least one children and is not marked --> optimised for unweighted STGs
	 * --> omit the check of the marking because of SeqParTree-Examples --> [seq_0_0_in-;seq_0_0_in+]
	 * @param place
	 * @return true if the place has no reverse conflict and false otherwise.
	 */
	private boolean checkPlaceForNoMergeAndNoMarking(Place place) {
		if (place.getParents().size() > 1 || place.getChildren().size() == 0) // || place.getMarking() > 0)
			return false;
		else
			return true;
	}

}
