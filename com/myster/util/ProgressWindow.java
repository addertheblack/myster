package com.myster.util;

import java.awt.*;
import java.awt.event.*;
import java.util.Vector;
import com.general.util.ProgressBar;

public class ProgressWindow extends Frame {
	public static final int X_SIZE 			= 468;
	public static final int Y_SIZE 			= 50;
	
	public static final int AD_HEIGHT 		= 60;
	
	public static final int X_TEXT_OFFSET 	= 5;      // x offset of text
    public static final int Y_TEXT_OFFSET 	= 5;     // y offset of text
	
	Vector progressPanels;
	protected AdPanel adPanel;

	public ProgressWindow() { commonInit(); }
	
	public ProgressWindow(String title) {
		super(title);
		
		commonInit();
	}
	
	private void commonInit() {
		setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
		
		adPanel = new AdPanel();
		add(adPanel);
	
		progressPanels = new Vector(10,10);
		
		addProgressPanel();
		
		addComponentListener(new ComponentAdapter() {
			public synchronized void componentShown(ComponentEvent e) {
				((ProgressWindow)(e.getComponent())).resize();
			}
			
			public synchronized void componentHidden(ComponentEvent e) {
				
			}
		});
		
		setResizable(false);
	}
	
	protected void resize() {
		Insets insets = getInsets();

		setSize(X_SIZE + insets.right + insets.left, AD_HEIGHT + (Y_SIZE * progressPanels.size()) + insets.top + insets.bottom);
	}
	
	// methods to update progress window text 
    public synchronized void setText(String s) {
		setText(s,0);
    }
    
    public synchronized void setText(String s, int bar)  {
		getProgressPanel(bar).setText(s);
    }
    
    public synchronized void setAdditionalText(String newText) {
		setAdditionalText(newText, 0);
	}
	
	public synchronized void setAdditionalText(String newText, int bar) {
		getProgressPanel(bar).setAdditionalText(newText);
	}
    
    public synchronized void setProgressBarNumber(int numberOfBars) {
    	if (numberOfBars<1) return; //yeah ha ha, less than 1, funny guy
    	if (numberOfBars>50) return; //more than 50 is rediculous for this implementation
    	
    	if (numberOfBars > progressPanels.size()) {
    		for (int i = progressPanels.size(); i < numberOfBars; i++) {
    			addProgressPanel();
    		}
    	} else if (numberOfBars < progressPanels.size()) {
    		for (int i = progressPanels.size(); i > numberOfBars; i--) {
    			removeProgressPanel(i-1);
    		}
    	} else {
    		return ;  //skip out the resize below
    	}
    	
    	resize();
    }
    
    public synchronized int getProgressBarNumber() {
    	return progressPanels.size();
    }
    
    private void removeProgressPanel(int index) {
    	remove((ProgressPanel)(progressPanels.elementAt(index)));
    	
    	progressPanels.removeElementAt(index);
    }
    
    private void addProgressPanel() {
    	ProgressPanel panel = new ProgressPanel();
    	
    	add(panel);
    	progressPanels.addElement(panel);
    }
    
    public synchronized void startBlock (int bar, long min, long max) {
		setMin(min, bar);
		setMax(max, bar);
		setValue(min, bar);
    }
	
    public synchronized void makeImage(byte[] b) {
		Image ad=getToolkit().createImage(b);
		
       	MediaTracker tracker=new MediaTracker(adPanel);
       	tracker.addImage(ad,0);
       	try {tracker.waitForID(0);} catch (Exception ex) {System.out.println("Crap");}
       	
       	adPanel.addImage(ad);
    }
	
	// Variation on standard suite
    public synchronized void setValue(long value, int bar) {
		getProgressPanel(bar).setValue(value);
		
		updateIcon();
    }
		
	public synchronized void setMax(long max, int bar) {
		getProgressPanel(bar).setMax(max);
	}
	
	public synchronized void setMin(long min, int bar) {
		getProgressPanel(bar).setMin(min);
	}

	public synchronized long getMax(int bar) {
		return getProgressPanel(bar).getMax();
	}

	public synchronized long getMin(int bar) {
		return getProgressPanel(bar).getMin();
	}
	
	public synchronized long getValue(int bar) {
		return getProgressPanel(bar).getValue();
	}
	
	//Ironically enough this check is done again in the progressPanels and again for the array.
	//I guess you can never be too safe.
	private void checkBounds(int index) {
		if (index < 0 || index > progressPanels.size()) throw new IndexOutOfBoundsException(index+" is not a valid progress bar");
	}
	
	private ProgressPanel getProgressPanel(int index) {
		checkBounds(index);
		
		return (ProgressPanel)(progressPanels.elementAt(index));
	}
	
	//Standard progress suite
	public synchronized void setValue(long value) {
		setValue(value, 0);
    }
		
	public synchronized void setMax(long max) {
		setMax(max, 0);
	}
		
	public synchronized void setMin(long min) {
		setMin(min, 0);
	}
	
	public synchronized long getMax() {
		return getMax(0);
	}
	
	public synchronized long getMin() {
		return getMin(0);
	}
	
	public synchronized long getValue() {
		return getValue(0);
	}
	
