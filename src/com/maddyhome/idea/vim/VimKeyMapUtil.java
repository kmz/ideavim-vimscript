package com.maddyhome.idea.vim;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.impl.stores.StorageUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.maddyhome.idea.vim.ui.VimKeymapDialog;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author oleg
 */
public class VimKeyMapUtil {
  private static Logger LOG = Logger.getInstance(VimKeyMapUtil.class.getName());
  private static final String VIM_XML = "Vim.xml";

  public static void installKeyBoardBindings(final VimPlugin vimPlugin) {
    LOG.debug("Check for keyboard bindings");

    final KeymapManagerImpl manager = (KeymapManagerImpl)KeymapManager.getInstance();

    final Keymap keymap = manager.getKeymap("Vim");
    if (keymap != null) {
      return;
    }

    final String keyMapsPath = PathManager.getConfigPath() + File.separatorChar + "keymaps";
    final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    final VirtualFile keyMapsFolder = localFileSystem.refreshAndFindFileByPath(keyMapsPath);
    if (keyMapsFolder == null) {
      LOG.debug("Failed to install vim keymap. Empty keymaps folder");
      return;
    }

    LOG.debug("No vim keyboard installed found. Installing");
    final String keymapPath = PathManager.getPluginsPath() + File.separatorChar + "IdeaVim" + File.separatorChar + VIM_XML;
    final File vimKeyMapFile = new File(keymapPath);
    if (!vimKeyMapFile.exists() || !vimKeyMapFile.isFile()) {
      final String error = "Installation of the Vim keymap failed because Vim keymap file not found: " + keymapPath;
      LOG.error(error);
      Notifications.Bus.notify(new Notification("ideavim", "IdeaVim", error, NotificationType.ERROR));
      return;
    }
    try {
      final VirtualFile vimKeyMap2Copy = localFileSystem.refreshAndFindFileByIoFile(vimKeyMapFile);
      if (vimKeyMap2Copy == null){
        final String error = "Installation of the Vim keymap failed because Vim keymap file not found: " + keymapPath;
        LOG.error(error);
        Notifications.Bus.notify(new Notification("ideavim", "IdeaVim", error, NotificationType.ERROR));
        return;
      }
      final VirtualFile vimKeyMapVFile = localFileSystem.copyFile(vimPlugin, vimKeyMap2Copy, keyMapsFolder, VIM_XML);

      final String path = vimKeyMapVFile.getPath();
      final Document document = StorageUtil.loadDocument(new FileInputStream(path));
      if (document == null) {
        LOG.error("Failed to install vim keymap. Vim.xml file is corrupted");
        Notifications.Bus.notify(new Notification("ideavim", "IdeaVim",
                                                  "Failed to install vim keymap. Vim.xml file is corrupted", NotificationType.ERROR));
        return;
      }

      // Prompt user to select the parent for the Vim keyboard
      configureVimParentKeymap(path, document);

      final KeymapImpl vimKeyMap = new KeymapImpl();
      final Keymap[] allKeymaps = manager.getAllKeymaps();
      vimKeyMap.readExternal(document.getRootElement(), allKeymaps);
      manager.addKeymap(vimKeyMap);
      Notifications.Bus.notify(new Notification("ideavim", "IdeaVim", "Successfully installed vim keymap", NotificationType.INFORMATION));
    }
    catch (FileNotFoundException e) {
      reportError(e);
    }
    catch (InvalidDataException e) {
      reportError(e);
    }
    catch (IOException e) {
      reportError(e);
    }
  }

  private static void requestRestartOrShutdown(final Project project) {
    final ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    if (app.isRestartCapable()) {
      if (Messages.showDialog(project, "Restart " + ApplicationNamesInfo.getInstance().getProductName() + " to activate changes?",
                              "Vim keymap changed", new String[]{"Shut Down", "&Postpone"}, 0, Messages.getQuestionIcon()) == 0) {
        app.restart();
      }
    } else {
      if (Messages.showDialog(project, "Shut down " + ApplicationNamesInfo.getInstance().getProductName() + " to activate changes?",
                              "Vim keymap changed", new String[]{"Shut Down", "&Postpone"}, 0, Messages.getQuestionIcon()) == 0){
        app.exit(true);
      }
    }
  }

