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

import java.util.List;
import java.util.Collection;
import java.util.Iterator;

import net.strongdesign.desij.decomposition.partitioning.Partition;
import net.strongdesign.stg.STG;
import net.strongdesign.stg.STGException;
import net.strongdesign.stg.Transition;

/**
 * @author Dominic Wist
 * Strategy interface for the place insertion in order to 
 * support the avoidance of irreducible CSC conflicts
 */
interface IPlaceHolderInsertionStrategy {
	
	public void initializeTraversal(STG component, List<Transition> ctp);
	public boolean execute(Transition t_en, Transition t_ex) throws STGException;
	public Partition getNewPartition() throws STGException;
	public Collection<STG> getReplacedComponents();
	public int getInsertedPlaceholderTransitionCount();
	public Transition doPlaceHolderInsertion(STG critComp);
	public void doInsertionForSameConflict(Iterator<STG> componentIterator, Transition insertedPlaceHolder);
	public void initialzePartition();
	
}
