package com.myster.hash.ui;

import java.awt.*;
import java.awt.event.*;

import com.general.util.ProgressBar;

import com.myster.hash.HashManager;
import com.myster.hash.HashManagerEvent;
import com.myster.hash.HashManagerListener;
import com.myster.ui.MysterFrame;
import com.myster.ui.WindowLocationKeeper;

public class HashManagerGUI extends MysterFrame {
	public static final String WINDOW_LOC_KEY = "Hash Manager Gui Window Locations";
	
	private static HashManagerGUI singleton;
	
	WindowLocationKeeper windowKeeper; 
	
	public static void initGui() {
		Rectangle[] rect=com.myster.ui.WindowLocationKeeper.getLastLocs(WINDOW_LOC_KEY);
		if (rect.length>0) {
			Dimension d=singleton.getSize();
			singleton.setBounds(rect[0]);
			singleton.setSize(d);
			singleton.setVisible(true);
		}
	}
	
	public static void init() {
		singleton = new HashManagerGUI();
		
	}
	
	public static com.myster.menubar.MysterMenuItemFactory getMenuItem() {
		return new com.myster.menubar.MysterMenuItemFactory("Show Hash Manager", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				singleton.setVisible(true);
			}
		},java.awt.event.KeyEvent.VK_H);
	}
	
	private static final int XSIZE = 480;
	private static final int YSIZE = 150;

	////End
	
	public HashManagerGUI() {
		super("Hash Manager");
		
		setSize(XSIZE, YSIZE);
		
		setBackground(new Color(240,240,240));
		
		windowKeeper = new WindowLocationKeeper(WINDOW_LOC_KEY);
		windowKeeper.addFrame(this);
		
		
		final InternalPanel internalPanel =new InternalPanel();
		add(internalPanel);
		
		
		addComponentListener(new ComponentAdapter() {
			public void componentShown(ComponentEvent e) {
				Insets insets = getInsets();
				
				setSize(XSIZE + insets.left + insets.right, YSIZE + insets.top + insets.bottom);
			}
		});
		
		setResizable(false);
	}
	
	private class InternalPanel extends Panel {
		private static final int PADDING	= 5;
			
		private static final int LABEL_X_SIZE	= XSIZE/2-PADDING-PADDING;
		private static final int LABEL_Y_SIZE	= 25;
		

		////End
		
		ProgressBar progress;
		Label currentFileLabel;
		Label totalFilesLabel, totalFilesValue, totalSizeLabel, totalSizeValue, isEnabledLabel, isEnabledValue;
		long totalSize 	= 0;
		int	 totalFiles	= 0;
		boolean enabled = false;
		
		public InternalPanel() {
			setLayout(null);
			setSize(XSIZE, YSIZE);
			
			enabled = HashManager.getHashingEnabled();
			
			totalFilesLabel = new Label("Number Of Files:");
			totalFilesLabel.setSize(LABEL_X_SIZE,LABEL_Y_SIZE);
			totalFilesLabel.setLocation(PADDING, PADDING);
			add(totalFilesLabel);
			
			totalFilesValue = new Label("0");
			totalFilesValue.setSize(LABEL_X_SIZE,LABEL_Y_SIZE);
			totalFilesValue.setLocation(PADDING + LABEL_X_SIZE, PADDING);
			add(totalFilesValue);
			
			totalSizeLabel 	= new Label("Amount of Data Processed:");
			totalSizeLabel.setSize(LABEL_X_SIZE,LABEL_Y_SIZE);
			totalSizeLabel.setLocation(PADDING, PADDING + PADDING + LABEL_Y_SIZE);
			add(totalSizeLabel);
			
			totalSizeValue	= new Label("0");
			totalSizeValue.setSize(LABEL_X_SIZE,LABEL_Y_SIZE);
			totalSizeValue.setLocation(PADDING + LABEL_X_SIZE, PADDING + PADDING + LABEL_Y_SIZE);
			add(totalSizeValue);
			
			isEnabledLabel	= new Label(enabled?"Hashing is enabled":"Hashing is disabled");
			isEnabledLabel.setSize(LABEL_X_SIZE,LABEL_Y_SIZE);
			isEnabledLabel.setLocation(PADDING, PADDING + PADDING + PADDING + LABEL_Y_SIZE + LABEL_Y_SIZE);
			add(isEnabledLabel);
			
			isEnabledValue	= new Label(""); //used for a spacer
			isEnabledValue.setSize(LABEL_X_SIZE,LABEL_Y_SIZE);
			isEnabledValue.setLocation(PADDING + LABEL_X_SIZE, PADDING + PADDING + PADDING + LABEL_Y_SIZE + LABEL_Y_SIZE);
			add(isEnabledValue);
			
			currentFileLabel = new Label("Waiting for work..");
			currentFileLabel.setLocation(PADDING, PADDING*4 + LABEL_Y_SIZE*3);
			currentFileLabel.setSize(XSIZE, LABEL_Y_SIZE);
			add(currentFileLabel);
			
			progress = new ProgressBar();
			progress.setLocation(PADDING, PADDING*5 + LABEL_Y_SIZE*4);
			progress.setSize(XSIZE-PADDING-PADDING,progress.getPreferredSize().height);
			progress.setValue(-1);
			progress.setBackground(Color.white);
			progress.setForeground(new Color(0,0,255));
			add(progress);
			
			addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					HashManagerGUI.this.setVisible(false);
				}
			});
			
			HashManager.addHashManagerListener(new HashManagerListener() {
				public void enabledStateChanged(HashManagerEvent e) {
					enabled = e.isEnabled();
					
					if (enabled) {
						isEnabledLabel.setText("Hashing is enabled");
					} else {
						isEnabledLabel.setText("Hashing is disabled");
					}
					
					
				}
				
				public void fileHashStart(HashManagerEvent e) {
					progress.setMax(e.getFile().length());
					progress.setMin(0);
					progress.setValue(0);
					
					currentFileLabel.setText("Hashing file: "+e.getFile().getName());
				}
				
				public void fileHashProgress(HashManagerEvent e) {
					progress.setValue(e.getProgress());
				}
				
				public void fileHasEnd(HashManagerEvent e) {
					currentFileLabel.setText("Done Hashing.");
					progress.setValue(-1);
					
					totalSize += e.getFile().length();
					totalFiles++;
					
					totalFilesValue.setText(""+totalFiles);
					totalSizeValue.setText(com.general.util.Util.getStringFromBytes(totalSize));
					
				}
			});
		}
	}
}