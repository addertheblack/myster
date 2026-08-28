package com.myster.filemanager;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Optional;

import com.myster.access.AccessList;
import com.myster.type.CustomTypeDefinition;
import com.myster.type.MetadataTypeId;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;
import com.myster.type.TypeListener;
import com.myster.type.TypeSource;
import org.junit.jupiter.api.Test;

class TestMetadataTypeRegistryResolution {
    private static final MysterType AUDIO_TYPE = type(1);
    private static final MysterType IMAGE_TYPE = type(2);
    private static final MysterType GENERIC_TYPE = type(3);
    private static final MysterType UNKNOWN_ID_TYPE = type(4);
    private static final MysterType UNKNOWN_TYPE = type(5);

    private final DefaultMetadataTypeRegistry registry = new DefaultMetadataTypeRegistry();
    private final TypeDescriptionList tdList = new TestTypeDescriptionList();

    @Test
    void get_returnsAudioProfileForMysterType() {
        assertSame(MetadataType.AUDIO,
                registry.get(tdList, AUDIO_TYPE));
    }

    @Test
    void get_returnsImageProfileForMysterType() {
        assertSame(MetadataType.IMAGE,
                registry.get(tdList, IMAGE_TYPE));
    }

    @Test
    void get_returnsGenericForNoMetadataTypeId() {
        assertSame(MetadataType.GENERIC,
                registry.get(tdList, GENERIC_TYPE));
    }

    @Test
    void get_returnsGenericForUnknownMetadataTypeId() {
        assertSame(MetadataType.GENERIC,
                registry.get(tdList, UNKNOWN_ID_TYPE));
        assertEquals(MetadataTypeId.fromString("movie"),
                tdList.get(UNKNOWN_ID_TYPE).orElseThrow().getMetadataTypeId());
    }

    @Test
    void get_returnsGenericForUnknownMysterType() {
        assertSame(MetadataType.GENERIC,
                registry.get(tdList, UNKNOWN_TYPE));
    }

    private static MysterType type(int value) {
        byte[] bytes = new byte[16];
        bytes[15] = (byte) value;
        return new MysterType(bytes);
    }

    private static TypeDescription description(MysterType type, MetadataTypeId metadataTypeId) {
        return new TypeDescription(type,
                "Test",
                "Test",
                new String[] {},
                false,
                true,
                TypeSource.CUSTOM,
                true,
                metadataTypeId);
    }

    private static class TestTypeDescriptionList implements TypeDescriptionList {
        @Override
        public Optional<TypeDescription> get(MysterType type) {
            if (AUDIO_TYPE.equals(type)) {
                return Optional.of(description(AUDIO_TYPE, MetadataTypeId.AUDIO));
            }
            if (IMAGE_TYPE.equals(type)) {
                return Optional.of(description(IMAGE_TYPE, MetadataTypeId.IMAGE));
            }
            if (GENERIC_TYPE.equals(type)) {
                return Optional.of(description(GENERIC_TYPE, MetadataTypeId.GENERIC));
            }
            if (UNKNOWN_ID_TYPE.equals(type)) {
                return Optional.of(description(UNKNOWN_ID_TYPE,
                        MetadataTypeId.fromString("movie")));
            }
            return Optional.empty();
        }

        @Override
        public TypeDescription[] getAllTypes() {
            return new TypeDescription[] {};
        }

        @Override
        public TypeDescription[] getEnabledTypes() {
            return new TypeDescription[] {};
        }

        @Override
        public boolean isTypeEnabled(MysterType type) {
            return false;
        }

        @Override
        public boolean isTypeEnabledInPrefs(MysterType type) {
            return false;
        }

        @Override
        public void addTypeListener(TypeListener l) {}

        @Override
        public void removeTypeListener(TypeListener l) {}

        @Override
        public void setEnabledType(MysterType type, boolean enable) {}

        @Override
        public void addCustomType(CustomTypeDefinition def) {}

        @Override
        public void removeCustomType(MysterType type) {}

        @Override
        public void updateCustomType(MysterType type, CustomTypeDefinition def) {}

        @Override
        public Optional<CustomTypeDefinition> getCustomTypeDefinition(MysterType type) {
            return Optional.empty();
        }

        @Override
        public void importType(AccessList accessList) throws IOException {}
    }
}
