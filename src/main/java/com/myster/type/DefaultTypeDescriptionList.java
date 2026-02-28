package com.myster.type;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import com.general.events.NewGenericDispatcher;
import com.general.thread.Invoker;
import com.myster.access.AccessList;
import com.myster.access.AccessListKeyUtils;
import com.myster.access.AccessListManager;
import com.myster.access.AccessListState;
import com.myster.identity.Util;
import com.myster.mml.RobustMML;
import com.myster.pref.PreferencesMML;
import com.myster.transaction.TransactionManager;

/**
 * Default implementation of {@link TypeDescriptionList}.
 *
 * <p>Built-in types are loaded from the {@code typedescriptionlist.mml} resource bundled inside
 * the application JAR. Custom types are loaded at startup from their {@link AccessList} files via
 * {@link AccessListManager}; the access list is the canonical metadata store for name,
 * description, extensions, policy, and the type's RSA public key.
 *
 * <p>{@link CustomTypeManager} stores only the enabled/disabled flag for each custom type. A
 * prefs node whose corresponding access list file is missing is treated as stale and silently
 * deleted at startup.
 */
public class DefaultTypeDescriptionList implements TypeDescriptionList {
    private static final Logger log = Logger.getLogger(TransactionManager.class.getName());

    private List<TypeDescriptionElement> types;

    private final NewGenericDispatcher<TypeListener> dispatcher;

    private final CustomTypeManager customTypeManager;
    private final AccessListManager accessListManager;

    /**
     * Constructs the type description list, loading built-in types from the bundled resource and
     * custom types from their access lists on disk.
     *
     * <p>Any prefs node for a custom type whose access list file cannot be found is deleted and
     * skipped with a WARNING log entry.
     *
     * @param pref               root Preferences node (the {@code CustomTypes} child is used)
     * @param accessListManager  manages loading/saving access list files
     */
    public DefaultTypeDescriptionList(Preferences pref, AccessListManager accessListManager) {
        this.customTypeManager = new CustomTypeManager(pref);
        this.accessListManager = accessListManager;

        List<TypeDescriptionElement> typesList = new ArrayList<>();

        TypeDescription[] defaultList = loadDefaultTypeAndDescriptionList();
        Map<String, Boolean> enabledHash = getEnabledFromPrefs();

        for (TypeDescription td : defaultList) {
            boolean enabled = enabledHash.getOrDefault(
                    td.getType().toString(), td.isEnabledByDefault());
            typesList.add(new TypeDescriptionElement(td, enabled));
        }

        Map<MysterType, Boolean> enabledTypes = customTypeManager.loadEnabledTypes();
        for (Map.Entry<MysterType, Boolean> entry : enabledTypes.entrySet()) {
            MysterType mysterType = entry.getKey();
            boolean enabled = entry.getValue();

            Optional<AccessList> accessList = accessListManager.loadAccessList(mysterType);
            if (accessList.isEmpty()) {
                log.warning("No access list for custom type " + mysterType.toHexString()
                        + " — removing stale prefs node");
                customTypeManager.deleteCustomType(mysterType);
                continue;
            }

            CustomTypeDefinition def = buildCustomTypeDefinition(accessList.get().getState());
            TypeDescription customDesc = buildTypeDescription(mysterType, def);
            typesList.add(new TypeDescriptionElement(customDesc, enabled));
        }

        types = typesList;
        dispatcher = new NewGenericDispatcher<>(TypeListener.class, Invoker.EDT_NOW_OR_LATER);
    }

    /**
     * Builds a {@link CustomTypeDefinition} from the derived state of an access list.
     * The public key may be null if the genesis block has not set one yet.
     */
    private static CustomTypeDefinition buildCustomTypeDefinition(AccessListState state) {
        PublicKey publicKey = state.getTypePublicKey();
        String name = state.getName() != null ? state.getName() : "";
        String description = state.getDescription() != null ? state.getDescription() : "";
        String[] extensions = state.getExtensions();
        boolean searchInArchives = state.isSearchInArchives();
        boolean isPublic = state.getPolicy().isListFilesPublic();
        return new CustomTypeDefinition(publicKey, name, description, extensions,
                                        searchInArchives, isPublic);
    }

    private static TypeDescription buildTypeDescription(MysterType type, CustomTypeDefinition def) {
        return new TypeDescription(
                type,
                def.getDescription(),   // internalName (longer text)
                def.getName(),          // description shown in UI (short name)
                def.getExtensions(),
                def.isSearchInArchives(),
                false,                  // custom types disabled by default
                TypeSource.CUSTOM);
    }

    private static final String DEFAULT_LIST_KEY = "DefaultTypeDescriptionList saved defaults";
    private static final String TYPE_KEY = "/type";
    private static final String TYPE_ENABLED = "/enabled";

