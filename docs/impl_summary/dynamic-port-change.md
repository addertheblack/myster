# Implementation Summary: Dynamic Port Change Support

## What was implemented

### Phase 1: Bug Fixes
1. **Fixed mDNS bug in `ServerFacade.initMDns()`**: Removed the conditional check that skipped mDNS when not on the default port. mDNS now always starts and announces the configured server port, enabling LAN discovery regardless of which port the server runs on.

2. **Fixed UPnP bug in `Myster.java`**: Changed hardcoded `MysterGlobals.DEFAULT_SERVER_PORT` to `serverPreferences.getServerPort()` so UPnP mappings are created for the actual configured port.

### Phase 2: Operator Stop Capability
3. **Updated `Operator.java`**:
   - Added `volatile boolean endFlag` field
   - Added `flagToEnd()` method that sets the flag, closes the server socket (breaking out of `accept()`), and cancels the timer
   - Changed `while (true)` to `while (!endFlag)` in the `run()` method
   - Added logging for shutdown events

### Phase 3: Port Change in ServerFacade
4. **Updated `ServerFacade.java`**:
   - Changed `operators` from `final Operator[]` to mutable `List<Operator>`
   - Added tracking for main port transaction protocols (`mainPortProtocols` list)
   - Added tracking for encryption lookup (`encryptionLookup` field)
   - Added `changePort(int oldPort, int newPort)` method that:
     - Flags old operators to end (they drain naturally)
     - Creates new operators for the new port configuration
     - Handles LAN operators when switching to/from default port
     - **Moves datagram transaction protocols to new port**
     - **Moves encryption support to new port (using STLS_CODE)**
     - Adds PingTransport to new port
     - Restarts mDNS with the new port
   - Updated `startServer()` to iterate over List instead of array
   - Updated `addDatagramTransactions()` to track protocols added to main port
   - Updated `addEncryptionSupport()` to track the encryption lookup
   - Made `initLanResourceDiscovery()` idempotent by removing existing protocols before adding new ones
   - Added `moveDatagramProtocolsToNewPort()` that handles all edge cases:
     - **6669 → 7000**: Moves main protocols from 6669 to 7000; LAN discovery adds its own to 6669
     - **7000 → 6669**: Cleans up LAN protocols from 6669, then moves main protocols there
     - **7000 → 8000**: Moves main protocols from 7000 to 8000; LAN protocols on 6669 recreated

### Phase 4: Port Change Callback
5. **Updated `ServerPreferencesPane.java`**:
   - Added `BiConsumer<Integer, Integer> onServerPortChanged` callback field (takes oldPort, newPort)
   - Added `setOnServerPortChanged(BiConsumer<Integer, Integer>)` method with Javadoc
   - Updated `save()` to detect port changes and invoke the callback with both old and new ports

### Phase 5: Wiring in Myster.java with UPnP Cleanup
6. **Updated `Myster.java`**:
   - Added callback wiring: `serverPrefsPane.setOnServerPortChanged((oldPort, newPort) -> {...})`
   - Callback calls `serverFacade.changePort(newPort)`
   - **Added UPnP cleanup**: Closes old port mappings with `UPnP.closePortTCP(oldPort)` and `UPnP.closePortUDP(oldPort)` before opening new ones

## Deviations from plan
- Changed callback from `Consumer<Integer>` to `BiConsumer<Integer, Integer>` to pass both old and new ports, enabling proper UPnP cleanup.

## Docs/Javadoc updated (need manual double check)
- `ServerFacade.initMDns()` - Updated Javadoc explaining it always starts mDNS
- `ServerFacade.changePort()` - Added comprehensive Javadoc explaining the operator swap mechanism and port transitions
- `Operator.flagToEnd()` - Added Javadoc explaining how it stops the accept loop without affecting existing connections
- `ServerPreferencesPane.setOnServerPortChanged()` - Updated Javadoc documenting the BiConsumer callback contract with (oldPort, newPort)

## Follow-up work or issues discovered
1. ~~**Datagram transports on old port**~~: **FIXED** - Transaction protocols and encryption support are now tracked and moved to the new port when `changePort()` is called.

2. ~~**UPnP old port cleanup**~~: **FIXED** - Old UPnP port mappings are now explicitly closed when changing ports using `UPnP.closePortTCP()` and `UPnP.closePortUDP()`.

