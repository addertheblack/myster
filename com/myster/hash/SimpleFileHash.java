package com.myster.hash;

import java.io.Serializable;

public class SimpleFileHash extends FileHash implements Serializable {
	private byte[] hash;
	private String hashName;
	
	private SimpleFileHash(){}
	
	protected SimpleFileHash(String hashName, byte[] hash) {
		this.hashName = hashName;
		this.hash = (byte[])hash.clone();
	}
	
	public byte[] getBytes() {
		return (byte[])hash.clone();
	}
	
	public short getHashLength() {
		return (short)hash.length;
	}
	
	public String getHashName() {
		return hashName;
	}
	
	public String toString() {
		return asHex(hash);
	}
	
	public boolean equals(Object o) {
		SimpleFileHash otherHash = (SimpleFileHash)o;
		
		if (otherHash.hash.length != hash.length) return false;
		
		for (int i = 0; i<hash.length; i++) {
			if (otherHash.hash[i] != hash[i]) {
				System.out.println("Hash length compare "+hash[i]+" "+otherHash.hash[i]+" "+i);
				return false;
			}
		}
		
		return true;
	}
	
	public static String asHex (byte hash[]) {
	    StringBuffer buf = new StringBuffer(hash.length * 2);
	    int i;

	    for (i = 0; i < hash.length; i++) {
	      if (((int) hash[i] & 0xff) < 0x10) 
		buf.append("0");
		  
	      buf.append(Long.toString((int) hash[i] & 0xff, 16));
	      
	      //if (i != hash.length -1) buf.append(":");
	    }

	    return buf.toString();
  	}
  	
  	public static FileHash buildFileHash(String hashName, byte[] hash) {
  		return new SimpleFileHash(hashName, hash);
  	}
  	
  	public static FileHash buildFromHexString(String hashName, String hexString) {
  		SimpleFileHash hash1 = new SimpleFileHash(hashName, fromHexString(hexString));
  		SimpleFileHash hash2 = new SimpleFileHash(hashName, com.general.util.Util.fromHexString(hexString));
  		
  		System.out.println(""+hash1+" "+hash2);
  		
  		return hash2;
  	}
  	
  	/**by Roedy Green ©1996-2003 Canadian Mind Products
	* Convert a hex string to a byte array.
	* Permits upper or lower case hex.
	*
	* @param s String must have even number of characters.
	* and be formed only of digits 0-9 A-F or
	* a-f. No spaces, minus or plus signs.
	* @return corresponding byte array.
	*/
	public static byte[] fromHexString ( String s ) {
		int stringLength = s.length() ;
		
		if ( (stringLength & 0x1) != 0 ) {
			throw new IllegalArgumentException ( "fromHexString requires an even number of hex characters" );
		}
		
		byte[] b = new byte[ stringLength / 2 ];

		for ( int i=0 ,j= 0; i< stringLength; i+= 2,j ++ ) {
			int high= charToNibble(s.charAt ( i ));
			int low = charToNibble( s.charAt ( i+1 ) );
			b[ j ] = (byte ) ( ( high << 4 ) | low );
		}
		return b;
	}


	/**by Roedy Green ©1996-2003 Canadian Mind Products
	* convert a single char to corresponding nibble.
	*
	* @param c char to convert. must be 0-9 a-f A-F, no
	* spaces, plus or minus signs.
	*
	* @return corresponding integer
	*/
	private static int charToNibble ( char c ) {
		if ( '0' <= c && c <= '9' ) {
			return c - '0' ;
		} else if ( 'a' <= c && c <= 'f' ) {
			return c - 'a' + 0xa ;
		} else if ( 'A' <= c && c <= 'F' ) {
			return c - 'A' + 0xa ;
		} else {
			throw new IllegalArgumentException ( "Invalid hex character: " + c ) ;
		}
	} 
}


