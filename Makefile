DESTINATION=classes
JAVAC=javac 

SRCFILES = \
	./Myster.java \
	./com/general/events/EventDispatcher.java \
	./com/general/events/EventListener.java \
	./com/general/events/GenericEvent.java \
	./com/general/events/SemiAsyncEventDispatcher.java \
	./com/general/events/SyncEventDispatcher.java \
	./com/general/mclist/DefaultMCRowTheme.java \
	./com/general/mclist/GenericMCListItem.java \
	./com/general/mclist/MCList.java \
	./com/general/mclist/MCListEvent.java \
	./com/general/mclist/MCListEventAdapter.java \
	./com/general/mclist/MCListEventHandler.java \
	./com/general/mclist/MCListEventListener.java \
	./com/general/mclist/MCListHeader.java \
	./com/general/mclist/MCListHeaderEventHandler.java \
	./com/general/mclist/MCListItemInterface.java \
	./com/general/mclist/MCListVector.java \
	./com/general/mclist/MCRowThemeInterface.java \
	./com/general/mclist/RowStats.java \
	./com/general/mclist/Sortable.java \
	./com/general/mclist/SortableBoolean.java \
	./com/general/mclist/SortableByte.java \
	./com/general/mclist/SortableLong.java \
	./com/general/mclist/SortableString.java \
	./com/general/mclist/TestMCListEventer.java \
	./com/general/net/AsyncDatagramListener.java \
	./com/general/net/AsyncDatagramSocket.java \
	./com/general/net/ImmutableDatagramPacket.java \
	./com/general/tab/Tab.java \
	./com/general/tab/TabEvent.java \
	./com/general/tab/TabInterface.java \
	./com/general/tab/TabListener.java \
	./com/general/tab/TabPanel.java \
	./com/general/tab/TabUtilities.java \
	./com/general/util/AbstractHeap.java \
	./com/general/util/AnswerDialog.java \
	./com/general/util/AskDialog.java \
	./com/general/util/BlockingQueue.java \
	./com/general/util/Channel.java \
	./com/general/util/Comparable.java \
	./com/general/util/Comparator.java \
	./com/general/util/DoubleBlockingQueue.java \
	./com/general/util/GridBagWindow.java \
	./com/general/util/Heap.java \
	./com/general/util/KeyValue.java \
	./com/general/util/LinkedList.java \
	./com/general/util/MaxHeap.java \
	./com/general/util/MessageField.java \
	./com/general/util/MinHeap.java \
	./com/general/util/MrWrap.java \
	./com/general/util/RInt.java \
	./com/general/util/SafeThread.java \
	./com/general/util/Semaphore.java \
	./com/general/util/StandardWindowBehavior.java \
	./com/general/util/TextSpinner.java \
	./com/general/util/Timer.java \
	./com/general/util/UnexpectedError.java \
	./com/general/util/UnexpectedInterrupt.java \
	./com/general/util/Util.java \
	./com/myster/bandwidth/BandwidthManager.java \
	./com/myster/bandwidth/ThrottledInputStream.java \
	./com/myster/bandwidth/ThrottledOutputStream.java \
	./com/myster/client/datagram/PingEvent.java \
	./com/myster/client/datagram/PingEventListener.java \
	./com/myster/client/datagram/PongTransport.java \
	./com/myster/client/datagram/UDPPingClient.java \
	./com/myster/client/stream/DownloaderThread.java \
	./com/myster/client/stream/ProtocolException.java \
	./com/myster/client/stream/StandardSuite.java \
	./com/myster/client/stream/TCPSocket.java \
	./com/myster/client/stream/UnknownProtocolException.java \
	./com/myster/client/ui/ClientWindow.java \
	./com/myster/client/ui/ConnectButtonEvent.java \
	./com/myster/client/ui/FileInfoListerThread.java \
	./com/myster/client/ui/FileInfoPane.java \
	./com/myster/client/ui/FileList.java \
	./com/myster/client/ui/FileListAction.java \
	./com/myster/client/ui/FileListerThread.java \
	./com/myster/client/ui/FileStatsAction.java \
	./com/myster/client/ui/FileTypeSelectListener.java \
	./com/myster/client/ui/TypeListerThread.java \
	./com/myster/filemanager/FileFilter.java \
	./com/myster/filemanager/FileTypeList.java \
	./com/myster/filemanager/FileTypeListManager.java \
	./com/myster/filemanager/ui/FMIChooser.java \
	./com/myster/menubar/MysterMenuBar.java \
	./com/myster/menubar/MysterMenuBarFactory.java \
	./com/myster/menubar/MysterMenuFactory.java \
	./com/myster/menubar/MysterMenuItemFactory.java \
	./com/myster/menubar/event/AddIPMenuAction.java \
	./com/myster/menubar/event/CloseWindowAction.java \
	./com/myster/menubar/event/MenuBarEvent.java \
	./com/myster/menubar/event/MenuBarListener.java \
	./com/myster/menubar/event/NewClientWindowAction.java \
	./com/myster/menubar/event/NewSearchWindowAction.java \
	./com/myster/menubar/event/NullAction.java \
	./com/myster/menubar/event/PreferencesAction.java \
	./com/myster/menubar/event/QuitMenuAction.java \
	./com/myster/menubar/event/StatsWindowAction.java \
	./com/myster/menubar/event/TrackerWindowAction.java \
	./com/myster/mml/BranchAsALeafException.java \
	./com/myster/mml/DoubleSlashException.java \
	./com/myster/mml/InvalidTokenException.java \
	./com/myster/mml/LeafAsABranchException.java \
	./com/myster/mml/MML.java \
	./com/myster/mml/MMLException.java \
	./com/myster/mml/MMLPathException.java \
	./com/myster/mml/MMLRuntimeException.java \
	./com/myster/mml/NoStartingSlashException.java \
	./com/myster/mml/NodeAlreadyExistsException.java \
	./com/myster/mml/NonExistantPathException.java \
	./com/myster/mml/NotABranchException.java \
	./com/myster/mml/NotALeafException.java \
	./com/myster/mml/NullValueException.java \
	./com/myster/mml/RobustMML.java \
	./com/myster/net/BadPacketException.java \
	./com/myster/net/DatagramProtocolManager.java \
	./com/myster/net/DatagramSender.java \
	./com/myster/net/DatagramTransport.java \
	./com/myster/net/MysterAddress.java \
	./com/myster/net/MysterSocket.java \
	./com/myster/net/MysterSocketFactory.java \
	./com/myster/net/NotAPingPacketException.java \
	./com/myster/net/NotAPongPacketException.java \
	./com/myster/net/PingPacket.java \
	./com/myster/net/PingPongPacket.java \
	./com/myster/net/PongPacket.java \
	./com/myster/plugin/JarClassLoader.java \
	./com/myster/plugin/MysterPlugin.java \
	./com/myster/plugin/PluginLoader.java \
	./com/myster/pref/Preferences.java \
	./com/myster/pref/ui/PreferencesDialogBox.java \
	./com/myster/pref/ui/PreferencesPanel.java \
	./com/myster/search/CrawlerThread.java \
	./com/myster/search/FileInfoGetter.java \
	./com/myster/search/GroupInt.java \
	./com/myster/search/IPQueue.java \
	./com/myster/search/MysterFileStub.java \
	./com/myster/search/MysterSearch.java \
	./com/myster/search/MysterSearchResult.java \
	./com/myster/search/SearchEngine.java \
	./com/myster/search/SearchResult.java \
	./com/myster/search/SearchResultListener.java \
	./com/myster/search/ui/ClientGenericHandleObject.java \
	./com/myster/search/ui/ClientHandleObject.java \
	./com/myster/search/ui/ClientInfoFactoryUtilities.java \
	./com/myster/search/ui/ClientMPG3HandleObject.java \
	./com/myster/search/ui/SearchButtonEvent.java \
	./com/myster/search/ui/SearchWindow.java \
	./com/myster/search/ui/SortableBit.java \
	./com/myster/search/ui/SortableHz.java \
	./com/myster/search/ui/SortablePing.java \
	./com/myster/server/ConnectionContext.java \
	./com/myster/server/ConnectionManager.java \
	./com/myster/server/ConnectionSection.java \
	./com/myster/server/DownloadInfo.java \
	./com/myster/server/DownloadQueue.java \
	./com/myster/server/Operator.java \
	./com/myster/server/QueuedTransfer.java \
	./com/myster/server/ServerFacade.java \
	./com/myster/server/datagram/PingTransport.java \
	./com/myster/server/datagram/UDPOperator.java \
	./com/myster/server/event/ConnectionManagerAdapter.java \
	./com/myster/server/event/ConnectionManagerEvent.java \
	./com/myster/server/event/ConnectionManagerListener.java \
	./com/myster/server/event/OperatorEvent.java \
	./com/myster/server/event/OperatorListener.java \
	./com/myster/server/event/ServerDownloadDispatcher.java \
	./com/myster/server/event/ServerDownloadEvent.java \
	./com/myster/server/event/ServerDownloadListener.java \
	./com/myster/server/event/ServerEvent.java \
	./com/myster/server/event/ServerEventManager.java \
	./com/myster/server/event/ServerSearchDispatcher.java \
	./com/myster/server/event/ServerSearchEvent.java \
	./com/myster/server/event/ServerSearchListener.java \
	./com/myster/server/stream/FileInfoLister.java \
	./com/myster/server/stream/FileSenderThread.java \
	./com/myster/server/stream/FileTypeLister.java \
	./com/myster/server/stream/HandshakeThread.java \
	./com/myster/server/stream/IPLister.java \
	./com/myster/server/stream/RequestDirThread.java \
	./com/myster/server/stream/RequestSearchThread.java \
	./com/myster/server/stream/ServerThread.java \
	./com/myster/server/ui/CountLabel.java \
	./com/myster/server/ui/DownloadInfoPanel.java \
	./com/myster/server/ui/DownloadMCListItem.java \
	./com/myster/server/ui/GraphInfoPanel.java \
	./com/myster/server/ui/ServerStatsWindow.java \
	./com/myster/server/ui/StatsInfoPanel.java \
	./com/myster/server/ui/XItemList.java \
	./com/myster/tracker/DeadIPCache.java \
	./com/myster/tracker/IPList.java \
	./com/myster/tracker/IPListManager.java \
	./com/myster/tracker/IPListManagerSingleton.java \
	./com/myster/tracker/MysterIP.java \
	./com/myster/tracker/MysterIPPool.java \
	./com/myster/tracker/MysterServer.java \
	./com/myster/tracker/ui/AddIPDialog.java \
	./com/myster/tracker/ui/TrackerWindow.java \
	./com/myster/ui/MysterFrame.java \
	./com/myster/ui/WindowManager.java \
	./com/myster/util/MP3Header.java \
	./com/myster/util/MysterThread.java \
	./com/myster/util/OpenConnectionHandler.java \
	./com/myster/util/ProgressWindow.java \
	./com/myster/util/ProgressWindowClose.java \
	./com/myster/util/Sayable.java \
	./com/myster/util/TypeChoice.java \
	./com/myster/util/TypeDescription.java

