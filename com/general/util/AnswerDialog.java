package com.general.util;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import com.general.util.MrWrap;

public class AnswerDialog extends Dialog {
	Button[] buttons;
	String it;	//just like hypercard :-)
	Vector message=new Vector(10,40);
	
	MrWrap wrapper;
	
	int height;
	int ascent;
	int descent;
	
	FontMetrics metrics;
	
	Insets insets;
	
	private final static int BUTTONX=30;
	private final static int BUTTONY=100;
	
	Frame parent;
	
	String thestring;

	public AnswerDialog(Frame f, String q, String[]b) {
		super(f,"Alert!",true);
		thestring=q;
		parent=f;
		pack();
		insets=getInsets();
		
		if (b.length==0) {
			b=new String[1];
			b[0]=new String("Ok");
		}
		
		initComponents(b);
		
		setResizable(false); 
	}
	
	public AnswerDialog(String q, String[] b) {
		this(getCenteredFrame(), q, b);
	}
	
	public static String simpleAlert(String s, String[] b) {
		return (new AnswerDialog(getCenteredFrame(), s, b)).answer();
	}
	
	public static String simpleAlert(String s) {
		return (new AnswerDialog(getCenteredFrame(), s)).answer();
	}
	
	public static String simpleAlert(Frame frame, String s) {
		return (new AnswerDialog(frame, s)).answer();
	}
	
	public static String simpleAlert(Frame frame, String s, String[] b) {
		return (new AnswerDialog(frame, s, b)).answer();
	}
	
	public static Frame getCenteredFrame() {
			Frame tempframe=new Frame();
			tempframe.setSize(0,0);
			Toolkit tool=Toolkit.getDefaultToolkit();
			tempframe.setLocation(tool.getScreenSize().width/2-200, tool.getScreenSize().height/2-150);
			tempframe.setTitle("Dialog Box!");
			//tempframe.show();
			return tempframe;
	}
	
	public AnswerDialog(Frame f, String q) {
		this(f, q, new String[0]);
	}
	
	private void initComponents(String[] b) {
		int length;
		
		setLayout(null);
		
		if (b.length<3) length=b.length;
		else length=3; //ha haaa!
		
		doMessageSetup(thestring);
		setSize(400+insets.right+insets.left, message.size()*height+ascent+5+BUTTONX+20+insets.top+insets.bottom);
		
		buttons=new Button[length];
		
		for (int i=0; i<length; i++) {
			buttons[i]=new Button(b[i]);
			buttons[i].addActionListener(new ActionHandler());
			buttons[i].setSize(100, BUTTONX);
			buttons[i].setLocation(getSize().width-(120*i+20)-100, getSize().height-BUTTONX-10-insets.bottom);//-1-insets.bottom);
			add(buttons[i]);
		}
	
		Dimension d=parent.getSize();
		Point l=parent.getLocation();
		
		Dimension mysize=getSize();
		
		setLocation( l.x+(d.width-mysize.width)/2, l.y);
	}
	
	private void doMessageSetup(String q) {
		metrics=getFontMetrics(getFont());
		
		height=metrics.getHeight();
		ascent=metrics.getAscent();
		descent=metrics.getDescent();

		
		MrWrap wrapper=new MrWrap(q, 380, metrics);
		for (int i=0; i<wrapper.numberOfElements(); i++) {
			message.addElement(wrapper.nextElement());
		}
		
		resetLocation();
	}
	
	private void resetLocation() {
		/*return;
		insets=getInsets();
		
		for (int i=0; i<buttons.length; i++) {
			buttons[i].setLocation(getSize().width-(120*i+20)-100, getSize().height-BUTTONX-20-insets.bottom);
		}
		
		setSize(400, message.size()*height+75+insets.top+insets.bottom);*/
	}
	
	public void paint(Graphics g) {
		g.setColor(Color.black);
		for (int i=0; i<message.size(); i++) {
			g.drawString(message.elementAt(i).toString(), 10, 5+height*(i)+ascent+insets.top);
		}
	}
	
	public String answer() {
		show();
		return it;
	}
	
	public String getIt() {
		return it;
	}
	
	private class ActionHandler implements ActionListener {
	
		public ActionHandler() {}
		
		public void actionPerformed(ActionEvent e) {
			Button b=((Button)(e.getSource()));
			
			it=b.getLabel();
			dispose();
		}
	
	}

}