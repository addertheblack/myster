/*
 * Main.java
 * 
 * Title: Server Stats Window Test App Author: Andrew Trumper Description: An
 * app to test the server stats window
 */

package com.general.tab;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.MemoryImageSource;

public class Tab implements TabInterface {
    private boolean isselected = false;

    private TabPanel tabPanel;

    private String text;

    private Image image, imageMask; //imageMask is null if customgraphics

    private Image selectedImageMask;

    private int advance;

    private String customGraphics;

    private boolean customFlag = false;

    protected Tab(String s, TabPanel p) { //uses generic middle graphic and
                                          // sets text to display.
        text = s;
        tabPanel = p;
        customFlag = false;
    }

    protected Tab(TabPanel p, String c) { //does not use generic middle graphic
                                          // and no preset text.
        this("", p);
        customGraphics = c;
        customFlag = true;
    }

    public void paint(Graphics g) {

        assertSetup();

        if (!isselected) {
            g.drawImage(image, 0, 0, tabPanel);
            if (imageMask != null) {
                g.drawImage(imageMask, 0, 0, tabPanel);
                //g.setColor(new Color(100,100,100));
                //g.drawLine(0, image.getHeight(tabPanel)-1, 0, 0);
                //g.drawLine(0,0,image.getWidth(tabPanel)-1,0);
                //g.drawLine(image.getWidth(tabPanel)-1, 0,
                // image.getWidth(tabPanel)-1, image.getHeight(tabPanel)-1);
            }
            g.setColor(Color.black);
            g.drawLine(0, image.getHeight(tabPanel) - 1, image
                    .getWidth(tabPanel), image.getHeight(tabPanel) - 1);
        } else {
            g.drawImage(image, 0, 0, tabPanel);
            g.drawImage(selectedImageMask, 0, 0, tabPanel);
            if (customFlag) {
                //g.drawLine(0, image.getHeight(tabPanel)-1, 0, 0);
                //g.drawLine(0,0,image.getWidth(tabPanel)-1,0);
                //g.drawLine(image.getWidth(tabPanel)-1, 0,
                // image.getWidth(tabPanel)-1, image.getHeight(tabPanel)-1);
            }
        }
        g.drawString(text, advance, 30);
    }

    public Dimension getSize() {
        assertSetup();
        return new Dimension(image.getWidth(tabPanel), 50);
    }

    public boolean isSelected() {
        return isselected;
    }

    public void setSelect(boolean b) {
        isselected = b;
        tabPanel.repaint();
    }

    //for use by ??
    private void makeSelectedImage() {
        int[] array = TabUtilities.getPixels(image, 0, 0, image
                .getWidth(tabPanel), image.getHeight(tabPanel));

        for (int i = 0; i < array.length; i++) {
            array[i] = 0x00000000;//handleSinglePixel(array[i]);
        }

        MemoryImageSource source = new MemoryImageSource(image
                .getWidth(tabPanel), image.getHeight(tabPanel), array, 0, image
                .getWidth(tabPanel));
        selectedImageMask = tabPanel.createImage(source);
    }

    private void makeUnselectedImage() {
        int[] array = TabUtilities.getPixels(image, 0, 0, image
                .getWidth(tabPanel), image.getHeight(tabPanel));

        for (int i = 0; i < array.length; i++) {
            array[i] = handleSinglePixel(array[i]);
        }

        MemoryImageSource source = new MemoryImageSource(image
                .getWidth(tabPanel), image.getHeight(tabPanel), array, 0, image
                .getWidth(tabPanel));
        imageMask = tabPanel.createImage(source);
    }

    //Does the select effect.
    private int handleSinglePixel(int pixel) {
        int alpha = (pixel >> 24) & 0xff;
        int red = (pixel >> 16) & 0xff;
        int green = (pixel >> 8) & 0xff;
        int blue = (pixel) & 0xff;

        if (red == 0xff && green == 0xff && blue == 0xff) {
            alpha = 0x00;
        } else {
            red = 0xA8;
            green = 0xA8;
            blue = 0xFF;
            alpha = 0x2F;
        }

        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    //for use by paint
    private void assertSetup() {
        if (isNotLoaded())
            doSetup();
    }

    //for use by assertSetup()
    private boolean isNotLoaded() {
        return (image == null);
    }

    //for use by assertSetup()
    private void doSetup() {
        int stringwidth = -1;
        if (!text.equals(""))
            stringwidth = tabPanel.getFontMetrics(tabPanel.getFont())
                    .stringWidth(text);

        if (customGraphics != null)
            image = TabUtilities.makeImage(stringwidth, tabPanel,
                    customGraphics); //hurray for compilers.
        else
            image = TabUtilities.makeImage(stringwidth, tabPanel); //hurray for
                                                                   // compilers.

        Image grr = TabUtilities.loadLeft(tabPanel); //bluh.
        if (grr == null)
            advance = 10;
        else
            advance = grr.getWidth(tabPanel);
        makeSelectedImage();
        if (customFlag)
            makeUnselectedImage();
    }
}