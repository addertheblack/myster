package com.myster.mml;

/**
 * Rubust MML differs from regular MML in that it NEVER throws a runtime
 * Exception. Usefull if you don't trust the MML object. (RobustMML still throws
 * an exception on creation if the MML text (data) is invalid).
 */
import java.util.Vector;

public class RobustMML extends MML {
    static final long serialVersionUID = -7600641043262045615L;

    private boolean trace;

    public RobustMML() {
        super();
    }

    public RobustMML(String s) throws MMLException {//THROWS
                                                    // NullPointerException if
                                                    // argument is null
        super(s);
    }

    public RobustMML(MML mml) { //cute trick.
        super(mml);
    }

    /**
     * @return true if trace is active, false is trace is not active.
     */
    public boolean isTrace() {
        return trace;
    }

    /**
     * Turns on printing stack traces.
     */
    public void setTrace(boolean b) {
        trace = b;
    }

    /**
     * Adds a value for the key path. If the path doesn't exist it is created.
     * Always returns true or throws an execption.
     */
    public boolean put(String path, String value) { //tried to add value to
                                                    // branch, syntax error (bad
                                                    // path)
        try {
            return super.put(path, value);
        } catch (MMLPathException ex) {
            if (trace)
                ex.printStackTrace();
        } catch (NullValueException ex) {
            if (trace)
                ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    /**
     * Removes the value at key path. All empty branch nodes along the path are
     * deleted. Returns the value at key path. If path is invalid does not
     * delete anything and returns null.
     */
    public String remove(String path) {
        try {
            return super.remove(path);
        } catch (MMLPathException ex) {
            if (trace)
                ex.printStackTrace();
        } catch (NullValueException ex) {
            if (trace)
                ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
            com.general.util.AnswerDialog.simpleAlert("" + ex);
        }
        return null;
    }

    /**
     * Gets the value at key path. If path doens't exoist, returns null.
     */
    public String get(String path) {
        try {
            return super.get(path);
        } catch (MMLPathException ex) {
            if (trace)
                ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * Gets the value at key path. If path doens't exoist, returns "".
     */
    public String query(String path) {
        try {
            return super.query(path);
        } catch (MMLPathException ex) {
            if (trace)
                ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "";
    }

    /**
     * gets a listing at path. if path is bad returns null.
     */
    public Vector list(String path) {
        try {
            return super.list(path);
        } catch (MMLPathException ex) {
            if (trace)
                ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * returns true if path points to a value. false otherwise.
     */
    public boolean isAValue(String path) {
        try {
            return super.isAValue(path);
        } catch (MMLPathException ex) {
            if (trace)
                ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    /**
     * returns true if path points to a value. false otherwise.
     */
    public boolean isAFile(String path) {
        try {
            return super.isAFile(path);
        } catch (MMLPathException ex) {
            if (trace)
                ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    /**
     *  
     */
    public boolean isADirectory(String path) {
        try {
            return super.isADirectory(path);
        } catch (MMLPathException ex) {
            if (trace)
                ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    public void removeDirectoryContents(String path) {
        try {
            super.removeDirectoryContents(path);
        } catch (MMLPathException ex) {
            if (trace)
                ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void removeDirectory(String path) {
        try {
            super.removeDirectory(path);
        } catch (MMLPathException ex) {
            if (trace)
                ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void removeFile(String path) {
        try {
            super.removeFile(path);
        } catch (MMLPathException ex) {
            if (trace)
                ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public boolean pathExists(String path) {
        try {
            return super.pathExists(path);
        } catch (MMLPathException ex) {
            if (trace)
                ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }
}