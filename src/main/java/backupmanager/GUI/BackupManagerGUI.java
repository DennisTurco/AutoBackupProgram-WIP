package backupmanager.GUI;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.formdev.flatlaf.FlatClientProperties;

import backupmanager.BackupOperations;
import backupmanager.Exporter;
import backupmanager.Dialogs.EntryUserDialog;
import backupmanager.Dialogs.PreferencesDialog;
import backupmanager.Dialogs.TimePicker;
import backupmanager.Email.EmailSender;
import backupmanager.Entities.Backup;
import backupmanager.Entities.BackupList;
import backupmanager.Entities.Preferences;
import backupmanager.Entities.RunningBackups;
import backupmanager.Entities.TimeInterval;
import backupmanager.Entities.User;
import backupmanager.Entities.ZippingContext;
import backupmanager.Enums.ConfigKey;
import backupmanager.Enums.MenuItems;
import backupmanager.Enums.TranslationLoaderEnum;
import backupmanager.Enums.TranslationLoaderEnum.TranslationCategory;
import backupmanager.Enums.TranslationLoaderEnum.TranslationKey;
import backupmanager.Json.JSONAutoBackup;
import backupmanager.Json.JSONConfigReader;
import backupmanager.Json.JsonUser;
import backupmanager.Managers.ThemeManager;
import backupmanager.Services.RunningBackupObserver;
import backupmanager.Services.ZippingThread;
import backupmanager.Table.BackupTable;
import backupmanager.Table.BackupTableModel;
import backupmanager.Table.CheckboxCellRenderer;
import backupmanager.Table.StripedRowRenderer;
import backupmanager.Table.TableDataManager;

/**
 * @author Dennis Turco
 */
public final class BackupManagerGUI extends javax.swing.JFrame {
    private static final Logger logger = LoggerFactory.getLogger(BackupManagerGUI.class);
    private static final JSONConfigReader configReader = new JSONConfigReader(ConfigKey.CONFIG_FILE_STRING.getValue(), ConfigKey.CONFIG_DIRECTORY_STRING.getValue());
    public static final DateTimeFormatter dateForfolderNameFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH.mm.ss");
    public static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    
    public static Backup currentBackup;
    private final RunningBackupObserver observer;
    private static List<Backup> backups;
    private static JSONAutoBackup JSON;
    public static DefaultTableModel model;
    public static BackupTable backupTable;
    public static BackupTableModel tableModel;
    private BackupProgressGUI progressBar;
    private boolean saveChanged;
    private Integer selectedRow;
    private String backupOnText;
    private String backupOffText;
    private final String currentVersion;
    
    public BackupManagerGUI() {
        ThemeManager.updateThemeFrame(this);
        
        initComponents();

        currentVersion = ConfigKey.VERSION.getValue();
        
        // logo application
        Image icon = new ImageIcon(this.getClass().getResource(ConfigKey.LOGO_IMG.getValue())).getImage();
        this.setIconImage(icon);
        
        JSON = new JSONAutoBackup();
        currentBackup = new Backup();
        saveChanged = true;
        
        setAutoBackupOff();

        customListeners();
        
        // load Menu items
        JSONConfigReader config = new JSONConfigReader(ConfigKey.CONFIG_FILE_STRING.getValue(), ConfigKey.CONFIG_DIRECTORY_STRING.getValue());
        MenuBugReport.setVisible(config.isMenuItemEnabled(MenuItems.BugReport.name()));
        MenuPreferences.setVisible(config.isMenuItemEnabled(MenuItems.Preferences.name()));
        MenuClear.setVisible(config.isMenuItemEnabled(MenuItems.Clear.name()));
        MenuDonate.setVisible(config.isMenuItemEnabled(MenuItems.Donate.name()));
        MenuHistory.setVisible(config.isMenuItemEnabled(MenuItems.History.name()));
        MenuInfoPage.setVisible(config.isMenuItemEnabled(MenuItems.InfoPage.name()));
        MenuNew.setVisible(config.isMenuItemEnabled(MenuItems.New.name()));
        MenuQuit.setVisible(config.isMenuItemEnabled(MenuItems.Quit.name()));
        MenuSave.setVisible(config.isMenuItemEnabled(MenuItems.Save.name()));
        MenuSaveWithName.setVisible(config.isMenuItemEnabled(MenuItems.SaveWithName.name()));
        MenuShare.setVisible(config.isMenuItemEnabled(MenuItems.Share.name()));
        MenuSupport.setVisible(config.isMenuItemEnabled(MenuItems.Support.name()));
        MenuWebsite.setVisible(config.isMenuItemEnabled(MenuItems.Website.name()));
        MenuImport.setVisible(config.isMenuItemEnabled(MenuItems.Import.name()));
        MenuExport.setVisible(config.isMenuItemEnabled(MenuItems.Export.name()));
        
        // set app sizes
        setScreenSize();

        // icons
        researchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new com.formdev.flatlaf.extras.FlatSVGIcon("res/img/search.svg", 16, 16));

        // translations
        setTranslations();

        // first initialize the table, then start observer thread
        initializeTable();
        observer = new RunningBackupObserver(backupTable, dateForfolderNameFormatter, 3000);
        observer.start();

        setCurrentBackupMaxBackupsToKeep(configReader.getMaxCountForSameBackup());

        // disable interruption backup operation option
        interruptBackupPopupItem.setEnabled(false);

        lastBackupLabel.setText("");
        
        // set all svg images
        setSvgImages();

