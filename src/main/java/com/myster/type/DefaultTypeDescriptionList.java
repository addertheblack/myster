
package com.myster.type;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import com.general.events.NewGenericDispatcher;
import com.general.thread.Invoker;
import com.myster.identity.Util;
import com.myster.mml.RobustMML;
import com.myster.pref.PreferencesMML;
import com.myster.transaction.TransactionManager;

public class DefaultTypeDescriptionList implements TypeDescriptionList {
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

    private final NewGenericDispatcher<TypeListener> dispatcher;

    public DefaultTypeDescriptionList() {
        TypeDescriptionElement[] oldTypes;

        TypeDescription[] list = loadDefaultTypeAndDescriptionList();

        types = new TypeDescriptionElement[list.length];
        oldTypes = new TypeDescriptionElement[list.length];

        Map<String, String> hash = getEnabledFromPrefs();

        for (int i = 0; i < list.length; i++) {
            String string_bool = (hash.get(list[i].getType()
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

        dispatcher = new NewGenericDispatcher<TypeListener>(TypeListener.class, Invoker.EDT_NOW_OR_LATER);
    }

    private static final String DEFAULT_LIST_KEY = "DefaultTypeDescriptionList saved defaults";

    private static final String TYPE_KEY = "/type";

    private static final String TYPE_ENABLED = "/enabled";

    private static Map<String, String> getEnabledFromPrefs() {
        com.myster.pref.MysterPreferences pref = com.myster.pref.MysterPreferences
                .getInstance();

        RobustMML mml = pref.getAsMML(DEFAULT_LIST_KEY, new RobustMML());

        mml.setTrace(true);

        Map<String, String> hash = new HashMap<>();

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
    
    @Override
    public MysterType getType(StandardTypes standardType) {
        for (TypeDescriptionElement t : types) {
            if (t.getTypeDescription().getInternalName().equals(standardType.toString())) {
                return t.getType();
            }
        }
        
        throw new IllegalStateException("Unknown standard type: " + standardType);
    }

    @Override
    //TypeDescription Methods
    public TypeDescription[] getAllTypes() {
        TypeDescription[] typeArray_temp = new TypeDescription[workingTypes.length];

        for (int i = 0; i < types.length; i++) {
            typeArray_temp[i] = workingTypes[i].getTypeDescription();
        }

        return typeArray_temp;
    }

    @Override
    public Optional<TypeDescription> get(MysterType type) {
        for (int i = 0; i < types.length; i++) {
            if (types[i].getTypeDescription().getType().equals(type)) {
                return Optional.of(types[i].getTypeDescription());
            }
        }

        return Optional.empty();
    }

    @Override
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

    @Override
    public boolean isTypeEnabled(MysterType type) {
        int index = getIndexFromType(type);

        return (index != -1 ? workingTypes[index].enabled : false);
    }

    @Override
    public boolean isTypeEnabledInPrefs(MysterType type) {
        int index = getIndexFromType(type);

        return (index != -1 ? types[index].enabled : false);
    }

    @Override
    public void addTypeListener(TypeListener listener) {
        dispatcher.addListener(listener);
    }

    @Override
    public void removeTypeListener(TypeListener listener) {
        dispatcher.removeListener(listener);
    }

    /**
     * Must be done on the event thread!
     */
    @Override
    public void setEnabledType(MysterType type, boolean enable) {
        int index = getIndexFromType(type);

        //errs
        if (index == -1)
            return; //no such type
        if (types[index].enabled == enable)
            return;

        types[index].setEnabled(enable);

        saveEverythingToDisk();

        if (enable) {
            dispatcher.fire().typeEnabled(new TypeDescriptionEvent( this, type));
        } else {
            dispatcher.fire().typeDisabled(new TypeDescriptionEvent( this, type));
        }
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

    private static TypeDescription getTypeDescriptionAtPath(RobustMML mml, String path) {
        final String TYPE = "Type", DESCRIPTION = "Description", EXTENSIONS = "Extensions/",
                ARCHIVED = "Archived", ENABLED_BY_DEFAULT = "Enabled By Default", PUBLIC_KEY = "Public Key";

        String internalName = mml.get(path + TYPE);
        String description = mml.get(path + DESCRIPTION);
        List<String> extensionsDirList = mml.list(path + EXTENSIONS);
        String archived = mml.get(path + ARCHIVED);
        String enabled = mml.get(path + ENABLED_BY_DEFAULT);
        String publicKey = mml.get(path + PUBLIC_KEY);

        if ((publicKey == null) || ((internalName == null) & (description == null)))
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
            return new TypeDescription(new MysterType(Util.publicKeyFromString(publicKey).get()),
                                       internalName,
                                       description,
                                       extensions,
                                       isArchived,
                                       isEnable);
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