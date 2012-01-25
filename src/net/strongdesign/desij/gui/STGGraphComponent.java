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

package net.strongdesign.desij.gui;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import net.strongdesign.stg.Node;
import net.strongdesign.stg.Place;
import net.strongdesign.stg.STG;
import net.strongdesign.stg.Transition;

import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.layout.mxCompactTreeLayout;
import com.mxgraph.layout.mxGraphLayout;
import com.mxgraph.layout.mxOrganicLayout;
import com.mxgraph.layout.mxParallelEdgeLayout;
import com.mxgraph.layout.mxPartitionLayout;
import com.mxgraph.layout.mxStackLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxGeometry;
import com.mxgraph.model.mxGraphModel;
import com.mxgraph.model.mxIGraphModel;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.handler.mxRubberband;
import com.mxgraph.util.mxConstants;
import com.mxgraph.util.mxRectangle;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxPerimeter;
import com.mxgraph.view.mxStylesheet;

public class STGGraphComponent extends mxGraphComponent {

	/** The current graph, representing the current STG. */
	private mxIGraphModel model;
	private final mxGraph graph;
	protected mxRubberband rubberband;
	HashMap<mxCell, Node> cell2Node;
	
	
	public boolean isVertexIgnored(Object vertex) {
		return !graph.getModel().isVertex(vertex)
				|| !graph.isCellVisible(vertex);
	}

	public mxRectangle getVertexBounds(Object vertex) {
		mxRectangle geo = graph.getModel().getGeometry(vertex);
		return new mxRectangle(geo);
	}

	public mxRectangle setVertexLocation(Object vertex, double x, double y) {
		mxIGraphModel model = graph.getModel();
		mxGeometry geometry = model.getGeometry(vertex);
		mxRectangle result = null;

		if (geometry != null) {
			result = new mxRectangle(x, y, geometry.getWidth(),
					geometry.getHeight());

			if (geometry.getX() != x || geometry.getY() != y) {
				geometry = (mxGeometry) geometry.clone();
				geometry.setX(x);
				geometry.setY(y);
				model.setGeometry(vertex, geometry);
			}
		}

		return result;

	}

	/** moves model nodes to the top left corner */
	public void shiftModel() {
		double max = 0;
		Double top = null;
		Double left = null;
		Object parent = graph.getDefaultParent();

		List<Object> vertices = new ArrayList<Object>();
		int childCount = model.getChildCount(parent);

		for (int i = 0; i < childCount; i++) {
			Object cell = model.getChildAt(parent, i);

			if (!isVertexIgnored(cell)) {
				vertices.add(cell);
				mxRectangle bounds = getVertexBounds(cell);

				if (top == null)
					top = bounds.getY();
				else
					top = Math.min(top, bounds.getY());

				if (left == null)
					left = bounds.getX();
				else
					left = Math.min(left, bounds.getX());

				max = Math.max(max,
						Math.max(bounds.getWidth(), bounds.getHeight()));
			}
		}

		for (Object obj : vertices) {
			if (!graph.isCellMovable(obj))
				continue;

			double x, y;
			mxRectangle bounds = getVertexBounds(obj);
			x = bounds.getX() - left + 50;
			y = bounds.getY() - top + 50;

			setVertexLocation(obj, x, y);
		}
	}
	
	
	public void storeCoordinates(STGEditorCoordinates coordinates) {
		
		coordinates.clear();
		for (Map.Entry<mxCell, Node> en: cell2Node.entrySet()) {
			mxGeometry g = en.getKey().getGeometry();
			coordinates.put(en.getValue(), new Point((int)g.getX(), (int)g.getY()));
		}
	}
	
	
/**
 * Initialises Graph component with given STG and coordinates  
 * @param stg
 * @param coordinates 
 */
	public STGEditorCoordinates initSTG(STG stg, STGEditorCoordinates coordinates) {

		((mxGraphModel)graph.getModel()).clear();
		
		Object parent = graph.getDefaultParent();
		graph.getModel().beginUpdate();
		
		try {
			
			
			
			Map<Node, mxCell> nc = new HashMap<Node, mxCell>();
			cell2Node.clear();
			
			if (stg==null) return coordinates;
			
			for (Node node : stg.getNodes()) {
				
				mxCell cell = null;

				if (node instanceof Place) {
					
					cell = new PlaceCell((Place)node);
//					cell = (mxCell) graph.insertVertex(parent, null,
//							((Place) node).getMarking(), 50, 50, 25, 25,
//							"shape=ellipse;perimeter=ellipsePerimeter");
					
				} else if (node instanceof Transition) {
					cell = new TransitionCell((Transition)node, stg);
					
//					int signalID = ((Transition) node).getLabel().getSignal();
//					String style = "fontColor=black";
//					if (stg.getSignature(signalID) == Signature.INPUT)
//						style = "fontColor=red";
//					if (stg.getSignature(signalID) == Signature.OUTPUT)
//						style = "fontColor=blue";
//					if (stg.getSignature(signalID) == Signature.INTERNAL)
//						style = "fontColor=green";
//					if (stg.getSignature(signalID) == Signature.ANY)
//						style = "fontColor=black;fillColor=yellow";
//
//					cell = (mxCell) graph.insertVertex(parent, null,
//							((Transition) node).getString(Transition.UNIQUE),
//							50, 50, 40, 20, style);
					
				}
				
				cell2Node.put(cell, node);
				nc.put(node, cell);
				graph.addCell(cell, parent);

			}

			for (Node node : stg.getNodes()) {
				mxCell source = nc.get(node);
				if (coordinates!=null) {
					Point p = coordinates.get(node);
					if (p!=null) {
						source.getGeometry().setX(p.x);
						source.getGeometry().setY(p.y);
					}
				}
				for (Node child : node.getChildren()) {
					mxCell target = nc.get(child);
					graph.insertEdge(parent, null, null, source, target);
				}
			}
			
			// do default layout, if coordinates are not given
			if (coordinates==null||coordinates.size()==0) {
				mxGraphLayout cl = new mxOrganicLayout(graph);
				cl.execute(parent);
				shiftModel();
				
				coordinates = new STGEditorCoordinates();
				// store new coordinates for the STG
				for (Object ob: graph.getChildCells(parent, true, false)) {
					if (ob instanceof mxCell) {
						if (ob instanceof TransitionCell || ob instanceof PlaceCell) {
							double dx = ((mxCell)ob).getGeometry().getX();
							double dy = ((mxCell)ob).getGeometry().getY();
							Node nd = cell2Node.get(ob);
							coordinates.put(nd, new Point((int)dx, (int)dy));
						}
					}
				}
				
			}
		} finally {
			graph.getModel().endUpdate();
		}
		
		return coordinates;
	}

