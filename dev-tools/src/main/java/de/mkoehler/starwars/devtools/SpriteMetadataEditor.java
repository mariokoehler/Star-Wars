package de.mkoehler.starwars.devtools;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;

/**
 * Dev-only Swing tool (never shipped in the game, see {@code dev-tools}'
 * module Javadoc) for visually authoring a ship's collision hitbox polygon
 * and named attachment points (design.md 2.4/4.3) against its sprite,
 * instead of guessing pixel coordinates in code.
 */
public class SpriteMetadataEditor extends JFrame {

    private final SpriteCanvas canvas;
    private final JLabel statusLabel;

    /**
     * Launches the editor.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SpriteMetadataEditor().setVisible(true));
    }

    /**
     * Creates the editor window.
     */
    public SpriteMetadataEditor() {
        super("Star Wars - Sprite Metadata Editor");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        statusLabel = new JLabel(" ");
        canvas = new SpriteCanvas(this::updateStatus);

        setJMenuBar(buildMenuBar());
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(new JScrollPane(canvas), BorderLayout.CENTER);
        getContentPane().add(statusLabel, BorderLayout.SOUTH);

        setSize(900, 700);
        setLocationRelativeTo(null);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");

        JMenuItem openItem = new JMenuItem("Open Image...");
        openItem.addActionListener(e -> openImage());
        fileMenu.add(openItem);

        JMenuItem saveItem = new JMenuItem("Save Metadata");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveItem.addActionListener(e -> canvas.saveMetadata(this));
        fileMenu.add(saveItem);

        JMenuItem reloadItem = new JMenuItem("Reload Metadata");
        reloadItem.addActionListener(e -> canvas.reloadMetadata(this));
        fileMenu.add(reloadItem);

        menuBar.add(fileMenu);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem controlsItem = new JMenuItem("Controls");
        controlsItem.addActionListener(e -> showControlsHelp());
        helpMenu.add(controlsItem);
        menuBar.add(helpMenu);

        return menuBar;
    }

    private void openImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG images", "png"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                canvas.loadImage(chooser.getSelectedFile());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to load image: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showControlsHelp() {
        JOptionPane.showMessageDialog(this,
            "Left click: add a hitbox point\n"
                + "Right click: remove the last hitbox point\n"
                + "Enter: place a named attachment point at the mouse position\n"
                + "Delete/Backspace: remove the attachment point nearest the mouse\n"
                + "Ctrl+S: save metadata",
            "Controls", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateStatus(String status) {
        statusLabel.setText(" " + status);
    }
}
