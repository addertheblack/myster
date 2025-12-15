package com.myster.plugin;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.logging.Logger;

import com.general.util.AnswerDialog;

/**
 * Loads pluggins from a directory using the jar plugins class loader.
 */

public class PluginLoader implements Runnable {
    private static final Logger log = Logger.getLogger(PluginLoader.class.getName());
    
    private final File pluginDirectory;

    public PluginLoader(File directory) {
        pluginDirectory = directory;
    }

    public void loadPlugins() {
        String[] dirList = pluginDirectory.list(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });

        if (dirList == null || dirList.length == 0)
            return;

        for (int i = 0; i < dirList.length; i++) {
            File f_temp = new File(pluginDirectory.getAbsolutePath()
                    + File.separator + dirList[i]);

            try {
                JarClassLoader loader = new JarClassLoader(f_temp);

                ((MysterPlugin) ((loader.loadClass("com.myster.plugins.Main",
                        true)).newInstance())).pluginInit();

            } catch (IOException ex) {
                log.info("Plugin " + f_temp.getName() + " could not be loaded due to a "
                        + ex.toString() + " error.");
            } catch (ClassNotFoundException ex) {
                log.info("Plugin " + f_temp.getName()
                        + " does not have a \"com.myster.plugins.Main\" class.");
            } catch (InstantiationException ex) {
                log.info("Plugin " + f_temp.getName()
                        + " refused to create a new instance of \"com.myster.plugins.Main\" class.");
            } catch (IllegalAccessException ex) {
                ex.printStackTrace();
            } catch (Exception ex) {
                log.info("Unexpected Exception. While loading a plugin.w");
                ex.printStackTrace();
            } catch (NoClassDefFoundError ex) {
                log.info("NoClassDefFoundError: The pluggin " + f_temp.getName()
                        + " is not compatiable with Myster.");
                log.info("NoClassDefFoundError: The pluggin uses a class that is "
                        + "not in this version of Myster.");
                ex.printStackTrace();
                AnswerDialog.simpleAlert("The pluggin " + f_temp.getName()
                        + " is not compatiable with this version of Myster." + "\n");
            } catch (Error ex) {
                log.info("Unexpected error. The pluggin is not compatiable with Myster.");
                ex.printStackTrace();
            }
        }
    }

    public void run() {
        loadPlugins();
    }
}