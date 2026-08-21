package com.myster.filemanager;

import java.util.Collection;

import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;

/**
 * Registry of metadata profiles keyed by stable metadata type id.
 */
public interface MetadataTypeRegistry {
    MetadataType get(String metadataTypeId);

    /**
     * Returns the metadata profile assigned to a concrete Myster network type.
     * Unknown Myster types, missing assignments, and unregistered metadata type ids resolve to
     * the generic profile.
     *
     * @param tdList the type descriptions containing metadata profile assignments
     * @param type the concrete Myster network type
     * @return the assigned metadata profile, or the generic profile when no assignment resolves
     */
    default MetadataType get(TypeDescriptionList tdList, MysterType type) {
        return tdList.get(type)
                .flatMap(TypeDescription::getMetadataTypeId)
                .map(this::get)
                .orElseGet(this::generic);
    }

    MetadataType generic();

    Collection<MetadataType> supportedTypes();
}
