
//Progress window version 1.2

package com.myster.util;

import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;
import java.util.*;
import com.general.util.Util;

public class ProgressWindow extends Frame {

    // Bar names that outsiders can use to choose a progress bar
	public static final int BAR_1 = 0;
	public static final int BAR_2 = 1;
	
	public boolean showBytes=true;
	
	// Parameters for progress bar, index i for ith bar
	Vector maxValue;           // maximum value of bar
	Vector minValue;           // minimum value of bar
	Vector currentValue;       // current value of bar
	Vector lastValue;          // value at last rate update
	Vector lastUpdateTime;     // system time at last rate update
	Vector progressWindowText; // text for bar
	Vector lastRate;           // last rate calculate


    Image ad;      // commercial to appear in window
    Image im;      // the window itself

    ThreadedProgressWindow t; // Subclass of Thread
    
    long currtime=0;
	
    // variables to make shades of gray
    private int fCycle;
    private int fCycleDirection;
    private static final int fCycleGrain = 16;
	
	// variable parameters for window
    System sys;           // to get system time
    int fontsize;         // for limiting the string
    int yWindowSize;      // calculated height of window
                          // depending on number of bars
    int numberOfBars;     // number of bars in window
    int numberOfBarsDone; // number of bars finished
	
    
    // fixed parameters for window
    final int XDEFAULT=468; // width of window
    final int YADD=60;      // y position of add
    final int XTEXT=5;      // x offset of text
    final int YTEXT=20;     // y offset of text
    final int XBAR=10;      // x offset of progress bar
    final int YBAR=25;      // y offset of progress bar
    final int BARLENGTH=440; // length of progress bar
    final int BARHEIGHT=10;  // height of progress bar
    final int YSTARTFACTOR=50; // factor for area of progress bar, each progress bar takes up 60
    final int MAXCHAR=38;      // maximum number of characters allowed in text
    final int XRATE=360;       // x point where display of rate begins
    final int YDEFAULT=180;

    // Constructors

    public ProgressWindow() // One progress bar with default values
    {
	 commonInit(100, new String("Progress..."), 0, 1);
    }

    public ProgressWindow(long max) // One progress bar with given maximum
    {
	  commonInit(max, new String("Progress..."), 0, 1);
    }
	
    public ProgressWindow(long min, long max) // One progress bar with given min and max 
    {
      	commonInit(max, new String("Progress..."), 0, 1);
    }
	
    public ProgressWindow(long min, long max, int bar) // More than 1 progress bar with min and max 
    {
       	commonInit(max, new String("Progress.."), min, bar);
    }
	

    // Common initializer for the constructors
	
	private void commonInit(long max, String s, long min, int bar) 
	{	
    	addMouseListener(new MouseClickHandler());
    	long systemTime = sys.currentTimeMillis();
    	String ss;
    	
	// setup for shades of gray
	// useless but cute
		fCycle = 0;
		fCycleDirection = 1;
		
	// set up size of window, check string length
		yWindowSize = YADD + YSTARTFACTOR*bar + getInsets().top;
		numberOfBars = bar;
       	if (s.length() > MAXCHAR) ss = s.substring(0,MAXCHAR-1);
       	else ss = s;
		
	// set up all the vectors
		maxValue = new Vector(bar);
		minValue = new Vector(bar);
		currentValue = new Vector(bar);
		lastValue = new Vector(bar);
		lastUpdateTime = new Vector(bar);
		progressWindowText = new Vector(bar);
		lastRate = new Vector(bar);
		
	// set number of bars done
	    numberOfBarsDone = 0;

	// set up all the bars with the same values for now
		for (int i=1; i<=bar; i++) 
		{
       		maxValue.addElement(new Long(max));
       		minValue.addElement(new Long(min));
       		currentValue.addElement(new Long(0));
       		lastValue.addElement(new Long(0));
       		lastUpdateTime.addElement(new Long(systemTime));
       		progressWindowText.addElement(ss);
       		lastRate.addElement(new Double(0.));
		}		     	
        	
	// set up window, thread, start thread
       	im=createImage(XDEFAULT, yWindowSize);
       	ad=Toolkit.getDefaultToolkit().getImage("cat.GIF");
		setSize(XDEFAULT, yWindowSize);
       	setVisible(true);
       	//update();
       	setResizable(false);
       //	repaint();
       	t=new ThreadedProgressWindow();
       	t.start();
       	setLocation(10,getInsets().top+10);
       	//addWindowListener(new StandardWindowBehavior());
    }

    // methods to update progress window text
    