        checkForFirstAccess();
    }
    
    private void checkForFirstAccess() {
        logger.debug("Checking for first access");
        try {
            User user = JsonUser.readUserFromJson(ConfigKey.USER_FILE_STRING.getValue(), ConfigKey.CONFIG_DIRECTORY_STRING.getValue());

            if (user != null) {
                logger.info("Current user: " + user.toString());
                return;
            }

            // first access
            EntryUserDialog userDialog = new EntryUserDialog(this, true);
            userDialog.setVisible(true);
            User newUser = userDialog.getUser();

            if (newUser == null) {
                return;
            }

            JsonUser.writeUserToJson(newUser, ConfigKey.USER_FILE_STRING.getValue(), ConfigKey.CONFIG_DIRECTORY_STRING.getValue()); 
            EmailSender.sendUserCreationEmail();
        } catch (IOException e) {
            logger.error("I/O error occurred during read user data: " + e.getMessage(), e);
            JsonUser.writeUserToJson(User.getDefaultUser(), ConfigKey.USER_FILE_STRING.getValue(), ConfigKey.CONFIG_DIRECTORY_STRING.getValue());
        }
    }

    public void showWindow() {
        setVisible(true);
        toFront();
        requestFocus();
    }

    private void setScreenSize() {
        Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.min((int) size.getWidth(), Integer.parseInt(ConfigKey.GUI_WIDTH.getValue()));
        int height = Math.min((int) size.getHeight(), Integer.parseInt(ConfigKey.GUI_HEIGHT.getValue()));

        this.setSize(width,height);
    }
    
    private TimeInterval openTimePicker(TimeInterval time) {
        TimePicker picker = new TimePicker(this, time, true);
        picker.setVisible(true);
        return picker.getTimeInterval();
    }
    
    private void openPreferences() {
        logger.info("Event --> opening preferences dialog");

        PreferencesDialog prefs = new PreferencesDialog(this, true, this);
        prefs.setVisible(true);
    }

    public void reloadPreferences() {
        logger.info("Reloading preferences");

        // load language
        try {
            TranslationLoaderEnum.loadTranslations(ConfigKey.LANGUAGES_DIRECTORY_STRING.getValue() + Preferences.getLanguage().getFileName());
            setTranslations();
        } catch (IOException ex) {
            logger.error("An error occurred during reloading preferences operation: " + ex.getMessage(), ex);
            openExceptionMessage(ex.getMessage(), Arrays.toString(ex.getStackTrace()));
        }
        
        // load theme
        ThemeManager.updateThemeFrame(this);
        ThemeManager.refreshPopup(TablePopup);
        setSvgImages();
    }
    
    private void renameBackup(Backup backup) {
        logger.info("Event --> backup renaming");
        
        String backup_name = getBackupName(false);
        if (backup_name == null || backup_name.isEmpty()) return;
        
        backup.setBackupName(backup_name);
        backup.setLastUpdateDate(LocalDateTime.now());
        BackupOperations.updateBackupList(backups);
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
                    openExceptionMessage(ex.getMessage(), Arrays.toString(ex.getStackTrace()));
                }
            } else {
                logger.warn("Desktop not supported on this operating system");
            }
        } else {
            logger.warn("The folder does not exist or is invalid");

            JOptionPane.showMessageDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_MESSAGE_FOR_FOLDER_NOT_EXISTING), TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_GENERIC_TITLE), JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void savedChanges(boolean saved) {
        if (saved) {
            setCurrentBackupName(currentBackup.getBackupName());
            saveChanged = true;
        } else {
            setCurrentBackupName(currentBackup.getBackupName() + "*");
            saveChanged = false;
        }
    }
    
    public void setAutoBackupPreference(boolean option) {         
        currentBackup.setAutoBackup(option);
        
        if (option) {
            setAutoBackupOn(currentBackup);
        } else {
            disableAutoBackup(currentBackup);
        }
    }
    
    public void setAutoBackupPreference(Backup backup, boolean option) {        
        backup.setAutoBackup(option);
        if (backup.getBackupName().equals(currentBackup.getBackupName())) {
            toggleAutoBackup.setSelected(option);
            if (option) {
                enableTimePickerButton(backup);
            } else {
                disableTimePickerButton();
            }
        }
        if (!option) {
            disableAutoBackup(backup);
        }

        toggleAutoBackup.setText(toggleAutoBackup.isSelected() ? backupOnText : backupOffText);
    }
    
    // it returns true if is correctly setted, false otherwise
    public boolean AutomaticBackup() {
        logger.info("Event --> automatic backup");
        
        if(!BackupOperations.CheckInputCorrect(currentBackup.getBackupName(),startPathField.getText(), destinationPathField.getText(), null)) return false;

        // if the file has not been saved you need to save it before setting the auto backup
        if(currentBackup.isAutoBackup() == false || currentBackup.getNextDateBackup() == null || currentBackup.getTimeIntervalBackup() == null) {
            if (currentBackup.getBackupName() == null || currentBackup.getBackupName().isEmpty()) SaveWithName();
            if (currentBackup.getBackupName() == null || currentBackup.getBackupName().isEmpty()) return false;

            // message
            TimeInterval timeInterval = openTimePicker(null);
            if (timeInterval == null) return false;

            //set date for next backup
            LocalDateTime nextDateBackup = LocalDateTime.now().plusDays(timeInterval.getDays())
                    .plusHours(timeInterval.getHours())
                    .plusMinutes(timeInterval.getMinutes());

            currentBackup.setTimeIntervalBackup(timeInterval);
            currentBackup.setNextDateBackup(nextDateBackup);
            btnPathSearch2.setToolTipText(timeInterval.toString());
            btnPathSearch2.setEnabled(true);

            logger.info("Event --> Next date backup setted to " + nextDateBackup);

            openBackupActivationMessage(timeInterval, null);
        }

        currentBackup.setInitialPath(GetStartPathField());
        currentBackup.setDestinationPath(GetDestinationPathField());
        for (Backup b : backups) {
            if (b.getBackupName().equals(currentBackup.getBackupName())) {
                b.UpdateBackup(currentBackup);
                break;
            }
        }
        BackupOperations.updateBackupList(backups);
        return true;
    }
    
    public boolean AutomaticBackup(Backup backup) {
        logger.info("Event --> automatic backup");
        
        if(!BackupOperations.CheckInputCorrect(backup.getBackupName(), backup.getInitialPath(), backup.getDestinationPath(), null)) return false;
    
        if(backup.isAutoBackup() == false || backup.getNextDateBackup() == null || backup.getTimeIntervalBackup() == null) {
            // if the file has not been saved you need to save it before setting the auto backup
            if (backup.getBackupName() == null || backup.getBackupName().isEmpty()) SaveWithName();
            if (backup.getBackupName() == null || backup.getBackupName().isEmpty()) return false;

            // message
            TimeInterval timeInterval = openTimePicker(null);
            if (timeInterval == null) return false;

            //set date for next backup
            LocalDateTime nextDateBackup = LocalDateTime.now().plusDays(timeInterval.getDays())
                    .plusHours(timeInterval.getHours())
                    .plusMinutes(timeInterval.getMinutes());

            backup.setTimeIntervalBackup(timeInterval);
            backup.setNextDateBackup(nextDateBackup);
            btnPathSearch2.setToolTipText(timeInterval.toString());
            btnPathSearch2.setEnabled(true);

            logger.info("Event --> Next date backup setted to " + nextDateBackup);

            openBackupActivationMessage(timeInterval, backup);
        }

        for (Backup b : backups) {
            if (b.getBackupName().equals(backup.getBackupName())) {
                b.UpdateBackup(backup);
                break;
            }
        }
        
        // if the backup is currentBackup
        if (currentBackup.getBackupName().equals(backup.getBackupName())) 
            currentBackup.UpdateBackup(backup);
        
        BackupOperations.updateBackupList(backups);
        return true;
    }

    private void openBackupActivationMessage(TimeInterval timeInterval, Backup backup) {
        if (timeInterval == null) 
            throw new IllegalArgumentException("Time interval cannot be null");

        String from = TranslationCategory.GENERAL.getTranslation(TranslationKey.FROM);
        String to = TranslationCategory.GENERAL.getTranslation(TranslationKey.TO);
        String activated = TranslationCategory.DIALOGS.getTranslation(TranslationKey.AUTO_BACKUP_ACTIVATED_MESSAGE);
        String setted = TranslationCategory.DIALOGS.getTranslation(TranslationKey.SETTED_EVERY_MESSAGE);
        String days = TranslationCategory.DIALOGS.getTranslation(TranslationKey.DAYS_MESSAGE);

        String startPath = backup != null ? backup.getInitialPath() : GetStartPathField();
        String destinationPath = backup != null ? backup.getDestinationPath() : GetDestinationPathField();
        showMessageActivationAutoBackup(activated, from, to, setted, timeInterval, days, startPath, destinationPath);
    }

    private void showMessageActivationAutoBackup(String activated, String from, String to, String setted, TimeInterval timeInterval, String days, String startPath, String destinationPath) {
        JOptionPane.showMessageDialog(null,
                activated + "\n\t" + from + ": " + startPath + "\n\t" + to + ": "
                + destinationPath + setted + " " + timeInterval.toString() + days,
                "AutoBackup", 1);
    }
    
    private void SaveWithName() {
        logger.info("Event --> save with name");
        
        if (startPathField.getText().length() == 0 || destinationPathField.getText().length() == 0) {
            logger.warn("Unable to save the file. Both the initial and destination paths must be specified and cannot be empty");            
            JOptionPane.showMessageDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_MESSAGE_FOR_SAVING_FILE_WITH_PATHS_EMPTY), TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_GENERIC_TITLE), JOptionPane.ERROR_MESSAGE);
            return;
        }

        String backup_name = getBackupName(true);
        
        if (backup_name == null || backup_name.length() == 0) return;

        try {
            LocalDateTime dateNow = LocalDateTime.now();
            Backup backup = new Backup (
                    backup_name,
                    GetStartPathField(),
                    GetDestinationPathField(),
                    currentBackup.getLastBackup(),
                    currentBackup.isAutoBackup(),
                    currentBackup.getNextDateBackup(),
                    currentBackup.getTimeIntervalBackup(),
                    GetNotesTextArea(),
                    dateNow,
                    dateNow,
                    0,
                    GetMaxBackupsToKeep()
            );
            
            backups.add(backup);
            currentBackup = backup;
            
            BackupOperations.updateBackupList(backups);
            logger.info("Backup '" + currentBackup.getBackupName() + "' saved successfully!");
            JOptionPane.showMessageDialog(this, "Backup '" + currentBackup.getBackupName() + "' " + TranslationCategory.DIALOGS.getTranslation(TranslationKey.BACKUP_SAVED_CORRECTLY_MESSAGE), TranslationCategory.DIALOGS.getTranslation(TranslationKey.BACKUP_SAVED_CORRECTLY_TITLE), JOptionPane.INFORMATION_MESSAGE);
            savedChanges(true);
        } catch (IllegalArgumentException ex) {
            logger.error("An error occurred: "  + ex.getMessage(), ex);
            openExceptionMessage(ex.getMessage(), Arrays.toString(ex.getStackTrace()));
        } catch (HeadlessException ex) {
            logger.error("Error saving backup", ex);
            JOptionPane.showMessageDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_SAVING_BACKUP_MESSAGE), TranslationCategory.GENERAL.getTranslation(TranslationKey.ERROR_GENERIC_TITLE), JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private String getBackupName(boolean canOverwrite) {
        String backup_name;
        
        do {
            backup_name = JOptionPane.showInputDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.BACKUP_NAME_INPUT)); // pop-up message
            for (Backup backup : backups) {
                if (backup.getBackupName().equals(backup_name) && canOverwrite) {
                    int response = JOptionPane.showConfirmDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.DUPLICATED_BACKUP_NAME_MESSAGE), TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_REQUIRED_TITLE), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (response == JOptionPane.YES_OPTION) {
                        backups.remove(backup);
                        break;
                    } else {
                        backup_name = null;
                    }
                } else if (backup.getBackupName().equals(backup_name)) {
                    logger.warn("Error saving backup");
                    JOptionPane.showConfirmDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.BACKUP_NAME_ALREADY_USED_MESSAGE), TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_GENERIC_TITLE), JOptionPane.OK_OPTION, JOptionPane.ERROR_MESSAGE);
                }
            }
            if (backup_name == null) return null;
        } while (backup_name.equals("null") ||  backup_name.equals("null*"));	
        
        if (backup_name.isEmpty()) 
            return null;
        
        return backup_name;
    }
    
    public void SingleBackup(String path1, String path2) {
        logger.info("Event --> single backup");
		
        String temp = "\\";

        //------------------------------INPUT CONTROL ERRORS------------------------------
        if (!BackupOperations.CheckInputCorrect(currentBackup.getBackupName(), path1, path2, null)) return;

        //------------------------------TO GET THE CURRENT DATE------------------------------
        LocalDateTime dateNow = LocalDateTime.now();

        //------------------------------SET ALL THE VARIABLES------------------------------
        String name1; // folder name/initial file
        String date = dateNow.format(dateForfolderNameFormatter);

        //------------------------------SET ALL THE STRINGS------------------------------
        name1 = path1.substring(path1.length()-1, path1.length()-1);

        for(int i=path1.length()-1; i>=0; i--) {
            if(path1.charAt(i) != temp.charAt(0)) name1 = path1.charAt(i) + name1;
            else break;
        }

        name1 = BackupOperations.removeExtension(name1);
        path2 = path2 + "\\" + name1 + " (Backup " + date + ")";

        //------------------------------COPY THE FILE OR DIRECTORY------------------------------
        logger.info("date backup: " + date);
    	
        progressBar = new BackupProgressGUI(path1, path2);
        progressBar.setVisible(true);

        SingleBackup.setEnabled(false);
        setAutoBackupOff();

        ZippingContext context = new ZippingContext(currentBackup, null, backupTable, progressBar, SingleBackup, toggleAutoBackup, interruptBackupPopupItem, RunBackupPopupItem);
        ZippingThread.zipDirectory(path1, path2 + ".zip", context);

        //if current_file_opened is null it means they are not in a backup but it is a backup with no associated json file
        if (currentBackup.getBackupName() != null && !currentBackup.getBackupName().isEmpty()) { 
            currentBackup.setInitialPath(GetStartPathField());
            currentBackup.setDestinationPath(GetDestinationPathField());
            currentBackup.setLastBackup(LocalDateTime.now());
        }
    }
    
    private void setCurrentBackupName(String name) {
        currentFileLabel.setText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.CURRENT_FILE) + ": " + name);
    }
    
    private void setCurrentBackupNotes(String notes) {
        backupNoteTextArea.setText(notes);
    }

    public void setCurrentBackupMaxBackupsToKeep(int maxBackupsCount) {
        maxBackupCountSpinner.setValue(maxBackupsCount);
    }
	
    public void setStringToText() {
        try {
            String last_date = LocalDateTime.now().format(formatter);
            lastBackupLabel.setText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.LAST_BACKUP) + last_date);
        } catch(Exception ex) {
            logger.error("An error occurred: " + ex.getMessage(), ex);
            openExceptionMessage(ex.getMessage(), Arrays.toString(ex.getStackTrace()));
        }
    }
	
    public void setTextValues() {
        try {
            updateCurrentFiedsByBackup(currentBackup);
        } catch (IllegalArgumentException ex) {
            logger.error("An error occurred: " + ex.getMessage(), ex);
            openExceptionMessage(ex.getMessage(), Arrays.toString(ex.getStackTrace()));
        }
        setAutoBackupPreference(currentBackup.isAutoBackup());
    }
    
    private void customListeners() {
        startPathField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { 
                somethingHasChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                somethingHasChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {}
        });
        
        destinationPathField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                somethingHasChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                somethingHasChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {}
        });
        
        backupNoteTextArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                somethingHasChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                somethingHasChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {}
        });
    }

    private void somethingHasChanged() {
        boolean backupNameNotNull = currentBackup.getBackupName() != null;
        boolean pathsOrNotesChanged = 
                !startPathField.getText().equals(currentBackup.getInitialPath()) ||
                !destinationPathField.getText().equals(currentBackup.getDestinationPath()) ||
                !backupNoteTextArea.getText().equals(currentBackup.getNotes());

        if (backupNameNotNull && !pathsOrNotesChanged) {
            savedChanges(true);
        } else {
            savedChanges(false);
        }
    }
    
    public String GetStartPathField() {
        return startPathField.getText();
    }
    public String GetDestinationPathField() {
        return destinationPathField.getText();
    }
    public String GetNotesTextArea() {
        return backupNoteTextArea.getText();
    }
    public boolean GetAutomaticBackupPreference() {
        return toggleAutoBackup.isSelected();
    }
    public int GetMaxBackupsToKeep() {
        return (int) maxBackupCountSpinner.getValue();
    }
    public void SetStartPathField(String text) {
        startPathField.setText(text);
    }
    public void SetDestinationPathField(String text) {
        destinationPathField.setText(text);
    }
    public void SetLastBackupLabel(LocalDateTime date) {
        if (date != null) {
            String dateStr = date.format(formatter);
            dateStr = TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.LAST_BACKUP) + ": " + dateStr;
            lastBackupLabel.setText(dateStr);
        }
        else lastBackupLabel.setText("");
    }
    
    public static void openExceptionMessage(String errorMessage, String stackTrace) {
        Object[] options = {TranslationCategory.GENERAL.getTranslation(TranslationKey.CLOSE_BUTTON), TranslationCategory.DIALOGS.getTranslation(TranslationKey.EXCEPTION_MESSAGE_CLIPBOARD_BUTTON), TranslationCategory.DIALOGS.getTranslation(TranslationKey.EXCEPTION_MESSAGE_REPORT_BUTTON)};

        if (errorMessage == null) {
            errorMessage = "";
        }

        stackTrace = !errorMessage.isEmpty() ? errorMessage + "\n" + stackTrace : errorMessage + stackTrace;

        EmailSender.sendErrorEmail("Critical Error Report", stackTrace);

        String stackTraceMessage = TranslationCategory.DIALOGS.getTranslation(TranslationKey.EXCEPTION_MESSAGE_REPORT_MESSAGE) + "\n" + stackTrace;

        int choice;

        // Set a maximum width for the error message
        final int MAX_WIDTH = 500;

        // Keep displaying the dialog until the "Close" option (index 0) is chosen
        do {
            if (stackTraceMessage.length() > 1500) {
                stackTraceMessage = stackTraceMessage.substring(0, 1500) + "...";
            }

            // Create a JTextArea to hold the error message with line wrapping
            JTextArea messageArea = new JTextArea(stackTraceMessage);
            messageArea.setLineWrap(true);
            messageArea.setWrapStyleWord(true);
            messageArea.setEditable(false);
            messageArea.setColumns(50); // Approximate width, adjust as necessary

            // Limit the maximum width
            messageArea.setSize(new Dimension(MAX_WIDTH, Integer.MAX_VALUE));
            messageArea.setPreferredSize(new Dimension(MAX_WIDTH, messageArea.getPreferredSize().height));

            // Put the JTextArea in a JScrollPane for scrollable display if needed
            JScrollPane scrollPane = new JScrollPane(messageArea);
            scrollPane.setPreferredSize(new Dimension(MAX_WIDTH, 300));

            // Display the option dialog with the JScrollPane
            String error = TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_GENERIC_TITLE);
            choice = JOptionPane.showOptionDialog(
                null,
                scrollPane,                           // The JScrollPane containing the error message
                error,                                // The error message/title
                JOptionPane.DEFAULT_OPTION,           // Option type (default option type)
                JOptionPane.ERROR_MESSAGE,            // Message type (error message icon)
                null,                            // Icon (null means default icon)
                options,                              // The options for the buttons
                options[0]                            // The default option (Close)
            );

            if (choice == 1) {
                StringSelection selection = new StringSelection(stackTrace);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
                logger.info("Error text has been copied to the clipboard");
                JOptionPane.showMessageDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.EXCEPTION_MESSAGE_CLIPBOARD_MESSAGE));
            } else if (choice == 2) {
                openWebSite(ConfigKey.ISSUE_PAGE_LINK.getValue());
            }
        } while (choice == 1 || choice == 2);
    }
    
    private static void openWebSite(String reportUrl) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI(reportUrl));
                }
            }
        } catch (IOException | URISyntaxException e) {
            logger.error("Failed to open the web page: " + e.getMessage(), e);
            JOptionPane.showMessageDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_MESSAGE_OPENING_WEBSITE), TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_GENERIC_TITLE), JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void displayBackupList(List<Backup> backups) {
        BackupTableModel model = new BackupTableModel(getColumnTranslations(), 0);
    
        // Populate the model with backup data
        for (Backup backup : backups) {
            model.addRow(new Object[]{
                backup.getBackupName(),
                backup.getInitialPath(),
                backup.getDestinationPath(),
                backup.getLastBackup() != null ? backup.getLastBackup().format(formatter) : "",
                backup.isAutoBackup(),
                backup.getNextDateBackup() != null ? backup.getNextDateBackup().format(formatter) : "",
                backup.getTimeIntervalBackup() != null ? backup.getTimeIntervalBackup().toString() : ""
            });
        }
    
        backupTable = new BackupTable(model);

        // Add key bindings using InputMap and ActionMap
        InputMap inputMap = backupTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = backupTable.getActionMap();

        // Handle Enter key
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enterKey");
        actionMap.put("enterKey", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int selectedRow = backupTable.getSelectedRow();
                if (selectedRow == -1) return;

                logger.debug("Enter key pressed on row: " + selectedRow);
                OpenBackup((String) backupTable.getValueAt(selectedRow, 0));

                if (TabbedPane != null) {
                    TabbedPane.setSelectedIndex(0);
                }
            }
        });

        // Handle Delete key
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteKey");
        actionMap.put("deleteKey", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int[] selectedRows = backupTable.getSelectedRows();
                if (selectedRows.length == 0) return;
        
                logger.debug("Delete key pressed on rows: " + Arrays.toString(selectedRows));

                int response = JOptionPane.showConfirmDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_DELETION_MESSAGE), TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_DELETION_TITLE), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (response != JOptionPane.YES_OPTION) {
                    return;
                }

                for (int row : selectedRows) {
                    deleteBackup(row, false);
                }
            }
        });

        // Apply renderers for each column
        TableColumnModel columnModel = backupTable.getColumnModel();

        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            if (i == 4) {
                columnModel.getColumn(i).setCellRenderer(new CheckboxCellRenderer());
                columnModel.getColumn(i).setCellEditor(backupTable.getDefaultEditor(Boolean.class));
            } else {
                columnModel.getColumn(i).setCellRenderer(new StripedRowRenderer());
            }
        }
            
        // Add the existing mouse listener to the new table
        backupTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tableMouseClicked(evt); // Reuse the existing method
            }
        });
    
        // Update the global model reference
        BackupManagerGUI.model = model;
    
        // Replace the existing table in the GUI
        JScrollPane scrollPane = (JScrollPane) table.getParent().getParent();
        table = backupTable; // Update the reference to the new table
        scrollPane.setViewportView(table); // Replace the table in the scroll pane
    }
    
    // Method to properly encode the URI with special characters (spaces, symbols, etc.)
    private static String encodeURI(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (IOException e) {
            logger.error("An error occurred while trying to get the encode URI for string \"" + value + "\": " + e.getMessage(), e);
            return value; // If encoding fails, return the original value
        }
    }

    public boolean Clear(boolean canMessageAppear) {
        logger.info("Event --> clear");

        if (canMessageAppear && ((!saveChanged && !currentBackup.getBackupName().isEmpty()) || (!startPathField.getText().isEmpty() || !destinationPathField.getText().isEmpty() || !backupNoteTextArea.getText().isEmpty()))) {
            int response = JOptionPane.showConfirmDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_MESSAGE_FOR_CLEAR), TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_REQUIRED_TITLE), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (response != JOptionPane.YES_OPTION) {
                return false;
            }
        }
        
        saveChanged = false;
        setCurrentBackupName("");
        startPathField.setText("");
        destinationPathField.setText("");
        lastBackupLabel.setText("");
        backupNoteTextArea.setText("");
        maxBackupCountSpinner.setValue(configReader.getMaxCountForSameBackup());

        return true;
    }
        
    private void RemoveBackup(String backupName) {
        logger.info("Event --> removing backup" + backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName).toString());

        // backup list update
        for (Backup backup : backups) {
            if (backupName.equals(backup.getBackupName())) {
                backups.remove(backup);
                logger.info("Backup removed successfully: " + backup.toString());
                break;
            }
        }

        BackupOperations.updateBackupList(backups);
    }
    
    private void saveFile() {
        logger.info("Event --> saving backup");
        
        if (startPathField.getText().length() == 0 || destinationPathField.getText().length() == 0) {
            logger.warn("Unable to save the file. Both the initial and destination paths must be specified and cannot be empty");
            JOptionPane.showMessageDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_MESSAGE_FOR_SAVING_FILE_WITH_PATHS_EMPTY), TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_GENERIC_TITLE), JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (currentBackup.getBackupName() == null || currentBackup.getBackupName().isEmpty()) {
            SaveWithName();
        }

        try {
            currentBackup.setInitialPath(GetStartPathField());
            currentBackup.setDestinationPath(GetDestinationPathField());
            currentBackup.setNotes(GetNotesTextArea());
            currentBackup.setMaxBackupsToKeep(GetMaxBackupsToKeep());
            
            LocalDateTime dateNow = LocalDateTime.now();
            currentBackup.setLastUpdateDate(dateNow);
            
            for (Backup b : backups) {
                if (b.getBackupName().equals(currentBackup.getBackupName())) {
                    b.UpdateBackup(currentBackup);
                    break;
                }
            }
            BackupOperations.updateBackupList(backups);
            savedChanges(true);
        } catch (IllegalArgumentException ex) {
            logger.error("An error occurred: " + ex.getMessage(), ex);
            openExceptionMessage(ex.getMessage(), Arrays.toString(ex.getStackTrace()));
        }
    }
    
    private void OpenBackup(String backupName) {
        logger.info("Event --> opening backup");
        
        // if canges are not saved and if something has been written
        if (!saveChanged && (startPathField.getText().length() != 0 || destinationPathField.getText().length() != 0 || backupNoteTextArea.getText().length() != 0)) {
            int response = JOptionPane.showConfirmDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_MESSAGE_FOR_UNSAVED_CHANGES), TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_REQUIRED_TITLE), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (response == JOptionPane.YES_OPTION) {
                saveFile();
            } else if (response == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }
        
        try {
            for(Backup backup : backups) {
                if (backup.getBackupName().equals(backupName)) {
                    currentBackup = backup;
                    break;
                }
            }
            
            updateCurrentFiedsByBackup(currentBackup);
            backupNoteTextArea.setEnabled(true);
            savedChanges(true);
        } catch (IllegalArgumentException ex) {
            logger.error("An error occurred: " + ex.getMessage(), ex);
            openExceptionMessage(ex.getMessage(), Arrays.toString(ex.getStackTrace()));
        }
    }
    
    private void researchInTable() {
        List<Backup> tempBackups = new ArrayList<>();
        
        String research = researchField.getText();
        
        for (Backup backup : backups) {
            if (backup.getBackupName().contains(research) || 
                    backup.getInitialPath().contains(research) || 
                    backup.getDestinationPath().contains(research) || 
                    (backup.getLastBackup() != null && backup.getLastBackup().toString().contains(research)) ||
                    (backup.getNextDateBackup() != null && backup.getNextDateBackup().toString().contains(research)) ||
                    (backup.getTimeIntervalBackup() != null && backup.getTimeIntervalBackup().toString().contains(research))) {
                tempBackups.add(backup);
            }
        }
        
        TableDataManager.updateTableWithNewBackupList(tempBackups, formatter);
    }
    
    private void updateCurrentFiedsByBackup(Backup backup) {
        SetStartPathField(backup.getInitialPath());
        SetDestinationPathField(backup.getDestinationPath());
        SetLastBackupLabel(backup.getLastUpdateDate());
        setAutoBackupPreference(backup.isAutoBackup());
        setCurrentBackupName(backup.getBackupName());
        setCurrentBackupNotes(backup.getNotes());
        setCurrentBackupMaxBackupsToKeep(backup.getMaxBackupsToKeep());
        
        if (backup.getTimeIntervalBackup() != null) {
            setAutoBackupOn(backup);
        } else {
            setAutoBackupOff();
        }  
    }
    
    private void NewBackup() {
        logger.info("Event --> new backup");
        
        if (!saveChanged) {    
            int response = JOptionPane.showConfirmDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_MESSAGE_FOR_UNSAVED_CHANGES), TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_REQUIRED_TITLE), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (response == JOptionPane.YES_OPTION) {
                saveFile();
            } else if (response == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }
        
        if (!Clear(false)) {
            return;
        }
        currentBackup = new Backup();
        currentBackup.setAutoBackup(false);
        currentBackup.setBackupName("");
        currentBackup.setMaxBackupsToKeep(configReader.getMaxCountForSameBackup());
        
        // basic auto enable is disabled
        setAutoBackupPreference(currentBackup.isAutoBackup());

        // I remove the current open backup
        setCurrentBackupName("untitled*");
    }
    
    private void disableAutoBackup(Backup backup) {
        logger.info("Event --> auto backup disabled");
                
        backup.setTimeIntervalBackup(null);
        backup.setNextDateBackup(null);
        backup.setAutoBackup(false);
        backup.setLastUpdateDate(LocalDateTime.now());
        BackupOperations.updateBackupList(backups);
        
        // if the backup is the current backup i have to update the main panel
        if (backup.getBackupName().equals(currentBackup.getBackupName())) {
            setAutoBackupOff();
        }
    }
    
    private void maxBackupCountSpinnerChange() {        
        Integer backupCount = (Integer) maxBackupCountSpinner.getValue();
        
        if (backupCount == null || backupCount < 1) {
            maxBackupCountSpinner.setValue(1);
        }  else if (backupCount > 10) {
            maxBackupCountSpinner.setValue(10);
        } 
    }

    private void mouseWeel(java.awt.event.MouseWheelEvent evt) {
        javax.swing.JSpinner spinner = (javax.swing.JSpinner) evt.getSource();
        int rotation = evt.getWheelRotation();

        if (rotation < 0) {
            spinner.setValue((Integer) spinner.getValue() + 1);
        } else {
            spinner.setValue((Integer) spinner.getValue() - 1);
        }

        if ((int) spinner.getValue() != currentBackup.getMaxBackupsToKeep()) 
            savedChanges(false);
        else 
            savedChanges(true);
    }


    private void setAutoBackupOn(Backup backup) {
        toggleAutoBackup.setSelected(true);
        toggleAutoBackup.setText(backupOnText);

        if (backup != null)
            enableTimePickerButton(backup);
        else
            disableTimePickerButton();
    }

    private void setAutoBackupOff() {
        toggleAutoBackup.setSelected(false);
        toggleAutoBackup.setText(backupOffText);
        disableTimePickerButton();
    }

    private void enableTimePickerButton(Backup backup) {
        if (backup.getTimeIntervalBackup() != null) {
            btnTimePicker.setToolTipText(backup.getTimeIntervalBackup().toString());
            btnTimePicker.setEnabled(true);
        } else {
            btnTimePicker.setEnabled(true);
        }  
    }

    private void disableTimePickerButton() {
        btnTimePicker.setToolTipText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.TIME_PICKER_TOOLTIP));
        btnTimePicker.setEnabled(false);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     *  
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        TablePopup = new javax.swing.JPopupMenu();
        EditPoputItem = new javax.swing.JMenuItem();
        DeletePopupItem = new javax.swing.JMenuItem();
        interruptBackupPopupItem = new javax.swing.JMenuItem();
        DuplicatePopupItem = new javax.swing.JMenuItem();
        renamePopupItem = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        OpenInitialFolderItem = new javax.swing.JMenuItem();
        OpenInitialDestinationItem = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        Backup = new javax.swing.JMenu();
        RunBackupPopupItem = new javax.swing.JMenuItem();
        AutoBackupMenuItem = new javax.swing.JCheckBoxMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        jMenu4 = new javax.swing.JMenu();
        CopyBackupNamePopupItem = new javax.swing.JMenuItem();
        CopyInitialPathPopupItem = new javax.swing.JMenuItem();
        CopyDestinationPathPopupItem = new javax.swing.JMenuItem();
        TabbedPane = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        txtTitle = new javax.swing.JLabel();
        currentFileLabel = new javax.swing.JLabel();
        startPathField = new javax.swing.JTextField();
        destinationPathField = new javax.swing.JTextField();
        lastBackupLabel = new javax.swing.JLabel();
        SingleBackup = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        backupNoteTextArea = new javax.swing.JTextArea();
        jLabel2 = new javax.swing.JLabel();
        toggleAutoBackup = new javax.swing.JToggleButton();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0));
        jLabel4 = new javax.swing.JLabel();
        maxBackupCountSpinner = new javax.swing.JSpinner();
        btnPathSearch1 = new backupmanager.svg.SVGButton();
        btnPathSearch2 = new backupmanager.svg.SVGButton();
        btnTimePicker = new backupmanager.svg.SVGButton();
        jPanel2 = new javax.swing.JPanel();
        tablePanel = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        table = new javax.swing.JTable();
        jLabel1 = new javax.swing.JLabel();
        researchField = new javax.swing.JTextField();
        ExportLabel = new javax.swing.JLabel();
        exportAsCsvBtn = new backupmanager.svg.SVGButton();
        exportAsPdfBtn = new backupmanager.svg.SVGButton();
        addBackupEntryButton = new backupmanager.svg.SVGButton();
        detailsPanel = new javax.swing.JPanel();
        detailsLabel = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        MenuNew = new backupmanager.svg.SVGMenuItem();
        MenuSave = new backupmanager.svg.SVGMenuItem();
        MenuSaveWithName = new backupmanager.svg.SVGMenuItem();
        jSeparator4 = new javax.swing.JPopupMenu.Separator();
        MenuImport = new backupmanager.svg.SVGMenuItem();
        MenuExport = new backupmanager.svg.SVGMenuItem();
        jSeparator5 = new javax.swing.JPopupMenu.Separator();
        MenuClear = new backupmanager.svg.SVGMenuItem();
        MenuHistory = new backupmanager.svg.SVGMenuItem();
        jMenu2 = new javax.swing.JMenu();
        MenuPreferences = new backupmanager.svg.SVGMenuItem();
        MenuQuit = new backupmanager.svg.SVGMenuItem();
        jMenu3 = new javax.swing.JMenu();
        MenuWebsite = new backupmanager.svg.SVGMenuItem();
        MenuInfoPage = new backupmanager.svg.SVGMenuItem();
        MenuShare = new backupmanager.svg.SVGMenuItem();
        MenuDonate = new backupmanager.svg.SVGMenuItem();
        jMenu5 = new javax.swing.JMenu();
        MenuBugReport = new backupmanager.svg.SVGMenuItem();
        MenuSupport = new backupmanager.svg.SVGMenuItem();

        EditPoputItem.setText("Edit");
        EditPoputItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditPoputItemActionPerformed(evt);
            }
        });
        TablePopup.add(EditPoputItem);

        DeletePopupItem.setText("Delete");
        DeletePopupItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DeletePopupItemActionPerformed(evt);
            }
        });
        TablePopup.add(DeletePopupItem);

        interruptBackupPopupItem.setText("Interrupt");
        interruptBackupPopupItem.setToolTipText("");
        interruptBackupPopupItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                interruptBackupPopupItemActionPerformed(evt);
            }
        });
        TablePopup.add(interruptBackupPopupItem);

        DuplicatePopupItem.setText("Duplicate");
        DuplicatePopupItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DuplicatePopupItemActionPerformed(evt);
            }
        });
        TablePopup.add(DuplicatePopupItem);

        renamePopupItem.setText("Rename backup");
        renamePopupItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                renamePopupItemActionPerformed(evt);
            }
        });
        TablePopup.add(renamePopupItem);
        TablePopup.add(jSeparator1);

        OpenInitialFolderItem.setText("Open initial folder");
        OpenInitialFolderItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                OpenInitialFolderItemActionPerformed(evt);
            }
        });
        TablePopup.add(OpenInitialFolderItem);

        OpenInitialDestinationItem.setText("Open destination folder");
        OpenInitialDestinationItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                OpenInitialDestinationItemActionPerformed(evt);
            }
        });
        TablePopup.add(OpenInitialDestinationItem);
        TablePopup.add(jSeparator3);

        Backup.setText("Backup");

        RunBackupPopupItem.setText("Run single backup");
        RunBackupPopupItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RunBackupPopupItemActionPerformed(evt);
            }
        });
        Backup.add(RunBackupPopupItem);

        AutoBackupMenuItem.setSelected(true);
        AutoBackupMenuItem.setText("Auto Backup");
        AutoBackupMenuItem.setToolTipText("");
        AutoBackupMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AutoBackupMenuItemActionPerformed(evt);
            }
        });
        Backup.add(AutoBackupMenuItem);

        TablePopup.add(Backup);
        TablePopup.add(jSeparator2);

        jMenu4.setText("Copy text");

        CopyBackupNamePopupItem.setText("Copy backup name");
        CopyBackupNamePopupItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CopyBackupNamePopupItemActionPerformed(evt);
            }
        });
        jMenu4.add(CopyBackupNamePopupItem);

        CopyInitialPathPopupItem.setText("Copy initial path");
        CopyInitialPathPopupItem.setToolTipText("");
        CopyInitialPathPopupItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CopyInitialPathPopupItemActionPerformed(evt);
            }
        });
        jMenu4.add(CopyInitialPathPopupItem);

        CopyDestinationPathPopupItem.setText("Copy destination path");
        CopyDestinationPathPopupItem.setToolTipText("");
        CopyDestinationPathPopupItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CopyDestinationPathPopupItemActionPerformed(evt);
            }
        });
        jMenu4.add(CopyDestinationPathPopupItem);

        TablePopup.add(jMenu4);

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Backup Manager");
        setResizable(false);

        jPanel1.setMaximumSize(new java.awt.Dimension(464, 472));

        txtTitle.setFont(new java.awt.Font("Segoe UI", 0, 36)); // NOI18N
        txtTitle.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        txtTitle.setLabelFor(txtTitle);
        txtTitle.setText("Backup Entry");
        txtTitle.setToolTipText("");
        txtTitle.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        txtTitle.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);

        currentFileLabel.setText("Current file: ");

        startPathField.setToolTipText("(Required) Initial path");
        startPathField.setActionCommand("null");
        startPathField.setAlignmentX(0.0F);
        startPathField.setAlignmentY(0.0F);
        startPathField.setAutoscrolls(false);
        startPathField.setMaximumSize(new java.awt.Dimension(465, 26));
        startPathField.setMinimumSize(new java.awt.Dimension(465, 26));
        startPathField.setPreferredSize(new java.awt.Dimension(465, 26));

        destinationPathField.setToolTipText("(Required) Destination path");
        destinationPathField.setActionCommand("<Not Set>");
        destinationPathField.setAlignmentX(0.0F);
        destinationPathField.setAlignmentY(0.0F);
        destinationPathField.setMaximumSize(new java.awt.Dimension(465, 26));
        destinationPathField.setMinimumSize(new java.awt.Dimension(465, 26));
        destinationPathField.setPreferredSize(new java.awt.Dimension(465, 26));

        lastBackupLabel.setText("last backup: ");

        SingleBackup.setBackground(new java.awt.Color(51, 153, 255));
        SingleBackup.setForeground(new java.awt.Color(255, 255, 255));
        SingleBackup.setText("Single Backup");
        SingleBackup.setToolTipText("Perform the backup");
        SingleBackup.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        SingleBackup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SingleBackupActionPerformed(evt);
            }
        });

        backupNoteTextArea.setColumns(20);
        backupNoteTextArea.setRows(5);
        backupNoteTextArea.setToolTipText("(Optional) Backup description");
        backupNoteTextArea.setMaximumSize(new java.awt.Dimension(232, 84));
        backupNoteTextArea.setMinimumSize(new java.awt.Dimension(232, 84));
        jScrollPane2.setViewportView(backupNoteTextArea);

        jLabel2.setText("notes:");

        toggleAutoBackup.setText("Auto Backup");
        toggleAutoBackup.setToolTipText("Enable/Disable automatic backup");
        toggleAutoBackup.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        toggleAutoBackup.setPreferredSize(new java.awt.Dimension(108, 27));
        toggleAutoBackup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleAutoBackupActionPerformed(evt);
            }
        });

        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jLabel4.setText("Keep only last");

        maxBackupCountSpinner.setToolTipText("Maximum number of backups before removing the oldest.");
        maxBackupCountSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                maxBackupCountSpinnerStateChanged(evt);
            }
        });
        maxBackupCountSpinner.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                maxBackupCountSpinnerMouseWheelMoved(evt);
            }
        });

        btnPathSearch1.setToolTipText("");
        btnPathSearch1.setMaximumSize(new java.awt.Dimension(35, 35));
        btnPathSearch1.setMinimumSize(new java.awt.Dimension(35, 35));
        btnPathSearch1.setPreferredSize(new java.awt.Dimension(35, 35));
        btnPathSearch1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPathSearch1ActionPerformed(evt);
            }
        });

        btnPathSearch2.setToolTipText("Open file explorer");
        btnPathSearch2.setMaximumSize(new java.awt.Dimension(35, 35));
        btnPathSearch2.setMinimumSize(new java.awt.Dimension(35, 35));
        btnPathSearch2.setPreferredSize(new java.awt.Dimension(35, 35));
        btnPathSearch2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPathSearch2ActionPerformed(evt);
            }
        });

        btnTimePicker.setToolTipText("Time picker");
        btnTimePicker.setMaximumSize(new java.awt.Dimension(36, 36));
        btnTimePicker.setMinimumSize(new java.awt.Dimension(36, 36));
        btnTimePicker.setPreferredSize(new java.awt.Dimension(36, 36));
        btnTimePicker.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnTimePickerActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(248, 248, 248)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(lastBackupLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 461, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 462, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addComponent(currentFileLabel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(txtTitle, javax.swing.GroupLayout.PREFERRED_SIZE, 466, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(destinationPathField, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(startPathField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(4, 4, 4)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(btnPathSearch1, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(btnPathSearch2, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(maxBackupCountSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(0, 387, Short.MAX_VALUE)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(SingleBackup, javax.swing.GroupLayout.PREFERRED_SIZE, 188, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(toggleAutoBackup, javax.swing.GroupLayout.PREFERRED_SIZE, 188, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnTimePicker, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(345, 345, 345))
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel1Layout.createSequentialGroup()
                    .addGap(0, 0, Short.MAX_VALUE)
                    .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(0, 0, Short.MAX_VALUE)))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(49, 49, 49)
                .addComponent(txtTitle, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(currentFileLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(startPathField, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(btnPathSearch1, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(destinationPathField, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnPathSearch2, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 190, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lastBackupLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(SingleBackup, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnTimePicker, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(toggleAutoBackup, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(maxBackupCountSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4))
                .addGap(20, 20, 20))
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel1Layout.createSequentialGroup()
                    .addGap(0, 0, Short.MAX_VALUE)
                    .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(0, 0, Short.MAX_VALUE)))
        );

        TabbedPane.addTab("BackupEntry", jPanel1);

        tablePanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tablePanelMouseClicked(evt);
            }
        });

        table.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Backup name", "Initial path", "Destination path", "Last backup", "Auto backup", "Next date backup", "Days interval backup"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.Boolean.class, java.lang.String.class, java.lang.Integer.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        table.setCursor(new java.awt.Cursor(java.awt.Cursor.CROSSHAIR_CURSOR));
        table.setRowHeight(50);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tableMouseClicked(evt);
            }
        });
        jScrollPane1.setViewportView(table);

        jLabel1.setFont(new java.awt.Font("Segoe UI", 0, 20)); // NOI18N
        jLabel1.setText("|");
        jLabel1.setAlignmentY(0.0F);

        researchField.setToolTipText("Research bar");
        researchField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                researchFieldKeyTyped(evt);
            }
        });

        ExportLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        ExportLabel.setText("Export As:");

        exportAsCsvBtn.setToolTipText("Export as .csv");
        exportAsCsvBtn.setMaximumSize(new java.awt.Dimension(32, 32));
        exportAsCsvBtn.setMinimumSize(new java.awt.Dimension(32, 32));
        exportAsCsvBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportAsCsvBtnActionPerformed(evt);
            }
        });

        exportAsPdfBtn.setToolTipText("Export as .pdf");
        exportAsPdfBtn.setMaximumSize(new java.awt.Dimension(32, 32));
        exportAsPdfBtn.setMinimumSize(new java.awt.Dimension(32, 32));
        exportAsPdfBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportAsPdfBtnActionPerformed(evt);
            }
        });

        addBackupEntryButton.setToolTipText("Add new backup");
        addBackupEntryButton.setMaximumSize(new java.awt.Dimension(32, 32));
        addBackupEntryButton.setMinimumSize(new java.awt.Dimension(32, 32));
        addBackupEntryButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addBackupEntryButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout tablePanelLayout = new javax.swing.GroupLayout(tablePanel);
        tablePanel.setLayout(tablePanelLayout);
        tablePanelLayout.setHorizontalGroup(
            tablePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tablePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tablePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 950, Short.MAX_VALUE)
                    .addGroup(tablePanelLayout.createSequentialGroup()
                        .addComponent(addBackupEntryButton, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(9, 9, 9)
                        .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 9, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(researchField, javax.swing.GroupLayout.PREFERRED_SIZE, 321, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(ExportLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 238, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exportAsCsvBtn, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exportAsPdfBtn, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(1, 1, 1)))
                .addContainerGap())
        );
        tablePanelLayout.setVerticalGroup(
            tablePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tablePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tablePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(tablePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(exportAsCsvBtn, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGroup(tablePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel1)
                            .addComponent(researchField, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(ExportLabel)))
                    .addComponent(addBackupEntryButton, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exportAsPdfBtn, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 485, Short.MAX_VALUE)
                .addContainerGap())
        );

        researchField.getAccessibleContext().setAccessibleName("");

        detailsLabel.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        javax.swing.GroupLayout detailsPanelLayout = new javax.swing.GroupLayout(detailsPanel);
        detailsPanel.setLayout(detailsPanelLayout);
        detailsPanelLayout.setHorizontalGroup(
            detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, detailsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(detailsLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        detailsPanelLayout.setVerticalGroup(
            detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(detailsPanelLayout.createSequentialGroup()
                .addComponent(detailsLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 78, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(detailsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(tablePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(tablePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(detailsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        TabbedPane.addTab("BackupList", jPanel2);

        jLabel3.setText("Version 2.0.2");

        jMenu1.setText("File");

        MenuNew.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        MenuNew.setText("New");
        MenuNew.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MenuNewActionPerformed(evt);
            }
        });
        jMenu1.add(MenuNew);

        MenuSave.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        MenuSave.setText("Save");
        MenuSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MenuSaveActionPerformed(evt);
            }
        });
        jMenu1.add(MenuSave);

        MenuSaveWithName.setText("Save with name");
        MenuSaveWithName.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MenuSaveWithNameActionPerformed(evt);
            }
        });
        jMenu1.add(MenuSaveWithName);
        jMenu1.add(jSeparator4);

        MenuImport.setText("Import backup list");
        MenuImport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MenuImportActionPerformed(evt);
            }
        });
        jMenu1.add(MenuImport);

        MenuExport.setText("Export backup list");
        MenuExport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MenuExportActionPerformed(evt);
            }
        });
        jMenu1.add(MenuExport);
        jMenu1.add(jSeparator5);

        MenuClear.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        MenuClear.setText("Clear");
        MenuClear.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MenuClearActionPerformed(evt);
            }
        });
        jMenu1.add(MenuClear);

        MenuHistory.setText("History");
        MenuHistory.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MenuHistoryActionPerformed(evt);
            }
        });
        jMenu1.add(MenuHistory);

        jMenuBar1.add(jMenu1);

        jMenu2.setText("Options");

        MenuPreferences.setText("Preferences");
        MenuPreferences.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MenuPreferencesActionPerformed(evt);
            }
        });
        jMenu2.add(MenuPreferences);

        MenuQuit.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, java.awt.event.InputEvent.ALT_DOWN_MASK));
        MenuQuit.setText("Quit");
        MenuQuit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MenuQuitActionPerformed(evt);
            }
        });
        jMenu2.add(MenuQuit);

        jMenuBar1.add(jMenu2);

        jMenu3.setText("About");

        MenuWebsite.setText("Website");
        MenuWebsite.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MenuWebsiteActionPerformed(evt);
            }
        });
        jMenu3.add(MenuWebsite);

        MenuInfoPage.setText("Info");
        MenuInfoPage.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MenuInfoPageActionPerformed(evt);
            }
        });
        jMenu3.add(MenuInfoPage);

        MenuShare.setText("Share");
        MenuShare.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MenuShareActionPerformed(evt);
            }
        });
        jMenu3.add(MenuShare);

        MenuDonate.setText("Donate");
        MenuDonate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MenuDonateActionPerformed(evt);
            }
        });
        jMenu3.add(MenuDonate);

        jMenuBar1.add(jMenu3);

        jMenu5.setText("Help");

        MenuBugReport.setText("Report a bug");
        MenuBugReport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MenuBugReportActionPerformed(evt);
            }
        });
        jMenu5.add(MenuBugReport);

        MenuSupport.setText("Support");
        MenuSupport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MenuSupportActionPerformed(evt);
            }
        });
        jMenu5.add(MenuSupport);

        jMenuBar1.add(jMenu5);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(TabbedPane, javax.swing.GroupLayout.Alignment.TRAILING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(TabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 636, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jLabel3)
                .addContainerGap(7, Short.MAX_VALUE))
        );

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void MenuQuitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MenuQuitActionPerformed
        logger.info("Event --> exit");
        observer.stop();
        System.exit(EXIT_ON_CLOSE);
    }//GEN-LAST:event_MenuQuitActionPerformed

    private void MenuHistoryActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MenuHistoryActionPerformed
        logger.info("Event --> history");
        try {
            logger.debug("Opening log file with path: " + ConfigKey.LOG_DIRECTORY_STRING.getValue() + ConfigKey.LOG_FILE_STRING.getValue());
            new ProcessBuilder("notepad.exe", ConfigKey.LOG_DIRECTORY_STRING.getValue() + ConfigKey.LOG_FILE_STRING.getValue()).start();
        } catch (IOException e) {
            logger.error("Error opening history file: " + e.getMessage(), e);
            JOptionPane.showMessageDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_MESSAGE_OPEN_HISTORY_FILE), TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_GENERIC_TITLE), JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_MenuHistoryActionPerformed

    private void MenuClearActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MenuClearActionPerformed
        Clear(true);
    }//GEN-LAST:event_MenuClearActionPerformed

    private void MenuSaveWithNameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MenuSaveWithNameActionPerformed
        SaveWithName();
    }//GEN-LAST:event_MenuSaveWithNameActionPerformed

    private void MenuSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MenuSaveActionPerformed
        saveFile();
    }//GEN-LAST:event_MenuSaveActionPerformed
    
    private void MenuNewActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MenuNewActionPerformed
        NewBackup();
    }//GEN-LAST:event_MenuNewActionPerformed
    
    private void SingleBackupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SingleBackupActionPerformed
        SingleBackup(startPathField.getText(), destinationPathField.getText());
    }//GEN-LAST:event_SingleBackupActionPerformed
    
    private void EditPoputItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EditPoputItemActionPerformed
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName);

            logger.info("Edit row : " + selectedRow);
            OpenBackup(backup.getBackupName());
            TabbedPane.setSelectedIndex(0);
        }
    }//GEN-LAST:event_EditPoputItemActionPerformed

    private void DeletePopupItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_DeletePopupItemActionPerformed
        deleteBackup();
    }//GEN-LAST:event_DeletePopupItemActionPerformed

    private void researchFieldKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_researchFieldKeyTyped
        researchInTable();
    }//GEN-LAST:event_researchFieldKeyTyped

    private void deleteBackup() {
        logger.info("Event --> deleting backup");
        
        if (selectedRow != -1) {
            int response = JOptionPane.showConfirmDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_MESSAGE_BEFORE_DELETE_BACKUP), TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_REQUIRED_TITLE), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (response == JOptionPane.YES_OPTION) {
                // get correct backup
                String backupName = (String) backupTable.getValueAt(selectedRow, 0);
                Backup backup = backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName);

                RemoveBackup(backup.getBackupName());
            }
        }
    }

    private void deleteBackup(int selectedRow, boolean isConfermationRequired) {
        logger.info("Event --> deleting backup");
        
        if (isConfermationRequired) {
            int response = JOptionPane.showConfirmDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_MESSAGE_BEFORE_DELETE_BACKUP), TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_REQUIRED_TITLE), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (response != JOptionPane.YES_OPTION) {
                return;
            }
        }

        // get correct backup
        String backupName = (String) backupTable.getValueAt(selectedRow, 0);
        Backup backup = backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName);

        RemoveBackup(backup.getBackupName());
    }

    private void tableMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tableMouseClicked
        selectedRow = table.rowAtPoint(evt.getPoint()); // get index of the row

        if (selectedRow == -1) { // if clicked outside valid rows
            table.clearSelection(); // deselect any selected row
            detailsLabel.setText(""); // clear the label
        } else {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            backupmanager.Entities.Backup backup = backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName);

            logger.debug("Selected backup: " + backupName);

            // Handling right mouse button click
            if (SwingUtilities.isRightMouseButton(evt)) {
                logger.info("Right click on row: " + selectedRow);
                AutoBackupMenuItem.setSelected(backup.isAutoBackup());
                table.setRowSelectionInterval(selectedRow, selectedRow); // select clicked row
                TablePopup.show(evt.getComponent(), evt.getX(), evt.getY()); // show popup

                // check if the backup is running
                if (RunningBackups.readBackupFromJSON(backupName) == null) {
                    DeletePopupItem.setEnabled(true);
                    interruptBackupPopupItem.setEnabled(false);
                } else {
                    DeletePopupItem.setEnabled(false);
                    interruptBackupPopupItem.setEnabled(true);
                }
            }

            // Handling left mouse button double-click
            else if (SwingUtilities.isLeftMouseButton(evt) && evt.getClickCount() == 2) {
                logger.info("Double-click on row: " + selectedRow);
                OpenBackup(backupName);
                TabbedPane.setSelectedIndex(0);
            }

            // Handling single left mouse button click
            else if (SwingUtilities.isLeftMouseButton(evt)) {
                String backupNameStr = TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.BACKUP_NAME_DETAIL);
                String initialPathStr = TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.INITIAL_PATH_DETAIL);
                String destinationPathStr = TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.DESTINATION_PATH_DETAIL);
                String lastBackupStr = TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.LAST_BACKUP_DETAIL);
                String nextBackupStr = TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.NEXT_BACKUP_DATE_DETAIL);
                String timeIntervalBackupStr = TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.TIME_INTERVAL_DETAIL);
                String creationDateStr = TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.CREATION_DATE_DETAIL);
                String lastUpdateDateStr = TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.LAST_UPDATE_DATE_DETAIL);
                String backupCountStr = TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.BACKUP_COUNT_DETAIL);
                String notesStr = TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.NOTES_DETAIL);
                String maxBackupsToKeepStr = TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.MAX_BACKUPS_TO_KEEP_DETAIL);

                detailsLabel.setText(
                    "<html><b>" + backupNameStr + ":</b> " + backup.getBackupName() + ", " +
                    "<b>" + initialPathStr + ":</b> " + backup.getInitialPath() + ", " +
                    "<b>" + destinationPathStr + ":</b> " + backup.getDestinationPath() + ", " +
                    "<b>" + lastBackupStr + ":</b> " + (backup.getLastBackup() != null ? backup.getLastBackup().format(formatter) : "") + ", " +
                    "<b>" + nextBackupStr + ":</b> " + (backup.getNextDateBackup() != null ? backup.getNextDateBackup().format(formatter) : "_") + ", " +
                    "<b>" + timeIntervalBackupStr + ":</b> " + (backup.getTimeIntervalBackup() != null ? backup.getTimeIntervalBackup().toString() : "_") + ", " +
                    "<b>" + creationDateStr + ":</b> " + (backup.getCreationDate() != null ? backup.getCreationDate().format(formatter) : "_") + ", " +
                    "<b>" + lastUpdateDateStr + ":</b> " + (backup.getLastUpdateDate() != null ? backup.getLastUpdateDate().format(formatter) : "_") + ", " +
                    "<b>" + backupCountStr + ":</b> " + (backup.getBackupCount()) + ", " +
                    "<b>" + maxBackupsToKeepStr + ":</b> " + (backup.getMaxBackupsToKeep()) + ", " +
                    "<b>" + notesStr + ":</b> " + (backup.getNotes()) +
                    "</html>"
                );
            }
        }
    }//GEN-LAST:event_tableMouseClicked

    private void tablePanelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tablePanelMouseClicked
        table.clearSelection(); // deselect any selected row
        detailsLabel.setText(""); // clear the label
    }//GEN-LAST:event_tablePanelMouseClicked

    private void DuplicatePopupItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_DuplicatePopupItemActionPerformed
        logger.info("Event --> duplicating backup");
        
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName);

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
            BackupOperations.updateBackupList(backups);
        }
    }//GEN-LAST:event_DuplicatePopupItemActionPerformed

    private void RunBackupPopupItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RunBackupPopupItemActionPerformed
        if (selectedRow != -1) {
            
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName);
            
            progressBar = new BackupProgressGUI(backup.getInitialPath(), backup.getDestinationPath());

            ZippingContext context = new ZippingContext(backup, null, backupTable, progressBar, SingleBackup, toggleAutoBackup, interruptBackupPopupItem, RunBackupPopupItem);
            BackupOperations.SingleBackup(context);
            
            // if the backup is currentBackup
            if (currentBackup.getBackupName().equals(backup.getBackupName())) 
                currentBackup.UpdateBackup(backup);
            }
    }//GEN-LAST:event_RunBackupPopupItemActionPerformed

    private void CopyBackupNamePopupItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CopyBackupNamePopupItemActionPerformed
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName);

            StringSelection selection = new StringSelection(backup.getBackupName());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }//GEN-LAST:event_CopyBackupNamePopupItemActionPerformed

    private void CopyInitialPathPopupItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CopyInitialPathPopupItemActionPerformed
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName);

            StringSelection selection = new StringSelection(backup.getInitialPath());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }//GEN-LAST:event_CopyInitialPathPopupItemActionPerformed

    private void CopyDestinationPathPopupItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CopyDestinationPathPopupItemActionPerformed
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName);

            StringSelection selection = new StringSelection(backup.getDestinationPath());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }//GEN-LAST:event_CopyDestinationPathPopupItemActionPerformed

    private void AutoBackupMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AutoBackupMenuItemActionPerformed
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName);

            boolean res = !backup.isAutoBackup();
            setAutoBackupPreference(backup, res);
            AutoBackupMenuItem.setSelected(res);
            if (res) {
                AutomaticBackup(backup);
            }
        }
    }//GEN-LAST:event_AutoBackupMenuItemActionPerformed

    private void OpenInitialFolderItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_OpenInitialFolderItemActionPerformed
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName);

            OpenFolder(backup.getInitialPath());
        }
    }//GEN-LAST:event_OpenInitialFolderItemActionPerformed

    private void OpenInitialDestinationItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_OpenInitialDestinationItemActionPerformed
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName);

            OpenFolder(backup.getDestinationPath());
        }
    }//GEN-LAST:event_OpenInitialDestinationItemActionPerformed

    private void renamePopupItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_renamePopupItemActionPerformed
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            Backup backup = backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName);

            renameBackup(backup);
        }
    }//GEN-LAST:event_renamePopupItemActionPerformed

    private void MenuDonateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MenuDonateActionPerformed
        logger.info("Event --> donate");
        openWebSite(ConfigKey.DONATE_PAGE_LINK.getValue());
    }//GEN-LAST:event_MenuDonateActionPerformed

    private void MenuBugReportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MenuBugReportActionPerformed
        logger.info("Event --> bug report");
        openWebSite(ConfigKey.ISSUE_PAGE_LINK.getValue());
    }//GEN-LAST:event_MenuBugReportActionPerformed

    private void MenuShareActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MenuShareActionPerformed
        logger.info("Event --> share");

        // pop-up message
        JOptionPane.showMessageDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.SHARE_LINK_COPIED_MESSAGE));

        // copy link to the clipboard
        StringSelection stringSelectionObj = new StringSelection(ConfigKey.SHARE_LINK.getValue());
        Clipboard clipboardObj = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboardObj.setContents(stringSelectionObj, null);
    }//GEN-LAST:event_MenuShareActionPerformed
    
    private void toggleAutoBackupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleAutoBackupActionPerformed
        logger.info("Event --> Changing auto backup preference");

        // checks
        if (!BackupOperations.CheckInputCorrect(currentBackup.getBackupName(),startPathField.getText(), destinationPathField.getText(), null)) {
            setAutoBackupOff();
            return;
        }
        if (currentBackup.isAutoBackup()) {
            int response = JOptionPane.showConfirmDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_MESSAGE_CANCEL_AUTO_BACKUP), TranslationCategory.DIALOGS.getTranslation(TranslationKey.CONFIRMATION_REQUIRED_TITLE), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (response != JOptionPane.YES_OPTION) {
                setAutoBackupOff();
                return;
            } 
        }
                    
        boolean enabled = toggleAutoBackup.isSelected();
        if (enabled && AutomaticBackup()) {
            logger.info("Event --> Auto Backup setted to Enabled");
            setAutoBackupOn(currentBackup);
        }
        else {
            logger.info("Event --> Auto Backup setted to Disabled");
            disableAutoBackup(currentBackup);
            setAutoBackupOff();
            return;
        }
        
        currentBackup.setAutoBackup(enabled);
        BackupOperations.updateBackupList(backups);
    }//GEN-LAST:event_toggleAutoBackupActionPerformed

    private void MenuWebsiteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MenuWebsiteActionPerformed
        logger.info("Event --> shard website");
        openWebSite(ConfigKey.SHARD_WEBSITE.getValue());
    }//GEN-LAST:event_MenuWebsiteActionPerformed

    private void MenuSupportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MenuSupportActionPerformed
        logger.info("Event --> support");
        
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();

            if (desktop.isSupported(Desktop.Action.MAIL)) {
                String subject = "Support - Backup Manager";
                String mailTo = "mailto:" + ConfigKey.EMAIL.getValue() + "?subject=" + encodeURI(subject);

                try {
                    URI uri = new URI(mailTo);
                    desktop.mail(uri);
                } catch (IOException | URISyntaxException ex) {
                    logger.error("Failed to send email: " + ex.getMessage(), ex);
                    JOptionPane.showMessageDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_MESSAGE_UNABLE_TO_SEND_EMAIL), TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_GENERIC_TITLE), JOptionPane.ERROR_MESSAGE);
                }
            } else {
                logger.warn("Mail action is unsupported in your system's desktop environment.");
                JOptionPane.showMessageDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_MESSAGE_NOT_SUPPORTED_EMAIL), TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_GENERIC_TITLE), JOptionPane.ERROR_MESSAGE);
            }
        } else {
            logger.warn("Desktop integration is unsupported on this system.");
            JOptionPane.showMessageDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_MESSAGE_NOT_SUPPORTED_EMAIL_GENERIC), TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_GENERIC_TITLE), JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_MenuSupportActionPerformed
    
    private void MenuInfoPageActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MenuInfoPageActionPerformed
        logger.info("Event --> shard website");
        openWebSite(ConfigKey.INFO_PAGE_LINK.getValue());
    }//GEN-LAST:event_MenuInfoPageActionPerformed

    private void MenuPreferencesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MenuPreferencesActionPerformed
        openPreferences();
    }//GEN-LAST:event_MenuPreferencesActionPerformed

    private void MenuImportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MenuImportActionPerformed
        logger.info("Event --> importing backup list");

        JFileChooser jfc = new JFileChooser(ConfigKey.RES_DIRECTORY_STRING.getValue());
        jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);

        FileNameExtensionFilter jsonFilter = new FileNameExtensionFilter("JSON Files (*.json)", "json");
        jfc.setFileFilter(jsonFilter);
        int returnValue = jfc.showSaveDialog(null);

        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = jfc.getSelectedFile();
            if (selectedFile.isFile() && selectedFile.getName().toLowerCase().endsWith(".json")) {
                logger.info("File imported: " + selectedFile);

                Preferences.setBackupList(new BackupList(selectedFile.getParent()+File.separator, selectedFile.getName()));
                Preferences.updatePreferencesToJSON();

                try {
                    backups = JSON.readBackupListFromJSON(Preferences.getBackupList().getDirectory(), Preferences.getBackupList().getFile());
                    TableDataManager.updateTableWithNewBackupList(backups, formatter);
                } catch (IOException ex) {
                    logger.error("An error occurred: " + ex.getMessage(), ex);
                }

                JOptionPane.showMessageDialog(this, TranslationCategory.DIALOGS.getTranslation(TranslationKey.BACKUP_LIST_CORRECTLY_IMPORTED_MESSAGE), TranslationCategory.DIALOGS.getTranslation(TranslationKey.BACKUP_LIST_CORRECTLY_IMPORTED_TITLE), JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_MESSAGE_FOR_WRONG_FILE_EXTENSION_MESSAGE), TranslationCategory.DIALOGS.getTranslation(TranslationKey.ERROR_MESSAGE_FOR_WRONG_FILE_EXTENSION_TITLE), JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_MenuImportActionPerformed

    private void MenuExportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MenuExportActionPerformed
        logger.info("Event --> exporting backup list");

        Path desktopPath = Paths.get(System.getProperty("user.home"), "Desktop", Preferences.getBackupList().getFile());
        Path sourcePath = Paths.get(Preferences.getBackupList().getDirectory() + Preferences.getBackupList().getFile());

        try {
            Files.copy(sourcePath, desktopPath, StandardCopyOption.REPLACE_EXISTING);
            JOptionPane.showMessageDialog(null, TranslationCategory.DIALOGS.getTranslation(TranslationKey.BACKUP_LIST_CORRECTLY_EXPORTED_MESSAGE), TranslationCategory.DIALOGS.getTranslation(TranslationKey.BACKUP_LIST_CORRECTLY_EXPORTED_TITLE), JOptionPane.INFORMATION_MESSAGE);
        } catch (java.nio.file.NoSuchFileException ex) {
            logger.error("Source file not found: " + ex.getMessage());
            JOptionPane.showMessageDialog(null, "Error: The source file was not found.\nPlease check the file path.", "Export Error", JOptionPane.ERROR_MESSAGE);
        } catch (java.nio.file.AccessDeniedException ex) {
            logger.error("Access denied to desktop: " + ex.getMessage());
            JOptionPane.showMessageDialog(null, "Error: Access to the Desktop is denied.\nPlease check folder permissions and try again.","Export Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException ex) {
            logger.error("Unexpected error: " + ex.getMessage());
            openExceptionMessage(ex.getMessage(), Arrays.toString(ex.getStackTrace()));
        }
    }//GEN-LAST:event_MenuExportActionPerformed

    private void maxBackupCountSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_maxBackupCountSpinnerStateChanged
        maxBackupCountSpinnerChange();
    }//GEN-LAST:event_maxBackupCountSpinnerStateChanged

    private void maxBackupCountSpinnerMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_maxBackupCountSpinnerMouseWheelMoved
        mouseWeel(evt);
    }//GEN-LAST:event_maxBackupCountSpinnerMouseWheelMoved

    private void interruptBackupPopupItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_interruptBackupPopupItemActionPerformed
        if (selectedRow != -1) {
            // get correct backup
            String backupName = (String) backupTable.getValueAt(selectedRow, 0);
            backupmanager.Entities.Backup backup = backupmanager.Entities.Backup.getBackupByName(new ArrayList<>(backups), backupName);
            ZippingContext context = new ZippingContext(backup, null, backupTable, progressBar, SingleBackup, toggleAutoBackup, interruptBackupPopupItem, RunBackupPopupItem);
            BackupOperations.interruptBackupProcess(context);
        }
    }//GEN-LAST:event_interruptBackupPopupItemActionPerformed

    private void btnPathSearch1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPathSearch1ActionPerformed
        logger.debug("File chooser: " + startPathField.getName() + ", files allowed: " + true);
        String text = BackupOperations.pathSearchWithFileChooser(true);
        if (text != null) {
            startPathField.setText(text);
            savedChanges(false);
        }
    }//GEN-LAST:event_btnPathSearch1ActionPerformed

    private void exportAsCsvBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportAsCsvBtnActionPerformed
        Exporter.exportAsCSV(new ArrayList<>(backups), backupmanager.Entities.Backup.getCSVHeader());
    }//GEN-LAST:event_exportAsCsvBtnActionPerformed

    private void exportAsPdfBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportAsPdfBtnActionPerformed
        Exporter.exportAsPDF(new ArrayList<>(backups), backupmanager.Entities.Backup.getCSVHeader());
    }//GEN-LAST:event_exportAsPdfBtnActionPerformed

    private void addBackupEntryButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addBackupEntryButtonActionPerformed
        TabbedPane.setSelectedIndex(0);
        NewBackup();
    }//GEN-LAST:event_addBackupEntryButtonActionPerformed

    private void btnTimePickerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnTimePickerActionPerformed
        TimeInterval timeInterval = openTimePicker(currentBackup.getTimeIntervalBackup());
        if (timeInterval == null) return;

        btnTimePicker.setToolTipText(timeInterval.toString());
        LocalDateTime nextDateBackup = LocalDateTime.now().plusDays(timeInterval.getDays())
        .plusHours(timeInterval.getHours())
        .plusMinutes(timeInterval.getMinutes());

        currentBackup.setTimeIntervalBackup(timeInterval);
        currentBackup.setNextDateBackup(nextDateBackup);
        currentBackup.setInitialPath(GetStartPathField());
        currentBackup.setDestinationPath(GetDestinationPathField());
        for (Backup b : backups) {
            if (b.getBackupName().equals(currentBackup.getBackupName())) {
                b.UpdateBackup(currentBackup);
                break;
            }
        }
        BackupOperations.updateBackupList(backups);

        openBackupActivationMessage(timeInterval, null);
    }//GEN-LAST:event_btnTimePickerActionPerformed

    private void btnPathSearch2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPathSearch2ActionPerformed
        logger.debug("File chooser: " + destinationPathField.getName() + ", files allowed: " + false);
        String text = BackupOperations.pathSearchWithFileChooser(false);
        if (text != null) {
            destinationPathField.setText(text);
            savedChanges(false);
        }
    }//GEN-LAST:event_btnPathSearch2ActionPerformed

    private void setTranslations() {
        // update table translations
        if (backups != null)
            displayBackupList(backups);

        backupOnText = TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.AUTO_BACKUP_BUTTON_ON);
        backupOffText = TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.AUTO_BACKUP_BUTTON_OFF);

        // general
        jLabel3.setText(TranslationCategory.GENERAL.getTranslation(TranslationKey.VERSION) + " " + currentVersion);
        
        // menu
        jMenu1.setText(TranslationCategory.MENU.getTranslation(TranslationKey.FILE));
        jMenu2.setText(TranslationCategory.MENU.getTranslation(TranslationKey.OPTIONS));
        jMenu3.setText(TranslationCategory.MENU.getTranslation(TranslationKey.ABOUT));
        jMenu5.setText(TranslationCategory.MENU.getTranslation(TranslationKey.HELP));

        // menu items
        MenuBugReport.setText(TranslationCategory.MENU.getTranslation(TranslationKey.BUG_REPORT));
        MenuClear.setText(TranslationCategory.MENU.getTranslation(TranslationKey.CLEAR));
        MenuDonate.setText(TranslationCategory.MENU.getTranslation(TranslationKey.DONATE));
        MenuHistory.setText(TranslationCategory.MENU.getTranslation(TranslationKey.HISTORY));
        MenuInfoPage.setText(TranslationCategory.MENU.getTranslation(TranslationKey.INFO_PAGE));
        MenuNew.setText(TranslationCategory.MENU.getTranslation(TranslationKey.NEW));
        MenuQuit.setText(TranslationCategory.MENU.getTranslation(TranslationKey.QUIT));
        MenuSave.setText(TranslationCategory.MENU.getTranslation(TranslationKey.SAVE));
        MenuSaveWithName.setText(TranslationCategory.MENU.getTranslation(TranslationKey.SAVE_WITH_NAME));
        MenuPreferences.setText(TranslationCategory.MENU.getTranslation(TranslationKey.PREFERENCES));
        MenuImport.setText(TranslationCategory.MENU.getTranslation(TranslationKey.IMPORT));
        MenuExport.setText(TranslationCategory.MENU.getTranslation(TranslationKey.EXPORT));
        MenuShare.setText(TranslationCategory.MENU.getTranslation(TranslationKey.SHARE));
        MenuSupport.setText(TranslationCategory.MENU.getTranslation(TranslationKey.SUPPORT));
        MenuWebsite.setText(TranslationCategory.MENU.getTranslation(TranslationKey.WEBSITE));

        // backup entry
        TabbedPane.setTitleAt(0, TranslationCategory.TABBED_FRAMES.getTranslation(TranslationKey.BACKUP_ENTRY));
        TabbedPane.setTitleAt(1, TranslationCategory.TABBED_FRAMES.getTranslation(TranslationKey.BACKUP_LIST));
        btnPathSearch1.setToolTipText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.INITIAL_FILE_CHOOSER_TOOLTIP));
        btnPathSearch2.setToolTipText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.DESTINATION_FILE_CHOOSER_TOOLTIP));
        startPathField.setToolTipText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.INITIAL_PATH_TOOLTIP));
        destinationPathField.setToolTipText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.DESTINATION_PATH_TOOLTIP));
        backupNoteTextArea.setToolTipText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.NOTES_TOOLTIP));
        SingleBackup.setText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.SINGLE_BACKUP_BUTTON));
        SingleBackup.setToolTipText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.SINGLE_BACKUP_TOOLTIP));
        toggleAutoBackup.setText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.AUTO_BACKUP_BUTTON_OFF));
        toggleAutoBackup.setToolTipText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.AUTO_BACKUP_TOOLTIP));
        currentFileLabel.setText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.CURRENT_FILE) + ":");
        jLabel2.setText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.NOTES) + ":");
        lastBackupLabel.setText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.LAST_BACKUP) + ": ");
        txtTitle.setText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.PAGE_TITLE));
        startPathField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.INITIAL_PATH_PLACEHOLDER));
        destinationPathField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.DESTINATION_PATH_PLACEHOLDER));
        btnTimePicker.setToolTipText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.TIME_PICKER_TOOLTIP));
        maxBackupCountSpinner.setToolTipText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.MAX_BACKUPS_TO_KEEP_TOOLTIP) + "\n" + TranslationCategory.TIME_PICKER_DIALOG.getTranslation(TranslationKey.SPINNER_TOOLTIP));
        jLabel4.setText(TranslationCategory.BACKUP_ENTRY.getTranslation(TranslationKey.MAX_BACKUPS_TO_KEEP));

        // backup list
        ExportLabel.setText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.EXPORT_AS));
        addBackupEntryButton.setToolTipText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.ADD_BACKUP_TOOLTIP));
        exportAsPdfBtn.setToolTipText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.EXPORT_AS_PDF_TOOLTIP));
        exportAsCsvBtn.setToolTipText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.EXPORT_AS_CSV_TOOLTIP));
        researchField.setToolTipText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.RESEARCH_BAR_TOOLTIP));
        researchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.RESEARCH_BAR_PLACEHOLDER));

        // popup
        CopyBackupNamePopupItem.setText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.COPY_BACKUP_NAME_POPUP));
        CopyDestinationPathPopupItem.setText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.COPY_DESTINATION_PATH_BACKUP));
        RunBackupPopupItem.setText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.SINGLE_BACKUP_POPUP));
        CopyInitialPathPopupItem.setText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.COPY_INITIAL_PATH_POPUP));
        DeletePopupItem.setText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.DELETE_POPUP));
        interruptBackupPopupItem.setText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.INTERRUPT_POPUP));
        DuplicatePopupItem.setText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.DUPLICATE_POPUP));
        EditPoputItem.setText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.EDIT_POPUP));
        OpenInitialDestinationItem.setText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.OPEN_DESTINATION_FOLDER_POPUP));
        OpenInitialFolderItem.setText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.OPEN_INITIAL_FOLDER_POPUP));
        renamePopupItem.setText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.RENAME_BACKUP_POPUP));
        jMenu4.setText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.COPY_TEXT_POPUP));
        AutoBackupMenuItem.setText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.AUTO_BACKUP_POPUP));
        Backup.setText(TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.BACKUP_POPUP));
    }

    private String[] getColumnTranslations() {
        String[] columnNames = {
            TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.BACKUP_NAME_COLUMN),
            TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.INITIAL_PATH_COLUMN),
            TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.DESTINATION_PATH_COLUMN),
            TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.LAST_BACKUP_COLUMN),
            TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.AUTOMATIC_BACKUP_COLUMN),
            TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.NEXT_BACKUP_DATE_COLUMN),
            TranslationCategory.BACKUP_LIST.getTranslation(TranslationKey.TIME_INTERVAL_COLUMN)
        };
        return columnNames;
    }

    private void initializeTable() {
        try {
            backups = JSON.readBackupListFromJSON(Preferences.getBackupList().getDirectory(), Preferences.getBackupList().getFile());
            displayBackupList(backups);
        } catch (IOException ex) {
            backups = null;
            logger.error("An error occurred: " + ex.getMessage(), ex);
            openExceptionMessage(ex.getMessage(), Arrays.toString(ex.getStackTrace()));
        }
    }
    
    private void setSvgImages() {
        btnPathSearch1.setSvgImage("res/img/folder.svg", 30, 30);
        btnPathSearch2.setSvgImage("res/img/folder.svg", 30, 30);
        exportAsCsvBtn.setSvgImage("res/img/csv.svg", 30, 30);
        exportAsPdfBtn.setSvgImage("res/img/pdf.svg", 30, 30);
        addBackupEntryButton.setSvgImage("res/img/add.svg", 30, 30);
        btnTimePicker.setSvgImage("res/img/timer.svg", 30, 30);
        MenuSave.setSvgImage("res/img/save.svg", 16, 16);
        MenuSaveWithName.setSvgImage("res/img/save_as.svg", 16, 16);
        MenuImport.setSvgImage("res/img/import.svg", 16, 16);
        MenuExport.setSvgImage("res/img/export.svg", 16, 16);
        MenuNew.setSvgImage("res/img/new_file.svg", 16, 16);
        MenuBugReport.setSvgImage("res/img/bug.svg", 16, 16);
        MenuClear.setSvgImage("res/img/clear.svg", 16, 16);
        MenuHistory.setSvgImage("res/img/history.svg", 16, 16);
        MenuDonate.setSvgImage("res/img/donate.svg", 16, 16);
        MenuPreferences.setSvgImage("res/img/settings.svg", 16, 16);
        MenuShare.setSvgImage("res/img/share.svg", 16, 16);
        MenuSupport.setSvgImage("res/img/support.svg", 16, 16);
        MenuWebsite.setSvgImage("res/img/website.svg", 16, 16);
        MenuQuit.setSvgImage("res/img/quit.svg", 16, 16);
        MenuInfoPage.setSvgImage("res/img/info.svg", 16, 16);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBoxMenuItem AutoBackupMenuItem;
    private javax.swing.JMenu Backup;
    private javax.swing.JMenuItem CopyBackupNamePopupItem;
    private javax.swing.JMenuItem CopyDestinationPathPopupItem;
    private javax.swing.JMenuItem CopyInitialPathPopupItem;
    private javax.swing.JMenuItem DeletePopupItem;
    private javax.swing.JMenuItem DuplicatePopupItem;
    private javax.swing.JMenuItem EditPoputItem;
    private javax.swing.JLabel ExportLabel;
    private backupmanager.svg.SVGMenuItem MenuBugReport;
    private backupmanager.svg.SVGMenuItem MenuClear;
    private backupmanager.svg.SVGMenuItem MenuDonate;
    private backupmanager.svg.SVGMenuItem MenuExport;
    private backupmanager.svg.SVGMenuItem MenuHistory;
    private backupmanager.svg.SVGMenuItem MenuImport;
    private backupmanager.svg.SVGMenuItem MenuInfoPage;
    private backupmanager.svg.SVGMenuItem MenuNew;
    private backupmanager.svg.SVGMenuItem MenuPreferences;
    private backupmanager.svg.SVGMenuItem MenuQuit;
    private backupmanager.svg.SVGMenuItem MenuSave;
    private backupmanager.svg.SVGMenuItem MenuSaveWithName;
    private backupmanager.svg.SVGMenuItem MenuShare;
    private backupmanager.svg.SVGMenuItem MenuSupport;
    private backupmanager.svg.SVGMenuItem MenuWebsite;
    private javax.swing.JMenuItem OpenInitialDestinationItem;
    private javax.swing.JMenuItem OpenInitialFolderItem;
    private javax.swing.JMenuItem RunBackupPopupItem;
    private javax.swing.JButton SingleBackup;
    private javax.swing.JTabbedPane TabbedPane;
    private javax.swing.JPopupMenu TablePopup;
    private backupmanager.svg.SVGButton addBackupEntryButton;
    private javax.swing.JTextArea backupNoteTextArea;
    private backupmanager.svg.SVGButton btnPathSearch1;
    private backupmanager.svg.SVGButton btnPathSearch2;
    private backupmanager.svg.SVGButton btnTimePicker;
    private javax.swing.JLabel currentFileLabel;
    private javax.swing.JTextField destinationPathField;
    private javax.swing.JLabel detailsLabel;
    private javax.swing.JPanel detailsPanel;
    private backupmanager.svg.SVGButton exportAsCsvBtn;
    private backupmanager.svg.SVGButton exportAsPdfBtn;
    private javax.swing.Box.Filler filler1;
    private javax.swing.JMenuItem interruptBackupPopupItem;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenu jMenu3;
    private javax.swing.JMenu jMenu4;
    private javax.swing.JMenu jMenu5;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JPopupMenu.Separator jSeparator4;
    private javax.swing.JPopupMenu.Separator jSeparator5;
    private javax.swing.JLabel lastBackupLabel;
    private javax.swing.JSpinner maxBackupCountSpinner;
    private javax.swing.JMenuItem renamePopupItem;
    private javax.swing.JTextField researchField;
    private javax.swing.JTextField startPathField;
    private javax.swing.JTable table;
    private javax.swing.JPanel tablePanel;
    private javax.swing.JToggleButton toggleAutoBackup;
    private javax.swing.JLabel txtTitle;
    // End of variables declaration//GEN-END:variables
}