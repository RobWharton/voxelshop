package com.vitco.logic.texture;

import com.jidesoft.action.CommandMenuBar;
import com.jidesoft.swing.JideScrollPane;
import com.vitco.engine.data.Data;
import com.vitco.engine.data.notification.DataChangeAdapter;
import com.vitco.logic.ViewPrototype;
import com.vitco.res.VitcoSettings;
import com.vitco.util.WorldUtil;
import com.vitco.util.WrapLayout;
import com.vitco.util.action.types.StateActionPrototype;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Manages the different Textures.
 */
public class TextureManager extends ViewPrototype implements TextureManagerInterface {

    // var & setter
    protected Data data;
    @Autowired
    public final void setData(Data data) {
        this.data = data;
    }

    // true if there exist textured
    private boolean texturesExist = false;

    // currently selected texture
    private int selectedTexture = -1;

    // texture panel class
    private final class TexturePanel extends JPanel {

        private final Integer textureId;

        // we need the hash to determine when to change the picture
        private String hash = "";

        private Color inactiveColor = VitcoSettings.TEXTURE_BORDER;
        private Color activeColor = VitcoSettings.TEXTURE_BORDER_ACTIVE;
        private Color selectedColor = VitcoSettings.TEXTURE_BORDER_SELECTED;

        private boolean selected = false;

