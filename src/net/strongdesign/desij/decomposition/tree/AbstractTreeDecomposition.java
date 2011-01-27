package net.strongdesign.desij.decomposition.tree;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import net.strongdesign.desij.CLW;
import net.strongdesign.desij.decomposition.AbstractDecomposition;
import net.strongdesign.desij.decomposition.BasicDecomposition;
import net.strongdesign.desij.decomposition.DecompositionEvent;
import net.strongdesign.desij.decomposition.STGInOutParameter;
import net.strongdesign.stg.Partition;
import net.strongdesign.stg.STG;
import net.strongdesign.stg.STGException;
import net.strongdesign.stg.Signature;
import net.strongdesign.stg.Transition;
import net.strongdesign.util.BitsetTree;
import net.strongdesign.util.Pair;
import net.strongdesign.util.PresetTree;

public abstract class AbstractTreeDecomposition extends AbstractDecomposition {
    
	
	public AbstractTreeDecomposition(String filePrefix) {
		super(filePrefix);
	}



	protected void updateSignature(Collection<Integer> outputs, STG component) {
		//update input/output signals according to outputs signals in the leafs
		for (Integer signal : component.getSignals(Signature.OUTPUT)) {						
			if (! outputs.contains(signal))
				component.setSignature(signal, Signature.INPUT);
		}
		for (Integer signal : component.getSignals(Signature.INPUT)) {						
			if (outputs.contains(signal))
				component.setSignature(signal, Signature.OUTPUT);
		}
		
	}

	public Collection<STG> decompose(STG stg, Partition partition) throws STGException, IOException {
		
		logging(stg, DecompositionEvent.TREE_START, null);		
	
		this.specification = stg;
		
		//The final components
		Collection<STG> components = new LinkedList<STG>();
			
		
		Collection<Pair<Collection<Integer>, Collection<Integer>>> leafs = 
			new LinkedList<Pair<Collection<Integer>, Collection<Integer>>>();
		
		//Determine the signals which should be contracted in every component
		//and add the outputs of the components to the leafs		
		for (Collection<String> actSignals : partition.getPartition()) {
			leafs.add( new Pair<Collection<Integer>, Collection<Integer>>( 
						stg.getSignalNumbers(Partition.getReversePartition(actSignals, stg)),
						stg.getSignalNumbers(actSignals)));
		}
		
		//the initial decomposition tree
		PresetTree<Integer, Collection<Integer>> tree = null;

		
		int method = 0;
		
		if (CLW.instance.DECOMPOSITION_TREE.getValue().equals("combined")) method = 1;
		else if (CLW.instance.DECOMPOSITION_TREE.getValue().equals("random")) method = 2;
		
//		tree = optimiseDecompositionTree(PresetTree.buildTreeLeafInfo(leafs, method), null);	
		tree = BitsetTree.buildTreeLeafInfo(leafs, method);
		
		if (tree != null) {
			logging(stg, DecompositionEvent.TREE_CREATED, tree.getSize());
			logging(stg, DecompositionEvent.TREE_CREATED_VALUE, tree);
		
			decomposeTree(stg, tree, components);
			
			logging(stg, DecompositionEvent.FINISHED, null);
						
			return components;
		}
		else { // tree == null
			return new BasicDecomposition(filePrefix).decompose(stg, partition);
		}
		
	}
	
	

	
    protected abstract void decomposeTree(STG stg, PresetTree<Integer, Collection<Integer>> tree, Collection<STG> components) throws STGException, IOException ;
	
    
   
	@Override
	/**
	 * @deprecated
	 */
	public final List<Transition> reduce(STGInOutParameter stg) {
		return null;
	}

	
	/**
     * Optimises a preset tree, i.e. removes empty inner nodes by adding its children to its parent.
     * Doing this, the order of the tree may be changed.
     * @param tree
     * @param parent must be null for direct calls
     * @return tree               
     */
    protected  PresetTree<Integer, Collection<Integer>> optimiseDecompositionTree(
    		PresetTree<Integer, Collection<Integer>> tree, PresetTree<Integer, Collection<Integer>> parent ) {
    	
    	//only for the root node which must not be optimised
    	if (parent == null) {    
    		for (PresetTree<Integer, Collection<Integer>> child : new LinkedList<PresetTree<Integer, Collection<Integer>>>(tree.getSubtrees()))
    			optimiseDecompositionTree(child, tree);
        	return tree;
    	}
    	
    	//empty leafs are allowed
    	if (tree.getSubtrees().isEmpty())
    		return null;
    	
    	//now the real work
    	if (tree.getValue().isEmpty()) {
    		parent.getSubtrees().remove(tree);
    		parent.getSubtrees().addAll(tree.getSubtrees());
    	}

   		for (PresetTree<Integer, Collection<Integer>> child : new LinkedList<PresetTree<Integer, Collection<Integer>>>(tree.getSubtrees()))
   			optimiseDecompositionTree(child, tree);
    	
    	//the resulting tree is only returned for convenience from the first call with null as second parameter
    	//subsequent calls will not work with the return value
    	return null;
    }
    
    
	
}
