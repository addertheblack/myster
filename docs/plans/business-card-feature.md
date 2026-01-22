# Bidirectional Server Stats Exchange (Business Card Feature)

## Summary
Add a new bidirectional server stats transaction that allows two Myster servers to exchange their server stats in a single UDP transaction. Unlike the existing one-way `SERVER_STATS_TRANSACTION_CODE` (101), this new transaction sends the requester's stats in the request and receives the responder's stats in the reply. This solves the problem where remote servers cannot discover our actual server port due to NAT port scrambling or when we're running on a non-default port and sending UDP packets from the default port.

## Goals
- Create a new UDP datagram transaction type for bidirectional server stats exchange
- Keep the existing one-way server stats transaction (101) for backwards compatibility
- Implement both client and server sides of the bidirectional exchange
- Integrate bidirectional exchange into the server discovery flow (`refreshMysterServer`)
- Allow both parties to learn each other's port and identity in a single round-trip
- Use UDP/datagram protocol exclusively (as specified in requirements)

## Non-goals
- Replacing or modifying the existing one-way server stats transaction (maintain backwards compatibility)
- Implementing bidirectional exchange over TCP/stream connections
- Forcing bidirectional exchange on every transaction (only on first discovery)
- Persisting business card information separately from normal server tracking

## Proposed Design (High-level)

The bidirectional server stats feature introduces a new transaction type where both parties exchange stats in a single transaction.

### Key Concepts

1. **Transaction Code**: A new `BIDIRECTIONAL_SERVER_STATS_TRANSACTION_CODE` constant (102) in `DatagramConstants`

2. **Client Side**: `BidirectionalServerStatsDatagramClient` - packages our server stats and sends them with the request
   - Uses the existing `ServerStats.getServerStatsMessagePack()` to generate our stats
   - Sends our stats in the request payload
   - Receives remote server's stats in the response payload
   - Returns the remote server's MessagePak

3. **Server Side**: `BidirectionalServerStatsDatagramServer` (implements `TransactionProtocol`) - receives client stats and returns our stats
   - Parses the incoming MessagePak containing client's server stats
   - Extracts client's identity and port
   - Calls `MysterServerPool.suggestAddress()` to add/update the client in our pool
   - Returns our own server stats in the response

4. **Integration Point**: In `MysterServerPoolImpl.refreshMysterServer()`
   - After successfully pinging a remote server via TCP
   - Instead of calling the one-way `getServerStats()`, call the new bidirectional exchange
   - Both parties learn about each other in a single transaction

### Data Flow

```
Client                                  Remote Server
------                                  -------------
1. TCP ping success
2. Send our stats (UDP)             --> Receive client stats
                                        Extract identity/port
                                        Call pool.suggestAddress()
                                        Package our stats
3. Receive remote stats             <-- Send our stats
   Extract identity/port
   Process as normal
```

### Comparison with Existing One-Way Transaction

| Feature | One-Way (101) | Bidirectional (102) |
|---------|---------------|---------------------|
| Request payload | Empty | Client's server stats |
| Response payload | Server's stats | Server's stats |
| Server learns about client | No | Yes (from request) |
| Client learns about server | Yes | Yes (from response) |
| Round trips | 1 | 1 |
| Backwards compatible | N/A | Yes (old clients use 101) |

## Affected Modules/Packages

- `com.myster.net.datagram` - Constants
- `com.myster.net.datagram.client` - Client-side bidirectional exchange
- `com.myster.net.server.datagram` - Server-side bidirectional exchange
- `com.myster.tracker` - Integration in `MysterServerPoolImpl`
- `com.myster` - Wire up server-side transaction protocol in `Myster.java`

## Files/Classes to Change or Create

### Files to Create

1. **`com/myster/net/datagram/client/BidirectionalServerStatsDatagramClient.java`**
   - Implements `StandardDatagramClientImpl<MessagePak>`
   - Constructor takes our server stats parameters (name, port, identity, fileManager)
   - `getDataForOutgoingPacket()`: Serializes our server stats MessagePak
   - `getObjectFromTransaction()`: Deserializes remote server's MessagePak from response
   - `getCode()`: Returns `BIDIRECTIONAL_SERVER_STATS_TRANSACTION_CODE`

