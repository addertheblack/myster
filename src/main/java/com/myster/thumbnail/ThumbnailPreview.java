package com.myster.thumbnail;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatLightLaf;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;

/**
 * Standalone JPEG/AVI/MKV/MP4 thumbnail timing grid; run main without Myster's network services.
 * This code is part of a test application to show off the awesomeness that is our thumbnail icon loader.
 */
public final class ThumbnailPreview extends JPanel {
    private static final Logger log = Logger.getLogger(ThumbnailPreview.class.getName());
    private final JTextField folderField = new JTextField(30);
    private final JSpinner sizeField = new JSpinner(new SpinnerNumberModel(64, 1, 1024, 1));
    private final DefaultListModel<Cell> model = new DefaultListModel<>();
    private final JList<Cell> grid = new JList<>(model);
    private final JLabel status = new JLabel("Choose a folder to load JPEG, AVI, MKV or MP4 thumbnails.");
    private final List<PromiseFuture<?>> requests = new ArrayList<>();
    private Path folder;
    private int generation;
    private int nextIndex;
    private int completed;
    private int available;
    private int thumbnailSize;
    private long started;

    private ThumbnailPreview() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        folderField.setEditable(false);
        JButton choose = new JButton("Choose folder…");
        choose.addActionListener(_ -> chooseFolder());
        JButton load = new JButton("Load / reload");
        load.addActionListener(_ -> loadFolder());
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING));
        controls.add(choose);
        controls.add(folderField);
        controls.add(new JLabel("Size (pixels):"));
        controls.add(sizeField);
        controls.add(load);
        add(controls, BorderLayout.NORTH);
        grid.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        grid.setVisibleRowCount(-1);
        grid.setCellRenderer(new CellRenderer());
        add(new JScrollPane(grid), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a folder containing JPEG, AVI, MKV or MP4 files");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (folder != null) {
            chooser.setCurrentDirectory(folder.toFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            folder = chooser.getSelectedFile().toPath();
            folderField.setText(folder.toString());
            loadFolder();
        }
    }

    private void cancelLoads() {
        generation++;
        for (PromiseFuture<?> request : List.copyOf(requests)) {
            request.cancel();
        }
        requests.clear();
    }

    private void loadFolder() {
        if (folder == null) {
            status.setText("Choose a folder first.");
            return;
        }
        try {
            sizeField.commitEdit();
        } catch (ParseException e) {
            status.setText("Enter a thumbnail size from 1 to 1024 pixels.");
            return;
        }
        cancelLoads();
        int run = generation;
        Path selectedFolder = folder;
        thumbnailSize = (int) sizeField.getValue();
        grid.setFixedCellWidth(Math.max(140, thumbnailSize + 24));
        grid.setFixedCellHeight(thumbnailSize + 64);
        model.clear();
        nextIndex = 0;
        completed = 0;
        available = 0;
        started = System.nanoTime();
        status.setText("Scanning " + selectedFolder + "…");
        PromiseFuture<List<Path>> scan = PromiseFutures.execute(() -> {
            try (var files = Files.list(selectedFolder)) {
                return files.filter(ThumbnailPreview::isAcceptedFile).filter(Files::isRegularFile)
                        .sorted().toList();
            }
        }).useEdt();
        requests.add(scan);
        scan.addResultListener(files -> {
            if (run != generation) {
                return;
            }
            model.addAll(files.stream().map(file -> new Cell(file, null, "Waiting")).toList());
            updateStatus();
        }).addExceptionListener(error -> {
            if (run == generation) {
                log.log(Level.WARNING, "Cannot scan " + selectedFolder, error);
                status.setText("Cannot scan folder: " + error.getMessage());
            }
        }).addFinallyListener(() -> {
            if (run == generation) {
                requests.remove(scan);
                pump(run);
            }
        });
    }

    private static boolean isAcceptedFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".avi") || name.endsWith(".mkv") || name.endsWith(".mp4");
    }

    private void pump(int run) {
        while (run == generation && requests.size() < 4 && nextIndex < model.size()) {
            int index = nextIndex++;
            Path file = model.get(index).file();
            model.set(index, new Cell(file, null, "Loading…"));
            long requestStarted = System.nanoTime();
            PromiseFuture<BufferedImage> request = Thumbnails.summonThumbnailAsync(file, thumbnailSize).useEdt();
            requests.add(request);
            request.addResultListener(image -> {
                if (run != generation) {
                    return;
                }
                long millis = (System.nanoTime() - requestStarted) / 1_000_000;
                model.set(index, new Cell(file, image,
                        image == null ? "Unavailable (" + millis + " ms)" : millis + " ms"));
                if (image != null) {
                    available++;
                }
            }).addExceptionListener(error -> {
                if (run == generation) {
                    model.set(index, new Cell(file, null, "Failed"));
                    log.log(Level.WARNING, "Thumbnail task failed for " + file, error);
                }
            }).addFinallyListener(() -> {
                if (run == generation) {
                    requests.remove(request);
                    completed++;
                    updateStatus();
                    pump(run);
                }
            });
        }
    }

    private void updateStatus() {
        if (model.isEmpty()) {
            status.setText("No .jpg, .jpeg, .avi, .mkv or .mp4 files in this folder.");
            return;
        }
        status.setText(completed + " / " + model.size() + " finished • " + available
                + " thumbnails • " + (completed - available) + " unavailable • "
                + (System.nanoTime() - started) / 1_000_000 + " ms elapsed");
    }

    /** Optional arguments: folder path and maximum thumbnail dimension (default 64). */
    public static void main(String[] args) {
        Path initialFolder = args.length > 0 ? Path.of(args[0]) : null;
        int initialSize = args.length > 1 ? Integer.parseInt(args[1]) : 64;
        if (initialSize < 1 || initialSize > 1024) {
            throw new IllegalArgumentException("Thumbnail size must be between 1 and 1024 pixels");
        }
        Logger.getLogger("com.myster.thumbnail").setLevel(Level.INFO);
        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();
            ThumbnailPreview preview = new ThumbnailPreview();
            JFrame frame = new JFrame("Myster Thumbnail Preview");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    preview.cancelLoads();
                }
            });
            frame.setContentPane(preview);
            frame.setSize(new Dimension(1050, 720));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            preview.sizeField.setValue(initialSize);
            if (initialFolder != null) {
                preview.folder = initialFolder;
                preview.folderField.setText(initialFolder.toString());
                preview.loadFolder();
            }
        });
    }

    private record Cell(Path file, BufferedImage image, String detail) {}

    private static final class CellRenderer extends JPanel implements ListCellRenderer<Cell> {
        private final JLabel image = new JLabel("", SwingConstants.CENTER);
        private final JLabel name = new JLabel("", SwingConstants.CENTER);
        private final JLabel detail = new JLabel("", SwingConstants.CENTER);

        private CellRenderer() {
            super(new BorderLayout(4, 4));
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            name.putClientProperty("html.disable", true);
            JPanel footer = new JPanel(new GridLayout(2, 1));
            footer.setOpaque(false);
            footer.add(name);
            footer.add(detail);
            add(image, BorderLayout.CENTER);
            add(footer, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Cell> list, Cell cell,
                                                       int index, boolean selected, boolean focused) {
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            for (JLabel label : List.of(image, name, detail)) {
                label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            }
            image.setIcon(cell.image() == null ? null : new ImageIcon(cell.image()));
            image.setText(cell.image() == null ? "—" : "");
            name.setText(cell.file().getFileName().toString());
            detail.setText(cell.detail());
            setToolTipText(cell.file().toString());
            return this;
        }
    }
}
