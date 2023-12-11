package com.myster.type;

import com.general.events.GenericEvent;

/**
 * The TypeDescriptionEvent is fired by the TypeDescriptionList.
 * 
 * @author Andrew Trumper
 *
 */

public class TypeDescriptionEvent extends GenericEvent {
    public static final int DISABLE = 0;

    public static final int ENABLE = 1;

    TypeDescriptionList list;

    MysterType type;

    public TypeDescriptionEvent(int id, TypeDescriptionList list,
            MysterType type) {
        super(id);

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