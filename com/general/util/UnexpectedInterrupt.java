package com.general.util;





public class UnexpectedInterrupt extends UnexpectedError { 



    public static boolean silent;



    public UnexpectedInterrupt() { }



    public UnexpectedInterrupt(String msg) { super(msg); }



    public void printStackTrace() { if (!silent) super.printStackTrace(); }



    public String getMessage() { return (silent ? "" : super.getMessage()); }



    public String toString() { return (silent ? "" : super.toString() ); }



}



