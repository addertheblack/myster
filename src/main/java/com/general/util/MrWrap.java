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
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;


public class MrWrap {
    private final List<String> lines;

    private int counter = 0;

    public MrWrap(String s, int x, FontMetrics m) {
        lines = wrap(s, x, m); //Yummy.
    }

    public static List<String> wrap(String string, final int x, FontMetrics metrics) {
        List<String> lines = new ArrayList<>();

        StringTokenizer tokens = new StringTokenizer(string, " ", true);

        String working = "";

        String temp = "";

        //Andrew's awsome wrap system :-)
        if (tokens.hasMoreTokens()) {
            for (temp = tokens.nextToken(); true; temp = tokens.nextToken()) {
                working = doReturns(working, lines);

                //continue working on the string.
                if ((metrics.stringWidth(working + "" + temp)) >= x) {
                    lines.add(working);
                    working = "";
                }
                working = working + temp + "";
                if (!(tokens.hasMoreTokens())) {
                    break;
                }
            }

            working = doReturns(working, lines);
            lines.add(working);
        }

        return lines;
    }

    public static String doReturns(String working, List<String> lines) {
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
            lines.add(tempstring);
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
        return lines.get(counter - 1);
    }

    public int numberOfElements() {
        return lines.size();
    }
}