3. **Port validation**: The plan suggested validating port availability before stopping old operators to enable rollback. This was not implemented - if the new port fails to bind, the old operators are already stopped.

## Tests Added

**`TestOperator.java`** (new file):
- `testFlagToEndSetsEndFlag()` - Verifies flagToEnd sets the end flag
- `testFlagToEndIsIdempotent()` - Verifies multiple calls don't throw
- `testFlagToEndBeforeRun()` - Verifies flagToEnd works before run() is called
- `testGetPort()` - Verifies port getter works correctly
- `testOperatorWithBindAddress()` - Verifies operator works with specific bind address

**`TestServerFacadePortChange.java`** (new file):
- `testChangePortFromDefaultToNonDefault()` - Verifies 6669 → 7000 transition
- `testChangePortFromNonDefaultToDefault()` - Verifies 7000 → 6669 transition with LAN cleanup
- `testChangePortBetweenNonDefaults()` - Verifies 7000 → 8000 transition
- `testEncryptionSupportMovedOnPortChange()` - Verifies STLS encryption protocols are moved
- `testInitLanResourceDiscoveryIsIdempotent()` - Verifies initLanResourceDiscovery can be called multiple times safely

**`TestServerPreferencesPane.java`** (new file):
- `testPortChangeCallbackInvoked()` - Verifies callback contract
- `testPortChangeCallbackNotInvokedWhenSamePort()` - Verifies no callback when port unchanged
- `testPortChangeCallbackReceivesBothPorts()` - Verifies BiConsumer<Integer, Integer> signature
- `testServerNameCallbackStillWorks()` - Verifies existing name callback still works
- `testNullPortChangeCallback()` - Verifies null callback is handled gracefully

## Remaining Tests (lower priority)
These tests would require more complex mocking or integration testing:
- `testChangePortStopsOldOperators()` - Would need to verify operators are flagged
- `testChangePortRestartsMdns()` - Would need mDNS mocking
- `testMdnsStartsOnNonDefaultPort()` - Would need mDNS integration
- `testLanOperatorsBoundToLanAddressesOnly()` - Would need network interface mocking

## Notes for maintainer
- The mDNS bug fix is a standalone improvement - mDNS now works correctly even if users never change ports dynamically.
- The UPnP bug fix is also standalone - users on non-default ports now get proper UPnP mappings.
- Existing connections drain naturally when port changes - no forced disconnections.
- The `Operator.flagToEnd()` pattern could be reused for clean shutdown scenarios.
- UPnP port cleanup note: Some routers may refuse to close ports (per WaifUPnP documentation), but we attempt it anyway.

## Files changed
- `src/main/java/com/myster/net/server/ServerFacade.java`
- `src/main/java/com/myster/net/server/Operator.java`
- `src/main/java/com/myster/server/ui/ServerPreferencesPane.java`
- `src/main/java/com/myster/Myster.java`
- `src/main/java/com/myster/transaction/TransactionManager.java` (added `removeTransactionProtocol()`)
- `src/test/java/com/myster/net/server/TestOperator.java` (new test file)
- `src/test/java/com/myster/net/server/TestServerFacadePortChange.java` (new test file)
- `src/test/java/com/myster/server/ui/TestServerPreferencesPane.java` (new test file)

## Test results
All existing tests pass. New tests added:
- `TestOperator.java` - Tests for Operator.flagToEnd() functionality
- `TestServerFacadePortChange.java` - Tests for datagram protocol port change edge cases
- `TestServerPreferencesPane.java` - Tests for port change callback functionality

## Final Checklist Verification
- [x] mDNS bug fixed - always runs and announces configured port
- [x] UPnP bug fixed - uses configured port, not hardcoded DEFAULT
- [x] UPnP old port cleanup - closes old mappings when port changes
- [x] User can change server port in preferences UI
- [x] Port change takes effect immediately without restart
- [x] New connections use the new port
- [x] Existing connections on old port continue uninterrupted (operators drain)
- [x] When switching FROM default port: LAN operators created on 6669
- [x] When switching TO default port: No LAN operators (not needed)
- [x] LAN operators only accept LAN connections (bound to LAN addresses)
- [x] mDNS announces the configured server port
- [x] No thread leaks (Operator threads exit cleanly via flagToEnd)
- [x] All modified classes/methods have updated Javadoc
- [x] Implementation summary written
