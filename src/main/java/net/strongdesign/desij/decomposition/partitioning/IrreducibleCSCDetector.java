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

package net.strongdesign.desij.decomposition.partitioning;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.strongdesign.desij.CLW;
import net.strongdesign.desij.decomposition.BasicDecomposition;
import net.strongdesign.desij.decomposition.LazyDecompositionMultiSignal;
import net.strongdesign.desij.decomposition.LazyDecompositionSingleSignal;
import net.strongdesign.desij.decomposition.avoidconflicts.ComponentAnalyser;
import net.strongdesign.desij.decomposition.tree.CscAwareDecomposition;
import net.strongdesign.desij.decomposition.tree.IrrCscAwareDecomposition;
import net.strongdesign.desij.decomposition.tree.TreeDecomposition;
import net.strongdesign.stg.STG;
import net.strongdesign.stg.STGException;
import net.strongdesign.stg.Transition;
import net.strongdesign.util.Pair;

/**
 * @author Dominic Wist
 *
 */
public class IrreducibleCSCDetector extends ComponentAnalyser {
		
	/**
	 * @param stg
	 * @param filePrefix
	 * @param partition
	 * @throws IOException 
	 * @throws STGException 
	 */
	public IrreducibleCSCDetector(STG stg, String filePrefix, Partition partition) throws STGException, IOException {
		this.stg = stg;
		this.filePrefix = filePrefix;
						
		// decomposition 
		if (CLW.instance.VERSION.getValue().equals("lazy-multi"))  
			this.components = new LazyDecompositionMultiSignal(filePrefix).decompose(stg, partition);

		else if (CLW.instance.VERSION.getValue().equals("lazy-single"))  
			this.components = new LazyDecompositionSingleSignal(filePrefix).decompose(stg, partition);

		else if (CLW.instance.VERSION.getValue().equals("basic")) 
			this.components = new BasicDecomposition(filePrefix).decompose(stg, partition);

		else if (CLW.instance.VERSION.getValue().equals("tree")) 
			this.components = new TreeDecomposition(filePrefix).decompose(stg, partition);

		else if (CLW.instance.VERSION.getValue().equals("csc-aware"))
			this.components = new CscAwareDecomposition(filePrefix).decompose(stg, partition);
		
		else if (CLW.instance.VERSION.getValue().equals("irr-csc-aware"))
			components = new IrrCscAwareDecomposition(filePrefix).decompose(stg, partition);

		// irr. CSC conflict detection
		if (CLW.instance.CONFLICT_TYPE.getValue().endsWith("st"))
			identifyIrrCSCConflicts(true);
		else if (CLW.instance.CONFLICT_TYPE.getValue().equals("general"))
			identifyIrrCSCConflicts(false);
			
	}

	/* (non-Javadoc)
	 * @see net.strongdesign.desij.decomposition.avoidconflicts.ComponentAnalyser#avoidIrrCSCConflicts()
	 */
	@Override
	public boolean avoidIrrCSCConflicts() throws IOException, STGException {
		// nothing to do, because it is not to solve the conflicts
		return false;
	}
	
	public Map<Set<Pair<Transition,Transition>>,Collection<STG>> getIrrCSCConf2Components() {
		
		if (CLW.instance.CONFLICT_TYPE.getValue().endsWith("st"))
			return transformSelfTriggerRepresentation();
		else
			return this.entryExitPairs2Components;
		
	}
	
	public Collection<STG> getConflictingComponents() {
		return this.components;
	}

	private Map<Set<Pair<Transition, Transition>>, Collection<STG>> transformSelfTriggerRepresentation() {
		Map<Set<Pair<Transition, Transition>>, Collection<STG>> result = 
			new HashMap<Set<Pair<Transition,Transition>>, Collection<STG>>(selfTriggers.size());
		
		for (List<Transition> selfTrigger : this.selfTriggers.keySet()) {
			Set<Pair<Transition,Transition>> newKey = new java.util.HashSet<Pair<Transition,Transition>>(1);
			newKey.add(new Pair<Transition, Transition>(selfTrigger.get(0), selfTrigger.get(1)));
			result.put(newKey, selfTriggers.get(selfTrigger));
		}
		
		return result;
	}

}
