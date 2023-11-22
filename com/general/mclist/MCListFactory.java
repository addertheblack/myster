package com.general.mclist;

import java.awt.Component;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 */
public class MCListFactory {

    public static MCList buildMCList(int numberofcolumns, boolean singleselect, Component c) {
        String version = System.getProperty("java.version");
        if (false || version.startsWith("1.1") || version.startsWith("1.0")) {
            throw new IllegalStateException("Nope. Not doing 1.1 or 1.0 Java anymore"); //  new AWTMCList(numberofcolumns, singleselect, c);
        } else {
            try {
                Class jmcListClass = Class.forName("com.general.mclist.JMCList");
                Constructor jmcListConstructor = jmcListClass.getConstructor(new Class[] {
                        Integer.TYPE, Boolean.TYPE });
                return (MCList) jmcListConstructor.newInstance(new Object[] {
                        new Integer(numberofcolumns), new Boolean(singleselect) });
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            return new AWTMCList(numberofcolumns, singleselect, c);
        }
    }
}