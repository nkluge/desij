package net.strongdesign.desij.decomposition;

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

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import net.strongdesign.desij.CLW;
import net.strongdesign.desij.DesiJ;
import net.strongdesign.desij.Messages;
import net.strongdesign.desij.decomposition.partitioning.Partition;
import net.strongdesign.stg.STG;
import net.strongdesign.stg.STGException;
import net.strongdesign.stg.STGUtil;
import net.strongdesign.stg.Signature;

public class BreezeDecomposition extends BasicDecomposition {
	
	public BreezeDecomposition(String filePrefix) {
		super(filePrefix);
	}
	
	public Collection<STG> decompose(STG stg, Partition partition) throws STGException, IOException {
		
		//  String fileNamePrefix = decoPara.filePrefix;
		this.specification = stg; // specification file --> for determination of initial dummies during OutDet Decomposition
		
		//valid Partition?
		if (CLW.instance.ALLOW_INCOMPLETE_PARTITION.isEnabled()) 
			if (! partition.correctSubPartitionOf(stg))
				throw new STGException("Incorrect subpartition");
		
		else if (! partition.correctPartitionOf(stg))
			throw new STGException("Incorrect complete partition (try -" + 
					CLW.instance.ALLOW_INCOMPLETE_PARTITION.getShortName() + " option for incomplete partition)");
		
		if (! partition.feasiblePartitionOf(stg) ) throw new STGException(Messages.getString("ParametrizedDecomposition.invalid_partition")); 
		
		// try to get rid of specification dummies(?)
		STGUtil.removeDummiesBreeze(stg, true, true);
		
		
		//Partitionen generieren
		List<STG> components = Partition.splitByPartition(stg, partition);
		
		//for the results
		List<STG> result= new LinkedList<STG>();

		//und aufrufen
		for (STG component : components) {
			StringBuilder signalNames = new StringBuilder();
			for (String s : component.getSignalNames(component.getSignals(Signature.OUTPUT)))
				signalNames.append(s.toString());

			logging(stg, signalNames.toString(), DecompositionEvent.NEW_COMPONENT, signalNames);

			STGInOutParameter componentParameter = new STGInOutParameter(component);
			STGUtil.removeDummiesBreeze(componentParameter.stg, true, false);
			
			result.add(componentParameter.stg);
			System.out.print(".");
		}
		System.out.println("\n");
		return result;
	}	
}
