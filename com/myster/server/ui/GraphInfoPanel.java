/*
 * Main.java
 * 
 * Title: Server Stats Window Test App Author: Andrew Trumper Description: An
 * app to test the server stats window
 * 
 * 
 * THIS CLASS IS NOT YET IMPLEMENTED!
 */

package com.myster.server.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Panel;

//import graph.*;

public class GraphInfoPanel extends Panel {
    //Graph graph;

    public GraphInfoPanel() {
        setBackground(new Color(255, 100, 255));

    }

    public void init() {
        setLayout(null);
        //graph=new Graph(new Graph.GraphScaleStruct());
        //graph.setBackgroundPicture("MysterLogo.jpg");
        //graph.setGraphOverlay(graph.getGenericOverlay(Color.red));
        //graph.setSize(400,300);
        //graph.setLocation(150, 15);
        //add(graph);
    }

    private Image doubleBuffer; //adds double buffering

    public void update(Graphics g) {
        if (doubleBuffer == null) {
            doubleBuffer = createImage(600, 400);
        }
        Graphics graphics = doubleBuffer.getGraphics();
        paint(graphics);
        g.drawImage(doubleBuffer, 0, 0, this);
    }

    public void paint(Graphics g) {

    }

}