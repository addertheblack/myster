/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.general.util;

import java.awt.FontMetrics;
import java.util.StringTokenizer;
import java.util.Vector;

public class MrWrap {
    private final Vector lines;

    private int counter = 0;

    public MrWrap(String s, int x, FontMetrics m) {
        lines = wrap(s, x, m); //Yummy.
    }

    public static Vector wrap(String string, final int x, FontMetrics metrics) {
        Vector lines = new Vector(100, 100);

        StringTokenizer tokens = new StringTokenizer(string, " ", true);

        String working = "";

        String temp = "";

        //Andrew's awsome wrap system :-)
        if (tokens.hasMoreTokens()) {
            for (temp = tokens.nextToken(); true; temp = tokens.nextToken()) {
                working = doReturns(working, lines);

                //continue working on the string.
                if ((metrics.stringWidth(working + "" + temp)) >= x) {
                    lines.addElement(working);
                    working = "";
                }
                working = working + temp + "";
                if (!(tokens.hasMoreTokens())) {
                    break;
                }
            }

            working = doReturns(working, lines);
            lines.addElement(working);
        }

        lines.trimToSize();
        return lines;
    }

    public static String doReturns(String working, Vector lines) {
        if (working.indexOf("\n") == -1) {
            return working;
        }

        // put returns in for \ns
        int first = 0;
        int last = 0;
        String tempstring;
        do {
            last = working.indexOf("\n", first);
            if (last < 0) { // if no more \n then break; (or if last==-1!)
                break;
            }
            tempstring = working.substring(first, last);
            lines.addElement(tempstring);
            first = last + 1;
        } while (true);
        return working.substring(first, working.length());
    }

    public synchronized boolean hasMoreElements() { //is thread safe
        if (counter < lines.size())
            return true;
        return false;
    }

    public synchronized String nextElement() { //is thread safe.
        counter++;
        return (String) (lines.elementAt(counter - 1));
    }

    public int numberOfElements() {
        return lines.size();
    }
}