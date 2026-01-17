# Dynamic Port Change Support

## Summary
Enable changing the Myster server port through the preferences UI without requiring an application restart. This involves stopping the existing `Operator`(s) within the `ServerFacade` and creating new ones on the new port.

## Goals
- Allow users to change the server port via preferences and have it take effect immediately
- Stop old Operator(s) and create new Operator(s) on the new port within the same ServerFacade
- Allow existing connections on the old port to complete naturally (no forced disconnection)
- Handle UPnP port mappings (open new port)
- Update mDNS announcements for the configured server port
- Properly handle transitions to/from DEFAULT_SERVER_PORT

## Non-goals
- Rebuilding the entire ServerFacade (not needed!)
- Live migration of active connections from old to new port
- Complex rollback mechanisms if the new port fails to bind
- Changing ports programmatically from code (only via preferences UI)

## Proposed Design (High-level)

The implementation follows the existing `setOnServerNameChanged` callback pattern:

1. **Add callback infrastructure to `ServerPreferencesPane`**
   - Add `setOnServerPortChanged(Consumer<Integer>)` method
   - In `save()`, detect if port changed and invoke callback with new port

2. **Add port change capability to `ServerFacade`**
   - Add `changePort(int newPort)` method that:
     - Stops old Operator(s) via `flagToEnd()`
     - Creates new Operator(s) for the new port
     - Restarts mDNS with new port
   - Add `flagToEnd()` method to `Operator` to stop the accept loop

3. **Wire up callback in `Myster.java`**
   - Similar to existing `setOnServerNameChanged` wiring
   - When port changes, call `serverFacade.changePort(newPort)`

## Current Architecture (One ServerFacade, Multiple Operators)

There is always **ONE ServerFacade** instance. The facade contains one or more `Operator` instances depending on the configured port:

### Scenario A: Running on DEFAULT_SERVER_PORT (6669)
- **One ServerFacade** containing:
  - **One Operator** on port 6669, bound to all addresses (`Optional.empty()`)
  - Full `connectionSections` map (all stream handlers)
  - mDNS announcer running, announces port 6669
  - UPnP mapping on 6669

### Scenario B: Running on Non-Default Port (e.g., 7000)
- **One ServerFacade** containing:
  - **Main Operator** on port 7000, bound to all addresses (`Optional.empty()`)
    - Uses full `connectionSections` map
  - **LAN Operators** on DEFAULT_PORT (6669), bound to LAN addresses only (`Optional.of(lanAddress)`)
    - Uses `new HashMap<>()` - empty connection sections (discovery/ping only)
  - PingTransport and ServerStatsDatagramServer on DEFAULT_PORT for UDP discovery
  - mDNS announcer - **CURRENTLY BUGGY: not started!**
  - UPnP mapping on 7000 only (NOT on 6669)

### mDNS Bug (MUST FIX)

Current code in `initMDns()`:
```java
if (preferences.getServerPort() != MysterGlobals.DEFAULT_SERVER_PORT) {
    // No need to start mDNS if we're not on the default port
    return;  // BUG: mDNS is skipped entirely!
}
```

**The Bug:** When running on port 7000, mDNS is NOT started. LAN clients cannot discover this server via mDNS.

**The Fix:** Always start mDNS, announcing `preferences.getServerPort()` (the actual server port).

**Good News:** The `MysterMdnsAnnouncer` class already correctly announces whatever port is passed to its constructor:
```java
serviceInfo = ServiceInfo.create(
    SERVICE_TYPE,
    serverName,
    port,  // ← Correctly uses the passed port
    ...
);
```

So the only fix needed is removing the early return in `initMDns()`. The announcer will correctly advertise the configured port to LAN clients.

### Port Change Transitions (Within Same ServerFacade)

| From | To | Action |
|------|-----|--------|
| DEFAULT (A) | Non-Default (B) | Stop main operator, create new main operator on new port + create LAN operators on DEFAULT |
| Non-Default (B) | DEFAULT (A) | Stop main operator + LAN operators, create single main operator on DEFAULT |
| Non-Default X (B) | Non-Default Y (B) | Stop main operator only, create new main operator on Y, LAN operators unchanged |

