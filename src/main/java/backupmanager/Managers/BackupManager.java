package backupmanager.Managers;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import backupmanager.BackupOperations;
import backupmanager.Dialogs.BackupEntryDialog;
import backupmanager.Dialogs.PreferencesDialog;
import backupmanager.Dialogs.TimePicker;
import backupmanager.Entities.Backup;
import backupmanager.Entities.Preferences;
import backupmanager.Entities.TimeInterval;
import backupmanager.Entities.ZippingContext;
import backupmanager.Enums.ConfigKey;
import backupmanager.Enums.TranslationLoaderEnum.TranslationCategory;
import backupmanager.Enums.TranslationLoaderEnum.TranslationKey;
import backupmanager.GUI.BackupManagerGUI;
import backupmanager.GUI.BackupProgressGUI;
import backupmanager.Json.JSONBackup;
import backupmanager.Services.BackupObserver;
import backupmanager.Table.BackupTable;
import backupmanager.Table.TableDataManager;

public final class BackupManager {
    private static final Logger logger = LoggerFactory.getLogger(BackupManager.class);
    public static final DateTimeFormatter dateForfolderNameFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH.mm.ss");
    public static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    
    private final BackupManagerGUI main;

    public BackupManager(BackupManagerGUI main) {
        this.main = main;
    } 

    private void renameBackup(List<Backup> backups, Backup backup) {
        logger.info("Event --> backup renaming");
        
        String backup_name = getBackupName(backups, backup.getBackupName(), false);
        if (backup_name == null || backup_name.isEmpty()) return;
        
        backup.setBackupName(backup_name);
        backup.setLastUpdateDate(LocalDateTime.now());
        updateBackupList(backups);
    }

    public void openBackup(String backupName) {
        logger.info("Event --> opening backup");

        Backup backup = Backup.getBackupByName(backupName);
        
        BackupEntryDialog dialog = new BackupEntryDialog(main, false, backup);
        dialog.setVisible(true);
    }

    public void newBackup(BackupProgressGUI progressBar) {
        logger.info("Event --> new backup");
        
        BackupEntryDialog dialog = new BackupEntryDialog(main, false);
        dialog.setVisible(true);
    }

    public static void newBackup(Backup backup) {
        List<Backup> backups = getBackupList();
        backups.add(backup);
        updateBackupList(backups);
    }

