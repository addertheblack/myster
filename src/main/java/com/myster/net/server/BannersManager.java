package com.myster.net.server;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextArea;
import javax.swing.text.DefaultCaret;

import com.general.util.AskDialog;
import com.general.util.GridBagBuilder;
import com.general.util.MessagePanel;
import com.myster.application.MysterGlobals;
import com.myster.pref.MysterPreferences;
import com.myster.pref.PreferencesMML;
import com.myster.pref.ui.PreferencesPanel;

/**
 * This code is responsible for managing everything to do with banners. It is
 * quite nasty.
 */
public class BannersManager {
    private static final String KEY_IN_PREFS = "/Banners Preferences/";
    private static final String PATH_TO_URLS = "/URLs/";
    private static final String PARTIAL_PATH_TO_IMAGES = "i";
    private static final String PARTIAL_PATH_TO_URLS = "u";
    private static final String IMAGE_DIRECTORY = "Images";
    private static boolean prefsHasChangedFlag = true; // to get manager to

    // initally read the
    // prefs

    private static Map<String, String> imageNamesToUrls = new HashMap<>();
    private static String[] imageNames;
    private static int currentIndex = 0;

    public static synchronized String getNextImageName() {
        if (prefsHasChangedFlag)
            updateEverything();

        if (imageNames == null || imageNames.length == 0)
            return null;

        if (currentIndex > imageNames.length - 1)
            currentIndex = 0;

        return imageNames[currentIndex++]; // it's post increment
    }

    public static synchronized File getImagesDirectory() {
        return new File(MysterGlobals.getAppDataPath(), IMAGE_DIRECTORY);
    }

    public static synchronized File getFileFromImageName(String imageName) {
        return new File(getImagesDirectory(), imageName);
    }

    public static synchronized String getURLFromImageName(String imageName) {
        String string = imageNamesToUrls.get(imageName);

        return (string == null ? "" : string);
    }

    private static synchronized void prefsHaveChanged() {
        prefsHasChangedFlag = true;
    }

    private static synchronized Map<String, String> getPrefsAsHash() {
        PreferencesMML mmlPrefs = new PreferencesMML(MysterPreferences.getInstance().getAsMML(
                KEY_IN_PREFS, new PreferencesMML()).copyMML());

        mmlPrefs.setTrace(true);

        List<String> folders = mmlPrefs.list(PATH_TO_URLS);
        Map<String, String> imageNameToUrl = new HashMap<>();

        if (folders != null) {
            for (int i = 0; i < folders.size(); i++) {
                String subFolder = folders.get(i);

                String imageName = mmlPrefs.get(PATH_TO_URLS + subFolder + "/"
                        + PARTIAL_PATH_TO_IMAGES);
                String url = mmlPrefs.get(PATH_TO_URLS + subFolder + "/" + PARTIAL_PATH_TO_URLS);

                if (imageName == null | url == null)
                    continue; // notice how I am using | here and not ||

                imageNameToUrl.put(imageName, url);
            }
        }

        return imageNameToUrl;
    }

    private static synchronized void setPrefsMML(PreferencesMML mml) { // is a
        MysterPreferences.getInstance().put(KEY_IN_PREFS, mml);
        prefsHaveChanged(); // signal that this object should be re-inited from
        // prefs.
    }

    private static synchronized void updateEverything() {
        prefsHasChangedFlag = false;

        imageNamesToUrls = getPrefsAsHash();

        imageNames = getImageNameList();

        currentIndex = 0;
    }

    public static synchronized String[] getImageNameList() {
        File imagesFolder = getImagesDirectory();

        if (imagesFolder.exists() && imagesFolder.isDirectory()) {
            String[] files = imagesFolder.list();

            int counter = 0;
            for (int i = 0; i < files.length; i++) {
                if (isAnImageName(files[i])) {
                    counter++;
                }
            }

            String[] filteredFileList = new String[counter];

            counter = 0;
            for (int i = 0; i < files.length; i++) {
                if (isAnImageName(files[i])) {
                    filteredFileList[counter++] = files[i];
                }
            }

            return filteredFileList;
        } else {
            return null; // error
        }
    }

    private static synchronized boolean isAnImageName(String imageName) { // code
        // reuse
        return (imageName.toLowerCase().endsWith(".jpg") || imageName.toLowerCase()
                .endsWith(".gif"));
    }

