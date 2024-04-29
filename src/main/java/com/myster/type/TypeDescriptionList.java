package com.myster.type;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Logger;

import com.general.events.EventDispatcher;
import com.general.events.SyncEventDispatcher;
import com.myster.mml.RobustMML;
import com.myster.pref.PreferencesMML;
import com.myster.transaction.TransactionManager;

/**
 * The TypeDescriptionList contains some basic type information for most file
 * based types on the Myster network. The TypeDescriptionList is loaded from a
 * file called "TypeDescriptionList.mml"
 *  
 */
public abstract class TypeDescriptionList {
    private static TypeDescriptionList defaultList;

    public static synchronized void init() {
        if (defaultList == null)
            defaultList = new DefaultTypeDescriptionList();
    }
    
	/**
	 * Request Myster's default TypeDescriptionList
	 * @return Myster's default TypeDescriptionList
	 */
    public static synchronized TypeDescriptionList getDefault() {
        init(); //possible pre-mature init here
        return defaultList;
    }

    /**
     * Returns a TypeDescription for that MysterType or null if no
     * TypeDescription Exists
     */
    public abstract TypeDescription get(MysterType type);

    /**
     * Returns all TypeDescriptionObjects known
     */
    public abstract TypeDescription[] getAllTypes();

    /**
     * Returns all enabled TypeDescriptionObjects known
     */
    public abstract TypeDescription[] getEnabledTypes();

    /**
     * Returns all TypeDescriptionObjects enabled
     */
    public abstract boolean isTypeEnabled(MysterType type);

    /**
     * Returns if the Type is enabled in the prefs (as opposed to if the type
     * was enabled as of the beginning of the last execution which is what the
     * "isTypeEnabled" functions does.
     */
    public abstract boolean isTypeEnabledInPrefs(MysterType type);

    /**
     * Adds a listener to be notified if there is a change in the enabled/
     * unenabled-ness of of type.
     */
    public abstract void addTypeListener(TypeListener l);

    /**
     * Adds a listener to be notified if there is a change in the enabled/
     * unenabled-ness of of type.
     */
    public abstract void removeTypeListener(TypeListener l);

    /**
     * Enabled / disabled a type. Note this value comes into effect next time
     * Myster is launched.
     */
    public abstract void setEnabledType(MysterType type, boolean enable);
}

class DefaultTypeDescriptionList extends TypeDescriptionList {
    private static final Logger LOGGER = Logger.getLogger(TransactionManager.class.getName());
    
    //Ok, so here's the situation
    //I designed this so that I could change types while the program is running
    //and have all modules auto update without Myster restarting.. but it's too
    //freaking long to program
    //so rather than do all that coding I've modified it to return the value
    // that was
    //last saved (so that values from typeDescriptionList are CONSTANT PER
    // PROGRAM EXECUTION
    //. The system will still fire events but the list won't send the
    //values stored in the prefs only the values that were true as of
    //the beginning of the last execution.
    //See code for how to get back the dynamic behavior... (comments actually)
    private final TypeDescriptionElement[] types;

    private final TypeDescriptionElement[] workingTypes;

    private final EventDispatcher dispatcher;

    public DefaultTypeDescriptionList() {
        TypeDescriptionElement[] oldTypes;

        TypeDescription[] list = loadDefaultTypeAndDescriptionList();

        types = new TypeDescriptionElement[list.length];
        oldTypes = new TypeDescriptionElement[list.length];

        Hashtable hash = getEnabledFromPrefs();

        for (int i = 0; i < list.length; i++) {
            String string_bool = (String) (hash.get(list[i].getType()
                    .toString()));

            if (string_bool == null) {
                string_bool = (list[i].isEnabledByDefault() ? "TRUE" : "FALSE");
            }

            types[i] = new TypeDescriptionElement(list[i], (string_bool
                    .equals("TRUE") ? true : false));
            oldTypes[i] = new TypeDescriptionElement(list[i], (string_bool
                    .equals("TRUE") ? true : false));
        }

        workingTypes = oldTypes; //set working types to "types" variable to
                                 // enable on the fly changes

        dispatcher = new SyncEventDispatcher();
    }

