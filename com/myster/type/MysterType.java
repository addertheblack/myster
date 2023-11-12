/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2003
 */

package com.myster.type;

import java.io.UnsupportedEncodingException;

import com.general.util.UnexpectedError;

/**
 * This class represents a MysterType. In Myster a MysterType is a 32 bit (4
 * byte) value representing a class of related files. Myster groups files by
 * these types and optimises its network using these types.
 * <p>
 * You might sometimes see Myster types represented as a string of 4 letters.
 * This is because most character encodings use 8 bits per character so to
 * convert a string to a MysterType you just need to convert using 7 bit (1 bit
 * extended) ascii. (cleared high bit)
 * <p>
 * Is immutable!
 * 
 * @author Andrew Trumper
 * 
 */
public final class MysterType {
    public static final int TYPE_LENGTH = 4;

    final byte[] type;

    public MysterType(byte[] type) {
        if (type.length != TYPE_LENGTH)
            throw new MysterTypeException("Not a Myster Type");

        this.type = type.clone();
    }

    /**
     * Gets the ascii string (extended to 8 bits) equivalent of the passed
     * string and uses the first 4 bytes to create the Myster type.
     * <p>
     * If the string ends up being more than 4 bytes a MysterTypeException is
     * thrown.
     * 
     * @param type
     *            string to try and convert to a Myster Type.
     */
    public MysterType(String type) {
        this(convertToBytes(type));
    }

    private static byte[] convertToBytes(String type) {
        try {
            return type.getBytes("cp1252");
        } catch (UnsupportedEncodingException ex) {
            throw new UnexpectedError("cp1252 is not supported chracter encoding");
        }
    }

    public MysterType(int type) {
        this(new byte[] { (byte) ((type >> 24) & 0xFF), (byte) ((type >> 16) & 0xFF),
                (byte) ((type >> 8) & 0xFF), (byte) ((type >> 0) & 0xFF) });
    }

    public boolean equals(Object o) {
        if (!(o instanceof MysterType)) {
            return false;
        }
        MysterType mysterType = (MysterType) o;

        for (int i = 0; i < type.length; i++) {
            if (type[i] != mysterType.type[i])
                return false;
        }

        return true;
    }

    public int hashCode() {
        return getAsInt();
    }

    /**
     * Gets the MysterType as an int.
     * 
     * @return an integer representaiton of a MysterType.
     */
    public int getAsInt() {
        int temp = 0;
        for (int i = 0; i < type.length; i++) {
            temp <<= 8;
            temp |= (type[i] & 0xFF);
        }

        return temp;
    }

    /**
     * Tests to see if the MysterType representation of the passed stirng
     * matches this MysterType.
     * 
     * @param mysterTypeAsString
     * @return true if equal, false otherwise.
     */
    public boolean equals(String mysterTypeAsString) {
        return mysterTypeAsString.equals(toString());
    }

    /**
     * Gets a representation of this type as an array of 4 bytes.
     * 
     * @return an array of 4 bytes representing this type.
     */
    public byte[] getBytes() {
        return type.clone();
    }

    public String toString() {
        try {
            return new String(type, "ASCII");
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
            return new String(type);
        }
    }
}