2. **`com/myster/net/server/datagram/BidirectionalServerStatsDatagramServer.java`**
   - Implements `TransactionProtocol`
   - Constructor takes both:
     - Server stats parameters (name, port, identity, fileManager) to generate our response
     - `MysterServerPool` to register the client
   - `getTransactionCode()`: Returns `BIDIRECTIONAL_SERVER_STATS_TRANSACTION_CODE`
   - `transactionReceived()`: 
     - Deserializes client's MessagePak from transaction
     - Extracts client's address and port
     - Calls `pool.suggestAddress(correctedAddress)`
     - Generates our server stats
     - Sends our stats in response

### Files to Modify

3. **`com/myster/net/datagram/DatagramConstants.java`**
   - Add `public static final int BIDIRECTIONAL_SERVER_STATS_TRANSACTION_CODE = 102;` after `SERVER_STATS_TRANSACTION_CODE`

4. **`com/myster/net/client/MysterDatagram.java`** (interface)
   - Add method: `PromiseFuture<MessagePak> getBidirectionalServerStats(ParamBuilder params);`

5. **`com/myster/net/datagram/client/MysterDatagramImpl.java`**
   - Implement `getBidirectionalServerStats()` method
   - Needs access to local server name, port, identity, and file manager
   - Constructor will need these dependencies added

