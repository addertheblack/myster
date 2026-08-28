package com.myster.filemanager;

import java.util.Collection;

import com.myster.type.MetadataTypeId;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;

/**
 * Registry of metadata profiles keyed by stable metadata type id.
 */
public interface MetadataTypeRegistry {
    /**
     * Resolves a serialized profile identity to local runtime behavior.
     *
     * @param metadataTypeId known or preserved future profile identity
     * @return the matching implementation, or Generic for an unsupported/non-canonical id
     */
    MetadataType get(MetadataTypeId metadataTypeId);

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
                .map(TypeDescription::getMetadataTypeId)
                .map(this::get)
                .orElseGet(this::generic);
    }

    MetadataType generic();

    Collection<MetadataType> supportedTypes();
}
