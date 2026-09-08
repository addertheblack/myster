package com.myster.type.join;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;

class TestNativeUriPackagingResources {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void linuxDesktopEntryDeclaresLauncherAndScheme() throws Exception {
        String desktop = Files.readString(
                ROOT.resolve("src/main/jpackage/linux/Myster.desktop"));

        assertTrue(desktop.contains("Type=Application"));
        assertTrue(desktop.contains("Exec=APPLICATION_LAUNCHER %u"));
        assertTrue(desktop.contains("Categories=Network;"));
        assertTrue(desktop.contains("MimeType=x-scheme-handler/myster;"));
    }

    @Test
    void macPlistDeclaresMysterSchemeAndRemainsWellFormed() throws Exception {
        Path plist = ROOT.resolve("src/main/jpackage/macos/Info.plist");
        String text = Files.readString(plist);

        assertTrue(text.contains("<key>CFBundleURLTypes</key>"));
        assertTrue(text.contains("<key>CFBundleURLSchemes</key>"));
        assertTrue(text.contains("<string>myster</string>"));
        parseXml(plist);
    }

    @Test
    void windowsInstallerOwnsQuotedProtocolCommand() throws Exception {
        Path wix = ROOT.resolve("src/main/jpackage/windows/main.wxs");
        String text = Files.readString(wix);

        assertTrue(text.contains("Component Id=\"MysterUrlProtocol\""));
        assertTrue(text.contains("Key=\"Software\\Classes\\myster\""));
        assertTrue(text.contains("Name=\"URL Protocol\""));
        assertTrue(text.contains("&quot;[INSTALLDIR]Myster.exe&quot; &quot;%1&quot;"));
        assertTrue(text.contains("Guid=\"8A126E9D-C829-4A3E-AF7B-A316126E44A8\""));
        parseXml(wix);
    }

    @Test
    void everyOsProfileWiresItsOwnJpackageResourceDirectory() throws Exception {
        String pom = Files.readString(ROOT.resolve("pom.xml"));

        assertTrue(pom.contains("src/main/jpackage/windows"));
        assertTrue(pom.contains("src/main/jpackage/macos"));
        assertTrue(pom.contains("src/main/jpackage/linux"));
        assertTrue(pom.contains("<linuxShortcut>true</linuxShortcut>"));
    }

    private static void parseXml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.newDocumentBuilder().parse(path.toFile());
    }
}