    private static final String DEFAULT_LIST_KEY = "DefaultTypeDescriptionList saved defaults";

    private static final String TYPE_KEY = "/type";

    private static final String TYPE_ENABLED = "/enabled";

    private static Hashtable getEnabledFromPrefs() {
        com.myster.pref.MysterPreferences pref = com.myster.pref.MysterPreferences
                .getInstance();

        RobustMML mml = pref.getAsMML(DEFAULT_LIST_KEY,
                new RobustMML());

        mml.setTrace(true);

        Hashtable hash = new Hashtable();

        List<String> list = mml.list("/");

        for (int i = 0; i < list.size(); i++) {
            hash.put(mml.get("/" + list.get(i).toString() + TYPE_KEY),
                    mml.get("/" + list.get(i).toString() + TYPE_ENABLED));
        }

        return hash;
    }

    private void saveEverythingToDisk() {

        PreferencesMML mml = new PreferencesMML();

        for (int i = 0; i < types.length; i++) {
            String temp = "/" + i;

            mml.put(temp + TYPE_KEY, types[i].getType().toString());
            mml.put(temp + TYPE_ENABLED, (types[i].enabled ? "TRUE"
                    : "FALSE"));
        }

        com.myster.pref.MysterPreferences.getInstance().put(DEFAULT_LIST_KEY, mml);
    }

    //TypeDescription Methods
    public TypeDescription[] getAllTypes() {
        TypeDescription[] typeArray_temp = new TypeDescription[workingTypes.length];

        for (int i = 0; i < types.length; i++) {
            typeArray_temp[i] = workingTypes[i].getTypeDescription();
        }

        return typeArray_temp;
    }

    public TypeDescription get(MysterType type) {
        for (int i = 0; i < types.length; i++) {
            if (types[i].getTypeDescription().getType().equals(type)) {
                return types[i].getTypeDescription();
            }
        }

        return null;
    }

    public TypeDescription[] getEnabledTypes() {
        int counter = 0;
        for (int i = 0; i < workingTypes.length; i++) {
            if (workingTypes[i].enabled)
                counter++;
        }

        TypeDescription[] typeArray_temp = new TypeDescription[counter];
        counter = 0;
        for (int i = 0; i < types.length; i++) {
            if (workingTypes[i].enabled)
                typeArray_temp[counter++] = types[i].getTypeDescription();
        }

        return typeArray_temp;
    }

    public boolean isTypeEnabled(MysterType type) {
        int index = getIndexFromType(type);

        return (index != -1 ? workingTypes[index].enabled : false);
    }

    public boolean isTypeEnabledInPrefs(MysterType type) {
        int index = getIndexFromType(type);

        return (index != -1 ? types[index].enabled : false);
    }

    public void addTypeListener(TypeListener listener) {
        dispatcher.addListener(listener);
    }

    public void removeTypeListener(TypeListener listener) {
        dispatcher.removeListener(listener);
    }

    /**
     * Must be done on the event thread!
     */
    public void setEnabledType(MysterType type, boolean enable) {
        int index = getIndexFromType(type);

        //errs
        if (index == -1)
            return; //no such type
        if (types[index].enabled == enable)
            return;

        types[index].setEnabled(enable);

        saveEverythingToDisk();

        dispatcher.fireEvent(new TypeDescriptionEvent(
                (enable ? TypeDescriptionEvent.ENABLE
                        : TypeDescriptionEvent.DISABLE), this, type));
    }

    private synchronized int getIndexFromType(MysterType type) {
        for (int i = 0; i < types.length; i++) {
            if (types[i].getType().equals(type))
                return i;
        }

        return -1;
    }