    private static Map<String, Boolean> getEnabledFromPrefs() {
        com.myster.pref.MysterPreferences pref = com.myster.pref.MysterPreferences.getInstance();
        RobustMML mml = pref.getAsMML(DEFAULT_LIST_KEY, new RobustMML());
        mml.setTrace(true);

        Map<String, Boolean> hash = new HashMap<>();
        List<String> list = mml.list("/");
        for (String s : list) {
            String typeKey  = mml.get("/" + s + TYPE_KEY);
            String enabled  = mml.get("/" + s + TYPE_ENABLED);
            if (typeKey != null && enabled != null) {
                hash.put(typeKey, enabled.equalsIgnoreCase("TRUE"));
            }
        }
        return hash;
    }

    /**
     * Persists enabled/disabled state for built-in types only. Custom type enabled state
     * is owned by {@link CustomTypeManager} and must not be written here.
     */
    private void saveEverythingToDisk() {
        PreferencesMML mml = new PreferencesMML();
        int index = 0;
        for (TypeDescriptionElement element : types) {
            if (element.getTypeDescription().getSource() == TypeSource.CUSTOM) {
                continue; // custom type enabled state lives in CustomTypeManager
            }
            String temp = "/" + index++;
            mml.put(temp + TYPE_KEY, element.getType().toString());
            mml.put(temp + TYPE_ENABLED, element.enabled ? "TRUE" : "FALSE");
        }
        com.myster.pref.MysterPreferences.getInstance().put(DEFAULT_LIST_KEY, mml);
    }

    @Override
    public synchronized MysterType getType(StandardTypes standardType) {
        for (TypeDescriptionElement t : types) {
            if (t.getTypeDescription().getInternalName().equals(standardType.toString())) {
                return t.getType();
            }
        }
        throw new IllegalStateException("Unknown standard type: " + standardType);
    }

    @Override
    public synchronized TypeDescription[] getAllTypes() {
        TypeDescription[] arr = new TypeDescription[types.size()];
        for (int i = 0; i < types.size(); i++) {
            arr[i] = types.get(i).getTypeDescription();
        }
        return arr;
    }

    @Override
    public synchronized Optional<TypeDescription> get(MysterType type) {
        for (TypeDescriptionElement t : types) {
            if (t.getType().equals(type)) {
                return Optional.of(t.getTypeDescription());
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized TypeDescription[] getEnabledTypes() {
        return types.stream()
                .filter(t -> t.enabled)
                .map(TypeDescriptionElement::getTypeDescription)
                .toArray(TypeDescription[]::new);
    }

    @Override
    public synchronized boolean isTypeEnabled(MysterType type) {
        int i = getIndexFromType(type);
        return i != -1 && types.get(i).enabled;
    }

    @Override
    public synchronized boolean isTypeEnabledInPrefs(MysterType type) {
        int i = getIndexFromType(type);
        return i != -1 && types.get(i).enabled;
    }

    @Override
    public void addTypeListener(TypeListener listener) {
        dispatcher.addListener(listener);
    }

    @Override
    public void removeTypeListener(TypeListener listener) {
        dispatcher.removeListener(listener);
    }

    @Override
    public synchronized void setEnabledType(MysterType type, boolean enable) {
        int index = getIndexFromType(type);
        if (index == -1) return;
        if (types.get(index).enabled == enable) return;

        types.get(index).setEnabled(enable);
        saveEverythingToDisk();

        if (enable) {
            dispatcher.fire().typeEnabled(new TypeDescriptionEvent(this, type));
        } else {
            dispatcher.fire().typeDisabled(new TypeDescriptionEvent(this, type));
        }
    }

    private synchronized int getIndexFromType(MysterType type) {
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).getType().equals(type)) return i;
        }
        return -1;
    }


    /**
     * Registers a newly-created custom type. The access list and admin key <em>must</em> already
     * be saved to disk before calling this method — this call only registers the type as disabled
     * in the prefs index and adds it to the in-memory list.
     *
     * @param def the custom type definition (derived from the access list state)
     * @throws IllegalArgumentException if the type already exists
     */
    @Override
    public synchronized void addCustomType(CustomTypeDefinition def) {
        MysterType type = def.toMysterType();
        if (getIndexFromType(type) != -1) {
            throw new IllegalArgumentException("Type already exists: " + def.getName());
        }

        TypeDescription customDesc = buildTypeDescription(type, def);
        types.add(new TypeDescriptionElement(customDesc, false));

        // Only write the enabled flag — all metadata lives in the access list
        customTypeManager.saveEnabled(type, false);
        saveEverythingToDisk();

        log.info("Added custom type: " + def.getName());
    }

    /**
     * Removes a custom type, deleting its access list from disk and its prefs node.
     *
     * @throws IllegalArgumentException if the type does not exist or is a built-in type
     */
    @Override
    public synchronized void removeCustomType(MysterType type) {
        int index = getIndexFromType(type);
        if (index == -1) {
            throw new IllegalArgumentException("Type does not exist: " + type);
        }
        TypeDescription desc = types.get(index).getTypeDescription();
        if (desc.getSource() != TypeSource.CUSTOM) {
            throw new IllegalArgumentException("Cannot remove built-in type: " + desc.getDescription());
        }

        types.remove(index);
        customTypeManager.deleteCustomType(type);
        accessListManager.removeAccessList(type);
        AccessListKeyUtils.deleteKeyPair(type);
        saveEverythingToDisk();

        log.info("Removed custom type: " + desc.getDescription());
        dispatcher.fire().typeDisabled(new TypeDescriptionEvent(this, type));
    }