**Key insight:** We're just swapping Operators within the same ServerFacade, not rebuilding the facade!

## Affected Modules/Packages
- `com.myster.net.server` - `ServerFacade`, `Operator`, `ServerPreferences`
- `com.myster.server.ui` - `ServerPreferencesPane`
- `com.myster` - `Myster.java` main initialization

## Files/Classes to Change or Create

### Files to Modify
1. **`ServerPreferencesPane.java`**
   - Add `onServerPortChanged` callback field
   - Add `setOnServerPortChanged(Consumer<Integer>)` method
   - Modify `save()` to detect port changes and invoke callback

2. **`Operator.java`**
   - Add `volatile boolean endFlag` field
   - Add `flagToEnd()` method to set flag and close serverSocket
   - Modify `run()` loop condition from `while (true)` to `while (!endFlag)`

3. **`ServerFacade.java`**
   - Change `operators` from `final` array to mutable list
   - Add `changePort(int newPort)` method that:
     - Flags old operators to end
     - Creates new operators for new port configuration
     - Starts the new operators
     - Restarts mDNS
   - **Fix `initMDns()` bug** - always start mDNS

4. **`Myster.java`**
   - Wire up `setOnServerPortChanged` callback to call `serverFacade.changePort(newPort)`
   - **Fix UPnP to use configured port instead of hardcoded DEFAULT_SERVER_PORT**

### Files to Create
1. **Unit test files** (see Testing section)

## Step-by-Step Implementation Plan

### Phase 1: Fix Existing Bugs
1. **Fix mDNS bug in `ServerFacade.initMDns()`:**
   - Remove the port check, always start mDNS announcing `preferences.getServerPort()`

2. **Fix UPnP bug in `Myster.java`:**
   - Change `UPnP.openPortTCP(MysterGlobals.DEFAULT_SERVER_PORT)` to `UPnP.openPortTCP(serverPreferences.getServerPort())`

### Phase 2: Add Operator Stop Capability
3. **Update `Operator.java`:**
   ```java
   private volatile boolean endFlag = false;
   
   public void flagToEnd() {
       endFlag = true;
       try {
           if (serverSocket != null) {
               serverSocket.close();
           }
       } catch (IOException e) {
           // Log but don't throw
       }
       if (timer != null) {
           timer.cancelTimer();
       }
   }
   ```
   - Change `while (true)` to `while (!endFlag)` in `run()` method

### Phase 3: Add Port Change to ServerFacade
4. **Update `ServerFacade.java`:**
   - Change `private final Operator[] operators;` to `private List<Operator> operators;`
   - Add method:
   ```java
   /**
    * Changes the server port. Stops old operators and creates new ones.
    * Existing connections on old port will drain naturally.
    * 
    * @param newPort the new port to listen on
    */
   public synchronized void changePort(int newPort) {
       int oldPort = preferences.getServerPort();
       boolean wasOnDefault = (oldPort == MysterGlobals.DEFAULT_SERVER_PORT);
       boolean goingToDefault = (newPort == MysterGlobals.DEFAULT_SERVER_PORT);
       
       // Stop old operators
       for (Operator operator : operators) {
           operator.flagToEnd();
       }
       // Old operators will drain naturally, connections continue
       
       // Create new operator list
       List<Operator> newOperators = new ArrayList<>();
       
       // Main operator on new port
       Consumer<Socket> socketConsumer =
               (socket) -> connectionExecutor.execute(new ConnectionRunnable(socket,
                                                                             identity,
                                                                             serverDispatcher,
                                                                             transferQueue,
                                                                             fileManager,
                                                                             connectionSections));
       newOperators.add(new Operator(socketConsumer, newPort, Optional.empty()));
       
       // LAN operators if not on default port
       if (!goingToDefault) {
           try {
               initLanResourceDiscovery(newOperators);
           } catch (SocketException e) {
               log.log(Level.WARNING, "Could not initialize LAN socket", e);
           }
       }
       
       // Start new operators
       for (Operator operator : newOperators) {
           operatorExecutor.execute(operator);
       }
       
       operators = newOperators;
       
       // Restart mDNS with new port
       shutdownMdns();
       initMDns();
       
       // Update UPnP
       UPnP.openPortTCP(newPort);
       UPnP.openPortUDP(newPort);
   }
   ```

