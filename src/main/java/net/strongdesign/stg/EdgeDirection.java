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

package net.strongdesign.stg;


/**
 * This is a enum class for the direction of a signal
 * transition. There can be no instances of this class, 
 * but the static ones provided by the class itself.
 * 
 * Each instance is singular for its values, therefore a test for
 * equality can be made with ==, instead of equals()
 * 
 * A short description - more exactly an abbreviation - and long description is 
 * stored.
 * 
 * <p><b>History:</b><br>
 * 20.09.2004: Generated<br>
 * 
 * <p>
 * @version 20.09.2004
 * @since 20.09.2004
 * @author Mark Sch�er
 */
public enum EdgeDirection {
	
	/**A direction representing a raising signal edge.*/
	UP ("+", "Raising transition"),
	
	/**A direction representing a falling signal edge.*/
	DOWN  ("-", "Falling transition"),
	
	/**A direction representing a toggle signal edge, i.e. every this 
	 * transition occurs the signal value changes from low to high
	 * or vice versa.*/
	TOGGLE	("~", "Toggle transition"),
	
	/**A direction representing a don't care signal edge. It represents both directions and i
	 * s intended for query purposes, not to be part  of a concrete STG.*/
	DONT_CARE("*", "Don't care transition"),
	
	/**A direction representing a unknown or non interesting signal edge.
	 * Intended for dummy transitions.*/
	UNKNOWN	 ("", "Unknown transition");
	
	
	
	private String name;
	private String long_name;
	
	
	private EdgeDirection(String name, String long_name) {
		this.name = name;
		this.long_name = long_name;
		
	}
	
	

	/**
	 * Returns the long description of an instance
	 * @return Long direction description
	 */
	public String toLongString() {
		return long_name;		
	}
	/**
	 * Returns the abbreviation of the instance.
	 * @return Short description
	 */
	public String toString() {
		return name;		
	}
	
	
}

