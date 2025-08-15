package com.general.util;

import java.awt.GridBagConstraints;

public class GridBagBuilder extends GridBagConstraints {
    
    public GridBagBuilder() {
        gridwidth=1;
        gridheight=1;
    }
    
    @Override
    public GridBagBuilder clone() {
        return (GridBagBuilder) super.clone();
    }

    public GridBagBuilder withGridLoc(int gridx, int gridy) {
        GridBagBuilder copy = this.clone();
        copy.gridx = gridx;
        copy.gridy = gridy;
        return copy;
    }

    public GridBagBuilder withSize(int gridWidth, int gridHeight) {
        GridBagBuilder copy = this.clone();
        copy.gridwidth = gridWidth;
        copy.gridheight = gridHeight;
        return copy;
    }

    public GridBagBuilder withWeight(double weightx, double weighty) {
        GridBagBuilder copy = this.clone();
        copy.weightx = weightx;
        copy.weighty = weighty;
        return copy;
    }

    public GridBagBuilder withFill(int fill) {
        GridBagBuilder copy = this.clone();
        copy.fill = fill;
        return copy;
    }

    public GridBagBuilder withAnchor(int anchor) {
        GridBagBuilder copy = this.clone();
        copy.anchor = anchor;
        return copy;
    }

    public GridBagBuilder withInsets(java.awt.Insets insets) {
        GridBagBuilder copy = this.clone();
        copy.insets = insets;
        return copy;
    }

    public GridBagBuilder withIpad(int ipadx, int ipady) {
        GridBagBuilder copy = this.clone();
        copy.ipadx = ipadx;
        copy.ipady = ipady;
        return copy;
    }
}