    public void deleteBackup(int selectedRow, List<Backup> backups, BackupTable backupTable, boolean isConfermationRequired) {
        logger.info("Event --> deleting backup");
        
        if (isConfermationRequired) {
            int response = JOptionPane.showConfirmDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_MESSAGE_BEFORE_DELETE_BACKUP), TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_REQUIRED_TITLE), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (response != JOptionPane.YES_OPTION) {
                return;
            }
        }

        // get correct backup
        String backupName = (String) backupTable.getValueAt(selectedRow, 0);
        Backup backup = backupmanager.Entities.Backup.getBackupByName(backups, backupName);

        RemoveBackup(backup.getBackupName(), backups);
    }

    public void deleteBackup(int selectedRow, List<Backup> backups, BackupTable backupTable) {
        logger.info("Event --> deleting backup");
        
        if (selectedRow != -1) {
            int response = JOptionPane.showConfirmDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_MESSAGE_BEFORE_DELETE_BACKUP), TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_REQUIRED_TITLE), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (response == JOptionPane.YES_OPTION) {
                // get correct backup
                String backupName = (String) backupTable.getValueAt(selectedRow, 0);
                Backup backup = backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName);

                RemoveBackup(backup.getBackupName(), backups);
            }
        }
    }

    public static void RemoveBackup(String backupName) {
        List<Backup> backups = getBackupList();
        Backup backup = Backup.getBackupByName(backupName);
        RemoveBackup(backup, backups);
    }

    public static void RemoveBackup(String backupName, List<Backup> backups) {
        Backup backup = Backup.getBackupByName(backupName);
        RemoveBackup(backup, backups);
    }

    public static void RemoveBackup(Backup backup, List<Backup> backups) {
        logger.info("Event --> removing backup" + backupmanager.Entities.Backup.getBackupByName(backups, backup.getBackupName()).toString());

        // backup list update
        for (Backup back : backups) {
            if (backup.getBackupName().equals(back.getBackupName())) {
                backups.remove(back);
                logger.info("Backup removed successfully: " + back.toString());
                break;
            }
        }

        updateBackupList(backups);
    }

    public static void updateBackupList(List<Backup> backups) {
        if (backups == null) throw new IllegalArgumentException("Backup list is null!");

        logger.info("Updating backup list");
        
        // update
        JSONBackup.updateBackupListJSON(Preferences.getBackupList().getDirectory(), Preferences.getBackupList().getFile(), backups);
        
        // get the new backup updated
        backups = getBackupList();
        
        if (BackupManagerGUI.model != null)
            TableDataManager.updateTableWithNewBackupList(backups, formatter);
    }
    
    public static void updateBackup(Backup updatedBackup) {
        if (updatedBackup == null) throw new IllegalArgumentException("Backup is null!");

        logger.info("Updating backup: " + updatedBackup.getBackupName());
        
        JSONBackup.updateSingleBackupInJSON(Preferences.getBackupList().getDirectory(), Preferences.getBackupList().getFile(), updatedBackup);
        List<Backup> backups = getBackupList();

        if (BackupManagerGUI.model != null) {
            TableDataManager.updateTableWithNewBackupList(backups, formatter);
        }
    }
    
    private void OpenFolder(String path) {
        logger.info("Event --> opening folder");
        
        File folder = new File(path);

        // if the object is a file i want to obtain the folder that contains that file
        if (folder.exists() && folder.isFile()) { 
            folder = folder.getParentFile();
        }

        if (folder.exists() && folder.isDirectory()) {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                try {
                    desktop.open(folder);
                } catch (IOException ex) {
                    logger.error("An error occurred: " + ex.getMessage(), ex);
                    ExceptionManager.openExceptionMessage(ex.getMessage(), Arrays.toString(ex.getStackTrace()));
                }
            } else {
                logger.warn("Desktop not supported on this operating system");
            }
        } else {
            logger.warn("The folder does not exist or is invalid");

            JOptionPane.showMessageDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_MESSAGE_FOR_FOLDER_NOT_EXISTING), TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_GENERIC_TITLE), JOptionPane.ERROR_MESSAGE);
        }
    }

    private String getBackupName(List<Backup> backups, String oldName, boolean canOverwrite) {
        while (true) {
            String backupName = JOptionPane.showInputDialog(null, 
                TranslationCategory.DIALOGS.getTranslation(TranslationKey.BACKUP_NAME_INPUT), oldName);
    
            // If the user cancels the operation
            if (backupName == null || backupName.trim().isEmpty()) {
                return null;
            }
            
            Optional<Backup> existingBackup = backups.stream()
                .filter(b -> b.getBackupName().equals(backupName))
                .findFirst();
    
            if (existingBackup.isPresent()) {
                if (canOverwrite) {
                    int response = JOptionPane.showConfirmDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.DUPLICATED_BACKUP_NAME_MESSAGE), TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_REQUIRED_TITLE), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                if (response == JOptionPane.YES_OPTION) {
                    backups.remove(existingBackup.get());
                    return backupName;
                }
                } else {
                    logger.warn("Backup name '{}' is already in use", backupName);
                    JOptionPane.showMessageDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.BACKUP_NAME_ALREADY_USED_MESSAGE), TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_GENERIC_TITLE), JOptionPane.ERROR_MESSAGE);
                }
            } else {
                return backupName;  // Return valid name
            }
        }
    }

    public void openBackupEntryDialog() {
        BackupEntryDialog dialog = new BackupEntryDialog(main, false);
        dialog.setVisible(true);
    }
    
    public static LocalDateTime getNexDateBackup(TimeInterval timeInterval) {
        return LocalDateTime.now()
            .plusDays(timeInterval.getDays())
            .plusHours(timeInterval.getHours())
            .plusMinutes(timeInterval.getMinutes());
    }

    public static List<Backup> getBackupList() {
        try {
            List<Backup> backups = JSONBackup.readBackupListFromJSON(Preferences.getBackupList().getDirectory(), Preferences.getBackupList().getFile());
            BackupManagerGUI.backups = backups; // i have to keep update also the backup list in the main panel
            return backups;
        } catch (IOException e) {
            logger.error("An error occurred while trying to get the backup list from json file: " + e.getMessage(), e);
            ExceptionManager.openExceptionMessage(e.getMessage(), Arrays.toString(e.getStackTrace()));
        } catch (Exception e) {
            logger.error("An error occurred: " + e.getMessage(), e);
        }

        return null;
    }
    
    // ################################################# Menu Items

    public void menuItemDonateViaBuymeacoffe() {
        logger.info("Event --> buymeacoffe donation");
        WebsiteManager.openWebSite(ConfigKey.DONATE_BUYMEACOFFE_LINK.getValue());
    }

    public void menuItemDonateViaPaypal() {
        logger.info("Event --> paypal donation");
        WebsiteManager.openWebSite(ConfigKey.DONATE_PAYPAL_LINK.getValue());
    }

    public void menuItemExportToJson() {
        logger.info("Event --> exporting backup list");
        ImportExportManager.exportListToJson();
    }

    public List<Backup> menuItemImportFromJson() {
        logger.info("Event --> importing backup list");
        return ImportExportManager.importListFromJson(main, formatter);
    }

    public void menuItemOpenPreferences() {
        logger.info("Event --> opening preferences dialog");
        PreferencesDialog prefs = new PreferencesDialog(main, true, main);
        prefs.setVisible(true);
    }

    public void menuItemInfoPage() {
        logger.info("Event --> shard website");
        WebsiteManager.openWebSite(ConfigKey.INFO_PAGE_LINK.getValue());
    }

    public void menuItemSupport() {
        logger.info("Event --> support");
        WebsiteManager.sendEmail();
    }

    public void menuItemWebsite() {
        logger.info("Event --> shard website");
        WebsiteManager.openWebSite(ConfigKey.SHARD_WEBSITE.getValue());
    }

    public void menuItemShare() {
        logger.info("Event --> share");

        // pop-up message
        JOptionPane.showMessageDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.SHARE_LINK_COPIED_MESSAGE));

        // copy link to the clipboard
        StringSelection stringSelectionObj = new StringSelection(ConfigKey.SHARE_LINK.getValue());
        Clipboard clipboardObj = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboardObj.setContents(stringSelectionObj, null);
    }

    public void menuItemBugReport() {
        logger.info("Event --> bug report");
        WebsiteManager.openWebSite(ConfigKey.ISSUE_PAGE_LINK.getValue());
    }

    public void menuItemNew(BackupProgressGUI progressBar) {
        newBackup(progressBar);
    }

    public void menuItemHistory() {
        logger.info("Event --> history");
        try {
            logger.debug("Opening log file with path: " + ConfigKey.LOG_DIRECTORY_STRING.getValue() + ConfigKey.LOG_FILE_STRING.getValue());
            new ProcessBuilder("notepad.exe", ConfigKey.LOG_DIRECTORY_STRING.getValue() + ConfigKey.LOG_FILE_STRING.getValue()).start();
        } catch (IOException e) {
            logger.error("Error opening history file: " + e.getMessage(), e);
            JOptionPane.showMessageDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_MESSAGE_OPEN_HISTORY_FILE), TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_GENERIC_TITLE), JOptionPane.ERROR_MESSAGE);
        }
    }

    public void menuItemQuit(BackupObserver observer) {
        logger.info("Event --> exit");
        observer.stop();
        System.exit(main.EXIT_ON_CLOSE);
    }

    // returns the new time inteval
    public static Backup toggleAutomaticBackup(Backup backup) {
        logger.info("Event --> automatic backup");
        
        if (backup.isAutoBackup()) {
            int response = JOptionPane.showConfirmDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_MESSAGE_CANCEL_AUTO_BACKUP), TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_REQUIRED_TITLE), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (response != JOptionPane.YES_OPTION) {
                return null;
            }

            backup.setAutoBackup(false);
            backup.setTimeIntervalBackup(null);
            backup.setNextDateBackup(null);
            backup.setLastUpdateDate(LocalDateTime.now());

            logger.info("Automatic backup turned off");

            updateBackup(backup);
            return backup;
        }

        if(!BackupOperations.CheckInputCorrect(backup.getBackupName(),backup.getInitialPath(), backup.getDestinationPath(), null)) return null;

        // if the file has not been saved you need to save it before setting the auto backup
        if(!backup.isAutoBackup() || backup.getNextDateBackup() == null || backup.getTimeIntervalBackup() == null) {
            if (backup.getBackupName() == null || backup.getBackupName().isEmpty()) return null;

            // message
            TimeInterval timeInterval = openTimePicker(null, null);
            if (timeInterval == null) return null;

            //set date for next backup
            LocalDateTime nextDateBackup = getNexDateBackup(timeInterval);  

            backup.setAutoBackup(true);
            backup.setTimeIntervalBackup(timeInterval);
            backup.setNextDateBackup(nextDateBackup);
            backup.setLastUpdateDate(LocalDateTime.now());

            logger.info("Automatic backup turned On and next date backup setted to {}", nextDateBackup);

            showMessageActivationAutoBackup(timeInterval, backup.getInitialPath(), backup.getDestinationPath());

            updateBackup(backup);
            return backup;

        } 

        return null;
    }

    public static TimeInterval openTimePicker(java.awt.Dialog parent, TimeInterval time) {
        TimePicker picker = new TimePicker(parent, time, true);
        picker.setVisible(true);
        return picker.getTimeInterval();
    }

    public static void showMessageActivationAutoBackup(TimeInterval timeInterval, String startPath, String destinationPath) {
        String from = TranslationCategory.GENERAL.getTranslation(TranslationKey.FROM);
        String to = TranslationCategory.GENERAL.getTranslation(TranslationKey.TO);
        String activated = TranslationCategory.DIALOGS.getTranslation(TranslationKey.AUTO_BACKUP_ACTIVATED_MESSAGE);
        String setted = TranslationCategory.DIALOGS.getTranslation(TranslationKey.SETTED_EVERY_MESSAGE);
        String days = TranslationCategory.DIALOGS.getTranslation(TranslationKey.DAYS_MESSAGE);
        
        JOptionPane.showMessageDialog(null,
                activated + "\n\t" + from + ": " + startPath + "\n\t" + to + ": "
                + destinationPath + setted + " " + timeInterval.toString() + days,
                "AutoBackup", 1);
    }


    // ################################################# Popup items
    public void popupItemInterrupt(int selectedRow, BackupTable backupTable, List<Backup> backups, JMenuItem interruptBackupPopupItem, JMenuItem RunBackupPopupItem) {
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            backupmanager.Entities.Backup backup = backupmanager.Entities.Backup.getBackupByName(backups, backupName);
            ZippingContext context = new ZippingContext(backup, null, backupTable, BackupManagerGUI.progressBar, interruptBackupPopupItem, RunBackupPopupItem);
            BackupOperations.interruptBackupProcess(context);
        }
    }

    public void popupItemRenameBackup(int selectedRow, BackupTable backupTable, List<Backup> backups) {
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(backups, backupName);

            renameBackup(backups, backup);
        }
    }

    public void popupItemOpenDestinationPath(int selectedRow, BackupTable backupTable, List<Backup> backups) {
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(backups, backupName);

            OpenFolder(backup.getDestinationPath());
        }
    }

    public void popupItemOpenInitialPath(int selectedRow, BackupTable backupTable, List<Backup> backups) {
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(backups, backupName);

            OpenFolder(backup.getInitialPath());
        }
    }

    public void popupItemAutoBackup(int selectedRow, BackupTable backupTable, List<Backup> backups, JCheckBoxMenuItem autoBackupMenuItem) {
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(backups, backupName);

            autoBackupMenuItem.setSelected(!backup.isAutoBackup());
            toggleAutomaticBackup(backup);
        }
    }

    public void popupItemCopyDestinationPath(int selectedRow, BackupTable backupTable, List<Backup> backups) {
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(backups, backupName);

            StringSelection selection = new StringSelection(backup.getDestinationPath());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }

    public void popupItemCopyInitialPath(int selectedRow, BackupTable backupTable, List<Backup> backups) {
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(backups, backupName);

            StringSelection selection = new StringSelection(backup.getInitialPath());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }

    public void popupItemCopyBackupName(int selectedRow, BackupTable backupTable, List<Backup> backups) {
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(backups, backupName);

            StringSelection selection = new StringSelection(backup.getBackupName());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }

    public void popupItemRunBackup(int selectedRow, BackupTable backupTable, List<Backup> backups, JMenuItem interruptBackupPopupItem, JMenuItem RunBackupPopupItem) {
        if (selectedRow != -1) {
            
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(backups, backupName);
            
            BackupManagerGUI.progressBar = new BackupProgressGUI(backup.getInitialPath(), backup.getDestinationPath());

            ZippingContext context = new ZippingContext(backup, null, backupTable, BackupManagerGUI.progressBar, interruptBackupPopupItem, RunBackupPopupItem);
            BackupOperations.SingleBackup(context);
        }
    }

    public void popupItemEditBackupName(int selectedRow, BackupTable backupTable, List<Backup> backups) {
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName);

            logger.info("Edit row : " + selectedRow);
            openBackup(backup.getBackupName());
        }
    }

    public void popupItemDuplicateBackup(int selectedRow, BackupTable backupTable, List<Backup> backups) {
        logger.info("Event --> duplicating backup");
        
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(backups, backupName);

            LocalDateTime dateNow = LocalDateTime.now();
            Backup newBackup = new Backup(
                    backup.getBackupName() + "_copy",
                    backup.getInitialPath(),
                    backup.getDestinationPath(),
                    null,
                    backup.isAutoBackup(),
                    backup.getNextDateBackup(),
                    backup.getTimeIntervalBackup(),
                    backup.getNotes(),
                    dateNow,
                    dateNow,
                    0,
                    backup.getMaxBackupsToKeep()
            );
            
            backups.add(newBackup); 
            updateBackupList(backups);
        }
    }

    public void popupItemDelete(int selectedRow, List<Backup> backups, BackupTable backupTable) {
        deleteBackup(selectedRow, backups, backupTable);
    }
}