    /**
     * Refreshes the in-memory state for a custom type from its access list. The updated access
     * list must already be saved to disk before calling this.
     *
     * @throws IllegalArgumentException if the type does not exist or is a built-in type
     */
    @Override
    public synchronized void updateCustomType(MysterType type, CustomTypeDefinition def) {
        int index = getIndexFromType(type);
        if (index == -1) {
            throw new IllegalArgumentException("Type does not exist: " + type);
        }
        TypeDescription existing = types.get(index).getTypeDescription();
        if (existing.getSource() != TypeSource.CUSTOM) {
            throw new IllegalArgumentException("Cannot update built-in type: " + existing.getDescription());
        }
        if (!type.equals(def.toMysterType())) {
            throw new IllegalArgumentException("Type mismatch: cannot change public key during update");
        }

        // Reload metadata from the access list that the editor has already saved
        Optional<AccessList> accessList = accessListManager.loadAccessList(type);
        if (accessList.isEmpty()) {
            log.warning("updateCustomType: no access list found for " + type.toHexString()
                    + ", using supplied def");
        }

        CustomTypeDefinition updatedDef = accessList
                .map(al -> buildCustomTypeDefinition(al.getState()))
                .orElse(def);

        boolean wasEnabled = types.get(index).enabled;
        types.get(index).typeDescription = buildTypeDescription(type, updatedDef);

        saveEverythingToDisk();
        log.info("Updated custom type: " + updatedDef.getName());

        if (wasEnabled) {
            dispatcher.fire().typeEnabled(new TypeDescriptionEvent(this, type));
        }
    }

    /**
     * Returns the {@link CustomTypeDefinition} for a custom type by re-deriving it from
     * the access list on disk. Returns empty for built-in types or unknown types.
     */
    @Override
    public synchronized Optional<CustomTypeDefinition> getCustomTypeDefinition(MysterType type) {
        int index = getIndexFromType(type);
        if (index == -1 || types.get(index).getTypeDescription().getSource() != TypeSource.CUSTOM) {
            return Optional.empty();
        }
        return accessListManager.loadAccessList(type)
                .map(al -> buildCustomTypeDefinition(al.getState()));
    }


    private static synchronized TypeDescription[] loadDefaultTypeAndDescriptionList() {
        try {
            InputStream in = Class.forName("com.myster.Myster")
                    .getResourceAsStream("typedescriptionlist.mml");
            if (in == null) {
                log.severe("There is no \"typedescriptionlist.mml\" file at com.myster level.");
                com.general.util.AnswerDialog.simpleAlert(
                        "PROGRAMMER'S ERROR: No typedescriptionlist.mml found. Myster will exit.");
                System.exit(0);
            }

            List<TypeDescription> list = new ArrayList<>();
            RobustMML mml = new RobustMML(new String(readResource(in)));
            in.close();

            final String LIST = "/List/";
            List<String> typeList = mml.list(LIST);
            for (String key : typeList) {
                TypeDescription td = getTypeDescriptionAtPath(mml, LIST + key + "/");
                if (td != null) list.add(td);
            }

            log.info("Loaded " + list.size() + " built-in type descriptions");
            return list.toArray(TypeDescription[]::new);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    private static TypeDescription getTypeDescriptionAtPath(RobustMML mml, String path) {
        String internalName = mml.get(path + "Type");
        String description  = mml.get(path + "Description");
        List<String> extKeys = mml.list(path + "Extensions/");
        String archived = mml.get(path + "Archived");
        String enabled  = mml.get(path + "Enabled By Default");
        String publicKey = mml.get(path + "Public Key");

        if (publicKey == null || (internalName == null && description == null)) return null;

        boolean isArchived = archived != null && archived.equalsIgnoreCase("True");
        boolean isEnabled  = enabled  != null && enabled.equalsIgnoreCase("True");

        List<String> extList = new ArrayList<>();
        if (extKeys != null) {
            for (String k : extKeys) {
                String ext = mml.get(path + "Extensions/" + k);
                if (ext != null) extList.add(ext);
            }
        }

        try {
            return new TypeDescription(
                    new MysterType(Util.publicKeyFromString(publicKey).get()),
                    internalName, description,
                    extList.toArray(new String[0]),
                    isArchived, isEnabled);
        } catch (Exception ex) {
            throw new com.general.util.UnexpectedException(ex);
        }
    }

    private static byte[] readResource(InputStream in) throws IOException {
        byte[] buffer = new byte[2000];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toByteArray();
    }


    private static class TypeDescriptionElement {
        TypeDescription typeDescription;
        boolean enabled;

        TypeDescriptionElement(TypeDescription td, boolean enabled) {
            this.typeDescription = td;
            this.enabled = enabled;
        }

        boolean setEnabled(boolean e) { return this.enabled = e; }
        TypeDescription getTypeDescription() { return typeDescription; }
        MysterType getType() { return typeDescription.getType(); }
    }
}
