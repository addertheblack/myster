package com.myster.server;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Hashtable;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JTextArea;

import com.general.util.AskDialog;
import com.myster.application.MysterGlobals;
import com.myster.pref.Preferences;
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

    private static Hashtable imageNamesToUrls = new Hashtable();

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
        return new File(MysterGlobals.getCurrentDirectory(), IMAGE_DIRECTORY);
    }

    public static synchronized File getFileFromImageName(String imageName) {
        return new File(getImagesDirectory(), imageName);
    }

    public static synchronized String getURLFromImageName(String imageName) {
        String string = (String) imageNamesToUrls.get(imageName);

        return (string == null ? "" : string);
    }

    private static synchronized void prefsHaveChanged() {
        prefsHasChangedFlag = true;
    }

    private static synchronized Hashtable getPrefsAsHash() {
        PreferencesMML mmlPrefs = new PreferencesMML(Preferences.getInstance().getAsMML(
                KEY_IN_PREFS, new PreferencesMML()).copyMML());

        mmlPrefs.setTrace(true);

        List<String> folders = mmlPrefs.list(PATH_TO_URLS);
        Hashtable hashtable = new Hashtable();

        if (folders != null) {
            for (int i = 0; i < folders.size(); i++) {
                String subFolder = (String) folders.get(i);

                String imageName = mmlPrefs.get(PATH_TO_URLS + subFolder + "/"
                        + PARTIAL_PATH_TO_IMAGES);
                String url = mmlPrefs.get(PATH_TO_URLS + subFolder + "/" + PARTIAL_PATH_TO_URLS);

                if (imageName == null | url == null)
                    continue; // notice how I am using | here and not ||

                hashtable.put(imageName, url);
            }
        }

        return hashtable;
    }

    private static synchronized void setPrefsMML(PreferencesMML mml) { // is a
        // string
        // ->
        // String
        // hash
        // of
        // imageNames
        // to
        // URLs
        // (strongly
        // typed
        // language
        // be
        // damned!)
        Preferences.getInstance().put(KEY_IN_PREFS, mml);
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
        private JList list;

        private JTextArea msg;

        private JButton refreshButton;

        private Hashtable hashtable = new Hashtable();

        public static final int LIST_YSIZE = 150;

        public static final int BUTTON_YSIZE = 25;

        public static final int PADDING = 5;

        public BannersPreferences() {
            setSize(STD_XSIZE, STD_YSIZE);

            setLayout(null);

            msg = new JTextArea(); // mental
            // note, put
            // in I18n
            msg
                    .setText("This panel allows you to associate a web page with a banner images to " +
                            "be sent to people who download files from you. Any images in \""
                            + getImagesDirectory().getAbsolutePath()
                            + "\" will appear below. Images of any dimension other than 468 X 60 " +
                                    "pixels will be squished to fit. Double click"
                            + " on a image below to " + "associate it with a web address");
            msg.setWrapStyleWord(true);
            msg.setLineWrap(true);
            msg.setEditable(false);
            msg.setBackground(getBackground());
            msg.setLocation(PADDING, PADDING);
            msg.setSize(STD_XSIZE - 2 * PADDING, STD_YSIZE - 4 * PADDING - LIST_YSIZE
                    - BUTTON_YSIZE);
            add(msg);

            refreshButton = new JButton(com.myster.util.I18n.tr("Refresh"));
            refreshButton.setLocation(PADDING, STD_YSIZE - LIST_YSIZE - PADDING - PADDING
                    - BUTTON_YSIZE);
            refreshButton.setSize(150, BUTTON_YSIZE);
            refreshButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    refreshImagesList();
                }
            });
            add(refreshButton);

            list = new JList<String>();
            list.setLocation(PADDING, STD_YSIZE - LIST_YSIZE - PADDING);
            list.setSize(STD_XSIZE - 2 * PADDING, LIST_YSIZE);
            list.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() != 2) {
                        return;
                    }

                    String currentURL = (String) hashtable.get(list.getSelectedValue());

                    if (currentURL == null)
                        currentURL = "";

                    AskDialog askDialog = new AskDialog(BannersPreferences.this.getFrame(),
                                                        com.myster.util.I18n
                                                                .tr("What URL would you like to link to this image?\n(Leave it blank to remove the url completely)"),
                                                        currentURL);

                    String answerString = askDialog.ask();

                    if (answerString == null)
                        return; // box has been canceled.

                    hashtable.put(list.getSelectedValue(), answerString);
                }
            });
            add(list);
        }

        public void save() {
            PreferencesMML mmlPrefs = new PreferencesMML();

            mmlPrefs.setTrace(true);

            for (int i = 0; i < list.getModel().getSize(); i++) {
                String url = (String) hashtable.get(list.getModel().getElementAt(i));

                if (url == null || url.equals(""))
                    continue; // Don't bother saving this one, skip to the
                // next
                // one.

                mmlPrefs.put(PATH_TO_URLS + i + "/" + PARTIAL_PATH_TO_IMAGES, (String)list.getModel().getElementAt(i));
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
