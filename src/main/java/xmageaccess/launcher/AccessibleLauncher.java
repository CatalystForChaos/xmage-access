package xmageaccess.launcher;

import javax.swing.*;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Properties;

/**
 * Wrapper launcher that adds an "Enable screen reader support" checkbox
 * before launching the normal XMage launcher. Toggles the -javaagent
 * flag in installed.properties so the accessibility agent loads with
 * the client.
 */
public class AccessibleLauncher {

    private static final String PROPS_FILE = "installed.properties";
    private static final String CLIENT_OPTS_KEY = "xmage.client.javaopts";
    private static final String AGENT_FLAG = "-javaagent:./lib/xmage-access-0.1.0.jar";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    // Ignore - use default look and feel
                }
                new AccessibleLauncher().show();
            }
        });
    }

    private void show() {
        JFrame frame = new JFrame("XMage Accessible Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(420, 180);
        frame.setLocationRelativeTo(null);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        boolean accessEnabled = isAccessibilityEnabled();

        final JCheckBox chkAccess = new JCheckBox("Enable screen reader support", accessEnabled);
        chkAccess.setMnemonic(KeyEvent.VK_E);
        chkAccess.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkAccess.getAccessibleContext().setAccessibleDescription(
                "Check this box to enable screen reader support for XMage gameplay");

        JButton btnLaunch = new JButton("Launch XMage");
        btnLaunch.setMnemonic(KeyEvent.VK_L);
        btnLaunch.setAlignmentX(Component.LEFT_ALIGNMENT);

        btnLaunch.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                setAccessibilityEnabled(chkAccess.isSelected());
                launchXMage();
                frame.dispose();
                System.exit(0);
            }
        });

        // Enter key on the button triggers it
        frame.getRootPane().setDefaultButton(btnLaunch);

        panel.add(chkAccess);
        panel.add(Box.createVerticalStrut(15));
        panel.add(btnLaunch);

        frame.add(panel);
        frame.setVisible(true);
        chkAccess.requestFocusInWindow();
    }

    private boolean isAccessibilityEnabled() {
        Properties props = loadProperties();
        String opts = props.getProperty(CLIENT_OPTS_KEY, "");
        return opts.contains("-javaagent:");
    }

    private void setAccessibilityEnabled(boolean enabled) {
        Properties props = loadProperties();
        String opts = props.getProperty(CLIENT_OPTS_KEY, "");

        // Remove any existing agent flag
        opts = opts.replaceAll("\\s*-javaagent:\\S*", "").trim();

        if (enabled) {
            opts = opts + " " + AGENT_FLAG;
        }

        props.setProperty(CLIENT_OPTS_KEY, opts.trim());
        saveProperties(props);

        System.out.println("[XMage Access] Accessibility " + (enabled ? "enabled" : "disabled")
                + ". Client opts: " + opts.trim());
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        File file = new File(PROPS_FILE);
        if (file.exists()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                props.load(fis);
            } catch (IOException e) {
                System.err.println("Error reading " + PROPS_FILE + ": " + e.getMessage());
            } finally {
                if (fis != null) {
                    try { fis.close(); } catch (IOException e) { /* ignore */ }
                }
            }
        }
        return props;
    }

    private void saveProperties(Properties props) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(PROPS_FILE);
            props.store(fos, "---XMage Properties---");
        } catch (IOException e) {
            System.err.println("Error writing " + PROPS_FILE + ": " + e.getMessage());
            JOptionPane.showMessageDialog(null,
                    "Could not save settings: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException e) { /* ignore */ }
            }
        }
    }

    private void launchXMage() {
        File launcherJar = findLauncherJar();
        if (launcherJar == null) {
            JOptionPane.showMessageDialog(null,
                    "Could not find XMageLauncher JAR in the current directory.\n"
                    + "Make sure this script is in the same folder as XMageLauncher.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("java",
                    "-Djava.net.preferIPv4Stack=true", "-jar", launcherJar.getName());
            pb.directory(new File("."));
            pb.start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Could not launch XMage: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private File findLauncherJar() {
        File dir = new File(".");
        File[] jars = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File d, String name) {
                return name.startsWith("XMageLauncher") && name.endsWith(".jar");
            }
        });
        if (jars != null && jars.length > 0) {
            return jars[0];
        }
        return null;
    }
}