    public void say(String s) // Update first bar with text
    {
    	say(s,BAR_1);
    }
    
    public void say(String s, int bar) // Update a particular bar with text
    {
    	String ss;
       	if (bar >= 0 && bar <= numberOfBars) // ignore if out of range
       	{
   	       	if (s.length() > 80) ss = s.substring(0,79);
   			else ss = s;
       		progressWindowText.setElementAt((Object) ss, bar);
       		repaint();
       	}
 
    }
    
    public void startBlock (int bar, long min, long max)
    {
    	if (bar >= 0 && bar <= numberOfBars) 
    	{
    		maxValue.setElementAt(new Long(max),bar);
    		minValue.setElementAt(new Long(min),bar);
    		lastUpdateTime.setElementAt(new Long(System.currentTimeMillis()), bar);
    		//setMin (min,bar);
    		//setMax (max,bar);
    	}
    }
	
	// update the ad
	
    public void makeImage(byte[] b) 
    {
       	ad=getToolkit().createImage(b);
       	MediaTracker tracker=new MediaTracker(this);
       	tracker.addImage(ad,0);
       	try {tracker.waitForID(0);} catch (Exception ex) {System.out.println("Crap");}
       	repaint();
    }

    // methods to update value in the progress bar
	
    public void update(long value) // update value in first bar
    {
    	update (value, BAR_1);
    }

    public void update(long value, int bar) // Update value of particular bar
    {
    	if (bar >= 0 && bar <= numberOfBars)  // Ignore if out of range
    	{
    		long maxval = ((Long) maxValue.elementAt(bar)).longValue();
   			long minval = ((Long) minValue.elementAt(bar)).longValue();
   			long currval = ((Long) currentValue.elementAt(bar)).longValue();
			if ((value <= maxval && value >= minval && value != currval) || value == -1)
			{ 
       			currentValue.setElementAt(new Long(value), bar);
			    //update();
	       	}
	    }
    }

    public void update() // redraw the image with all of the current values
    {
       	repaint();
       	//System.gc();
    }

    // update with image
	
    public void update(Graphics g) 
    {
    	yWindowSize = YADD + YSTARTFACTOR*numberOfBars + getInsets().top;
    	setSize(getSize().width,yWindowSize);
       	if (im==null){
       		setSize(getSize().width, yWindowSize);
       		im=createImage(XDEFAULT, yWindowSize);
       	}
       	paint(im.getGraphics());
       	g.drawImage(im, 0, 0, XDEFAULT, yWindowSize, this);
    }
    
	Image piChart;
	private void updateIcon() {
	    if (piChart==null) piChart=createImage(32,32);
	    double percent=0;
		if ((((Long) maxValue.elementAt(0)).longValue()-((Long) minValue.elementAt(0)).longValue())!=0) percent=(((Long) currentValue.elementAt(0)).longValue()*10000/(((Long) maxValue.elementAt(0)).longValue()-((Long) minValue.elementAt(0)).longValue()));
    	Graphics gp=piChart.getGraphics();
    	gp.setColor(Color.white);
    	gp.fillRect(0,0,32,32);
		gp.setColor(new Color(200,200,200));
    	gp.fillArc(1,1,30,30,90,360);
    	gp.setColor(Color.blue);
    	gp.fillArc(1,1,30,30,90,-(int)(percent*360)/10000-1);
    	//gp.setColor(Color.black);
    	//gp.drawString(""+((int)(percent/100-1))+"%", 1, 16);
    	setIconImage(piChart);
	}

    // draw or redraw the window
	
