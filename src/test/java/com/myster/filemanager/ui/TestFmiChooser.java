package com.myster.filemanager.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.awt.Component;
import java.awt.Container;
import java.util.Optional;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import com.myster.filemanager.FileItem;
import com.myster.filemanager.FileTypeList;
import com.myster.filemanager.FileTypeListManager;
import com.myster.link.MysterLinkService;
import com.myster.type.*;

class TestFmiChooser {
    private <T extends Component> T find(Container root, Class<T> type) {
        for(Component child:root.getComponents()) {
            if(type.isInstance(child)) return type.cast(child);
            if(child instanceof Container container) {
                T found=find(container,type);
                if(found!=null) return found;
            }
        }
        return null;
    }

    @Test void onlySingleSavedRealRowsAreEligibleForCopy() throws Exception {
        var type=new MysterType(new byte[16]);
        var description=new TypeDescription(type,"test","Test",new String[]{},false,true);
        var descriptions=mock(TypeDescriptionList.class);
        when(descriptions.getEnabledTypes()).thenReturn(new TypeDescription[]{description});
        when(descriptions.get(type)).thenReturn(Optional.of(description));
        var manager=mock(FileTypeListManager.class);
        when(manager.getPathFromType(type)).thenReturn("/tmp");
        when(manager.isShared(type)).thenReturn(true);
        var index=mock(FileTypeList.class);
        when(manager.getFileTypeList(type)).thenReturn(index);
        when(manager.getDirList(type)).thenReturn(new String[]{"file","second"});
        when(manager.getFileItem(eq(type),anyString())).thenReturn(mock(FileItem.class));
        var links=mock(MysterLinkService.class);
        SwingUtilities.invokeAndWait(() -> {
            var panel=new FmiChooser(manager,descriptions,links);
            @SuppressWarnings("unchecked") JList<String> rows=(JList<String>)find(panel,JList.class);
            assertNotNull(rows);
            assertTrue(panel.selectedLinkFile().isEmpty());
            rows.setSelectedIndex(0);
            assertEquals("file",panel.selectedLinkFile().orElseThrow().name());
            rows.setSelectionInterval(0,1);
            assertTrue(panel.selectedLinkFile().isEmpty());
            rows.setSelectedIndex(0);
            when(index.isIndexing()).thenReturn(true);
            assertTrue(panel.selectedLinkFile().isEmpty());
            when(index.isIndexing()).thenReturn(false);
            JCheckBox sharing=find(panel,JCheckBox.class);
            sharing.doClick();
            assertTrue(panel.selectedLinkFile().isEmpty());
            sharing.doClick();
            assertTrue(panel.selectedLinkFile().isPresent());
            when(manager.getDirList(type)).thenReturn(new String[351]);
            assertTrue(panel.selectedLinkFile().isEmpty());
            when(manager.getDirList(type)).thenReturn(new String[]{"file"});
            rows.setListData(new String[]{"<Indexing files...>"}); rows.setSelectedIndex(0);
            assertTrue(panel.selectedLinkFile().isEmpty());
            verifyNoInteractions(links);
        });
    }
}
