package com.myster.search.ui;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.util.Optional;

import com.myster.access.AccessList;
import com.myster.type.CustomTypeDefinition;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;
import com.myster.type.TypeListener;
import org.junit.jupiter.api.Test;

class TestClientInfoFactoryUtils {
    private static final MysterType AUDIO = type(1);
    private static final MysterType IMAGE = type(2);
    private static final MysterType OTHER = type(3);

    @Test
    void getHandler_returnsImageHandlerForPicturesType() {
        assertInstanceOf(ClientImageHandleObject.class,
                ClientInfoFactoryUtils.getHandler(new TestTypeDescriptionList(), IMAGE));
    }

    @Test
    void getHandler_returnsAudioHandlerForMpg3Type() {
        assertInstanceOf(ClientMPG3HandleObject.class,
                ClientInfoFactoryUtils.getHandler(new TestTypeDescriptionList(), AUDIO));
    }

    @Test
    void getHandler_returnsGenericHandlerForOtherTypes() {
        assertInstanceOf(ClientGenericHandleObject.class,
                ClientInfoFactoryUtils.getHandler(new TestTypeDescriptionList(), OTHER));
    }

    private static MysterType type(int value) {
        byte[] bytes = new byte[16];
        bytes[15] = (byte) value;
        return new MysterType(bytes);
    }

    private static class TestTypeDescriptionList implements TypeDescriptionList {
        @Override
        public Optional<TypeDescription> get(MysterType type) {
            if (AUDIO.equals(type)) {
                return Optional.of(typeDescription(AUDIO, "Audio", "audio"));
            }
            if (IMAGE.equals(type)) {
                return Optional.of(typeDescription(IMAGE, "Image", "image"));
            }
            if (OTHER.equals(type)) {
                return Optional.of(typeDescription(OTHER, "Other", null));
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

        private static TypeDescription typeDescription(MysterType type,
                                                       String name,
                                                       String metadataTypeId) {
            return new TypeDescription(type,
                    name,
                    name,
                    new String[] {},
                    false,
                    true,
                    com.myster.type.TypeSource.DEFAULT,
                    true,
                    metadataTypeId);
        }
    }
}
