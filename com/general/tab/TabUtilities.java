/* 
	Main.java

	Title:			Server Stats Window Test App
	Author:			Andrew Trumper
	Description:	An app to test the server stats window
*/

package com.general.tab;

import com.general.util.*;
import java.awt.*;
import java.awt.image.*;

public class TabUtilities {
	public static Image makeImage(int stringwidth, Component c, String middleName) {
		int xsize=0;
		int ysize=0;
		
		boolean isCustom=false;
		if (stringwidth==-1) isCustom=true;
		
		Image left=loadLeft(c);
		Image right=loadRight(c);
		Image middle=loadMiddle(middleName, c);
		
		if (left==null||right==null||middle==null) {
			Image temp=c.createImage(100, 50);
			Graphics g=temp.getGraphics();
			g.setColor(Color.red);
			g.fillRect(0,0,100,500);
			g.setColor(Color.black);
			g.drawString("<Missing Image>",5,25);
			return temp;
		}
		
		ysize=middle.getHeight(c);
		
		if (isCustom) stringwidth=middle.getWidth(c);
		
		if (!isCustom){
			xsize+=left.getWidth(c);
			xsize+=right.getWidth(c);
		}
		
		xsize+=stringwidth;
		
		Image workingImage=c.createImage(xsize, ysize);
		
		Graphics g=workingImage.getGraphics();
		
		
		
		if (!isCustom) {
			g.drawImage(left, 0,0,c);
			int borders=left.getWidth(c)+right.getWidth(c);
			
			for (int i=0; i+middle.getWidth(c)<stringwidth; i+=middle.getWidth(c)) {
				g.drawImage(middle,left.getWidth(c)+i,0,c);
			}
			
			g.drawImage(middle, xsize-right.getWidth(c)-middle.getWidth(c), 0,c);
			g.drawImage(right,xsize-right.getWidth(c),0,c);
		} else {
			g.drawImage(middle,0,0,c);
		}
		
		
		// Make Transparent
			
			int[] array=getPixels(workingImage, 0,0,workingImage.getWidth(c), workingImage.getHeight(c));
		
			for (int i=0; i<array.length; i++) {
				array[i]=makeWhiteTransparent(array[i]);
			}
			
			MemoryImageSource source = new MemoryImageSource(workingImage.getWidth(c), workingImage.getHeight(c), array, 0, workingImage.getWidth(c));
			workingImage=c.createImage(source);
		//..
		
		return workingImage;
	}
	
	public static Image makeImage(int stringwidth, Component c) {
		Image temp=makeImage(stringwidth, c, "middle.gif");
		error(temp, "middle.gif");
		return temp;
	}
	
	public static Image loadLeft(Component c) {
		Image temp=Util.loadImage("left.gif", c);
		error(temp, "left.gif");
		return temp;
	}
	
	public static Image loadRight(Component c) {
		Image temp=Util.loadImage("right.gif", c);
		error(temp, "right.gif");
		return temp;
	}
	
	public static Image loadMiddle(String text, Component c) {
		Image temp=Util.loadImage(text, c);
		error(temp, text);
		return temp;
	}
	
	public static Image loadBackground(Component c) {
		Image temp=Util.loadImage("tab_background.jpg", c);
		error(temp, "tab_background.jpg");
		return temp;
	}
	
	public static int makeWhiteTransparent(int pixel) {
		int alpha = (pixel >> 24) & 0xff;
		int red   = (pixel >> 16) & 0xff;
		int green = (pixel >>  8) & 0xff;
		int blue  = (pixel      ) & 0xff;
		
		//blue=0xff;
		if (red==0xff&&blue==0xff&&green==0xff) alpha=0;
		
		return (alpha << 24) | (red << 16) | (green << 8 ) | blue;
 	}
 	
 	public static int[] getPixels(Image img, int x, int y, int w, int h) {    
		int[] pixels = new int[w * h];    // PixelGrabber does the work of getting actual RGB pixel values for    // us from the image.    
		PixelGrabber pg = new PixelGrabber(img, x, y, w, h, pixels, 0, w);    
		try {      
			pg.grabPixels();    
		} catch (InterruptedException e) {
			System.err.println("interrupted waiting for pixels!");
		}
		    
		if ((pg.getStatus() & ImageObserver.ABORT) != 0) {
		      System.err.println("image fetch aborted or errored");    
		}    
		return pixels;  
	}
	
	private static void error(Image i, String g) {
		if (i==null) com.general.util.AnswerDialog.simpleAlert("WARNING: The file com.general.util.tab."+g+" could not be found. Please replace this file or tell the developer he forgot to merge it into the program (.jar).");
	}

}