6. **`com/myster/tracker/MysterServerPoolImpl.java`**
   - Modify `refreshMysterServer()` method
   - After TCP ping succeeds, call `getBidirectionalServerStats()` instead of `getServerStats()`
   - This gives us the same result (remote server's stats) but also advertises our info

7. **`com/myster/Myster.java`**
   - Add `BidirectionalServerStatsDatagramServer` to the datagram transaction protocols
   - Pass both server stats parameters AND `tracker.getPool()` as constructor arguments
   - Wire up in `addServerConnectionSettings()` alongside other datagram servers

8. **`com/myster/net/client/MysterProtocolImpl.java`** (constructor)
   - May need to pass additional context (server preferences, identity, file manager) to `MysterDatagramImpl` if not already available

## Step-by-Step Implementation Plan

### Phase 1: Define Constants and Interfaces

1. **Add transaction code constant**
   - Edit `DatagramConstants.java`
   - Add `BIDIRECTIONAL_SERVER_STATS_TRANSACTION_CODE = 102` after `SERVER_STATS_TRANSACTION_CODE`
   - Add Javadoc comment explaining the bidirectional exchange:
     ```java
     /**
      * Transaction code for bidirectional server statistics exchange.
      * Unlike SERVER_STATS_TRANSACTION_CODE, this transaction sends the client's
      * stats in the request and receives the server's stats in the response,
      * allowing both parties to learn about each other in a single round-trip.
      */
     public static final int BIDIRECTIONAL_SERVER_STATS_TRANSACTION_CODE = 102;
     ```

2. **Add method signature to `MysterDatagram` interface**
   - Edit `MysterDatagram.java`
   - Add method after `getServerStats()`:
     ```java
     /**
      * Performs a bidirectional server stats exchange with the remote server.
      * Sends our server stats in the request and receives the remote server's
      * stats in the response. Both parties learn about each other in one transaction.
      * 
      * @param params connection parameters including the remote server address
      * @return PromiseFuture containing the remote server's stats
      */
     PromiseFuture<MessagePak> getBidirectionalServerStats(ParamBuilder params);
     ```

### Phase 2: Implement Server Side

3. **Create `BidirectionalServerStatsDatagramServer.java`**
   - Location: `com/myster/net/server/datagram/`
   - Implements `TransactionProtocol`
   - Fields:
     ```java
     private static final Logger log = Logger.getLogger(BidirectionalServerStatsDatagramServer.class.getName());
     
     private final Supplier<String> getServerName;
     private final Supplier<Integer> getPort;
     private final Identity identity;
     private final FileTypeListManager fileManager;
     private final MysterServerPool pool;
     ```
   - Constructor:
     ```java
     public BidirectionalServerStatsDatagramServer(Supplier<String> getServerName,
                                                    Supplier<Integer> getPort,
                                                    Identity identity,
                                                    FileTypeListManager fileManager,
                                                    MysterServerPool pool) {
         this.getServerName = getServerName;
         this.getPort = getPort;
         this.identity = identity;
         this.fileManager = fileManager;
         this.pool = pool;
     }
     ```
   - `getTransactionCode()`: return `BIDIRECTIONAL_SERVER_STATS_TRANSACTION_CODE`
   - `transactionReceived()`:
     ```java
     @Override
     public void transactionReceived(TransactionSender sender,
                                     Transaction transaction,
                                     Object transactionObject)
             throws BadPacketException {
         // Parse client's stats from request
         try (var in = new MysterDataInputStream(new ByteArrayInputStream(transaction.getData()))) {
             MessagePak clientStats = in.readMessagePak();
             
             // Extract client's address and port
             MysterAddress senderAddress = transaction.getAddress();
             Optional<Integer> advertisedPort = clientStats.getInt(ServerStats.PORT);
             
             MysterAddress correctedAddress;
             if (advertisedPort.isPresent()) {
                 correctedAddress = new MysterAddress(
                     senderAddress.getIP(), 
                     advertisedPort.get()
                 );
             } else {
                 // Fallback: use sender's source address
                 correctedAddress = senderAddress;
             }
             
             // Add client to our pool
             pool.suggestAddress(correctedAddress);
             
             log.fine("Received bidirectional server stats from " + correctedAddress);
             
             // Generate our server stats response
             ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
             try (var out = new MysterDataOutputStream(byteOutputStream)) {
                 out.writeMessagePack(ServerStats.getServerStatsMessagePack(
                     getServerName.get(),
                     getPort.get(),
                     identity,
                     fileManager));

                 sender.sendTransaction(new Transaction(transaction,
                                                        byteOutputStream.toByteArray(),
                                                        DatagramConstants.NO_ERROR));
             }
         } catch (IOException ex) {
             throw new BadPacketException("Bad packet " + ex);
         }
     }
     ```

4. **Wire up server-side protocol in `Myster.java`**
   - In `addServerConnectionSettings()`, add to `addDatagramTransactions()` call:
     ```java
     new BidirectionalServerStatsDatagramServer(preferences::getIdentityName,
                                                 preferences::getServerPort,
                                                 identity,
                                                 fileManager,
                                                 tracker.getPool())
     ```
   - Add after `ServerStatsDatagramServer` (keep both for backwards compatibility)

### Phase 3: Implement Client Side

5. **Create `BidirectionalServerStatsDatagramClient.java`**
   - Location: `com/myster/net/datagram/client/`
   - Implements `StandardDatagramClientImpl<MessagePak>`
   - Fields:
     ```java
     private static final Logger log = Logger.getLogger(BidirectionalServerStatsDatagramClient.class.getName());
     
     private final String serverName;
     private final int port;
     private final Identity identity;
     private final FileTypeListManager fileManager;
     ```
   - Constructor:
     ```java
     public BidirectionalServerStatsDatagramClient(String serverName,
                                                    int port,
                                                    Identity identity,
                                                    FileTypeListManager fileManager) {
         this.serverName = serverName;
         this.port = port;
         this.identity = identity;
         this.fileManager = fileManager;
     }
     ```
   - `getCode()`: return `BIDIRECTIONAL_SERVER_STATS_TRANSACTION_CODE`
   - `getDataForOutgoingPacket()`:
     ```java
     @Override
     public byte[] getDataForOutgoingPacket() {
         try {
             MessagePak ourStats = ServerStats.getServerStatsMessagePack(
                 serverName, port, identity, fileManager
             );
             ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             try (var out = new MysterDataOutputStream(byteOut)) {
                 out.writeMessagePack(ourStats);
             }
             return byteOut.toByteArray();
         } catch (NotInitializedException e) {
             log.warning("File manager not initialized, sending minimal stats");
             // Send minimal stats with just port
             try {
                 MessagePak minimalStats = MessagePak.newEmpty();
                 minimalStats.putInt(ServerStats.PORT, port);
                 ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                 try (var out = new MysterDataOutputStream(byteOut)) {
                     out.writeMessagePack(minimalStats);
                 }
                 return byteOut.toByteArray();
             } catch (IOException e2) {
                 log.severe("Failed to create minimal stats: " + e2.getMessage());
                 return new byte[0];
             }
         } catch (IOException e) {
             log.severe("Failed to serialize stats: " + e.getMessage());
             return new byte[0];
         }
     }
     ```
   - `getObjectFromTransaction()`:
     ```java
     @Override
     public MessagePak getObjectFromTransaction(Transaction transaction) throws IOException {
         try (MysterDataInputStream in =
                 new MysterDataInputStream(new ByteArrayInputStream(transaction.getData()))) {
             return in.readMessagePack();
         }
     }
     ```

6. **Update `MysterDatagramImpl` to support bidirectional exchange**
   - Add constructor parameters: `Supplier<String> serverName`, `Supplier<Integer> serverPort`, `Identity identity`, `FileTypeListManager fileManager`
   - Store these as fields
   - Implement `getBidirectionalServerStats()`:
     ```java
     @Override
     public PromiseFuture<MessagePak> getBidirectionalServerStats(ParamBuilder params) {
         return doSection(params, new BidirectionalServerStatsDatagramClient(
             serverName.get(),
             serverPort.get(), 
             identity,
             fileManager
         ));
     }
     ```

7. **Update `MysterDatagramImpl` instantiation**
   - Find where `MysterDatagramImpl` is created (likely in `Myster.java`)
   - Add the new constructor parameters
   - This will require passing `ServerPreferences`, `Identity`, and `FileTypeListManager` to the creation point

### Phase 4: Integration

8. **Modify `MysterServerPoolImpl.refreshMysterServer()`**
   - Replace the call to `getServerStats()` with `getBidirectionalServerStats()`:
     ```java
     // Before:
     // return protocol.getDatagram().getServerStats(new ParamBuilder(address));
     
     // After:
     return protocol.getDatagram().getBidirectionalServerStats(new ParamBuilder(address));
     ```
   - The rest of the logic remains the same - we still get the remote server's MessagePak
   - But now the remote server also learns about us automatically!

### Phase 5: Testing and Polish

9. **Add unit tests**
   - Test `BidirectionalServerStatsDatagramClient` serialization
   - Test `BidirectionalServerStatsDatagramServer` deserialization and pool integration
   - Test that both client and server process each other's stats correctly

10. **Add logging**
    - Log when bidirectional exchange is sent (client side)
    - Log when bidirectional exchange is received (server side)
    - Use `FINE` or `FINER` level to avoid log spam

11. **Handle edge cases**
    - What if file manager is not initialized? (Send minimal stats with just port/identity)
    - What if port is invalid? (Validate on server side)
    - What if sender has no identity? (Allow anonymous exchanges)
    - What if server doesn't support bidirectional (still on old version)? (Fall back to one-way)

## Tests/Verification

### Unit Tests Required

**`TestBidirectionalServerStatsDatagramClient.java`:**
- `testGetDataForOutgoingPacketWithFullStats()` - verify our stats are serialized
- `testGetDataForOutgoingPacketWithUninitializedFileManager()` - graceful degradation
- `testGetObjectFromTransactionParsesRemoteStats()` - verify we parse response correctly
- `testGetCodeReturnsCorrectTransactionCode()` - verify constant

**`TestBidirectionalServerStatsDatagramServer.java`:**
- `testTransactionReceivedCallsSuggestAddress()` - verify pool integration
- `testTransactionReceivedWithValidPort()` - extracts corrected client address
- `testTransactionReceivedWithMissingPort()` - fallback to source address
- `testTransactionReceivedReturnsOurStats()` - verify our stats sent in response
- `testBothPartiesLearnAboutEachOther()` - integration test with mock pool

**`TestMysterServerPoolImpl.java`** (additions):
- `testRefreshMysterServerUsesBidirectionalExchange()` - verify new method called
- `testRefreshMysterServerBothPartiesLearnAboutEachOther()` - verify mutual discovery

### Integration Tests

**Manual Testing Checklist:**
- Start two Myster instances on different ports (e.g., 6669 and 7000)
- Add one as bookmark in the other
- Verify in logs that bidirectional exchange occurs
- Verify that **both servers** learn about each other
- Check that both servers show up in each other's pools with correct ports
- Test with NAT simulation (bind to different ports)
- Test with server running on non-default port
- Test fallback: one server on old code (use one-way 101), one on new code (use bidirectional 102)

### Edge Case Verification

- Bidirectional exchange with empty file list (file manager not initialized)
- Bidirectional exchange with no identity (anonymous server)
- Bidirectional exchange to server that doesn't support the feature (old version - use one-way fallback)
- Network timeout during exchange (should not block)
- Concurrent bidirectional exchanges from multiple clients

## Docs/Comments to Update

1. **`DatagramConstants.java`**
   - Add comprehensive Javadoc for `BIDIRECTIONAL_SERVER_STATS_TRANSACTION_CODE`

2. **`MysterDatagram.java`**
   - Document `getBidirectionalServerStats()` method with purpose and behavior

3. **`BidirectionalServerStatsDatagramClient.java` and `BidirectionalServerStatsDatagramServer.java`**
   - Class-level Javadoc explaining bidirectional exchange feature
   - Explain the difference from one-way server stats (101)
   - Method-level Javadoc for key methods

4. **Architecture documentation** (if it exists)
   - Document the bidirectional server stats transaction in protocol documentation
   - Explain when/why bidirectional exchange is used vs one-way

## Acceptance Criteria

1. ✅ New transaction code (102) defined and documented
2. ✅ Client can send its stats and receive remote server's stats in one transaction
3. ✅ Server can receive client stats and return its own stats in response
4. ✅ Server adds/updates client in its MysterServerPool with correct port
5. ✅ Client processes server's stats using existing logic
6. ✅ **Both parties learn about each other** in a single round-trip
7. ✅ Bidirectional exchange is used in `refreshMysterServer()`
8. ✅ Backwards compatibility maintained (old one-way transaction still exists)
9. ✅ Bidirectional exchange works with encrypted datagram protocol
10. ✅ Unit tests pass for client and server components
11. ✅ Integration test shows mutual discovery
12. ✅ Servers running on non-default ports are correctly discovered
13. ✅ Code is properly documented with Javadoc

## Risks/Edge Cases/Rollout Notes

### Risks

1. **Backwards Compatibility**
   - Old Myster clients won't understand transaction code 102
   - **Mitigation**: Keep transaction 101 (one-way) for old clients
   - New clients can fall back to one-way if bidirectional fails
   - Server responds with `TRANSACTION_TYPE_UNKNOWN` for unknown codes

2. **Privacy Concerns**
   - Bidirectional exchange reveals our server identity to any server we query
   - **Mitigation**: This is intentional - we're advertising our server
   - Only sent to servers we're already connecting to
   - Same privacy profile as the one-way business card approach

3. **DoS Attack Surface**
   - Malicious clients could spam bidirectional exchanges to fill up server pool
   - **Mitigation**: `MysterServerPool` already has mechanisms to track and limit servers
   - Bidirectional exchanges trigger same validation as `suggestAddress()`
   - Same attack surface as one-way business card approach

4. **Request Payload Size**
   - Bidirectional exchange has larger request payload (contains our stats)
   - Could hit UDP packet size limits on networks with small MTU
   - **Mitigation**: Server stats are typically small (few hundred bytes)
   - Well under typical MTU of 1500 bytes
   - Graceful degradation: send minimal stats if full stats too large

### Edge Cases

1. **File Manager Not Initialized**
   - Client may send incomplete file listings in request
   - Send minimal stats with just identity and port

2. **No Server Identity**
   - Client has no cryptographic identity yet
   - Send anonymous stats with just port

3. **Port Extraction Failure**
   - Client's stats MessagePak missing PORT field
   - Server falls back to using source address from datagram packet

4. **Network Timeout**
   - Bidirectional exchange UDP packet lost in transit
   - Acceptable - it's a best-effort optimization
   - Next refresh will try again

5. **Asymmetric Support**
   - Client supports bidirectional (102), server only supports one-way (101)
   - Client could implement fallback to one-way
   - Future enhancement: try 102 first, fall back to 101 on TRANSACTION_TYPE_UNKNOWN

### Rollout Notes

1. **Phased Rollout**
   - Deploy server-side first (can receive bidirectional exchanges)
   - Then deploy client-side (can send bidirectional exchanges)
   - Both are backwards compatible with transaction 101

2. **Monitoring**
   - Monitor logs for bidirectional exchange events
   - Track ratio of bidirectional (102) vs one-way (101) transactions
   - Watch for any unexpected errors or exceptions
   - Monitor request/response payload sizes

3. **Feature Flag** (optional future enhancement)
   - Could add preference to enable/disable bidirectional exchange
   - Default to bidirectional, fall back to one-way if disabled

4. **Performance Impact**
   - Minimal: Same number of round-trips as one-way (1)
   - Slightly larger request payload (our stats instead of empty)
   - Same response payload size
   - Net benefit: Eliminates need for separate one-way business card transaction

5. **Fallback Strategy** (future enhancement)
   - Client could try bidirectional (102) first
   - If server responds with TRANSACTION_TYPE_UNKNOWN, fall back to one-way (101)
   - Requires retry logic in client

## Assumptions

1. **UDP Reliability**: Bidirectional exchange delivery is best-effort, loss is acceptable
2. **Pool Integration**: `MysterServerPool.suggestAddress()` is the correct integration point on server side
3. **Server Stats Reusability**: `ServerStats.getServerStatsMessagePack()` can be reused for both request and response
4. **Transaction Protocol**: Existing transaction infrastructure handles new transaction codes automatically
5. **Identity Availability**: Identity and file manager are available when exchange occurs (or graceful degradation is acceptable)
6. **Port Correctness**: The port in `ServerStats` MessagePak accurately reflects the server's listening port
7. **Synchronization**: `MysterServerPoolImpl` is already thread-safe for concurrent updates
8. **Packet Size**: Server stats MessagePak fits comfortably in a UDP packet (typically < 1500 bytes)
9. **Backwards Compatibility**: Old clients will continue to use transaction 101, new clients will use 102

## Open Questions

None - the bidirectional exchange design is clear and elegant!

## Implementation Notes

### Why Bidirectional is Better Than One-Way Business Card

The original plan had a separate one-way "business card" transaction where the client pushed its stats without getting anything back. The bidirectional approach is superior because:

1. **Fewer Transactions**: One round-trip instead of two (business card + get stats)
2. **Atomic Exchange**: Both parties learn about each other atomically
3. **Simpler Logic**: Client gets same return value as before (remote stats), just sends more data
4. **Same Performance**: One UDP round-trip in both cases
5. **More Symmetric**: Both parties have same responsibilities (send stats, receive stats)
6. **Backwards Compatible**: Old one-way transaction (101) remains for old clients

### Migration Path

1. **Old Client → Old Server**: Uses transaction 101 (one-way)
2. **New Client → Old Server**: Client tries 102, server returns TRANSACTION_TYPE_UNKNOWN, client could fall back to 101 (future enhancement)
3. **Old Client → New Server**: Client uses 101, server responds normally
4. **New Client → New Server**: Uses transaction 102 (bidirectional), both learn about each other!