    private static synchronized TypeDescription[] loadDefaultTypeAndDescriptionList() {
        try {
            InputStream in = Class.forName("com.myster.Myster")
                    .getResourceAsStream("typedescriptionlist.mml");
            if (in == null) {
                LOGGER.severe("There is no \"typedescriptionlist.mml\" file at com.myster level. Myster needs this file. Myster will exit now.");
                LOGGER.severe("Please get a Type Description list.");

                com.general.util.AnswerDialog
                        .simpleAlert("PROGRAMER'S ERROR: There is no \"typedescriptionlist.mml\" file at com.myster level. Myster needs this file. Myster will exit now.\n\nThe Type Description List should be inside the Myster program. If you can see this message it means the developer has forgotten to merge this file into the program. You should contact the developer and tell him of his error.");
                System.exit(0);
            }

            List<TypeDescription> list = new ArrayList<>();

            RobustMML mml = new RobustMML(new String(readResource(in)));
            
            in.close();
            
            /*
             * <List> <1> <Type>... </Type> <Description>... </Description>
             * <Extentions> <1>.exe </1> <2>.zip </2> </Extensions>
             * <Archived>false </Archived> </1>
             */

            final String LIST = "/List/";

            List<String> typeList = mml.list(LIST);
            for (int i = 0; i < typeList.size(); i++) {
                TypeDescription typeDescription = getTypeDescriptionAtPath(mml,
                        LIST + typeList.get(i) + "/");

                if (typeDescription != null)
                    list.add(typeDescription);
            }

            LOGGER.info("Type descriptions length " + list.size());

            return list.toArray(TypeDescription[]::new);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException("" + ex);
        }

    }

    private static TypeDescription getTypeDescriptionAtPath(RobustMML mml,
            String path) {
        final String TYPE = "Type", DESCRIPTION = "Description", EXTENSIONS = "Extensions/", ARCHIVED = "Archived", ENABLED_BY_DEFAULT = "Enabled By Default";

        String type = mml.get(path + TYPE);
        String description = mml.get(path + DESCRIPTION);
        List<String> extensionsDirList = mml.list(path + EXTENSIONS);
        String archived = mml.get(path + ARCHIVED);
        String enabled = mml.get(path + ENABLED_BY_DEFAULT);

        if ((type == null) & (description == null))
            return null;

        boolean isArchived = (archived == null ? false : (archived
                .equalsIgnoreCase("True")));

        boolean isEnable = (enabled == null ? false : (enabled
                .equalsIgnoreCase("True")));

        String[] extensions = new String[0];
        if (extensionsDirList != null) {
            List<String> recovered = new ArrayList<String>();
            String extensionsPath = path + EXTENSIONS;

            for (int i = 0; i < extensionsDirList.size(); i++) {
                String extension = mml.get(extensionsPath
                        + (extensionsDirList.get(i)));
                if (extension != null) {
                    recovered.add(extension);
                }
            }

            extensions = new String[recovered.size()];
            for (int i = 0; i < extensions.length; i++) {
                extensions[i] = recovered.get(i);
            }
        }

        try {
            return new TypeDescription(new MysterType(type
                    .getBytes(com.myster.application.MysterGlobals.DEFAULT_ENCODING)),
                    description, extensions, isArchived, isEnable);
        } catch (Exception ex) {
            throw new com.general.util.UnexpectedException(ex);
        }
    }

    private static byte[] readResource(InputStream in) throws IOException {
        final int BUFFER_SIZE = 2000;

        byte[] buffer = new byte[BUFFER_SIZE];

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        for (;;) {
            int bytesRead = in.read(buffer);

            if (bytesRead == -1)
                break;

            out.write(buffer, 0, bytesRead);
        }

        return out.toByteArray();
    }

    private static class TypeDescriptionElement {
        private TypeDescription typeDescription;

        private boolean enabled;

        public TypeDescriptionElement(TypeDescription typeDescription,
                boolean enabled) {
            this.typeDescription = typeDescription;
            this.enabled = enabled;
        }

        public boolean setEnabled(boolean enabled) {
            this.enabled = enabled;
            return enabled;
        }

        public TypeDescription getTypeDescription() {
            return typeDescription;
        }

        public MysterType getType() {
            return typeDescription.getType();
        }
    }
}

