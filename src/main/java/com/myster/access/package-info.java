/**
 * Provides access control for Myster types via cryptographic, blockchain-style access lists.
 *
 * <p>Every Myster type (public or private) has an associated access list — an append-only,
 * signed chain of blocks that tracks membership, permissions, policies, and type metadata.
 * The distinction between "public" and "private" types is purely a policy setting: public
 * types allow anyone to discover and access files, while private types restrict access to
 * listed members.
 *
 * <p>Types are identified by {@link com.myster.type.MysterType} (MD5 shortBytes of the type's
 * RSA public key). The full public key is stored in the genesis block via a
 * {@code SET_TYPE_PUBLIC_KEY} operation, allowing remote nodes to resolve shortBytes to the
 * full key by downloading the access list.
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link com.myster.access.AccessList} - Main class for managing a single access list chain</li>
 *   <li>{@link com.myster.access.AccessBlock} - Individual block in the blockchain</li>
 *   <li>{@link com.myster.access.AccessListState} - Derived state from applying all operations</li>
 *   <li>{@link com.myster.access.BlockOperation} - Operations that can be appended to the chain</li>
 * </ul>
 *
 * <h2>Security Model</h2>
 * <p>Access lists use Ed25519 signatures to ensure only authorized writers can append blocks.
 * Each block references the previous block's hash, forming an append-only log. The genesis
 * block establishes initial writers, members, policies, and the type's public key.
 *
 * <h2>Extensible Operations</h2>
 * <p>Operations use string-based type identifiers for forward compatibility. Unknown operation
 * types from future versions are preserved in the chain as non-canonical operations — the node
 * can't interpret their effect but the chain remains valid.
 *
 * <h2>Roles</h2>
 * <ul>
 *   <li><b>MEMBER</b> - Can access files of this type</li>
 *   <li><b>ADMIN</b> - Can access files AND append blocks to modify membership</li>
 * </ul>
 *
 * @see com.myster.type.MysterType
 * @see com.myster.type.CustomTypeDefinition
 */
package com.myster.access;