	int piCounter=0;
    public void paint(Graphics g) 
    {
    	piCounter++;
    	if (piCounter%10==0) {
			updateIcon();
    	}
    	
    	
    	int inityoff=getInsets().top;
    	String strate, s;
    	long min, max, currval, lastval, lasttime;
    	double rate;
	// draw the box, fill in colours, draw ad, etc	
       	//Graphics g=im.getGraphics();
   //  	fontsize = g.getFont().getSize();
       	g.setColor(Color.white);
       	g.fillRect(0,inityoff,XDEFAULT,yWindowSize);
       	g.setColor(Color.black);		
       	g.drawImage(ad, 0, inityoff, XDEFAULT, YADD, this);	// add on top half
       	
    // 	Draw all of the bars, calculate rates
    	for (int i = 0; i< numberOfBars; i++)
    	{
    		min = ((Long) minValue.elementAt(i)).longValue();
    		max = ((Long) maxValue.elementAt(i)).longValue();
    		currval = ((Long) currentValue.elementAt(i)).longValue();
    		lastval = ((Long) lastValue.elementAt(i)).longValue();
    		lasttime = ((Long) lastUpdateTime.elementAt(i)).longValue();
    		s = (String) progressWindowText.elementAt(i);
    		rate = ((Double) lastRate.elementAt(i)).doubleValue();
    		
    		if ((i==0)&&(currval<0)&&(queue!=0)) s="Position in queue: "+queue;
    		
    		// String and rectangle
    		g.setColor(Color.black);
       		g.drawString(s, XTEXT, YADD+YTEXT+YSTARTFACTOR*(i)+inityoff);
        	g.drawRect(XBAR, YADD+YBAR+YSTARTFACTOR*(i)+inityoff, BARLENGTH, BARHEIGHT);
		
	       	if(currval >= 0) // update the value in bar
			{
	      	    if (i==0) g.setColor(Color.blue);
	      	    else if (i==1) g.setColor(Color.magenta);
	      	    else g.setColor(Color.green);
	      	    
	       	    g.fillRect(XBAR+1, YADD+YBAR+YSTARTFACTOR*(i)+1+inityoff, 
	       	      (int)((currval*10000/(max-min))*(BARLENGTH))/10000-1, BARHEIGHT-1);

		    	// calculate and display rate
       		   rate = (double) (currval - min) / (System.currentTimeMillis() - lasttime) * 1000; // bytes per second
       		   lastValue.setElementAt(new Long(lastval), i);
       		   lastRate.setElementAt(new Double(rate), i);
	       
	       		if (rate != 0)
	       		{
	       			strate = Util.getStringFromBytes((long)rate)+"/sec";
	       			g.setColor(Color.black);
	       			if (showBytes) g.drawString (strate,XRATE,YADD+YTEXT+YSTARTFACTOR*(i)+inityoff);
	       		}

	        }
	       	else // change to a different shade of gray
	       	{
			    int greyValue = fCycle*256/fCycleGrain;
			    g.setColor(new Color(greyValue, greyValue, greyValue));
			    fCycle = (fCycle + fCycleDirection) % fCycleGrain;
			    if((fCycle == 0) || (fCycle == fCycleGrain - 1)) fCycleDirection *= -1;			
			    g.fillRect(XBAR + 1, YADD+YBAR+YSTARTFACTOR*(i) + 1+inityoff, 
			       (100 * BARLENGTH) / 100 - 1, BARHEIGHT - 1);
	       	}
       	
    	}	
      	
        	
	//graphics.drawImage(im, 0, 0, XDEFAULT, yWindowSize, this);
    }

    // methods when progress bar is finished
    
	public void done() // The one bar is done
	{	
		done(BAR_1);
	}
	
    public void done(int bar) // A particular bar is done
    {
    	queue=0;
    	String s = (String) progressWindowText.elementAt(bar);
    	if (bar >= 0 && bar < numberOfBars)
    	{
	       	progressWindowText.setElementAt(s + " (Completed)", bar);
	       	lastRate.setElementAt(new Double(0), bar);
	       	numberOfBarsDone++;
	    }
	    
	    // if all bars completed stop thread
       	t.end();
       	update();
       	updateIcon();
    }
    
    public void endError(String s) {
    	endError(s,BAR_1);
    }
    
    public void endError(String s, int bar) {
    	done();
    	if (bar >= 0 && bar < numberOfBars)
    	{
	       	progressWindowText.setElementAt(s + " (Error)", bar);
	       	lastRate.setElementAt(new Double(0.), bar);
	    }
    	
    }
    
    int queue=0;
    public void setQueue(int i) {
    	queue=i;
    }
    
    public void finalize() {
    	try {
    		t.end();
    	} catch (Exception ex) {
    	
    	}
    
    }

    // create special thread class for progress bar window
	
    private class ThreadedProgressWindow implements Runnable {
       	private final long fUpdateTime = 100;       	
       	public ThreadedProgressWindow() {}
		
		boolean endFlag=false;
		
		public void start() {
			com.general.util.Timer t=new com.general.util.Timer(this, 100);
		}
		
       	public void run() {
       		repaint();
       	    if (!endFlag) start();
		}
		
		public void end() {
			endFlag=true;
		}
    }
       
    private class MouseClickHandler extends MouseAdapter {
    
    	public void mouseClicked(MouseEvent e){
    		//try { Runtime.getRuntime().exec("explorer http://www.apple.com/"); } catch (Exception ex) {
    		//	ex.printStackTrace();
    		//}
    	}

    }

}