	Image piChart;
	int lastPercent;
	private void updateIcon() {
		//if (true==true) return;
	    if (piChart==null) piChart=createImage(32,32);
	    if (piChart==null) return; //How does this happen??
	    double percent=0;
		
		percent = (getValue() < getMin() || getValue() > getMax() ? 0 : ((double)(getValue() - getMin()))/((double)(getMax() - getMin())));
		
		int int_temp = (int)(percent * 100);
		
		if (int_temp == lastPercent) return;
		
		lastPercent = int_temp;
		
		Graphics gp=piChart.getGraphics();
    	gp.setColor(Color.white);
    	gp.fillRect(0,0,32,32);
		gp.setColor(new Color(240,240,240));
    	gp.fillArc(1,1,30,30,90,360);
    	gp.setColor(getBarColor());
    	gp.fillArc(1,1,30,30,90,-(int)(percent*360));
    	//gp.setColor(Color.black);
    	//gp.drawString(""+((int)(percent/100-1))+"%", 1, 16);

    	setIconImage(piChart);
	}
    
    public synchronized void setBarColor(Color color) {
    	setBarColor(color, 0);
    }
    
    public synchronized void setBarColor(Color color, int bar) {
    	getProgressPanel(bar).setBarColor(color);
    }
    
    public synchronized Color getBarColor() {
    	return getBarColor(0);
    }
    
    public synchronized Color getBarColor(int bar) {
    	return getProgressPanel(bar).getBarColor();
    }
    
    public synchronized void addAdClickListener(MouseListener l) {
    	adPanel.addMouseListener(l);
    }
    
	private static class ProgressPanel extends DoubleBufferPanel {
		public static final int PROGRESS_X_OFFSET = 10;
		public static final int PROGRESS_Y_OFFSET = 25;
		
		public static final int ADDITIONAL_X_SIZE = 50;
		
		ProgressBar progressBar;
		
		Label textLabel;
		Label additionalLabel;
		
		public ProgressPanel() {
			setLayout(null);
		
			progressBar = new ProgressBar();
			progressBar.setLocation(PROGRESS_X_OFFSET, PROGRESS_Y_OFFSET);
			progressBar.setSize(440, 10);
			progressBar.setForeground(Color.blue);
			add(progressBar);
			
			textLabel = new Label();
			textLabel.setLocation(X_TEXT_OFFSET, Y_TEXT_OFFSET);
			textLabel.setSize(X_SIZE - X_TEXT_OFFSET - ADDITIONAL_X_SIZE, 20);
			add(textLabel);
			
			additionalLabel = new Label();
			additionalLabel.setLocation(X_SIZE - ADDITIONAL_X_SIZE, Y_TEXT_OFFSET);
			additionalLabel.setSize(ADDITIONAL_X_SIZE, 20);
			add(additionalLabel);
		}
		
		public synchronized Dimension getPreferredSize() {
			return new Dimension(X_SIZE, Y_SIZE);
		}
		
		//Standard progress suite, look ma, a forward... aka stupid OOP "is a" overhead
		public synchronized void setValue(long value) {
			progressBar.setValue(value);
	    }
			
		public synchronized void setMax(long max) {
			progressBar.setMax(max);
		}
			
		public synchronized void setMin(long min) {
			progressBar.setMin(min);
		}
		
		public synchronized long getMax() {
			return progressBar.getMax();
		}
		
		public synchronized long getMin() {
			return progressBar.getMin();
		}
		
		public synchronized long getValue() {
			return progressBar.getValue();
		}	
		
		public synchronized void setText(String newText) {
			textLabel.setText(newText);
		}
		
		public synchronized void setAdditionalText(String newText) {
			additionalLabel.setText(newText);
		}
		
		public synchronized void setBarColor(Color color) {
			progressBar.setForeground(color);
			progressBar.repaint();
		}
		
		public synchronized Color getBarColor() {
			return progressBar.getForeground();
		} 
	}
	
	protected static class AdPanel extends DoubleBufferPanel {
		Image ad;
		String labelText = "";
		
		public synchronized void setAd(Image im) {
			ad = im;
		}
		
		public void paint(Graphics g) {
			if (ad == null) return;
			
			g.drawImage(ad, 0, 0, X_SIZE, AD_HEIGHT, this);
			
			if (!labelText.equals("")) {
				final int xPadding = 3;
				
				FontMetrics metrics = getFontMetrics(getFont());
				
				int descent = metrics.getDescent();
				int ascent 	= metrics.getAscent();
				int leading = metrics.getLeading();
				int height 	= metrics.getHeight();
				
				g.setColor(new Color(255,255,200));
				g.fillRect(0,0, metrics.stringWidth(labelText) + xPadding + xPadding,height);
				
				g.setColor(Color.black);
				g.drawString(labelText, xPadding, ascent + leading/2);
			}
		}
		
		public synchronized Dimension getPreferredSize() {
			return new Dimension(X_SIZE, AD_HEIGHT);
		}
		
		public synchronized void addImage(Image newAd) {
			ad = newAd;
			labelText = "";
			repaint();
		}
		
		public synchronized void setLabelText(String someText) {
			labelText = someText;
			repaint();
		}
	}
	
	private static class DoubleBufferPanel extends Panel {
		public DoubleBufferPanel() {}
		
		Dimension currentBufferSize;
		Image im; //double buffer
		
		public void update(Graphics g) {
	       	if ((im == null) || (! currentBufferSize.equals(getSize()))) {
	       		currentBufferSize = getSize();
	       		
	       		im=createImage(currentBufferSize.width, currentBufferSize.height);
	       	}
	       	
	       	paint(im.getGraphics());
	       	
	       	g.drawImage(im, 0, 0, this);
	   	}
	}
}