package com.general.util;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import com.general.util.MrWrap;

public class AskDialog extends Dialog {
	Button[] buttons;
	String it;	//just like hypercard :-)
	Vector message=new Vector(10,10);
	
	TextField messagebox;
	
	MrWrap wrapper;
	
	int height;
	int ascent;
	int descent;
	
	FontMetrics metrics;
	
	Insets insets;
	
	private final static int XPAD=5;
	private final static int YPAD=5;
	
	private final static int XSIZE=400;
	
	private final static int BUTTONX=30;
	private final static int BUTTONY=100;
	
	private final static int MSIZEX=XSIZE-XPAD-XPAD;
	private final static int MSIZEY=35;
	
	Frame parent;
	
	String question, sample;

	public AskDialog(Frame f, String q, String s) {
		super(f,"Ask!",true);
		question=q;
		sample=s;
		parent=f;
		
		initComponents();

		

		setResizable(false); 
	}
	
	public AskDialog(Frame f, String q) {
		this(f, q, "");
	}
	
	private void initComponents() {
		setLayout(null);
		pack();
		insets=getInsets();
		doMessageSetup();
		setSize(getPreferredSize());
		setLayout(null);
		
		messagebox=new TextField(sample);
		messagebox.setSize(MSIZEX, MSIZEY);
		messagebox.setLocation(XPAD+insets.left, message.size()*height+YPAD+insets.top+10);
		add(messagebox);
		
		buttons=new Button[]{new Button("Cancle"), new Button("Ok")};
		
		for (int i=0; i<buttons.length; i++) {
			buttons[i].addActionListener(new ActionHandler());
			buttons[i].setSize(100, BUTTONX);
			buttons[i].setLocation(getSize().width-(120*i+20)-100, getSize().height-BUTTONX-20-insets.bottom);
			add(buttons[i]);
		}
		
		Dimension d=parent.getSize();
		Point l=parent.getLocation();
		Dimension mysize=getSize();
		setLocation( l.x+(d.width-mysize.width)/2, l.y);
		
		setSize(getPreferredSize());
		
	}
	
	private void doMessageSetup() {
		metrics=getFontMetrics(getFont());
		
		height=metrics.getHeight();
		ascent=metrics.getAscent();
		descent=metrics.getDescent();

		
		MrWrap wrapper=new MrWrap(question, XSIZE-2*XPAD, metrics);
		for (int i=0; i<wrapper.numberOfElements(); i++) {
			message.addElement(wrapper.nextElement());
		}
		
	}
	
	public Dimension getPreferredSize() {
		insets=getInsets();
		
		return new Dimension(400+insets.right+insets.left, message.size()*height+75+MSIZEY+2*YPAD+insets.top+insets.bottom);
	}
	
	public void paint(Graphics g) {
		g.setColor(Color.black);
		for (int i=0; i<message.size(); i++) {
			g.drawString(message.elementAt(i).toString(), XPAD+insets.left, YPAD+height*(i+1)+insets.top);
		}

	}
	
	String msg;
	public String ask() {
		show();
		return msg;
	}
	
	public String getIt() {
		return msg;
	}
	
	
	private class ActionHandler implements ActionListener {
	
		public ActionHandler() {}
		
		public void actionPerformed(ActionEvent e) {
			Button b=((Button)(e.getSource()));
			
			it=b.getLabel();
			if (it.equals("Ok")) msg=messagebox.getText();
			dispose();
		}
	
	}

}