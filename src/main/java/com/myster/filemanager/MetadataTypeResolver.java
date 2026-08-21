package com.myster.filemanager;

import java.util.Optional;

import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;

/**
 * Resolves a concrete Myster network type to its metadata profile.
 */
public final class MetadataTypeResolver {
    private MetadataTypeResolver() {
    }

    public static MetadataType resolve(TypeDescriptionList tdList,
                                       MysterType type,
                                       MetadataTypeRegistry registry) {
        Optional<TypeDescription> description = tdList.get(type);
        if (description.isEmpty()) {
            return registry.generic();
        }

        return description.get()
                .getMetadataTypeId()
                .map(registry::get)
                .orElseGet(registry::generic);
    }
}