  /**
   * Changes parent keymap for the Vim
   * @return true if document was changed succesfully
   */
  private static boolean configureVimParentKeymap(final String path, final Document document) throws IOException {
    final Element rootElement = document.getRootElement();
    final String parentKeymap = rootElement.getAttributeValue("parent");
    final VimKeymapDialog vimKeymapDialog = new VimKeymapDialog(parentKeymap);
    vimKeymapDialog.show();
    if (vimKeymapDialog.getExitCode() != DialogWrapper.OK_EXIT_CODE){
      return false;
    }
    rootElement.removeAttribute("parent");
    final Keymap selectedKeymap = vimKeymapDialog.getSelectedKeymap();
    final String keymapName = selectedKeymap.getName();
    rootElement.setAttribute("parent", keymapName);

    // Save modified keymap to the file
    JDOMUtil.writeDocument(document, path, "\n");
    Notifications.Bus.notify(new Notification("ideavim", "IdeaVim", "Successfully configured vim keymap to be based on " +
                                                                    selectedKeymap.getPresentableName(),
                                              NotificationType.INFORMATION));

    return true;
  }

  public static void enableKeyBoardBindings(final boolean enabled) {
    LOG.debug("Enabling keymap");
    final KeymapManagerImpl manager = (KeymapManagerImpl)KeymapManager.getInstance();
    if (manager.getActiveKeymap().getName().equals("Vim") == enabled){
      return;
    }
    final String keymapName2Enable = enabled ? "Vim" : VimPlugin.getInstance().getPreviousKeyMap();
    if (keymapName2Enable.isEmpty()) {
      return;
    }
    if (keymapName2Enable.equals(manager.getActiveKeymap().getName())) {
      return;
    }
    if (enabled) {
      // Ask user before changing keymap to Vim.xml
      if (Messages.showYesNoDialog("It is crucial to use predefined Vim keymap for IdeaVim plugin keymap.\nDo you want " +
                                ApplicationManagerEx.getApplicationEx().getName() +
                                " to enable it?", "Vim keymap", Messages.getQuestionIcon()) == Messages.NO){
        return;
      }
    }
    LOG.debug("Enabling keymap:" + keymapName2Enable);
    final Keymap keymap = manager.getKeymap(keymapName2Enable);
    if (keymap == null) {
      Notifications.Bus
        .notify(new Notification("ideavim", "IdeaVim", "Failed to enable keymap: " + keymapName2Enable, NotificationType.ERROR));
      LOG.error("Failed to enable keymap: " + keymapName2Enable);
      return;
    }
    // Save previous keymap to enable after VIM emulation is turned off
    if (enabled) {
      VimPlugin.getInstance().setPreviousKeyMap(manager.getActiveKeymap().getName());
    }

    manager.setActiveKeymap(keymap);

    final String keyMapPresentableName = keymap.getPresentableName();
    Notifications.Bus
      .notify(new Notification("ideavim", "IdeaVim", keyMapPresentableName + " keymap was enabled", NotificationType.INFORMATION));
    LOG.debug(keyMapPresentableName + " keymap was enabled");
  }


  public static void reconfigureParentKeymap(final Project project) {
    final VirtualFile vimKeymapFile = getVimKeymapFile();
    if (vimKeymapFile == null) {
      LOG.error("Failed to find Vim keymap");
      Notifications.Bus.notify(new Notification("ideavim", "IdeaVim", "Failed to find Vim keymap", NotificationType.ERROR));
      return;
    }

    try {
      final String path = vimKeymapFile.getPath();
      final Document document = StorageUtil.loadDocument(new FileInputStream(path));
      if (document == null) {
        LOG.error("Failed to install vim keymap. Vim.xml file is corrupted");
        Notifications.Bus.notify(new Notification("ideavim", "IdeaVim",
                                                  "Vim.xml file is corrupted", NotificationType.ERROR));
        return;
      }
      // Prompt user to select the parent for the Vim keyboard
      if (configureVimParentKeymap(path, document)) {
        final KeymapManagerImpl manager = (KeymapManagerImpl) KeymapManager.getInstance();
        final KeymapImpl vimKeyMap = new KeymapImpl();
        final Keymap[] allKeymaps = manager.getAllKeymaps();
        vimKeyMap.readExternal(document.getRootElement(), allKeymaps);
        manager.addKeymap(vimKeyMap);
        requestRestartOrShutdown(project);
      }
    }
    catch (FileNotFoundException e) {
      reportError(e);
    }
    catch (IOException e) {
      reportError(e);
    }
    catch (InvalidDataException e) {
      reportError(e);
    }
  }

  private static void reportError(final Exception e) {
    LOG.error("Failed to reconfigure vim keymap.\n" + e);
    Notifications.Bus
      .notify(new Notification("ideavim", "IdeaVim", "Failed to reconfigure vim keymap.\n" + e, NotificationType.ERROR));
  }

  @Nullable
  public static VirtualFile getVimKeymapFile() {
    final String keyMapsPath = PathManager.getConfigPath() + File.separatorChar + "keymaps";
    final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    return localFileSystem.refreshAndFindFileByPath(keyMapsPath + File.separatorChar + VIM_XML);
  }
}