        private TexturePanel(final Integer textureId) {
            this.textureId = textureId;
            this.setBorder(BorderFactory.createLineBorder(inactiveColor));
            this.setLayout(new BorderLayout(0, 0));
            //this.setToolTipText("Texture #" + textureId);
            this.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    super.mousePressed(e);
                    // unselect if this is already selected
                    data.selectTextureSoft(selected ? -1 : textureId);
                }
                @Override
                public void mouseEntered(MouseEvent e) {
                    super.mouseEntered(e);
                    setBorder(BorderFactory.createLineBorder(activeColor));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    super.mouseExited(e);
                    setBorder(BorderFactory.createLineBorder(selected?selectedColor:inactiveColor));
                }
            });
            refresh();
        }

        private void refresh() {
            String newHash = data.getTextureHash(textureId);
            if (!hash.equals(newHash)) { // only redraw when hash changed
                removeAll(); // make sure nothing is in this container
                ImageIcon myPicture = data.getTexture(textureId);
                JLabel picLabel = new JLabel(myPicture);
                add( picLabel );
                hash = newHash;
            }
            boolean selectedNew = textureId == selectedTexture;
            if (selectedNew != selected) {
                selected = selectedNew;
                this.setBorder(BorderFactory.createLineBorder(selected?selectedColor:inactiveColor));
            }
            this.updateUI();
        }
    }

    // import texture file chooser
    final JFileChooser fc_import = new JFileChooser();

    // handles the textures of the data class object
    @Override
    public JComponent build(final Frame mainFrame) {
        // panel that holds everything
        final JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        final JPanel textureWrapperPanel = new JPanel();
        textureWrapperPanel.setLayout(new WrapLayout(FlowLayout.CENTER, 3, 3));
        textureWrapperPanel.setBackground(VitcoSettings.TEXTURE_WINDOW_BG_COLOR);
        final JideScrollPane scrollPane = new JideScrollPane(textureWrapperPanel);
        panel.add(scrollPane, BorderLayout.CENTER);

        // add filter
        FileFilter filter = new FileFilter() {
            public boolean accept(File f)
            {
                return f.isDirectory() || f.getName().endsWith(".png");
            }

            public String getDescription()
            {
                return "PNG @ 64x96 (*.png)";
            }
        };
        fc_import.addChoosableFileFilter(filter);
        fc_import.setAcceptAllFileFilterUsed(false);
        fc_import.setFileFilter(filter);

        // create the menu actions
        actionManager.registerAction("texturemg_action_add", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (fc_import.showOpenDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
                    try {
                        ImageIcon texture = new ImageIcon(ImageIO.read(fc_import.getSelectedFile()));
                        if (texture.getIconWidth() != 64 || texture.getIconHeight() != 96) {
                            console.addLine(langSelector.getString("texturemg_file_dim_error"));
                        } else {
                            // make sure we can identify the texture
                            data.addTexture(texture);
                        }
                    } catch (IOException error) {
                        console.addLine(langSelector.getString("texturemg_general_file_error"));
                    }
                }
            }
        });
        actionGroupManager.addAction("texture_manager_buttons", "texturemg_action_remove", new StateActionPrototype() {
            @Override
            public boolean getStatus() {
                return selectedTexture != -1;
            }

            @Override
            public void action(ActionEvent e) {
                boolean success = data.removeTexture(selectedTexture);
                if (!success) {
                    console.addLine(langSelector.getString("texturemg_delete_failed_texture_in_use"));
                }
            }
        });
        actionGroupManager.addAction("texture_manager_buttons", "texturemg_action_replace", new StateActionPrototype() {
            @Override
            public boolean getStatus() {
                return selectedTexture != -1;
            }

            @Override
            public void action(ActionEvent e) {
                if (fc_import.showOpenDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
                    try {
                        ImageIcon texture = new ImageIcon(ImageIO.read(fc_import.getSelectedFile()));
                        if (texture.getIconWidth() != 64 || texture.getIconHeight() != 96) {
                            console.addLine(langSelector.getString("texturemg_file_dim_error"));
                        } else {
                            // make sure we can identify the texture
                            data.replaceTexture(selectedTexture, texture);
                        }
                    } catch (IOException error) {
                        console.addLine(langSelector.getString("texturemg_general_file_error"));
                    }
                }
            }
        });
        actionGroupManager.addAction("texture_manager_buttons", "texturemg_action_clear", new StateActionPrototype() {
            @Override
            public boolean getStatus() {
                return texturesExist;
            }

            @Override
            public void action(ActionEvent e) {
                data.removeAllTexture();
            }
        });
        actionGroupManager.registerGroup("texture_manager_buttons");

        // handle texture change notification (logic + layout)
        data.addDataChangeListener(new DataChangeAdapter() {
            // list of texture images currently on display
            private final HashMap<Integer, TexturePanel> texturePanels = new HashMap<Integer, TexturePanel>();
            // list of textures with md5 hash
            private final HashMap<Integer, String> textureHash = new HashMap<Integer, String>();

            @Override
            public void onTextureDataChanged() {
                ArrayList<Integer> dataTextureList =
                        new ArrayList<Integer>(Arrays.asList(data.getTextureList()));

                // previously selected texture
                int prevSelectedTexture = selectedTexture;

                // remember if textures exist
                texturesExist = dataTextureList.size() > 0;
                // get the currently selected texture
                selectedTexture = data.getSelectedTexture();
                // update buttons
                actionGroupManager.refreshGroup("texture_manager_buttons");

                // removed textures
                for (Integer texId : new ArrayList<Integer>(textureHash.keySet())) {
                    if (!dataTextureList.contains(texId)) {
                        // delete from internal store
                        textureHash.remove(texId);
                        // delete texture from world
                        WorldUtil.removeTexture(String.valueOf(texId));
                        // remove panel
                        textureWrapperPanel.remove(texturePanels.get(texId));
                        texturePanels.remove(texId);
                    }
                }
                // added textures
                for (int texId : dataTextureList) {
                    boolean changed = false;
                    if (textureHash.containsKey(texId)) {
                        if (!data.getTextureHash(texId).equals(textureHash.get(texId))) {
                            // hash changed
                            changed = true;
                            // update panel
                            texturePanels.get(texId).refresh();
                        }
                    } else {
                        // new texture
                        changed = true;
                        // ===================
                        // create panel
                        TexturePanel panel = new TexturePanel(texId);
                        texturePanels.put(texId, panel);

                        // insert to correct place
                        Integer[] currentObjects = new Integer[texturePanels.size()];
                        texturePanels.keySet().toArray(currentObjects);
                        // sort
                        Arrays.sort(currentObjects, new Comparator<Integer>() {
                            @Override
                            public int compare(Integer o1, Integer o2) {
                                return o1.compareTo(o2);
                            }
                        });
                        int pos = 0;
                        while (texId > currentObjects[pos] && pos < currentObjects.length) {
                            pos++;
                        }
                        textureWrapperPanel.add(panel, null, pos);
                        // ===================
                    }
                    if (changed) {
                        // store/update internal hash
                        textureHash.put(texId, data.getTextureHash(texId));
                        // load/update texture into world
                        WorldUtil.loadTexture(String.valueOf(texId), data.getTexture(texId));
                    }
                }

                // update the UI (force!)
                textureWrapperPanel.updateUI();

                // this updates the values for the getBound() function
                scrollPane.validate();

                // scroll the selected texture into view and refresh panel
                TexturePanel selectedTexturePanel = texturePanels.get(selectedTexture);
                if (selectedTexturePanel != null) {
                    textureWrapperPanel.scrollRectToVisible(selectedTexturePanel.getBounds());
                    selectedTexturePanel.refresh();
                }

                // refresh prev selected panels
                TexturePanel prevSelectedTexturePanel = texturePanels.get(prevSelectedTexture);
                if (prevSelectedTexturePanel != null) {
                    prevSelectedTexturePanel.refresh();
                }
            }
        });

        // create menu bar to bottom
        CommandMenuBar menuPanel = new CommandMenuBar();
        //menuPanel.setOrientation(1); // top down orientation
        menuGenerator.buildMenuFromXML(menuPanel, "com/vitco/logic/texture/toolbar.xml");
        panel.add(menuPanel, BorderLayout.SOUTH);

        return panel;
    }

    @PreDestroy
    public final void finish() {
        preferences.storeString("texture_import_dialog_last_directory", fc_import.getCurrentDirectory().getAbsolutePath());
    }

    @PostConstruct
    public final void init() {
        if (preferences.contains("texture_import_dialog_last_directory")) {
            File file = new File(preferences.loadString("texture_import_dialog_last_directory"));
            if (file.isDirectory()) {
                fc_import.setCurrentDirectory(file);
            }
        }
    }
}
