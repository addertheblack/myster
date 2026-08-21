package com.myster.filemanager;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.myster.search.ui.ClientGenericHandleObject;
import com.myster.search.ui.ClientImageHandleObject;
import com.myster.search.ui.ClientMPG3HandleObject;
import com.myster.search.ui.FileTypeColumnHandler;
import com.myster.type.TypeDescriptionList;

/**
 * Built-in metadata profiles shipped with Myster.
 */
enum BuiltInMetadataType implements MetadataType {
    GENERIC("generic", "", List.of()) {
        @Override
        public FileTypeColumnHandler getHandler(TypeDescriptionList tdList) {
            return new ClientGenericHandleObject();
        }

        @Override
        public FileItem createFileItem(Path root, Path path, FileMetadataExtractor metadataExtractor) {
            return new FileItem(root, path);
        }

        @Override
        public Optional<TypedMetadataExtractor> typedMetadataExtractor() {
            return Optional.empty();
        }
    },
    AUDIO("audio", "audio-v1",
            List.of("/BitRate", "/Hz", "/LengthSec", "/ID3Name", "/Artist", "/Album")) {
        @Override
        public FileTypeColumnHandler getHandler(TypeDescriptionList tdList) {
            return new ClientMPG3HandleObject();
        }

        @Override
        public FileItem createFileItem(Path root, Path path, FileMetadataExtractor metadataExtractor) {
            return new MPG3FileItem(root, path, metadataExtractor);
        }

        @Override
        public Optional<TypedMetadataExtractor> typedMetadataExtractor() {
            return Optional.of(new TikaAudioMetadataExtractor());
        }
    },
    IMAGE("image", "image-v1",
            List.of("/ImageWidth",
                    "/ImageHeight",
                    "/ImageBitDepth",
                    "/ImageTakenAtMillis",
                    "/ImageOrientation",
                    "/CameraMake",
                    "/CameraModel",
                    "/ImageSoftware")) {
        @Override
        public FileTypeColumnHandler getHandler(TypeDescriptionList tdList) {
            return new ClientImageHandleObject();
        }

        @Override
        public FileItem createFileItem(Path root, Path path, FileMetadataExtractor metadataExtractor) {
            return new ImageFileItem(root, path, metadataExtractor);
        }

        @Override
        public Optional<TypedMetadataExtractor> typedMetadataExtractor() {
            return Optional.of(new TikaImageMetadataExtractor());
        }
    };

    private final String id;
    private final String cacheKey;
    private final List<String> cacheableKeys;

    BuiltInMetadataType(String id, String cacheKey, List<String> cacheableKeys) {
        this.id = id;
        this.cacheKey = cacheKey;
        this.cacheableKeys = List.copyOf(cacheableKeys);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String cacheKey() {
        return cacheKey;
    }

    @Override
    public List<String> cacheableKeys() {
        return cacheableKeys;
    }
}