	protected Map<String, Object> setupStyles(mxGraph graph) {
		mxStylesheet stylesheet = graph.getStylesheet();

		Map<String, Object> style = new Hashtable<String, Object>();

		style.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
		style.put(mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter);
		style.put(mxConstants.STYLE_VERTICAL_ALIGN, mxConstants.ALIGN_MIDDLE);
		style.put(mxConstants.STYLE_ALIGN, mxConstants.ALIGN_CENTER);
		style.put(mxConstants.STYLE_FILLCOLOR, "white");
		style.put(mxConstants.STYLE_STROKECOLOR, "black");
		style.put(mxConstants.STYLE_FONTCOLOR, "black");

		stylesheet.setDefaultVertexStyle(style);

		style = new Hashtable<String, Object>();

		style.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_CONNECTOR);
		style.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
		style.put(mxConstants.STYLE_VERTICAL_ALIGN, mxConstants.ALIGN_MIDDLE);
		style.put(mxConstants.STYLE_ALIGN, mxConstants.ALIGN_CENTER);
		style.put(mxConstants.STYLE_STROKECOLOR, "black");
		style.put(mxConstants.STYLE_FONTCOLOR, "#446299");

		stylesheet.setDefaultEdgeStyle(style);

		return style;
	}
	/**
	 * 
	 */
	private static final long serialVersionUID = 7755698755334362626L;

	public void setLayout(int type) {

		Object parent = graph.getDefaultParent();
		graph.getModel().beginUpdate();
		try {
			mxGraphLayout gl;
			if (type == 1)
				gl = new mxOrganicLayout(graph);
			else if (type == 2)
				gl = new mxCircleLayout(graph);
			else if (type == 3)
				gl = new mxCompactTreeLayout(graph, false, false);
			else if (type == 4)
				gl = new mxParallelEdgeLayout(graph, 30);
			else if (type == 5)
				gl = new mxPartitionLayout(graph, false, 30);
			else if (type == 6)
				gl = new mxStackLayout(graph, false, 30);
			else
				gl = new mxOrganicLayout(graph);

			gl.execute(parent);
		} finally {
			graph.getModel().endUpdate();
		}
	}
	
	
	public STGGraphComponent() {
		super(new mxGraph(new mxGraphModel()));

		graph = getGraph();
		model = getGraph().getModel();
			
		setupStyles(graph);

		graph.setAutoSizeCells(false);
		graph.setCellsEditable(false);
		graph.setCellsDisconnectable(false);
		graph.setCellsCloneable(false);
		graph.setCellsResizable(false);
		graph.setCellsDeletable(false);
		graph.setCellsBendable(false);
		graph.setCellsDeletable(false);
		graph.setAllowDanglingEdges(false);
		graph.setCellsSelectable(true);
		
		this.setSwimlaneSelectionEnabled(true);
		this.setConnectable(false);
		rubberband = new mxRubberband(this);
		cell2Node = new HashMap <mxCell, Node>();
		
	}
	
	
}