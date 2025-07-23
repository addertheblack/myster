package com.myster.hash.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JLabel;
import javax.swing.JPanel;

import com.general.util.ProgressBar;
import com.myster.hash.HashManager;
import com.myster.hash.HashManagerEvent;
import com.myster.hash.HashManagerListener;
import com.myster.ui.MysterFrame;
import com.myster.ui.MysterFrameContext;
import com.myster.ui.WindowLocationKeeper;
import com.myster.ui.WindowLocationKeeper.WindowLocation;

public class HashManagerGUI extends MysterFrame {
    public static final String WINDOW_LOC_KEY = "Hash Manager Gui Window Locations";

    private static HashManagerGUI singleton;

    private WindowLocationKeeper windowKeeper;

    private static HashManager hashManager;

    public static int initGui(MysterFrameContext context) {
        WindowLocation[] lastLocs = context.keeper().getLastLocs(WINDOW_LOC_KEY);
        
        if (lastLocs.length > 0) {
            singleton.setBounds(lastLocs[0].bounds());
            singleton.setVisible(lastLocs[0].visible());
            singleton.pack();

            return 1;
        }
        
        return 0;
    }

    public static void init(MysterFrameContext context, HashManager hashManager) {
        HashManagerGUI.hashManager = hashManager;
        singleton = new HashManagerGUI(context);
    }

    public static com.myster.ui.menubar.MysterMenuItemFactory getMenuItem() {
        return new com.myster.ui.menubar.MysterMenuItemFactory(
                "Show Hash Manager", new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        singleton.setVisible(true);
                        singleton.pack();
                        singleton.toFrontAndUnminimize();
                    }
                }, java.awt.event.KeyEvent.VK_H);
    }

    private static final int XSIZE = 480;

    private static final int YSIZE = 150;

    ////End

    public HashManagerGUI(MysterFrameContext context) {
        super(context, "Hash Manager");

        setBackground(new Color(240, 240, 240));

        windowKeeper = context.keeper();
        windowKeeper.addFrame(this, WINDOW_LOC_KEY, WindowLocationKeeper.SINGLETON_WINDOW);

        final InternalPanel internalPanel = new InternalPanel();
        add(internalPanel);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                HashManagerGUI.this.setVisible(false);
            }
        });
        
        setResizable(false);
    }

    private static class InternalPanel extends JPanel {
        private static final int PADDING = 5;

        private static final int LABEL_X_SIZE = XSIZE / 2 - PADDING - PADDING;

        private static final int LABEL_Y_SIZE = 25;

        ////End

        private final ProgressBar progress;

        private final JLabel currentFileLabel;

        private final JLabel totalFilesLabel, totalFilesValue, totalSizeLabel, totalSizeValue,
                isEnabledLabel, isEnabledValue;

        long totalSize = 0;

        int totalFiles = 0;

        boolean enabled = false;

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(XSIZE, YSIZE);
        }
        
        public InternalPanel() {
            setLayout(null);

            enabled =  HashManagerGUI.hashManager.getHashingEnabled();

            totalFilesLabel = new JLabel("Number Of Files:");
            totalFilesLabel.setSize(LABEL_X_SIZE, LABEL_Y_SIZE);
            totalFilesLabel.setLocation(PADDING, PADDING);
            add(totalFilesLabel);

            totalFilesValue = new JLabel("0");
            totalFilesValue.setSize(LABEL_X_SIZE, LABEL_Y_SIZE);
            totalFilesValue.setLocation(PADDING + LABEL_X_SIZE, PADDING);
            add(totalFilesValue);

            totalSizeLabel = new JLabel("Amount of Data Processed:");
            totalSizeLabel.setSize(LABEL_X_SIZE, LABEL_Y_SIZE);
            totalSizeLabel.setLocation(PADDING, PADDING + PADDING
                    + LABEL_Y_SIZE);
            add(totalSizeLabel);

            totalSizeValue = new JLabel("0");
            totalSizeValue.setSize(LABEL_X_SIZE, LABEL_Y_SIZE);
            totalSizeValue.setLocation(PADDING + LABEL_X_SIZE, PADDING
                    + PADDING + LABEL_Y_SIZE);
            add(totalSizeValue);

            isEnabledLabel = new JLabel(enabled ? "Hashing is enabled"
                    : "Hashing is disabled");
            isEnabledLabel.setSize(LABEL_X_SIZE, LABEL_Y_SIZE);
            isEnabledLabel.setLocation(PADDING, PADDING + PADDING + PADDING
                    + LABEL_Y_SIZE + LABEL_Y_SIZE);
            add(isEnabledLabel);

            isEnabledValue = new JLabel(""); //used for a spacer
            isEnabledValue.setSize(LABEL_X_SIZE, LABEL_Y_SIZE);
            isEnabledValue.setLocation(PADDING + LABEL_X_SIZE, PADDING
                    + PADDING + PADDING + LABEL_Y_SIZE + LABEL_Y_SIZE);
            add(isEnabledValue);

            currentFileLabel = new JLabel("Waiting for work..");
            currentFileLabel.setLocation(PADDING, PADDING * 4 + LABEL_Y_SIZE
                    * 3);
            currentFileLabel.setSize(XSIZE, LABEL_Y_SIZE);
            add(currentFileLabel);

            progress = new ProgressBar();
            progress.setLocation(PADDING, PADDING * 5 + LABEL_Y_SIZE * 4);
            progress.setSize(XSIZE - PADDING - PADDING, progress
                    .getPreferredSize().height);
            progress.setValue(-1);
            progress.setBackground(Color.white);
            progress.setForeground(new Color(0, 0, 255));
            add(progress);
            
            HashManagerGUI.hashManager .addHashManagerListener(new HashManagerListener() {
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

                    currentFileLabel.setText("Hashing file: "
                            + e.getFile().getName());
                }

                public void fileHashProgress(HashManagerEvent e) {
                    progress.setValue(e.getProgress());
                }

                public void fileHashEnd(HashManagerEvent e) {
                    currentFileLabel.setText("Done Hashing.");
                    progress.setValue(-1);

                    totalSize += e.getFile().length();
                    totalFiles++;

                    totalFilesValue.setText("" + totalFiles);
                    totalSizeValue.setText(com.general.util.Util
                            .getStringFromBytes(totalSize));

                }
            });
        }
    }
}