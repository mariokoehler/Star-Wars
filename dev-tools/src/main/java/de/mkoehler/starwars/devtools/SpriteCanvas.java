package de.mkoehler.starwars.devtools;

import de.mkoehler.starwars.sim.metadata.PixelPoint;
import de.mkoehler.starwars.sim.metadata.ShipSpriteMetadata;
import de.mkoehler.starwars.sim.metadata.ShipSpriteMetadataLoader;

import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The editing surface: loads a ship sprite, draws it zoomed with a
 * transparency checkerboard behind it, and lets the user place a hitbox
 * polygon and named attachment points on it (design.md 2.4/4.3).
 * <p>
 * Controls:
 * <ul>
 *   <li>Left click — add a hitbox polygon point</li>
 *   <li>Right click — remove the last hitbox polygon point</li>
 *   <li>Enter — place a named attachment point at the current mouse position</li>
 *   <li>Delete/Backspace — remove the attachment point nearest the mouse</li>
 * </ul>
 */
class SpriteCanvas extends JPanel {

    private static final int ZOOM = 4;
    private static final int CHECKER_SIZE = 8;
    /** Box2D's {@code PolygonShape} vertex limit — the hitbox can't have more points than this. */
    private static final int MAX_HITBOX_POINTS = 8;
    private static final int ATTACHMENT_PICK_RADIUS_PIXELS = 12;

    private static final Color CHECKER_LIGHT = new Color(90, 90, 90);
    private static final Color CHECKER_DARK = new Color(60, 60, 60);
    private static final Color HITBOX_COLOR = new Color(255, 90, 90);
    private static final Color ATTACHMENT_COLOR = new Color(90, 220, 255);

    private static final String[] SUGGESTED_ATTACHMENT_NAMES = {"PROJECTILE", "ENGINE", "LIGHT_RED", "LIGHT_GREEN", "DAMAGE_SMOKE", "TURRET"};

    private final Consumer<String> statusListener;

    private BufferedImage image;
    private File imageFile;
    private File metadataFile;
    private ShipSpriteMetadata metadata = new ShipSpriteMetadata();

    private int mouseImageX;
    private int mouseImageY;

