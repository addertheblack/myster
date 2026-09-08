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
     * Requests the type associated with this event.
     *
     * @return the type that was enabled, disabled, or updated
     */
    public MysterType getType() {
        return type;
    }
}
