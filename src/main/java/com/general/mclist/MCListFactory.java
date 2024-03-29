package com.general.mclist;

import java.awt.Component;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 */
public class MCListFactory {

    public static <E> MCList<E> buildMCList(int numberofcolumns, boolean singleselect, Component c) {
        String version = System.getProperty("java.version");
        if (false || version.startsWith("1.1") || version.startsWith("1.0")) {
            throw new IllegalStateException("Nope. Not doing 1.1 or 1.0 Java anymore"); //  new AWTMCList(numberofcolumns, singleselect, c);
        } else {
            try {
                @SuppressWarnings("unchecked")
                Class<JMCList<E>> jmcListClass = (Class<JMCList<E>>) Class.forName("com.general.mclist.JMCList");
                Constructor<JMCList<E>> jmcListConstructor =
                        jmcListClass.getConstructor(Integer.TYPE, Boolean.TYPE);
                return jmcListConstructor.newInstance(numberofcolumns, singleselect);
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