    public static class BannersPreferences extends PreferencesPanel {
        public static final Logger log = Logger.getLogger(BannersPreferences.class.getName());
        
        public static final int LIST_YSIZE = 150;
        public static final int BUTTON_YSIZE = 25;
        public static final int PADDING = 5;

        private final JList<String> list;
        private final JTextArea msg;
        private final JButton refreshButton;
        private final JButton openButton;

        private Map<String, String> hashtable = new HashMap<>();

        public BannersPreferences() {
            setSize(STD_XSIZE, STD_YSIZE);

            setLayout(new GridBagLayout());
            
            GridBagBuilder params = new GridBagBuilder().withSize(1, 1).withInsets(new Insets(5, 0, 0, 5));

            msg =MessagePanel.createNew("This panel allows you to associate a web page with a banner images to " +
                    "be sent to people who download files from you. Any images in \""
                    + getImagesDirectory().getAbsolutePath()
                    + "\" will appear below. Images of any dimension other than 468 X 60 " +
                            "pixels will be squished to fit. Double click"
                    + " on a image below to " + "associate it with a web address");
            msg.setCaret(new DefaultCaret() {
                @Override
                public void setVisible(boolean v) {
                    super.setVisible(false); // Always keep the caret invisible
                }
            });
            add(msg, params.withFill(GridBagConstraints.HORIZONTAL).withWeight(1,0).withSize(2, 1));

            refreshButton = new JButton(com.myster.util.I18n.tr("Refresh"));
            refreshButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    refreshImagesList();
                }
            });
            add(refreshButton, params.withGridLoc(0, 1));
            
            // now we need to add a button called "Open" that will allow us to open up to the getImagesDirectory().getAbsolutePath()
            // path using the Finder or File Explorer or whatever Linux uses
            openButton = new JButton(com.myster.util.I18n.tr("Open Images Folder"));
            openButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        File dir = getImagesDirectory();
                        dir.mkdirs();
                        
                        java.awt.Desktop.getDesktop().open(dir);
                    } catch (Exception ex) {
                        log.fine("Could not open the file explorer on this path: " + getImagesDirectory());
                        ex.printStackTrace();
                    }
                }
            });
            add(openButton, params.withGridLoc(1, 1).withAnchor(GridBagConstraints.WEST).withWeight(1,0));

            list = new JList<String>();
            list.setLocation(PADDING, STD_YSIZE - LIST_YSIZE - PADDING);
            list.setSize(STD_XSIZE - 2 * PADDING, LIST_YSIZE);
            list.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() != 2) {
                        return;
                    }

                    String currentURL = hashtable.get(list.getSelectedValue());

                    if (currentURL == null)
                        currentURL = "";

                    String answerString = AskDialog.simpleAsk(BannersPreferences.this.getFrame(),
                                                        com.myster.util.I18n
                                                                .tr("What URL would you like to link to this image?\n(Leave it blank to remove the url completely)"),
                                                        currentURL);

                    if (answerString == null)
                        return; // box has been canceled.

                    hashtable.put(list.getSelectedValue(), answerString);
                }
            });
            add(list, params.withGridLoc(0, 2).withFill(GridBagConstraints.BOTH).withWeight(1,1).withSize(2, 1));
        }

        public void save() {
            PreferencesMML mmlPrefs = new PreferencesMML();

            mmlPrefs.setTrace(true);

            for (int i = 0; i < list.getModel().getSize(); i++) {
                String url = hashtable.get(list.getModel().getElementAt(i));

                if (url == null || url.equals(""))
                    continue; // Don't bother saving this one, skip to the
                // next
                // one.

                mmlPrefs.put(PATH_TO_URLS + i + "/" + PARTIAL_PATH_TO_IMAGES, list.getModel().getElementAt(i));
                mmlPrefs.put(PATH_TO_URLS + i + "/" + PARTIAL_PATH_TO_URLS, url);
            }

            setPrefsMML(mmlPrefs);
        }

        public void reset() {
            hashtable = getPrefsAsHash();

            refreshImagesList();
        }

        private void refreshImagesList() {
            String[] directoryListing = getImageNameList();
            if ( directoryListing == null)
                return;
            DefaultListModel<String> model = new DefaultListModel<>();
            for (String string : directoryListing) {
                model.addElement(string);
            }
            list.setModel(model);
        }

        public String getKey() {
            return com.myster.util.I18n.tr("Banners");
        }
    }
}
