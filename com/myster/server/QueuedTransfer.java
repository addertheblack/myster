
package com.myster.server;

import java.io.IOException;

public interface QueuedTransfer {
		public void refresh(int i) throws IOException ;
		public void disconnect() ;
		public void startDownload() ;
		public boolean isDone() ;
}