LIBRARIES = lib \
	lib/collections.jar \
	lib/hellikerID3v2.jar

EXTRAFILES = ./com/myster/server/stream/firewall.gif \
	./com/myster/server/stream/queued.gif \
	./com/general/tab/tab_background.jpg \
	./com/general/tab/left.gif \
	./com/general/tab/graphs.gif \
	./com/general/tab/middle.gif \
	./com/general/tab/outbound.gif \
	./com/general/tab/right.gif \
	./com/general/tab/serverstats.gif \
	./typedescriptionlist.txt \
	./mysterprefs.mml \
	./com/properties/Myster.properties \
	./com/properties/Myster_ja.properties

#######

.PHONY : all clean test

CLASSFILES = $(patsubst %.java,$(DESTINATION)/%.class,$(SRCFILES))

empty:=
space:= $(empty) $(empty)
LIBRARYPATHS = ${subst $(space),:,${LIBRARIES}}

all : ${DESTINATION} $(CLASSFILES)

clean : 
	\rm $(CLASSFILES); find $(DESTINATION) -name \*.class -exec rm {} \;


$(DESTINATION)/%.class : %.java
	export CLASSPATH=${DESTINATION}:${LIBRARYPATHS}:${CLASSPATH}; $(JAVAC) -d $(DESTINATION) $<

test: all
	(cd ${DESTINATION}; export CLASSPATH=${LIBRARYPATHS}:.; java Myster);


${DESTINATION}:
	mkdir -p ${DESTINATION}
	for f in ${EXTRAFILES} ${LIBRARIES}; \
		do mkdir -p ${DESTINATION}/`dirname $$f`; \
		   cp $$f ${DESTINATION}/$$f; \
	done