    /**
     * Creates the canvas.
     *
     * @param statusListener called with a human-readable status string whenever
     *                       something worth reporting changes (image/metadata
     *                       loaded, points added/removed)
     */
    SpriteCanvas(Consumer<String> statusListener) {
        this.statusListener = statusListener;
        setBackground(Color.DARK_GRAY);
        setFocusable(true);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (image == null) {
                    return;
                }
                if (SwingUtilities.isLeftMouseButton(e)) {
                    addHitboxPoint();
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    removeLastHitboxPoint();
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseImageX = e.getX() / ZOOM;
                mouseImageY = e.getY() / ZOOM;
                repaint();
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (image == null) {
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    placeAttachmentPoint();
                } else if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    removeNearestAttachmentPoint();
                }
            }
        });

        reportStatus();
    }

    /**
     * Loads a sprite image, and its metadata file if one already exists
     * alongside it (same directory, same base filename with a
     * {@code .meta.json} extension) — otherwise starts with empty metadata.
     *
     * @param file the image file to load
     * @throws IOException if the file isn't a readable image
     */
    void loadImage(File file) throws IOException {
        BufferedImage loaded = ImageIO.read(file);
        if (loaded == null) {
            throw new IOException("Not a readable image: " + file);
        }
        image = loaded;
        imageFile = file;
        metadata = new ShipSpriteMetadata();

        File defaultMetadataFile = deriveMetadataFile(file);
        metadataFile = defaultMetadataFile;
        if (defaultMetadataFile.exists()) {
            try {
                metadata = ShipSpriteMetadataLoader.loadFromFile(defaultMetadataFile);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Found " + defaultMetadataFile.getName()
                    + " but couldn't read it: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        setPreferredSize(new Dimension(image.getWidth() * ZOOM, image.getHeight() * ZOOM));
        revalidate();
        repaint();
        requestFocusInWindow();
        reportStatus();
    }

    private static File deriveMetadataFile(File imageFile) {
        String name = imageFile.getName();
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        return new File(imageFile.getParentFile(), base + ".meta.json");
    }

    /**
     * Saves the current metadata, prompting for a destination file (defaulting
     * to whatever was last loaded/saved).
     *
     * @param parent the parent component for dialogs
     */
    void saveMetadata(Component parent) {
        if (image == null) {
            JOptionPane.showMessageDialog(parent, "Open an image first.", "Nothing to save", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser(metadataFile.getParentFile());
        chooser.setSelectedFile(metadataFile);
        chooser.setFileFilter(new FileNameExtensionFilter("Sprite metadata (*.json)", "json"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        try {
            ShipSpriteMetadataLoader.saveToFile(metadata, target);
            metadataFile = target;
            reportStatus();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, "Failed to save: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Discards in-progress edits and reloads metadata from the current
     * metadata file.
     *
     * @param parent the parent component for dialogs
     */
    void reloadMetadata(Component parent) {
        if (metadataFile == null || !metadataFile.exists()) {
            JOptionPane.showMessageDialog(parent, "No metadata file to reload.", "Nothing to reload", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            metadata = ShipSpriteMetadataLoader.loadFromFile(metadataFile);
            repaint();
            reportStatus();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, "Failed to reload: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addHitboxPoint() {
        List<PixelPoint> points = metadata.getHitboxPolygon();
        if (points.size() >= MAX_HITBOX_POINTS) {
            statusListener.accept("Hitbox already has the Box2D max of " + MAX_HITBOX_POINTS + " points — remove one first.");
            return;
        }
        points.add(SpriteCoordinates.toSpritePoint(mouseImageX, mouseImageY, image.getWidth(), image.getHeight()));
        repaint();
        reportStatus();
    }

    private void removeLastHitboxPoint() {
        List<PixelPoint> points = metadata.getHitboxPolygon();
        if (!points.isEmpty()) {
            points.remove(points.size() - 1);
            repaint();
            reportStatus();
        }
    }

    private void placeAttachmentPoint() {
        JComboBox<String> nameField = new JComboBox<>(SUGGESTED_ATTACHMENT_NAMES);
        nameField.setEditable(true);
        int result = JOptionPane.showConfirmDialog(this, nameField, "Attachment point name",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        Object selected = nameField.getEditor().getItem();
        String name = selected == null ? "" : selected.toString().trim();
        if (name.isEmpty()) {
            return;
        }
        metadata.getAttachmentPoints()
            .computeIfAbsent(name, unused -> new ArrayList<>())
            .add(SpriteCoordinates.toSpritePoint(mouseImageX, mouseImageY, image.getWidth(), image.getHeight()));
        repaint();
        reportStatus();
    }

    private void removeNearestAttachmentPoint() {
        String nearestName = null;
        PixelPoint nearestPoint = null;
        double nearestDistanceSquared = Double.MAX_VALUE;

        for (Map.Entry<String, List<PixelPoint>> entry : metadata.getAttachmentPoints().entrySet()) {
            for (PixelPoint point : entry.getValue()) {
                double dx = SpriteCoordinates.toScreenX(point, image.getWidth(), ZOOM) - mouseImageX * ZOOM;
                double dy = SpriteCoordinates.toScreenY(point, image.getHeight(), ZOOM) - mouseImageY * ZOOM;
                double distanceSquared = dx * dx + dy * dy;
                if (distanceSquared < nearestDistanceSquared) {
                    nearestDistanceSquared = distanceSquared;
                    nearestName = entry.getKey();
                    nearestPoint = point;
                }
            }
        }

        double pickRadiusSquared = (double) ATTACHMENT_PICK_RADIUS_PIXELS * ATTACHMENT_PICK_RADIUS_PIXELS;
        if (nearestName != null && nearestDistanceSquared <= pickRadiusSquared) {
            List<PixelPoint> list = metadata.getAttachmentPoints().get(nearestName);
            list.remove(nearestPoint);
            if (list.isEmpty()) {
                metadata.getAttachmentPoints().remove(nearestName);
            }
            repaint();
            reportStatus();
        }
    }

    private void reportStatus() {
        int hitboxCount = metadata.getHitboxPolygon().size();
        int attachmentCount = metadata.getAttachmentPoints().values().stream().mapToInt(List::size).sum();
        String imageName = imageFile == null ? "(no image loaded)" : imageFile.getName();
        String metaName = metadataFile == null ? "(unsaved)" : metadataFile.getName();
        statusListener.accept(String.format("%s -> %s | hitbox points: %d/%d | attachment points: %d",
            imageName, metaName, hitboxCount, MAX_HITBOX_POINTS, attachmentCount));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (image == null) {
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawString("File > Open Image... to begin", 16, 24);
            return;
        }

        paintCheckerboard(g2);
        g2.drawImage(image, 0, 0, image.getWidth() * ZOOM, image.getHeight() * ZOOM, null);
        paintHitbox(g2);
        paintAttachmentPoints(g2);
        paintCrosshair(g2);
    }

    private void paintCheckerboard(Graphics2D g2) {
        int width = image.getWidth() * ZOOM;
        int height = image.getHeight() * ZOOM;
        for (int y = 0; y < height; y += CHECKER_SIZE) {
            for (int x = 0; x < width; x += CHECKER_SIZE) {
                boolean light = ((x / CHECKER_SIZE) + (y / CHECKER_SIZE)) % 2 == 0;
                g2.setColor(light ? CHECKER_LIGHT : CHECKER_DARK);
                g2.fillRect(x, y, CHECKER_SIZE, CHECKER_SIZE);
            }
        }
    }

    private void paintHitbox(Graphics2D g2) {
        List<PixelPoint> points = metadata.getHitboxPolygon();
        g2.setColor(HITBOX_COLOR);
        g2.setStroke(new BasicStroke(2f));

        for (int i = 0; i < points.size() - 1; i++) {
            drawEdge(g2, points.get(i), points.get(i + 1));
        }
        if (points.size() >= 3) {
            drawEdge(g2, points.get(points.size() - 1), points.get(0));
        }
        for (PixelPoint point : points) {
            int sx = SpriteCoordinates.toScreenX(point, image.getWidth(), ZOOM);
            int sy = SpriteCoordinates.toScreenY(point, image.getHeight(), ZOOM);
            g2.fillOval(sx - 3, sy - 3, 6, 6);
        }
    }

    private void drawEdge(Graphics2D g2, PixelPoint from, PixelPoint to) {
        int fromX = SpriteCoordinates.toScreenX(from, image.getWidth(), ZOOM);
        int fromY = SpriteCoordinates.toScreenY(from, image.getHeight(), ZOOM);
        int toX = SpriteCoordinates.toScreenX(to, image.getWidth(), ZOOM);
        int toY = SpriteCoordinates.toScreenY(to, image.getHeight(), ZOOM);
        g2.drawLine(fromX, fromY, toX, toY);
    }

    private void paintAttachmentPoints(Graphics2D g2) {
        g2.setColor(ATTACHMENT_COLOR);
        for (Map.Entry<String, List<PixelPoint>> entry : metadata.getAttachmentPoints().entrySet()) {
            for (PixelPoint point : entry.getValue()) {
                int sx = SpriteCoordinates.toScreenX(point, image.getWidth(), ZOOM);
                int sy = SpriteCoordinates.toScreenY(point, image.getHeight(), ZOOM);
                g2.fillOval(sx - 4, sy - 4, 8, 8);
                g2.drawString(entry.getKey(), sx + 6, sy - 6);
            }
        }
    }

    private void paintCrosshair(Graphics2D g2) {
        g2.setColor(Color.WHITE);
        int sx = mouseImageX * ZOOM;
        int sy = mouseImageY * ZOOM;
        g2.drawLine(sx - 6, sy, sx + 6, sy);
        g2.drawLine(sx, sy - 6, sx, sy + 6);
    }
}