### Phase 4: Add Port Change Callback
5. **Update `ServerPreferencesPane.java`:**
   - Add field: `private Consumer<Integer> onServerPortChanged;`
   - Add setter method
   - Update `save()` to detect port changes and invoke callback

### Phase 5: Wire Up in Myster.java
6. **Update `Myster.java`:**
   ```java
   serverPrefsPane.setOnServerPortChanged(newPort -> {
       serverFacade.changePort(newPort);
   });
   ```

## Tests/Verification

### Unit Tests Required

**`TestOperator.java`:**
- `testFlagToEndStopsAcceptLoop()` - verify thread exits
- `testFlagToEndClosesServerSocket()` - verify socket closed
- `testFlagToEndCancelsTimer()` - verify timer cancelled
- `testFlagToEndIsIdempotent()` - can call multiple times

**`TestServerFacade.java`:**
- `testChangePortFromDefaultToNonDefault()` - main + LAN operators created
- `testChangePortFromNonDefaultToDefault()` - single operator created
- `testChangePortBetweenNonDefaults()` - operators swapped correctly
- `testChangePortStopsOldOperators()` - old operators flagged
- `testChangePortRestartsMdns()` - mDNS restarted with new port
- `testMdnsStartsOnNonDefaultPort()` - mDNS bug fix verified
- `testLanOperatorsBoundToLanAddressesOnly()` - security check

**`TestServerPreferencesPane.java`:**
- `testPortChangeCallbackInvoked()` - callback called with new port
- `testPortChangeCallbackNotInvokedWhenSamePort()` - no callback if unchanged

### Manual Testing Checklist

| Test | From Port | To Port | Expected Behavior |
|------|-----------|---------|-------------------|
| T1 | 6669 | 7000 | Main operator on 7000, LAN operators on 6669, mDNS announces 7000 |
| T2 | 7000 | 6669 | Single operator on 6669, no LAN operators, mDNS announces 6669 |
| T3 | 7000 | 8000 | Main operator on 8000, LAN operators stay on 6669, mDNS announces 8000 |
| T4 | 6669 | 6669 | No change (callback not triggered) |

### Security Testing
- Verify DEFAULT_PORT (6669) not exposed via UPnP when main port is different
- Verify LAN operators bound to LAN addresses only

## Docs/Comments to Update
1. **Javadoc for `ServerFacade.changePort()`**: Explain operator swap mechanism
2. **Javadoc for `Operator.flagToEnd()`**: Explain how it stops accept loop
3. **Fix misleading comment in `initMDns()`**: Remove incorrect comment

## Acceptance Criteria
- [ ] mDNS bug fixed - always runs and announces configured port
- [ ] UPnP bug fixed - uses configured port, not hardcoded DEFAULT
- [ ] User can change server port in preferences UI
- [ ] Port change takes effect immediately without restart
- [ ] New connections use the new port
- [ ] Existing connections on old port continue uninterrupted (operators drain)
- [ ] When switching FROM default port: LAN operators created on 6669
- [ ] When switching TO default port: No LAN operators (not needed)
- [ ] LAN operators only accept LAN connections (bound to LAN addresses)
- [ ] mDNS announces the configured server port
- [ ] No thread leaks (Operator threads exit cleanly via flagToEnd)
- [ ] All unit tests pass

## Risks/Edge Cases

1. **Port binding failures**: New port may fail to bind
   - Mitigation: Create new operators before stopping old ones, rollback if fails

2. **LAN operator security**: Must not expose to internet
   - Already handled: Bound to LAN addresses only, no UPnP

3. **Rapid port changes**: Each change swaps operators
   - Old operators drain naturally

4. **Thread safety**: `changePort()` is synchronized, operators list access must be safe

## Open Questions
1. Should we validate port availability before stopping old operators? → **Yes, try to bind first**
2. What about datagram transports on old port? → May need cleanup

## Assumptions
- Operators can coexist briefly during transition (old draining, new accepting)
- `connectionExecutor` is shared and continues running
- `connectionSections` map is shared (same handlers for new operators)
- mDNS announcer binds to LAN addresses only

