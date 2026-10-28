package com.myster;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import com.simtechdata.waifupnp.UPnP;

class TestMysterUpnp {
    private static final int PORT = 8000;
    private final Logger logger = Logger.getLogger(Myster.class.getName());
    private final List<String> messages = new ArrayList<>();
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };
    private Level previousLevel;
    private boolean previousUseParentHandlers;
    private MockedStatic<UPnP> upnp;

    @BeforeEach
    void setUp() {
        previousLevel = logger.getLevel();
        previousUseParentHandlers = logger.getUseParentHandlers();
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        upnp = mockStatic(UPnP.class);
        upnp.when(UPnP::isUPnPAvailable).thenReturn(true);
        upnp.when(UPnP::getDefaultGatewayIP).thenReturn("192.168.1.1");
        upnp.when(UPnP::getLocalIP).thenReturn("192.168.1.2");
        upnp.when(() -> UPnP.isMappedTCP(PORT)).thenReturn(true);
        upnp.when(() -> UPnP.isMappedUDP(PORT)).thenReturn(true);
        upnp.when(() -> UPnP.closePortTCP(7000)).thenReturn(true);
        upnp.when(() -> UPnP.closePortUDP(7000)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        upnp.close();
        logger.removeHandler(handler);
        logger.setLevel(previousLevel);
        logger.setUseParentHandlers(previousUseParentHandlers);
    }

    @ParameterizedTest
    @CsvSource({
            "10.0.0.0, NAT of a NAT",
            "10.255.255.255, NAT of a NAT",
            "172.16.0.0, NAT of a NAT",
            "172.31.255.255, NAT of a NAT",
            "192.168.0.0, NAT of a NAT",
            "192.168.255.255, NAT of a NAT",
            "169.254.1.1, NAT of a NAT",
            "100.64.0.0, Carrier grade NAT",
            "100.100.100.100, Carrier grade NAT",
            "100.127.255.255, Carrier grade NAT"
    })
    void upstreamNatSkipsOpeningMappingsAndRetainsDiagnostics(String address, String reason) {
        upnp.when(UPnP::getExternalIP).thenReturn(address);

        Myster.updateUpnpPortMappings(0, PORT);
        verifyNoMappingChanges();

        Myster.updateUpnpPortMappings(7000, PORT);

        verifyOldMappingsClosedWithoutOpeningNewMappings();
        assertTrue(messages.contains("UPnP TCP/UDP port mapping disabled: " + reason + " - not public address."));
        assertTrue(messages.contains("UPnP available: true"));
        assertTrue(messages.contains("External UPnP gateway: 192.168.1.1"));
        assertTrue(messages.contains("External IP: " + address));
        assertTrue(messages.contains("Local IP: 192.168.1.2"));
        assertTrue(messages.contains("serverPreferences.getServerPort(): " + PORT));
        assertTrue(messages.contains("isMappedTCP(): true"));
        assertTrue(messages.contains("isMappedUDP(): true"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "8.8.8.8", "9.255.255.255", "11.0.0.0",
            "100.63.255.255", "100.128.0.0",
            "172.15.255.255", "172.32.0.0", "192.167.255.255", "192.169.0.0"
    })
    void publicAddressesOpenBothProtocolsAtStartupAndOnPortChange(String address) {
        upnp.when(UPnP::getExternalIP).thenReturn(address);
        upnp.when(() -> UPnP.openPortTCP(PORT)).thenReturn(true);
        upnp.when(() -> UPnP.openPortUDP(PORT)).thenReturn(true);

        Myster.updateUpnpPortMappings(0, PORT);

        upnp.verify(() -> UPnP.openPortTCP(PORT));
        upnp.verify(() -> UPnP.openPortUDP(PORT));
        upnp.verify(() -> UPnP.closePortTCP(anyInt()), never());
        upnp.verify(() -> UPnP.closePortUDP(anyInt()), never());
        assertTrue(messages.contains("External TCP/IP port enabled: true"));
        assertTrue(messages.contains("External UDP/IP port enabled: true"));

        upnp.clearInvocations();
        Myster.updateUpnpPortMappings(7000, PORT);

        upnp.verify(() -> UPnP.closePortTCP(7000));
        upnp.verify(() -> UPnP.closePortUDP(7000));
        upnp.verify(() -> UPnP.openPortTCP(PORT));
        upnp.verify(() -> UPnP.openPortUDP(PORT));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "router.example", "999.1.2.3", "0.0.0.0", "127.0.0.1", "224.0.0.1", "::1" })
    void unusableExternalAddressesSkipMapping(String address) {
        upnp.when(UPnP::getExternalIP).thenReturn(address);

        Myster.updateUpnpPortMappings(0, PORT);
        verifyNoMappingChanges();

        Myster.updateUpnpPortMappings(7000, PORT);

        verifyOldMappingsClosedWithoutOpeningNewMappings();
        assertTrue(messages.stream().anyMatch(message -> message.startsWith("UPnP TCP/UDP port mapping disabled:")));
    }

    @Test
    void unavailableGatewaySkipsMapping() {
        upnp.when(UPnP::isUPnPAvailable).thenReturn(false);

        Myster.updateUpnpPortMappings(0, PORT);
        Myster.updateUpnpPortMappings(7000, PORT);

        verifyNoMappingChanges();
        assertTrue(messages.contains("UPnP available: false"));
        assertTrue(messages.contains("UPnP TCP/UDP port mapping disabled: no UPnP gateway available."));
    }

    private void verifyNoMappingChanges() {
        upnp.verify(() -> UPnP.openPortTCP(anyInt()), never());
        upnp.verify(() -> UPnP.openPortUDP(anyInt()), never());
        upnp.verify(() -> UPnP.closePortTCP(anyInt()), never());
        upnp.verify(() -> UPnP.closePortUDP(anyInt()), never());
    }

    private void verifyOldMappingsClosedWithoutOpeningNewMappings() {
        upnp.verify(() -> UPnP.closePortTCP(7000));
        upnp.verify(() -> UPnP.closePortUDP(7000));
        upnp.verify(() -> UPnP.openPortTCP(anyInt()), never());
        upnp.verify(() -> UPnP.openPortUDP(anyInt()), never());
        assertTrue(messages.contains("Old TCP/IP port mapping closed: true"));
        assertTrue(messages.contains("Old UDP/IP port mapping closed: true"));
    }
}
