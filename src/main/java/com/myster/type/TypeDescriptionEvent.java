package com.myster.type;

/**
 * The TypeDescriptionEvent is fired by the TypeDescriptionList.
 * 
 * @author Andrew Trumper
 *
 */

public class TypeDescriptionEvent  {
    private final TypeDescriptionList list;

    private final MysterType type;

    public TypeDescriptionEvent(TypeDescriptionList list,
            MysterType type) {
        this.list = list;
        this.type = type;
    }
    
	/**
	 * Gets the TypeDescriptionList that fired this event.
	 * 
	 * @return The TypeDescriptionList that fired this event.
	 */
    public TypeDescriptionList getList() {
        return list;
    }

    
	/**
	 * Requests the type that is associated with this event.
	 * 
	 * @return The type that was enabled or disabled.
	 */
    public MysterType getType() {
        return type;
    }
}